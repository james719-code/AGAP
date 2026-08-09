package service_test

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
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
	user, err := authSvc.RegisterUser(ctx, "responder_john", "securepass123", domain.RoleResponder, "dev-john-01", nil, "John Doe", "", "", "", "", "", "")

	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}
	if user.Username != "responder_john" {
		t.Errorf("expected username responder_john, got %s", user.Username)
	}
	if user.AccountStatus != domain.AccountStatusPending {
		t.Errorf("expected account status PENDING, got %s", user.AccountStatus)
	}
	if user.IsVerified {
		t.Errorf("expected new responder account to require verification")
	}

	// 2. Attempt login before verification (should fail with ErrAccountUnverified)
	_, err = authSvc.Login(ctx, domain.LoginRequest{
		Username: "responder_john",
		Password: "securepass123",
	})
	if err == nil || err != domain.ErrAccountUnverified {
		t.Fatalf("expected ErrAccountUnverified on unverified user login, got %v", err)
	}

	// 3. Admin verifies user account
	if err := authSvc.VerifyUserAccount(ctx, user.ID); err != nil {
		t.Fatalf("failed to verify user account: %v", err)
	}

	// 4. Login with valid credentials after verification
	loginRes, err := authSvc.Login(ctx, domain.LoginRequest{
		Username: "responder_john",
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
	if claims.Username != "responder_john" {
		t.Errorf("expected claim username responder_john, got %s", claims.Username)
	}
	if claims.Role != domain.RoleResponder {
		t.Errorf("expected claim role RESPONDER, got %s", claims.Role)
	}

	// 4. Login with invalid password
	_, err = authSvc.Login(ctx, domain.LoginRequest{
		Username: "responder_john",
		Password: "wrongpassword",
	})
	if err == nil {
		t.Fatalf("expected error on invalid password, got nil")
	}
}

func TestAuthService_RejectsUnsupportedRole(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth_invalid_role.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, "super_secret_jwt_test_key")

	_, err = authSvc.RegisterUser(context.Background(), "operator_jane", "securepass123", domain.Role("OPERATOR"), "dev-jane-01", nil, "Jane Doe", "", "", "", "", "", "")
	if err == nil {
		t.Fatalf("expected invalid role error")
	}
}

func TestAuthService_AccountStatusLifecycle(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth_lifecycle.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, "super_secret_jwt_test_key")
	ctx := context.Background()

	user, err := authSvc.RegisterUser(ctx, "citizen_lina", "securepass123", domain.RoleCitizen, "dev-lina-01", nil, "Lina Cruz", "", "", "", "", "", "")
	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}

	if err := authSvc.UpdateUserAccountStatus(ctx, user.ID, domain.AccountStatusRejected, nil, "invalid ID"); err != nil {
		t.Fatalf("failed to reject account: %v", err)
	}
	_, err = authSvc.Login(ctx, domain.LoginRequest{Username: "citizen_lina", Password: "securepass123"})
	if err == nil || err != domain.ErrAccountRejected {
		t.Fatalf("expected rejected account login denial, got %v", err)
	}

	if err := authSvc.UpdateUserAccountStatus(ctx, user.ID, domain.AccountStatusSuspended, nil, "policy violation"); err != nil {
		t.Fatalf("failed to suspend account: %v", err)
	}
	_, err = authSvc.Login(ctx, domain.LoginRequest{Username: "citizen_lina", Password: "securepass123"})
	if err == nil || err != domain.ErrAccountSuspended {
		t.Fatalf("expected suspended account login denial, got %v", err)
	}

	if err := authSvc.UpdateUserAccountStatus(ctx, user.ID, domain.AccountStatusApproved, nil, "documents verified"); err != nil {
		t.Fatalf("failed to approve account: %v", err)
	}
	loginRes, err := authSvc.Login(ctx, domain.LoginRequest{Username: "citizen_lina", Password: "securepass123"})
	if err != nil {
		t.Fatalf("expected approved account login success, got %v", err)
	}
	if loginRes.User.AccountStatus != domain.AccountStatusApproved || !loginRes.User.IsVerified {
		t.Fatalf("expected approved and verified user, got status=%s verified=%v", loginRes.User.AccountStatus, loginRes.User.IsVerified)
	}
	if loginRes.User.UpdatedAt.IsZero() {
		t.Fatal("expected returned user updated_at to be populated")
	}

	responderRole := domain.RoleResponder
	updated, err := authSvc.UpdateProfile(ctx, user.ID, domain.UpdateProfileRequest{Role: &responderRole})
	if err != nil {
		t.Fatalf("failed to update administrative role field: %v", err)
	}
	if updated.Role != domain.RoleResponder {
		t.Fatalf("expected returned role RESPONDER, got %s", updated.Role)
	}
}

func TestAuthService_UserDeviceLifecycle(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth_devices.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, "super_secret_jwt_test_key")
	ctx := context.Background()

	user, err := authSvc.RegisterUser(ctx, "citizen_device_owner", "securepass123", domain.RoleCitizen, "primary-device", nil, "Device Owner", "", "", "", "", "", "")
	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}

	devices, err := authSvc.ListUserDevices(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list registered devices: %v", err)
	}
	if len(devices) != 1 || devices[0].DeviceHardwareID != "primary-device" {
		t.Fatalf("expected auto-linked registration device, got %+v", devices)
	}

	added, err := authSvc.AddUserDevice(ctx, user.ID, "field-phone-02", "Field Phone", "android")
	if err != nil {
		t.Fatalf("failed to add user device: %v", err)
	}
	if added.ID == 0 || added.DeviceName != "Field Phone" || added.Platform != "android" {
		t.Fatalf("unexpected added device: %+v", added)
	}

	devices, err = authSvc.ListUserDevices(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list devices after add: %v", err)
	}
	if len(devices) != 2 {
		t.Fatalf("expected two devices after add, got %+v", devices)
	}

	if err := authSvc.DeleteUserDevice(ctx, added.ID, user.ID); err != nil {
		t.Fatalf("failed to delete user device: %v", err)
	}
	devices, err = authSvc.ListUserDevices(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list devices after delete: %v", err)
	}
	if len(devices) != 1 || devices[0].DeviceHardwareID != "primary-device" {
		t.Fatalf("expected only primary device after delete, got %+v", devices)
	}
}

