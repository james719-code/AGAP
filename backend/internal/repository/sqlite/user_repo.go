package sqlite

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type sqliteUserRepo struct {
	db *sql.DB
}

func NewUserRepo(db *sql.DB) repository.UserRepository {
	return &sqliteUserRepo{db: db}
}

func (r *sqliteUserRepo) CreateUser(ctx context.Context, user *domain.User) (*domain.User, error) {
	now := time.Now().UTC()
	nowStr := now.Format(time.RFC3339Nano)

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	query := `
		INSERT INTO users (
			username, password_hash, role, device_id, agency_id, address_id,
			name, birthday, gender, phone_number, email, medical_info, national_id, account_status, is_verified, image_url,
			created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	isVerifiedInt := 0
	if user.IsVerified {
		isVerifiedInt = 1
	}
	if user.AgencyID != nil && *user.AgencyID > 0 {
		var exists int
		if err := tx.QueryRowContext(ctx, "SELECT 1 FROM agencies WHERE id = ?", *user.AgencyID).Scan(&exists); err != nil || exists != 1 {
			return nil, fmt.Errorf("agency with ID %d does not exist", *user.AgencyID)
		}
	}

	if user.AccountStatus == "" {
		user.AccountStatus = domain.AccountStatusPending
	}

	res, err := tx.ExecContext(ctx, query,
		user.Username, user.PasswordHash, string(user.Role), user.DeviceID, user.AgencyID, user.AddressID,
		user.Name, user.Birthday, user.Gender, user.PhoneNumber, user.Email, user.MedicalInfo, user.NationalID, string(user.AccountStatus), isVerifiedInt, user.ImageURL,
		nowStr, nowStr,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	// A hardware ID can belong to only one account. Do not silently ignore a
	// collision because incident ownership depends on this association.
	if user.DeviceID != "" {
		if _, err := tx.ExecContext(ctx,
			"INSERT INTO user_devices (user_id, device_hardware_id, created_at) VALUES (?, ?, ?)",
			id, user.DeviceID, nowStr,
		); err != nil {
			return nil, fmt.Errorf("failed to link user device (device may already belong to another account): %w", err)
		}
	}

	if user.RescueUnitID != nil && *user.RescueUnitID > 0 {
		if _, err := tx.ExecContext(ctx,
			"INSERT OR IGNORE INTO rescue_unit_members (rescue_unit_id, responder_id, is_leader, assigned_at) VALUES (?, ?, 0, ?)",
			*user.RescueUnitID, id, nowStr,
		); err != nil {
			return nil, fmt.Errorf("failed to link rescue unit membership: %w", err)
		}
	}

	if err := tx.Commit(); err != nil {
		return nil, err
	}

	user.ID = id
	user.CreatedAt = now
	user.UpdatedAt = now
	if user.DeviceID != "" {
		user.Devices = append(user.Devices, domain.UserDevice{
			UserID:           id,
			DeviceHardwareID: user.DeviceID,
			CreatedAt:        now,
		})
	}
	return user, nil
}

func (r *sqliteUserRepo) GetUserByID(ctx context.Context, id int64) (*domain.User, error) {
	query := `
		SELECT id, username, password_hash, role, device_id, agency_id, address_id,
		       name, birthday, gender, phone_number, email, medical_info, national_id, account_status, is_verified, image_url, last_login_at,
		       created_at, updated_at
		FROM users WHERE id = ?
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var u domain.User
	var roleStr, accountStatusStr, crTs, upTs string
	var lastLoginTs sql.NullString
	var isVerifiedInt int
	err := row.Scan(
		&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &u.AddressID,
		&u.Name, &u.Birthday, &u.Gender, &u.PhoneNumber, &u.Email, &u.MedicalInfo, &u.NationalID, &accountStatusStr, &isVerifiedInt, &u.ImageURL, &lastLoginTs,
		&crTs, &upTs,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	u.Role = domain.Role(roleStr)
	u.AccountStatus = domain.AccountStatus(accountStatusStr)
	u.IsVerified = isVerifiedInt == 1
	if lastLoginTs.Valid && lastLoginTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, lastLoginTs.String)
		u.LastLoginAt = &t
	}
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.Devices = make([]domain.UserDevice, 0)
	u.EmergencyContacts = make([]domain.EmergencyContact, 0)

	// Fetch Devices
	devRows, err := r.db.QueryContext(ctx, "SELECT id, user_id, device_hardware_id, device_name, platform, last_seen_at, created_at FROM user_devices WHERE user_id = ?", u.ID)
	if err == nil {
		defer devRows.Close()
		for devRows.Next() {
			var d domain.UserDevice
			var dTs, lsTs string
			if scanErr := devRows.Scan(&d.ID, &d.UserID, &d.DeviceHardwareID, &d.DeviceName, &d.Platform, &lsTs, &dTs); scanErr == nil {
				d.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lsTs)
				d.CreatedAt, _ = time.Parse(time.RFC3339Nano, dTs)
				u.Devices = append(u.Devices, d)
			}
		}
	}

	// Fetch Emergency Contacts
	ecRows, err := r.db.QueryContext(ctx, "SELECT id, user_id, contact_name, phone_number, relationship, is_primary, created_at FROM user_emergency_contacts WHERE user_id = ? ORDER BY is_primary DESC, id ASC", u.ID)
	if err == nil {
		defer ecRows.Close()
		for ecRows.Next() {
			var ec domain.EmergencyContact
			var ecTs string
			var isPrimaryInt int
			if scanErr := ecRows.Scan(&ec.ID, &ec.UserID, &ec.ContactName, &ec.PhoneNumber, &ec.Relationship, &isPrimaryInt, &ecTs); scanErr == nil {
				ec.IsPrimary = isPrimaryInt == 1
				ec.CreatedAt, _ = time.Parse(time.RFC3339Nano, ecTs)
				u.EmergencyContacts = append(u.EmergencyContacts, ec)
			}
		}
	}

	return &u, nil
}

