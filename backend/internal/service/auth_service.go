package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type AuthService interface {
	RegisterUser(ctx context.Context, username, password string, role domain.Role, deviceID string, agencyID *int64, name, birthday, gender, phoneNumber, email, medicalInfo, nationalID string, rescueUnitID ...*int64) (*domain.User, error)
	Login(ctx context.Context, req domain.LoginRequest) (*domain.LoginResponse, error)
	RefreshToken(ctx context.Context, refreshStr string) (*domain.LoginResponse, error)
	Logout(ctx context.Context, refreshStr string, userID int64) error
	GetUserByID(ctx context.Context, id int64) (*domain.User, error)
	ListUsers(ctx context.Context, role *domain.Role) ([]*domain.User, error)
	UpdateProfile(ctx context.Context, userID int64, req domain.UpdateProfileRequest) (*domain.User, error)
	DeleteUser(ctx context.Context, id int64) error
	VerifyUserAccount(ctx context.Context, id int64) error
	UpdateUserAccountStatus(ctx context.Context, id int64, status domain.AccountStatus, reviewedBy *int64, remarks string) error
	ValidateToken(tokenStr string) (*domain.AuthClaims, error)
	GenerateTokenForUser(user *domain.User) (string, time.Time, error)

	AddUserDevice(ctx context.Context, userID int64, deviceHardwareID, deviceName, platform string) (*domain.UserDevice, error)
	ListUserDevices(ctx context.Context, userID int64) ([]*domain.UserDevice, error)
	DeleteUserDevice(ctx context.Context, deviceID int64, userID int64) error

	AddEmergencyContact(ctx context.Context, userID int64, req domain.EmergencyContactRequest) (*domain.EmergencyContact, error)
	ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error)
	UpdateEmergencyContact(ctx context.Context, contactID int64, userID int64, req domain.EmergencyContactRequest) (*domain.EmergencyContact, error)
	SetPrimaryEmergencyContact(ctx context.Context, contactID int64, userID int64) (*domain.EmergencyContact, error)
	DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error
}

type authService struct {
	repo      repository.UserRepository
	auditRepo repository.AuditRepository
	jwtSecret []byte
}

func NewAuthService(repo repository.UserRepository, jwtSecret string, auditRepos ...repository.AuditRepository) AuthService {
	var auditRepo repository.AuditRepository
	if len(auditRepos) > 0 {
		auditRepo = auditRepos[0]
	}
	return &authService{
		repo:      repo,
		auditRepo: auditRepo,
		jwtSecret: []byte(jwtSecret),
	}
}

func (s *authService) RegisterUser(ctx context.Context, username, password string, role domain.Role, deviceID string, agencyID *int64, name, birthday, gender, phoneNumber, email, medicalInfo, nationalID string, rescueUnitID ...*int64) (*domain.User, error) {
	if role != domain.RoleCitizen && role != domain.RoleResponder && role != domain.RoleAdmin {
		return nil, fmt.Errorf("%w: %s", domain.ErrInvalidRole, role)
	}

	existing, err := s.repo.GetUserByUsername(ctx, username)
	if err == nil && existing != nil {
		return nil, fmt.Errorf("%w: username %q is taken", domain.ErrAlreadyExists, username)
	}
	if err != nil && err != domain.ErrNotFound {
		return nil, fmt.Errorf("failed to check username availability: %w", err)
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("failed to hash password: %w", err)
	}

	if deviceID == "" {
		deviceID = fmt.Sprintf("DEV-%s-%06d", strings.ToLower(string(role)), time.Now().UnixNano()%1000000)
	}

	if name == "" {
		name = username
	}

	var rUnitID *int64
	if len(rescueUnitID) > 0 {
		rUnitID = rescueUnitID[0]
	}

	user := &domain.User{
		Username:      username,
		PasswordHash:  string(hashedPassword),
		Role:          role,
		DeviceID:      deviceID,
		AgencyID:      agencyID,
		RescueUnitID:  rUnitID,
		Name:          name,
		Birthday:      birthday,
		Gender:        gender,
		PhoneNumber:   phoneNumber,
		Email:         email,
		MedicalInfo:   medicalInfo,
		NationalID:    nationalID,
		AccountStatus: domain.AccountStatusPending,
		IsVerified:    false,
	}
	if role == domain.RoleAdmin {
		user.AccountStatus = domain.AccountStatusApproved
		user.IsVerified = true
	}

	return s.repo.CreateUser(ctx, user)
}

