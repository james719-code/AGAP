package handler

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/middleware"
	"github.com/travelingsalesman/agap/backend/internal/platform/response"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type AuthHandler struct {
	authSvc service.AuthService
}

func NewAuthHandler(authSvc service.AuthService) *AuthHandler {
	return &AuthHandler{authSvc: authSvc}
}

type RegisterRequest struct {
	Username     string      `json:"username"`
	Password     string      `json:"password"`
	Role         domain.Role `json:"role"`
	DeviceID     string      `json:"device_id,omitempty"`
	AgencyID     *int64      `json:"agency_id,omitempty"`
	RescueUnitID *int64      `json:"rescue_unit_id,omitempty"`
	Name         string      `json:"name,omitempty"`
	Birthday     string      `json:"birthday,omitempty"`
	Gender       string      `json:"gender,omitempty"`
	PhoneNumber  string      `json:"phone_number,omitempty"`
	Email        string      `json:"email,omitempty"`
	MedicalInfo  string      `json:"medical_info,omitempty"`
	NationalID   string      `json:"national_id,omitempty"`
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid request payload")
		return
	}

	if req.Username == "" || req.Password == "" {
		response.JSONError(w, http.StatusBadRequest, "username and password are required")
		return
	}

	if req.Role == "" {
		req.Role = domain.RoleCitizen
	}
	if req.Role == domain.RoleAdmin {
		response.JSONError(w, http.StatusForbidden, "admin accounts cannot be self-registered")
		return
	}
	if req.Role != domain.RoleCitizen && req.Role != domain.RoleResponder {
		response.JSONError(w, http.StatusBadRequest, "role must be CITIZEN or RESPONDER")
		return
	}

	user, err := h.authSvc.RegisterUser(r.Context(), req.Username, req.Password, req.Role, req.DeviceID, req.AgencyID, req.Name, req.Birthday, req.Gender, req.PhoneNumber, req.Email, req.MedicalInfo, req.NationalID, req.RescueUnitID)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, map[string]interface{}{
		"message": "account registered and pending admin verification",
		"user":    user,
	})
}

func (h *AuthHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	roleParam := r.URL.Query().Get("role")
	var rolePtr *domain.Role
	if roleParam != "" {
		rVal := domain.Role(roleParam)
		rolePtr = &rVal
	}

	users, err := h.authSvc.ListUsers(r.Context(), rolePtr)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, users)
}

// GetUserByID returns the complete admin view of one account, including
// registered devices and emergency contacts loaded by the repository.
func (h *AuthHandler) GetUserByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil || id <= 0 {
		response.JSONError(w, http.StatusBadRequest, "invalid user ID")
		return
	}

	user, err := h.authSvc.GetUserByID(r.Context(), id)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "user not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, "failed to load user")
		return
	}

	response.JSONResponse(w, http.StatusOK, user)
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req domain.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid request payload")
		return
	}

	res, err := h.authSvc.Login(r.Context(), req)
	if err != nil {
		switch err {
		case domain.ErrAccountUnverified:
			response.JSONError(w, http.StatusForbidden, "account is pending admin verification")
			return
		case domain.ErrAccountRejected:
			response.JSONError(w, http.StatusForbidden, "account registration was rejected")
			return
		case domain.ErrAccountSuspended:
			response.JSONError(w, http.StatusForbidden, "account is suspended")
			return
		}
		response.JSONError(w, http.StatusUnauthorized, "invalid username or password")
		return
	}

	response.JSONResponse(w, http.StatusOK, res)
}

func (h *AuthHandler) RefreshToken(w http.ResponseWriter, r *http.Request) {
	var req domain.RefreshTokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.RefreshToken == "" {
		response.JSONError(w, http.StatusBadRequest, "refresh_token is required")
		return
	}

	res, err := h.authSvc.RefreshToken(r.Context(), req.RefreshToken)
	if err != nil {
		response.JSONError(w, http.StatusUnauthorized, "invalid or expired refresh token")
		return
	}

	response.JSONResponse(w, http.StatusOK, res)
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req domain.RefreshTokenRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	claims, ok := middleware.GetClaims(r.Context())
	userID := int64(0)
	if ok && claims != nil {
		userID = claims.UserID
	}

	_ = h.authSvc.Logout(r.Context(), req.RefreshToken, userID)
	response.JSONResponse(w, http.StatusOK, map[string]string{"message": "logged out successfully"})
}

func (h *AuthHandler) GetProfile(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	user, err := h.authSvc.GetUserByID(r.Context(), claims.UserID)
	if err != nil {
		response.JSONError(w, http.StatusNotFound, "user not found")
		return
	}

	response.JSONResponse(w, http.StatusOK, user)
}

func (h *AuthHandler) UpdateProfile(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req domain.UpdateProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}
	// Role and agency assignment are administrative operations, never
	// self-service profile fields.
	req.Role = nil
	req.AgencyID = nil

	updated, err := h.authSvc.UpdateProfile(r.Context(), claims.UserID, req)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, updated)
}

func (h *AuthHandler) UpdateUserByID(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid user ID")
		return
	}

	var req domain.UpdateProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}

	updated, err := h.authSvc.UpdateProfile(r.Context(), id, req)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, updated)
}