func (r *sqliteUserRepo) GetUserByUsername(ctx context.Context, username string) (*domain.User, error) {
	query := `SELECT id FROM users WHERE username = ?`
	var id int64
	err := r.db.QueryRowContext(ctx, query, username).Scan(&id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	return r.GetUserByID(ctx, id)
}

func (r *sqliteUserRepo) ListUsers(ctx context.Context, role *domain.Role) ([]*domain.User, error) {
	var query string
	var args []interface{}

	baseQuery := `SELECT id, username, password_hash, role, device_id, agency_id, address_id, name, birthday, gender, phone_number, email, medical_info, national_id, account_status, is_verified, image_url, last_login_at, created_at, updated_at FROM users`

	if role != nil && *role != "" {
		query = baseQuery + ` WHERE role = ? ORDER BY name ASC`
		args = append(args, string(*role))
	} else {
		query = baseQuery + ` ORDER BY name ASC`
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	users := make([]*domain.User, 0)
	for rows.Next() {
		var u domain.User
		var roleStr, accountStatusStr, crTs, upTs string
		var lastLoginTs sql.NullString
		var isVerifiedInt int

		if err := rows.Scan(
			&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &u.AddressID,
			&u.Name, &u.Birthday, &u.Gender, &u.PhoneNumber, &u.Email, &u.MedicalInfo, &u.NationalID, &accountStatusStr, &isVerifiedInt, &u.ImageURL, &lastLoginTs,
			&crTs, &upTs,
		); err != nil {
			return nil, err
		}

		u.Role = domain.Role(roleStr)
		u.AccountStatus = domain.AccountStatus(accountStatusStr)
		u.IsVerified = isVerifiedInt == 1
		if lastLoginTs.Valid && lastLoginTs.String != "" {
			t, _ := time.Parse(time.RFC3339Nano, lastLoginTs.String)
			u.LastLoginAt = &t
		}
		u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
		users = append(users, &u)
	}

	return users, nil
}

func (r *sqliteUserRepo) VerifyUserAccount(ctx context.Context, id int64) error {
	return r.UpdateUserAccountStatus(ctx, id, domain.AccountStatusApproved, nil, "")
}

func (r *sqliteUserRepo) UpdateUserAccountStatus(ctx context.Context, id int64, status domain.AccountStatus, reviewedBy *int64, remarks string) error {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	isVerified := 0
	if status == domain.AccountStatusApproved {
		isVerified = 1
	}

	query := `UPDATE users SET is_verified = ?, account_status = ?, updated_at = ? WHERE id = ?`
	res, err := r.db.ExecContext(ctx, query, isVerified, string(status), now, id)
	if err != nil {
		return fmt.Errorf("failed to update user account status: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	_, _ = r.db.ExecContext(ctx,
		`INSERT INTO account_verifications (user_id, status, reviewed_by, reviewed_at, remarks) VALUES (?, ?, ?, ?, ?)`,
		id, string(status), reviewedBy, now, remarks,
	)
	return nil
}

func (r *sqliteUserRepo) UpdateUser(ctx context.Context, user *domain.User) (*domain.User, error) {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	query := `
		UPDATE users SET
			name = ?, birthday = ?, gender = ?, phone_number = ?, email = ?,
			medical_info = ?, national_id = ?, image_url = ?, updated_at = ?
		WHERE id = ?
	`
	res, err := r.db.ExecContext(ctx, query,
		user.Name, user.Birthday, user.Gender, user.PhoneNumber, user.Email,
		user.MedicalInfo, user.NationalID, user.ImageURL, now, user.ID,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to update user: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	return r.GetUserByID(ctx, user.ID)
}

func (r *sqliteUserRepo) DeleteUser(ctx context.Context, id int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM users WHERE id = ?`, id)
	if err != nil {
		return fmt.Errorf("failed to delete user: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteUserRepo) UpdateLastLogin(ctx context.Context, userID int64) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	_, err := r.db.ExecContext(ctx, `UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?`, nowStr, nowStr, userID)
	return err
}

func (r *sqliteUserRepo) SaveRefreshToken(ctx context.Context, token *domain.RefreshToken) error {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	expStr := token.ExpiresAt.UTC().Format(time.RFC3339Nano)
	query := `INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked, created_at) VALUES (?, ?, ?, 0, ?)`
	res, err := r.db.ExecContext(ctx, query, token.UserID, token.TokenHash, expStr, nowStr)
	if err != nil {
		return fmt.Errorf("failed to save refresh token: %w", err)
	}
	id, err := res.LastInsertId()
	if err == nil {
		token.ID = id
	}
	return nil
}

func (r *sqliteUserRepo) GetRefreshToken(ctx context.Context, tokenHash string) (*domain.RefreshToken, error) {
	query := `SELECT id, user_id, token_hash, expires_at, revoked, created_at FROM refresh_tokens WHERE token_hash = ?`
	var t domain.RefreshToken
	var expTs, crTs string
	var revokedInt int

	err := r.db.QueryRowContext(ctx, query, tokenHash).Scan(&t.ID, &t.UserID, &t.TokenHash, &expTs, &revokedInt, &crTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	t.Revoked = revokedInt == 1
	t.ExpiresAt, _ = time.Parse(time.RFC3339Nano, expTs)
	t.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)

	return &t, nil
}

func (r *sqliteUserRepo) RevokeRefreshToken(ctx context.Context, tokenHash string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?`, tokenHash)
	return err
}

func (r *sqliteUserRepo) RevokeUserRefreshTokens(ctx context.Context, userID int64) error {
	_, err := r.db.ExecContext(ctx, `UPDATE refresh_tokens SET revoked = 1 WHERE user_id = ?`, userID)
	return err
}

func (r *sqliteUserRepo) AddUserDevice(ctx context.Context, device *domain.UserDevice) (*domain.UserDevice, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	_, err := r.db.ExecContext(ctx, `
		INSERT OR IGNORE INTO user_devices (user_id, device_hardware_id, device_name, platform, last_seen_at, created_at)
		VALUES (?, ?, ?, ?, ?, ?)
	`, device.UserID, device.DeviceHardwareID, device.DeviceName, device.Platform, nowStr, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to add user device: %w", err)
	}

	row := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, device_hardware_id, device_name, platform, last_seen_at, created_at
		FROM user_devices
		WHERE user_id = ? AND device_hardware_id = ?
	`, device.UserID, device.DeviceHardwareID)

	var saved domain.UserDevice
	var lastSeenTs, createdTs string
	if err := row.Scan(&saved.ID, &saved.UserID, &saved.DeviceHardwareID, &saved.DeviceName, &saved.Platform, &lastSeenTs, &createdTs); err != nil {
		return nil, err
	}
	saved.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lastSeenTs)
	saved.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdTs)
	return &saved, nil
}

func (r *sqliteUserRepo) ListUserDevices(ctx context.Context, userID int64) ([]*domain.UserDevice, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, user_id, device_hardware_id, device_name, platform, last_seen_at, created_at
		FROM user_devices
		WHERE user_id = ?
		ORDER BY last_seen_at DESC, id DESC
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	devices := make([]*domain.UserDevice, 0)
	for rows.Next() {
		var device domain.UserDevice
		var lastSeenTs, createdTs string
		if err := rows.Scan(&device.ID, &device.UserID, &device.DeviceHardwareID, &device.DeviceName, &device.Platform, &lastSeenTs, &createdTs); err != nil {
			return nil, err
		}
		device.LastSeenAt, _ = time.Parse(time.RFC3339Nano, lastSeenTs)
		device.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdTs)
		devices = append(devices, &device)
	}
	return devices, nil
}

func (r *sqliteUserRepo) DeleteUserDevice(ctx context.Context, deviceID int64, userID int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM user_devices WHERE id = ? AND user_id = ?`, deviceID, userID)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteUserRepo) AddEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	if contact.IsPrimary {
		if _, err := tx.ExecContext(ctx, `UPDATE user_emergency_contacts SET is_primary = 0 WHERE user_id = ?`, contact.UserID); err != nil {
			return nil, fmt.Errorf("failed to clear primary emergency contact: %w", err)
		}
	} else {
		var count int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_emergency_contacts WHERE user_id = ?`, contact.UserID).Scan(&count); err != nil {
			return nil, err
		}
		contact.IsPrimary = count == 0
	}

	isPrimaryInt := 0
	if contact.IsPrimary {
		isPrimaryInt = 1
	}
	query := `INSERT INTO user_emergency_contacts (user_id, contact_name, phone_number, relationship, is_primary, created_at) VALUES (?, ?, ?, ?, ?, ?)`
	res, err := tx.ExecContext(ctx, query, contact.UserID, contact.ContactName, contact.PhoneNumber, contact.Relationship, isPrimaryInt, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to add emergency contact: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	contact.ID = id
	contact.CreatedAt, _ = time.Parse(time.RFC3339Nano, nowStr)
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return contact, nil
}

func (r *sqliteUserRepo) ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error) {
	query := `SELECT id, user_id, contact_name, phone_number, relationship, is_primary, created_at FROM user_emergency_contacts WHERE user_id = ? ORDER BY is_primary DESC, id ASC`
	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	list := make([]*domain.EmergencyContact, 0)
	for rows.Next() {
		var ec domain.EmergencyContact
		var crTs string
		var isPrimaryInt int
		if err := rows.Scan(&ec.ID, &ec.UserID, &ec.ContactName, &ec.PhoneNumber, &ec.Relationship, &isPrimaryInt, &crTs); err != nil {
			return nil, err
		}
		ec.IsPrimary = isPrimaryInt == 1
		ec.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &ec)
	}

	return list, nil
}

func (r *sqliteUserRepo) UpdateEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	if contact.IsPrimary {
		if _, err := tx.ExecContext(ctx, `UPDATE user_emergency_contacts SET is_primary = 0 WHERE user_id = ?`, contact.UserID); err != nil {
			return nil, fmt.Errorf("failed to clear primary emergency contact: %w", err)
		}
	}

	isPrimaryInt := 0
	if contact.IsPrimary {
		isPrimaryInt = 1
	}
	res, err := tx.ExecContext(ctx, `
		UPDATE user_emergency_contacts
		SET contact_name = ?, phone_number = ?, relationship = ?, is_primary = ?
		WHERE id = ? AND user_id = ?
	`, contact.ContactName, contact.PhoneNumber, contact.Relationship, isPrimaryInt, contact.ID, contact.UserID)
	if err != nil {
		return nil, fmt.Errorf("failed to update emergency contact: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return nil, domain.ErrNotFound
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return r.getEmergencyContact(ctx, contact.ID, contact.UserID)
}

func (r *sqliteUserRepo) SetPrimaryEmergencyContact(ctx context.Context, contactID int64, userID int64) (*domain.EmergencyContact, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	var exists int
	if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_emergency_contacts WHERE id = ? AND user_id = ?`, contactID, userID).Scan(&exists); err != nil {
		return nil, err
	}
	if exists == 0 {
		return nil, domain.ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, `UPDATE user_emergency_contacts SET is_primary = 0 WHERE user_id = ?`, userID); err != nil {
		return nil, err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE user_emergency_contacts SET is_primary = 1 WHERE id = ? AND user_id = ?`, contactID, userID); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return r.getEmergencyContact(ctx, contactID, userID)
}

func (r *sqliteUserRepo) DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error {
	res, err := r.db.ExecContext(ctx, `DELETE FROM user_emergency_contacts WHERE id = ? AND user_id = ?`, contactID, userID)
	if err != nil {
		return err
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
	return nil
}

func (r *sqliteUserRepo) getEmergencyContact(ctx context.Context, contactID int64, userID int64) (*domain.EmergencyContact, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, user_id, contact_name, phone_number, relationship, is_primary, created_at
		FROM user_emergency_contacts
		WHERE id = ? AND user_id = ?
	`, contactID, userID)

	var contact domain.EmergencyContact
	var createdTs string
	var isPrimaryInt int
	if err := row.Scan(&contact.ID, &contact.UserID, &contact.ContactName, &contact.PhoneNumber, &contact.Relationship, &isPrimaryInt, &createdTs); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}
	contact.IsPrimary = isPrimaryInt == 1
	contact.CreatedAt, _ = time.Parse(time.RFC3339Nano, createdTs)
	return &contact, nil
}