func (s *authService) GetUserByID(ctx context.Context, id int64) (*domain.User, error) {
	return s.repo.GetUserByID(ctx, id)
}

func (s *authService) ListUsers(ctx context.Context, role *domain.Role) ([]*domain.User, error) {
	return s.repo.ListUsers(ctx, role)
}

func (s *authService) UpdateProfile(ctx context.Context, userID int64, req domain.UpdateProfileRequest) (*domain.User, error) {
	user, err := s.repo.GetUserByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	if req.Name != "" {
		user.Name = req.Name
	}
	if req.Birthday != "" {
		user.Birthday = req.Birthday
	}
	if req.Gender != "" {
		user.Gender = req.Gender
	}
	if req.PhoneNumber != "" {
		user.PhoneNumber = req.PhoneNumber
	}
	if req.Email != "" {
		user.Email = req.Email
	}
	if req.MedicalInfo != "" {
		user.MedicalInfo = req.MedicalInfo
	}
	if req.NationalID != "" {
		user.NationalID = req.NationalID
	}
	if req.ImageURL != "" {
		user.ImageURL = req.ImageURL
	}
	if req.Role != nil {
		if *req.Role != domain.RoleCitizen && *req.Role != domain.RoleResponder && *req.Role != domain.RoleAdmin {
			return nil, domain.ErrInvalidRole
		}
		user.Role = *req.Role
	}
	if req.AgencyID != nil {
		if *req.AgencyID <= 0 {
			user.AgencyID = nil
		} else {
			agencyID := *req.AgencyID
			user.AgencyID = &agencyID
		}
	}

	return s.repo.UpdateUser(ctx, user)
}

func (s *authService) DeleteUser(ctx context.Context, id int64) error {
	return s.repo.DeleteUser(ctx, id)
}

func (s *authService) Login(ctx context.Context, req domain.LoginRequest) (*domain.LoginResponse, error) {
	user, err := s.repo.GetUserByUsername(ctx, req.Username)
	if err != nil {
		return nil, domain.ErrInvalidCredentials
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)); err != nil {
		return nil, domain.ErrInvalidCredentials
	}

	switch user.AccountStatus {
	case domain.AccountStatusRejected:
		return nil, domain.ErrAccountRejected
	case domain.AccountStatusSuspended:
		return nil, domain.ErrAccountSuspended
	case domain.AccountStatusPending:
		return nil, domain.ErrAccountUnverified
	}
	if !user.IsVerified && user.Role != domain.RoleAdmin {
		return nil, domain.ErrAccountUnverified
	}

	_ = s.repo.UpdateLastLogin(ctx, user.ID)

	tokenStr, expiresAt, err := s.GenerateTokenForUser(user)
	if err != nil {
		return nil, fmt.Errorf("failed to generate token: %w", err)
	}

	// Refresh tokens are opaque random values. Only a SHA-256 digest is persisted so a
	// database leak cannot be used directly to mint a new access token.
	refreshStr, err := newRefreshToken()
	if err != nil {
		return nil, fmt.Errorf("failed to generate refresh token: %w", err)
	}
	rfToken := &domain.RefreshToken{
		UserID:    user.ID,
		TokenHash: hashRefreshToken(refreshStr),
		ExpiresAt: time.Now().Add(30 * 24 * time.Hour),
	}
	if err := s.repo.SaveRefreshToken(ctx, rfToken); err != nil {
		return nil, fmt.Errorf("failed to persist refresh token: %w", err)
	}

	return &domain.LoginResponse{
		Token:        tokenStr,
		RefreshToken: refreshStr,
		ExpiresAt:    expiresAt,
		User:         *user,
	}, nil
}

