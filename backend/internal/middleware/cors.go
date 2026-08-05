package middleware

import (
	"net/http"
)

func CORS(allowedOrigins []string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			if origin != "" {
				allowed := false
				for _, o := range allowedOrigins {
					if o == "*" || o == origin {
						allowed = true
						w.Header().Set("Access-Control-Allow-Origin", origin)
						break
					}
				}
				if !allowed && len(allowedOrigins) > 0 {
					w.Header().Set("Access-Control-Allow-Origin", allowedOrigins[0])
				}
			} else {
				w.Header().Set("Access-Control-Allow-Origin", "*")
			}

			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-AGAP-Signature, X-Device-ID")
			w.Header().Set("Access-Control-Allow-Credentials", "true")

			if r.Method == http.MethodOptions {
				w.WriteHeader(http.StatusOK)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
