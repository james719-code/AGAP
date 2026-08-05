package service

import (
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type AuthService interface {
	RegisterUser(ctx context.Context, username, password string, role domain.Role, deviceID string, agencyID *int64) (*domain.User, error)
	Login(ctx context.Context, req domain.LoginRequest) (*domain.LoginResponse, error)
	ValidateToken(tokenStr string) (*domain.AuthClaims, error)
	GenerateTokenForUser(user *domain.User) (string, time.Time, error)
}

type authService struct {
	repo      repository.UserRepository
	jwtSecret []byte
}

func NewAuthService(repo repository.UserRepository, jwtSecret string) AuthService {
	return &authService{
		repo:      repo,
		jwtSecret: []byte(jwtSecret),
	}
}

func (s *authService) RegisterUser(ctx context.Context, username, password string, role domain.Role, deviceID string, agencyID *int64) (*domain.User, error) {
	existing, err := s.repo.GetUserByUsername(ctx, username)
	if err == nil && existing != nil {
		return nil, fmt.Errorf("%w: username %q is taken", domain.ErrAlreadyExists, username)
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("failed to hash password: %w", err)
	}

	user := &domain.User{
		Username:     username,
		PasswordHash: string(hashedPassword),
		Role:         role,
		DeviceID:     deviceID,
		AgencyID:     agencyID,
	}

	return s.repo.CreateUser(ctx, user)
}

func (s *authService) Login(ctx context.Context, req domain.LoginRequest) (*domain.LoginResponse, error) {
	user, err := s.repo.GetUserByUsername(ctx, req.Username)
	if err != nil {
		return nil, domain.ErrInvalidCredentials
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		return nil, domain.ErrInvalidCredentials
	}

	tokenStr, expiresAt, err := s.GenerateTokenForUser(user)
	if err != nil {
		return nil, fmt.Errorf("failed to generate token: %w", err)
	}

	return &domain.LoginResponse{
		Token:     tokenStr,
		ExpiresAt: expiresAt,
		User:      *user,
	}, nil
}

func (s *authService) GenerateTokenForUser(user *domain.User) (string, time.Time, error) {
	expiresAt := time.Now().Add(24 * 7 * time.Hour) // 7 days valid
	claims := &domain.AuthClaims{
		UserID:   user.ID,
		Username: user.Username,
		Role:     user.Role,
		DeviceID: user.DeviceID,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Subject:   fmt.Sprintf("%d", user.ID),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenStr, err := token.SignedString(s.jwtSecret)
	if err != nil {
		return "", time.Time{}, err
	}
	return tokenStr, expiresAt, nil
}

func (s *authService) ValidateToken(tokenStr string) (*domain.AuthClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &domain.AuthClaims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return s.jwtSecret, nil
	})

	if err != nil || !token.Valid {
		return nil, domain.ErrInvalidToken
	}

	claims, ok := token.Claims.(*domain.AuthClaims)
	if !ok {
		return nil, domain.ErrInvalidToken
	}

	return claims, nil
}