func (s *authService) RefreshToken(ctx context.Context, refreshStr string) (*domain.LoginResponse, error) {
	if strings.TrimSpace(refreshStr) == "" {
		return nil, domain.ErrInvalidToken
	}

	rfToken, err := s.getRefreshTokenCompatible(ctx, refreshStr)
	if err != nil || rfToken == nil || rfToken.Revoked || !rfToken.ExpiresAt.After(time.Now()) {
		return nil, domain.ErrInvalidToken
	}

	user, err := s.repo.GetUserByID(ctx, rfToken.UserID)
	if err != nil {
		return nil, domain.ErrInvalidToken
	}
	if !isUserSessionAllowed(user) {
		_ = s.repo.RevokeUserRefreshTokens(ctx, user.ID)
		return nil, domain.ErrInvalidToken
	}

	tokenStr, expiresAt, err := s.GenerateTokenForUser(user)
	if err != nil {
		return nil, err
	}

	return &domain.LoginResponse{
		Token:        tokenStr,
		RefreshToken: refreshStr,
		ExpiresAt:    expiresAt,
		User:         *user,
	}, nil
}

func (s *authService) Logout(ctx context.Context, refreshStr string, userID int64) error {
	if refreshStr != "" {
		// Revoke the new hashed form and the legacy raw form so sessions created
		// before this hardening change can still be logged out cleanly.
		_ = s.repo.RevokeRefreshToken(ctx, hashRefreshToken(refreshStr))
		_ = s.repo.RevokeRefreshToken(ctx, refreshStr)
	}
	if userID > 0 {
		return s.repo.RevokeUserRefreshTokens(ctx, userID)
	}
	return nil
}

func (s *authService) VerifyUserAccount(ctx context.Context, id int64) error {
	return s.UpdateUserAccountStatus(ctx, id, domain.AccountStatusApproved, nil, "")
}

func (s *authService) UpdateUserAccountStatus(ctx context.Context, id int64, status domain.AccountStatus, reviewedBy *int64, remarks string) error {
	switch status {
	case domain.AccountStatusApproved, domain.AccountStatusRejected, domain.AccountStatusSuspended:
	default:
		return fmt.Errorf("%w: %s", domain.ErrInvalidAccountStatus, status)
	}

	before, err := s.repo.GetUserByID(ctx, id)
	if err != nil {
		return err
	}

	if err := s.repo.UpdateUserAccountStatus(ctx, id, status, reviewedBy, remarks); err != nil {
		return err
	}

	// Any non-approved lifecycle state invalidates server-side sessions immediately.
	if status != domain.AccountStatusApproved {
		if err := s.repo.RevokeUserRefreshTokens(ctx, id); err != nil {
			return fmt.Errorf("failed to revoke user sessions: %w", err)
		}
	}

	if s.auditRepo != nil {
		action := map[domain.AccountStatus]string{
			domain.AccountStatusApproved:  "VERIFY_USER",
			domain.AccountStatusRejected:  "REJECT_USER",
			domain.AccountStatusSuspended: "SUSPEND_USER",
		}[status]
		_ = s.auditRepo.LogAction(ctx, &domain.AuditLog{
			ActorID:    reviewedBy,
			Action:     action,
			EntityType: "USER",
			EntityID:   id,
			OldValue:   string(before.AccountStatus),
			NewValue:   string(status),
		})
	}

	return nil
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
		if t.Method.Alg() != jwt.SigningMethodHS256.Alg() {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return s.jwtSecret, nil
	})

	if err != nil || !token.Valid {
		return nil, domain.ErrInvalidToken
	}

	claims, ok := token.Claims.(*domain.AuthClaims)
	if !ok || claims.UserID <= 0 {
		return nil, domain.ErrInvalidToken
	}

	// JWTs are intentionally stateless for normal requests, but account lifecycle
	// changes (suspend/reject/delete/role change) must take effect immediately.
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	user, err := s.repo.GetUserByID(ctx, claims.UserID)
	if err != nil || !isUserSessionAllowed(user) {
		return nil, domain.ErrInvalidToken
	}
	if user.Username != claims.Username || user.Role != claims.Role || user.DeviceID != claims.DeviceID {
		return nil, domain.ErrInvalidToken
	}

	return claims, nil
}

