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

	query := `
		INSERT INTO users (
			username, password_hash, role, device_id, agency_id, address_id,
			name, birthday, gender, phone_number, email, medical_info, national_id, is_verified, image_url,
			created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`
	isVerifiedInt := 0
	if user.IsVerified {
		isVerifiedInt = 1
	}

	res, err := r.db.ExecContext(ctx, query,
		user.Username, user.PasswordHash, string(user.Role), user.DeviceID, user.AgencyID, user.AddressID,
		user.Name, user.Birthday, user.Gender, user.PhoneNumber, user.Email, user.MedicalInfo, user.NationalID, isVerifiedInt, user.ImageURL,
		nowStr, nowStr,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	user.ID = id
	user.CreatedAt = now
	user.UpdatedAt = now

	// Auto-link device to user_devices if provided
	if user.DeviceID != "" {
		_, _ = r.db.ExecContext(ctx,
			"INSERT OR IGNORE INTO user_devices (user_id, device_hardware_id, created_at) VALUES (?, ?, ?)",
			user.ID, user.DeviceID, nowStr,
		)
		user.Devices = append(user.Devices, domain.UserDevice{
			UserID:           user.ID,
			DeviceHardwareID: user.DeviceID,
			CreatedAt:        now,
		})
	}

	return user, nil
}

func (r *sqliteUserRepo) GetUserByID(ctx context.Context, id int64) (*domain.User, error) {
	query := `
		SELECT id, username, password_hash, role, device_id, agency_id, address_id,
		       name, birthday, gender, phone_number, email, medical_info, national_id, is_verified, image_url, last_login_at,
		       created_at, updated_at
		FROM users WHERE id = ?
	`
	row := r.db.QueryRowContext(ctx, query, id)

	var u domain.User
	var roleStr, crTs, upTs string
	var lastLoginTs sql.NullString
	var isVerifiedInt int
	err := row.Scan(
		&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &u.AddressID,
		&u.Name, &u.Birthday, &u.Gender, &u.PhoneNumber, &u.Email, &u.MedicalInfo, &u.NationalID, &isVerifiedInt, &u.ImageURL, &lastLoginTs,
		&crTs, &upTs,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	u.Role = domain.Role(roleStr)
	u.IsVerified = isVerifiedInt == 1
	if lastLoginTs.Valid && lastLoginTs.String != "" {
		t, _ := time.Parse(time.RFC3339Nano, lastLoginTs.String)
		u.LastLoginAt = &t
	}
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)

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
	ecRows, err := r.db.QueryContext(ctx, "SELECT id, user_id, contact_name, phone_number, relationship, created_at FROM user_emergency_contacts WHERE user_id = ?", u.ID)
	if err == nil {
		defer ecRows.Close()
		for ecRows.Next() {
			var ec domain.EmergencyContact
			var ecTs string
			if scanErr := ecRows.Scan(&ec.ID, &ec.UserID, &ec.ContactName, &ec.PhoneNumber, &ec.Relationship, &ec.CreatedAt); scanErr == nil {
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

	baseQuery := `SELECT id, username, password_hash, role, device_id, agency_id, address_id, name, birthday, gender, phone_number, email, medical_info, national_id, is_verified, image_url, last_login_at, created_at, updated_at FROM users`

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

	var users []*domain.User
	for rows.Next() {
		var u domain.User
		var roleStr, crTs, upTs string
		var lastLoginTs sql.NullString
		var isVerifiedInt int

		if err := rows.Scan(
			&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &u.AddressID,
			&u.Name, &u.Birthday, &u.Gender, &u.PhoneNumber, &u.Email, &u.MedicalInfo, &u.NationalID, &isVerifiedInt, &u.ImageURL, &lastLoginTs,
			&crTs, &upTs,
		); err != nil {
			return nil, err
		}

		u.Role = domain.Role(roleStr)
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
	now := time.Now().UTC().Format(time.RFC3339Nano)
	query := `UPDATE users SET is_verified = 1, updated_at = ? WHERE id = ?`
	res, err := r.db.ExecContext(ctx, query, now, id)
	if err != nil {
		return fmt.Errorf("failed to verify user account: %w", err)
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		return domain.ErrNotFound
	}
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

func (r *sqliteUserRepo) AddEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error) {
	nowStr := time.Now().UTC().Format(time.RFC3339Nano)
	query := `INSERT INTO user_emergency_contacts (user_id, contact_name, phone_number, relationship, created_at) VALUES (?, ?, ?, ?, ?)`
	res, err := r.db.ExecContext(ctx, query, contact.UserID, contact.ContactName, contact.PhoneNumber, contact.Relationship, nowStr)
	if err != nil {
		return nil, fmt.Errorf("failed to add emergency contact: %w", err)
	}

	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}

	contact.ID = id
	contact.CreatedAt, _ = time.Parse(time.RFC3339Nano, nowStr)
	return contact, nil
}

func (r *sqliteUserRepo) ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error) {
	query := `SELECT id, user_id, contact_name, phone_number, relationship, created_at FROM user_emergency_contacts WHERE user_id = ? ORDER BY id ASC`
	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []*domain.EmergencyContact
	for rows.Next() {
		var ec domain.EmergencyContact
		var crTs string
		if err := rows.Scan(&ec.ID, &ec.UserID, &ec.ContactName, &ec.PhoneNumber, &ec.Relationship, &crTs); err != nil {
			return nil, err
		}
		ec.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
		list = append(list, &ec)
	}

	return list, nil
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


