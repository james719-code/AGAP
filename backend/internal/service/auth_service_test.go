package service_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository/sqlite"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

func TestAuthService_RegisterAndLogin(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	jwtSecret := "super_secret_jwt_test_key"
	authSvc := service.NewAuthService(userRepo, jwtSecret)
	ctx := context.Background()

	// 1. Register User
	user, err := authSvc.RegisterUser(ctx, "dispatcher_john", "securepass123", domain.RoleDispatcher, "dev-john-01", nil)
	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}
	if user.Username != "dispatcher_john" {
		t.Errorf("expected username dispatcher_john, got %s", user.Username)
	}

	// 2. Login with valid credentials
	loginRes, err := authSvc.Login(ctx, domain.LoginRequest{
		Username: "dispatcher_john",
		Password: "securepass123",
	})
	if err != nil {
		t.Fatalf("failed to login: %v", err)
	}
	if loginRes.Token == "" {
		t.Fatalf("expected non-empty JWT token")
	}

	// 3. Validate Token
	claims, err := authSvc.ValidateToken(loginRes.Token)
	if err != nil {
		t.Fatalf("failed to validate JWT token: %v", err)
	}
	if claims.Username != "dispatcher_john" {
		t.Errorf("expected claim username dispatcher_john, got %s", claims.Username)
	}
	if claims.Role != domain.RoleDispatcher {
		t.Errorf("expected claim role DISPATCHER, got %s", claims.Role)
	}

	// 4. Login with invalid password
	_, err = authSvc.Login(ctx, domain.LoginRequest{
		Username: "dispatcher_john",
		Password: "wrongpassword",
	})
	if err == nil {
		t.Fatalf("expected error on invalid password, got nil")
	}
}