func isUserSessionAllowed(user *domain.User) bool {
	if user == nil || user.AccountStatus != domain.AccountStatusApproved {
		return false
	}
	return user.Role == domain.RoleAdmin || user.IsVerified
}

func newRefreshToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func hashRefreshToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func (s *authService) getRefreshTokenCompatible(ctx context.Context, raw string) (*domain.RefreshToken, error) {
	if token, err := s.repo.GetRefreshToken(ctx, hashRefreshToken(raw)); err == nil {
		return token, nil
	}
	// Transitional fallback for tokens issued before refresh-token hashing.
	return s.repo.GetRefreshToken(ctx, raw)
}

func (s *authService) AddUserDevice(ctx context.Context, userID int64, deviceHardwareID, deviceName, platform string) (*domain.UserDevice, error) {
	deviceHardwareID = strings.TrimSpace(deviceHardwareID)
	if deviceHardwareID == "" {
		return nil, fmt.Errorf("device_hardware_id is required")
	}
	return s.repo.AddUserDevice(ctx, &domain.UserDevice{
		UserID:           userID,
		DeviceHardwareID: deviceHardwareID,
		DeviceName:       strings.TrimSpace(deviceName),
		Platform:         strings.TrimSpace(platform),
	})
}

func (s *authService) ListUserDevices(ctx context.Context, userID int64) ([]*domain.UserDevice, error) {
	return s.repo.ListUserDevices(ctx, userID)
}

func (s *authService) DeleteUserDevice(ctx context.Context, deviceID int64, userID int64) error {
	return s.repo.DeleteUserDevice(ctx, deviceID, userID)
}

func (s *authService) AddEmergencyContact(ctx context.Context, userID int64, req domain.EmergencyContactRequest) (*domain.EmergencyContact, error) {
	isPrimary := req.IsPrimary != nil && *req.IsPrimary
	contact := &domain.EmergencyContact{
		UserID:       userID,
		ContactName:  req.ContactName,
		PhoneNumber:  req.PhoneNumber,
		Relationship: req.Relationship,
		IsPrimary:    isPrimary,
	}
	return s.repo.AddEmergencyContact(ctx, contact)
}

func (s *authService) ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error) {
	return s.repo.ListEmergencyContacts(ctx, userID)
}

func (s *authService) UpdateEmergencyContact(ctx context.Context, contactID int64, userID int64, req domain.EmergencyContactRequest) (*domain.EmergencyContact, error) {
	if strings.TrimSpace(req.ContactName) == "" || strings.TrimSpace(req.PhoneNumber) == "" {
		return nil, fmt.Errorf("contact_name and phone_number are required")
	}
	isPrimary := false
	if req.IsPrimary != nil {
		isPrimary = *req.IsPrimary
	} else {
		contacts, err := s.repo.ListEmergencyContacts(ctx, userID)
		if err != nil {
			return nil, err
		}
		for _, contact := range contacts {
			if contact.ID == contactID {
				isPrimary = contact.IsPrimary
				break
			}
		}
	}
	return s.repo.UpdateEmergencyContact(ctx, &domain.EmergencyContact{
		ID:           contactID,
		UserID:       userID,
		ContactName:  strings.TrimSpace(req.ContactName),
		PhoneNumber:  strings.TrimSpace(req.PhoneNumber),
		Relationship: strings.TrimSpace(req.Relationship),
		IsPrimary:    isPrimary,
	})
}

func (s *authService) SetPrimaryEmergencyContact(ctx context.Context, contactID int64, userID int64) (*domain.EmergencyContact, error) {
	return s.repo.SetPrimaryEmergencyContact(ctx, contactID, userID)
}

func (s *authService) DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error {
	return s.repo.DeleteEmergencyContact(ctx, contactID, userID)
}