func (h *AuthHandler) DeleteUser(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid user ID")
		return
	}
	if claims, ok := middleware.GetClaims(r.Context()); ok && claims != nil && claims.UserID == id {
		response.JSONError(w, http.StatusBadRequest, "cannot delete your own administrator account")
		return
	}

	if err := h.authSvc.DeleteUser(r.Context(), id); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "user not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *AuthHandler) VerifyUser(w http.ResponseWriter, r *http.Request) {
	var req struct {
		UserID int64 `json:"user_id"`
	}
	idStr := chi.URLParam(r, "id")
	if idStr != "" {
		if parsedID, err := strconv.ParseInt(idStr, 10, 64); err == nil {
			req.UserID = parsedID
		}
	}
	if req.UserID == 0 {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.UserID == 0 {
		response.JSONError(w, http.StatusBadRequest, "user_id is required")
		return
	}

	if err := h.authSvc.VerifyUserAccount(r.Context(), req.UserID); err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"message": "user account verified successfully",
		"user_id": req.UserID,
	})
}

func (h *AuthHandler) UpdateUserAccountStatus(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	userID, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil || userID == 0 {
		response.JSONError(w, http.StatusBadRequest, "invalid user ID")
		return
	}

	var req struct {
		Status  domain.AccountStatus `json:"status"`
		Remarks string               `json:"remarks,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}

	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	reviewerID := claims.UserID
	if reviewerID == userID && req.Status != domain.AccountStatusApproved {
		response.JSONError(w, http.StatusBadRequest, "cannot disable your own administrator account")
		return
	}

	if err := h.authSvc.UpdateUserAccountStatus(r.Context(), userID, req.Status, &reviewerID, req.Remarks); err != nil {
		switch err {
		case domain.ErrNotFound:
			response.JSONError(w, http.StatusNotFound, "user not found")
		default:
			response.JSONError(w, http.StatusBadRequest, err.Error())
		}
		return
	}

	response.JSONResponse(w, http.StatusOK, map[string]interface{}{
		"message": "user account status updated successfully",
		"user_id": userID,
		"status":  req.Status,
	})
}

func (h *AuthHandler) AddUserDevice(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req struct {
		DeviceHardwareID string `json:"device_hardware_id"`
		DeviceName       string `json:"device_name,omitempty"`
		Platform         string `json:"platform,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.DeviceHardwareID == "" {
		response.JSONError(w, http.StatusBadRequest, "device_hardware_id is required")
		return
	}

	device, err := h.authSvc.AddUserDevice(r.Context(), claims.UserID, req.DeviceHardwareID, req.DeviceName, req.Platform)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, device)
}

func (h *AuthHandler) ListUserDevices(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	devices, err := h.authSvc.ListUserDevices(r.Context(), claims.UserID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, devices)
}

func (h *AuthHandler) DeleteUserDevice(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	deviceIDStr := chi.URLParam(r, "id")
	deviceID, err := strconv.ParseInt(deviceIDStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid device ID")
		return
	}

	if err := h.authSvc.DeleteUserDevice(r.Context(), deviceID, claims.UserID); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "device not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *AuthHandler) AddEmergencyContact(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req domain.EmergencyContactRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.ContactName == "" || req.PhoneNumber == "" {
		response.JSONError(w, http.StatusBadRequest, "contact_name and phone_number are required")
		return
	}

	contact, err := h.authSvc.AddEmergencyContact(r.Context(), claims.UserID, req)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusCreated, contact)
}

func (h *AuthHandler) ListEmergencyContacts(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	contacts, err := h.authSvc.ListEmergencyContacts(r.Context(), claims.UserID)
	if err != nil {
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, contacts)
}

func (h *AuthHandler) DeleteEmergencyContact(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	contactIDStr := chi.URLParam(r, "id")
	contactID, err := strconv.ParseInt(contactIDStr, 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid contact ID")
		return
	}

	if err := h.authSvc.DeleteEmergencyContact(r.Context(), contactID, claims.UserID); err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "emergency contact not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (h *AuthHandler) UpdateEmergencyContact(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	contactID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid contact ID")
		return
	}

	var req domain.EmergencyContactRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid payload")
		return
	}

	contact, err := h.authSvc.UpdateEmergencyContact(r.Context(), contactID, claims.UserID, req)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "emergency contact not found")
			return
		}
		response.JSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, contact)
}

func (h *AuthHandler) SetPrimaryEmergencyContact(w http.ResponseWriter, r *http.Request) {
	claims, ok := middleware.GetClaims(r.Context())
	if !ok || claims == nil {
		response.JSONError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	contactID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.JSONError(w, http.StatusBadRequest, "invalid contact ID")
		return
	}

	contact, err := h.authSvc.SetPrimaryEmergencyContact(r.Context(), contactID, claims.UserID)
	if err != nil {
		if err == domain.ErrNotFound {
			response.JSONError(w, http.StatusNotFound, "emergency contact not found")
			return
		}
		response.JSONError(w, http.StatusInternalServerError, err.Error())
		return
	}

	response.JSONResponse(w, http.StatusOK, contact)
}
