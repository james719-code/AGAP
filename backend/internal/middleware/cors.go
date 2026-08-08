package middleware

import "net/http"

func CORS(allowedOrigins []string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := r.Header.Get("Origin")
			allowedOrigin, wildcard := matchAllowedOrigin(origin, allowedOrigins)

			w.Header().Add("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-AGAP-Signature, X-Device-ID")

			if origin != "" && allowedOrigin != "" {
				if wildcard {
					w.Header().Set("Access-Control-Allow-Origin", "*")
				} else {
					w.Header().Set("Access-Control-Allow-Origin", allowedOrigin)
					w.Header().Set("Access-Control-Allow-Credentials", "true")
				}
			}

			if r.Method == http.MethodOptions {
				if origin != "" && allowedOrigin == "" {
					http.Error(w, `{"error":"origin not allowed"}`, http.StatusForbidden)
					return
				}
				w.WriteHeader(http.StatusNoContent)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func matchAllowedOrigin(origin string, allowedOrigins []string) (string, bool) {
	if origin == "" {
		return "", false
	}
	for _, allowed := range allowedOrigins {
		if allowed == "*" {
			return "*", true
		}
		if allowed == origin {
			return origin, false
		}
	}
	return "", false
}
