package handler

import (
	"encoding/json"
	"net/http"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type AuthHandler struct {
	authSvc service.AuthService
}

func NewAuthHandler(authSvc service.AuthService) *AuthHandler {
	return &AuthHandler{authSvc: authSvc}
}

type RegisterRequest struct {
	Username string      `json:"username"`
	Password string      `json:"password"`
	Role     domain.Role `json:"role"`
	DeviceID string      `json:"device_id,omitempty"`
	AgencyID *int64      `json:"agency_id,omitempty"`
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid request payload"}`, http.StatusBadRequest)
		return
	}

	if req.Username == "" || req.Password == "" {
		http.Error(w, `{"error":"username and password are required"}`, http.StatusBadRequest)
		return
	}

	if req.Role == "" {
		req.Role = domain.RoleCitizen
	}

	user, err := h.authSvc.RegisterUser(r.Context(), req.Username, req.Password, req.Role, req.DeviceID, req.AgencyID)
	if err != nil {
		http.Error(w, `{"error":"`+err.Error()+`"}`, http.StatusBadRequest)
		return
	}

	tokenStr, expiresAt, err := h.authSvc.GenerateTokenForUser(user)
	if err != nil {
		http.Error(w, `{"error":"user registered but token generation failed"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(domain.LoginResponse{
		Token:     tokenStr,
		ExpiresAt: expiresAt,
		User:      *user,
	})
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req domain.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid request payload"}`, http.StatusBadRequest)
		return
	}

	res, err := h.authSvc.Login(r.Context(), req)
	if err != nil {
		http.Error(w, `{"error":"invalid username or password"}`, http.StatusUnauthorized)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(res)
}
