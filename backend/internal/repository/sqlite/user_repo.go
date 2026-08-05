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
		INSERT INTO users (username, password_hash, role, device_id, agency_id, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)
	`
	res, err := r.db.ExecContext(ctx, query,
		user.Username, user.PasswordHash, string(user.Role), user.DeviceID, user.AgencyID, nowStr, nowStr,
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
	return user, nil
}

func (r *sqliteUserRepo) GetUserByID(ctx context.Context, id int64) (*domain.User, error) {
	query := `SELECT id, username, password_hash, role, device_id, agency_id, created_at, updated_at FROM users WHERE id = ?`
	row := r.db.QueryRowContext(ctx, query, id)

	var u domain.User
	var roleStr, crTs, upTs string
	err := row.Scan(&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	u.Role = domain.Role(roleStr)
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &u, nil
}

func (r *sqliteUserRepo) GetUserByUsername(ctx context.Context, username string) (*domain.User, error) {
	query := `SELECT id, username, password_hash, role, device_id, agency_id, created_at, updated_at FROM users WHERE username = ?`
	row := r.db.QueryRowContext(ctx, query, username)

	var u domain.User
	var roleStr, crTs, upTs string
	err := row.Scan(&u.ID, &u.Username, &u.PasswordHash, &roleStr, &u.DeviceID, &u.AgencyID, &crTs, &upTs)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, domain.ErrNotFound
		}
		return nil, err
	}

	u.Role = domain.Role(roleStr)
	u.CreatedAt, _ = time.Parse(time.RFC3339Nano, crTs)
	u.UpdatedAt, _ = time.Parse(time.RFC3339Nano, upTs)
	return &u, nil
}