func TestAuthService_EmergencyContactEditAndPrimaryLifecycle(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth_contacts.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, "super_secret_jwt_test_key")
	ctx := context.Background()

	user, err := authSvc.RegisterUser(ctx, "citizen_contact_owner", "securepass123", domain.RoleCitizen, "contact-device", nil, "Contact Owner", "", "", "", "", "", "")
	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}

	first, err := authSvc.AddEmergencyContact(ctx, user.ID, domain.EmergencyContactRequest{
		ContactName:  "Ana Cruz",
		PhoneNumber:  "+639170000001",
		Relationship: "Mother",
	})
	if err != nil {
		t.Fatalf("failed to add first contact: %v", err)
	}
	if !first.IsPrimary {
		t.Fatalf("expected first contact to become primary")
	}

	makePrimary := true
	second, err := authSvc.AddEmergencyContact(ctx, user.ID, domain.EmergencyContactRequest{
		ContactName:  "Ben Cruz",
		PhoneNumber:  "+639170000002",
		Relationship: "Brother",
		IsPrimary:    &makePrimary,
	})
	if err != nil {
		t.Fatalf("failed to add second contact: %v", err)
	}
	if !second.IsPrimary {
		t.Fatalf("expected requested primary contact, got %+v", second)
	}

	contacts, err := authSvc.ListEmergencyContacts(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list contacts: %v", err)
	}
	if len(contacts) != 2 || contacts[0].ID != second.ID || !contacts[0].IsPrimary || contacts[1].IsPrimary {
		t.Fatalf("expected second contact only primary and listed first, got %+v", contacts)
	}

	updated, err := authSvc.UpdateEmergencyContact(ctx, first.ID, user.ID, domain.EmergencyContactRequest{
		ContactName:  "Ana Reyes",
		PhoneNumber:  "+639170000099",
		Relationship: "Parent",
	})
	if err != nil {
		t.Fatalf("failed to update contact: %v", err)
	}
	if updated.ContactName != "Ana Reyes" || updated.PhoneNumber != "+639170000099" || updated.IsPrimary {
		t.Fatalf("unexpected updated contact: %+v", updated)
	}

	primary, err := authSvc.SetPrimaryEmergencyContact(ctx, first.ID, user.ID)
	if err != nil {
		t.Fatalf("failed to set primary contact: %v", err)
	}
	if !primary.IsPrimary || primary.ID != first.ID {
		t.Fatalf("unexpected primary contact: %+v", primary)
	}

	contacts, err = authSvc.ListEmergencyContacts(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to list contacts after primary switch: %v", err)
	}
	if len(contacts) != 2 || contacts[0].ID != first.ID || !contacts[0].IsPrimary || contacts[1].IsPrimary {
		t.Fatalf("expected first contact only primary after switch, got %+v", contacts)
	}
}

func TestAuthService_RefreshTokensAreHashedAndSuspensionInvalidatesSession(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_auth_session_hardening.db")

	db, err := sqlite.Open(dbPath)
	if err != nil {
		t.Fatalf("failed to open sqlite DB: %v", err)
	}
	defer db.Close()

	userRepo := sqlite.NewUserRepo(db)
	authSvc := service.NewAuthService(userRepo, "super_secret_jwt_test_key")
	ctx := context.Background()

	user, err := authSvc.RegisterUser(ctx, "citizen_session", "securepass123", domain.RoleCitizen, "session-device", nil, "Session User", "", "", "", "", "", "")
	if err != nil {
		t.Fatalf("failed to register user: %v", err)
	}
	if err := authSvc.VerifyUserAccount(ctx, user.ID); err != nil {
		t.Fatalf("failed to approve user: %v", err)
	}
	login, err := authSvc.Login(ctx, domain.LoginRequest{Username: user.Username, Password: "securepass123"})
	if err != nil {
		t.Fatalf("failed to login: %v", err)
	}

	if _, err := userRepo.GetRefreshToken(ctx, login.RefreshToken); err != domain.ErrNotFound {
		t.Fatalf("expected raw refresh token to be absent from database, got %v", err)
	}
	sum := sha256.Sum256([]byte(login.RefreshToken))
	if _, err := userRepo.GetRefreshToken(ctx, hex.EncodeToString(sum[:])); err != nil {
		t.Fatalf("expected hashed refresh token in database: %v", err)
	}

	if err := authSvc.UpdateUserAccountStatus(ctx, user.ID, domain.AccountStatusSuspended, nil, "test suspension"); err != nil {
		t.Fatalf("failed to suspend user: %v", err)
	}
	if _, err := authSvc.ValidateToken(login.Token); err != domain.ErrInvalidToken {
		t.Fatalf("expected access token to become invalid after suspension, got %v", err)
	}
	if _, err := authSvc.RefreshToken(ctx, login.RefreshToken); err != domain.ErrInvalidToken {
		t.Fatalf("expected refresh token to become invalid after suspension, got %v", err)
	}
}
