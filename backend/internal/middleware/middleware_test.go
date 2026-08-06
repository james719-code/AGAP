package middleware_test

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
)

type mockAuthService struct{}

func (m *mockAuthService) RegisterUser(ctx context.Context, username, password string, role domain.Role, deviceID string, agencyID *int64, name, birthday, gender, phoneNumber, email, medicalInfo, nationalID string) (*domain.User, error) {
	return nil, nil
}
func (m *mockAuthService) Login(ctx context.Context, req domain.LoginRequest) (*domain.LoginResponse, error) {
	return nil, nil
}
func (m *mockAuthService) RefreshToken(ctx context.Context, refreshStr string) (*domain.LoginResponse, error) {
	return nil, nil
}
func (m *mockAuthService) Logout(ctx context.Context, refreshStr string, userID int64) error {
	return nil
}
func (m *mockAuthService) GetUserByID(ctx context.Context, id int64) (*domain.User, error) {
	return nil, nil
}
func (m *mockAuthService) ListUsers(ctx context.Context, role *domain.Role) ([]*domain.User, error) {
	return nil, nil
}
func (m *mockAuthService) UpdateProfile(ctx context.Context, userID int64, req domain.UpdateProfileRequest) (*domain.User, error) {
	return nil, nil
}
func (m *mockAuthService) DeleteUser(ctx context.Context, id int64) error {
	return nil
}
func (m *mockAuthService) VerifyUserAccount(ctx context.Context, id int64) error {
	return nil
}
func (m *mockAuthService) ValidateToken(tokenStr string) (*domain.AuthClaims, error) {
	if tokenStr == "valid-token" {
		return &domain.AuthClaims{UserID: 1, Username: "admin", Role: domain.RoleAdmin}, nil
	}
	return nil, domain.ErrInvalidToken
}
func (m *mockAuthService) GenerateTokenForUser(user *domain.User) (string, time.Time, error) {
	return "valid-token", time.Now().Add(time.Hour), nil
}
func (m *mockAuthService) AddEmergencyContact(ctx context.Context, userID int64, req domain.EmergencyContactRequest) (*domain.EmergencyContact, error) {
	return nil, nil
}
func (m *mockAuthService) ListEmergencyContacts(ctx context.Context, userID int64) ([]*domain.EmergencyContact, error) {
	return nil, nil
}
func (m *mockAuthService) DeleteEmergencyContact(ctx context.Context, contactID int64, userID int64) error {
	return nil
}


func TestJWTAuthMiddleware(t *testing.T) {
	mockAuth := &mockAuthService{}
	jwtMW := middleware.JWTAuth(mockAuth)

	handler := jwtMW(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		claims, ok := middleware.GetClaims(r.Context())
		if !ok || claims.Username != "admin" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))

	// Test missing header
	req := httptest.NewRequest("GET", "/api/v1/incidents", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for missing token, got %d", rec.Code)
	}

	// Test valid bearer token
	req = httptest.NewRequest("GET", "/api/v1/incidents", nil)
	req.Header.Set("Authorization", "Bearer valid-token")
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Errorf("expected 200 OK for valid token, got %d", rec.Code)
	}
}

func TestHMACVerifyMiddleware(t *testing.T) {
	secretKey := "0123456789abcdef0123456789abcdef"
	hmacMW := middleware.HMACVerify(secretKey)

	handler := hmacMW(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	body := `{"packet_id":"test-001"}`

	// Compute valid HMAC
	h := hmac.New(sha256.New, []byte(secretKey))
	h.Write([]byte(body))
	sig := hex.EncodeToString(h.Sum(nil))

	// Test with valid HMAC signature
	req := httptest.NewRequest("POST", "/api/v1/telemetry/uplink", strings.NewReader(body))
	req.Header.Set("X-AGAP-Signature", sig)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("expected 200 OK for valid HMAC signature, got %d", rec.Code)
	}

	// Test with invalid signature
	req = httptest.NewRequest("POST", "/api/v1/telemetry/uplink", strings.NewReader(body))
	req.Header.Set("X-AGAP-Signature", "bad-signature")
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for bad HMAC signature, got %d", rec.Code)
	}
}
