package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type UserRepository interface {
	CreateUser(ctx context.Context, user *domain.User) (*domain.User, error)
	GetUserByID(ctx context.Context, id int64) (*domain.User, error)
	GetUserByUsername(ctx context.Context, username string) (*domain.User, error)
	ListUsers(ctx context.Context, role *domain.Role) ([]*domain.User, error)
	VerifyUserAccount(ctx context.Context, id int64) error
	UpdateUserAccountStatus(ctx context.Context, id int64, status domain.AccountStatus, reviewedBy *int64, remarks string) error
	UpdateUser(ctx context.Context, user *domain.User) (*domain.User, error)
	DeleteUser(ctx context.Context, id int64) error
	UpdateLastLogin(ctx context.Context, userID int64) error

	// Refresh Tokens
	SaveRefreshToken(ctx context.Context, token *domain.RefreshToken) error
	GetRefreshToken(ctx context.Context, tokenHash string) (*domain.RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, tokenHash string) error
	RevokeUserRefreshTokens(ctx context.Context, userID int64) error

	// User Devices
	AddUserDevice(ctx context.Context, device *domain.UserDevice) (*domain.UserDevice, error)
	ListUserDevices(ctx context.Context, userID int64) ([]*domain.UserDevice, error)
	DeleteUserDevice(ctx context.Context, deviceID int64, userID int64) error

	// Emergency Contacts
	AddEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error)
	ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error)
	UpdateEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error)
	SetPrimaryEmergencyContact(ctx context.Context, contactID int64, userID int64) (*domain.EmergencyContact, error)
	DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error
}
