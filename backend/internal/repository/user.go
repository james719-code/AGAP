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
	UpdateUser(ctx context.Context, user *domain.User) (*domain.User, error)
	DeleteUser(ctx context.Context, id int64) error
	UpdateLastLogin(ctx context.Context, userID int64) error

	// Refresh Tokens
	SaveRefreshToken(ctx context.Context, token *domain.RefreshToken) error
	GetRefreshToken(ctx context.Context, tokenHash string) (*domain.RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, tokenHash string) error
	RevokeUserRefreshTokens(ctx context.Context, userID int64) error

	// Emergency Contacts
	AddEmergencyContact(ctx context.Context, contact *domain.EmergencyContact) (*domain.EmergencyContact, error)
	ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error)
	DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error
}


