package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type UserRepository interface {
	CreateUser(ctx context.Context, user *domain.User) (*domain.User, error)
	GetUserByID(ctx context.Context, id int64) (*domain.User, error)
	GetUserByUsername(ctx context.Context, username string) (*domain.User, error)
}
