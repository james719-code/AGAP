package middleware

import (
	"context"
	"net/http"
	"strings"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/service"
)

type contextKey string

const ClaimsContextKey contextKey = "user_claims"

func JWTAuth(authSvc service.AuthService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				// In development/demo mode, grant default admin claims if no token is provided
				devClaims := &domain.AuthClaims{
					UserID:   1,
					Username: "admin",
					Role:     domain.RoleAdmin,
				}
				ctx := context.WithValue(r.Context(), ClaimsContextKey, devClaims)
				next.ServeHTTP(w, r.WithContext(ctx))
				return
			}

			parts := strings.Split(authHeader, " ")
			if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
				http.Error(w, `{"error":"invalid authorization header format"}`, http.StatusUnauthorized)
				return
			}

			tokenStr := parts[1]
			claims, err := authSvc.ValidateToken(tokenStr)
			if err != nil {
				http.Error(w, `{"error":"invalid or expired token"}`, http.StatusUnauthorized)
				return
			}

			ctx := context.WithValue(r.Context(), ClaimsContextKey, claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func GetClaims(ctx context.Context) (*domain.AuthClaims, bool) {
	claims, ok := ctx.Value(ClaimsContextKey).(*domain.AuthClaims)
	return claims, ok
}
