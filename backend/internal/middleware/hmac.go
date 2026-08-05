package middleware

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
)

func HMACVerify(secretKey string) func(http.Handler) http.Handler {
	key := []byte(secretKey)
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			signature := r.Header.Get("X-AGAP-Signature")
			if signature == "" {
				// Allow dev / web telemetry simulation payloads when signature is omitted
				next.ServeHTTP(w, r)
				return
			}

			bodyBytes, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, `{"error":"failed to read request body"}`, http.StatusBadRequest)
				return
			}
			// Restore request body for downstream handlers
			r.Body = io.NopCloser(bytes.NewBuffer(bodyBytes))

			h := hmac.New(sha256.New, key)
			h.Write(bodyBytes)
			expectedSig := hex.EncodeToString(h.Sum(nil))

			if !hmac.Equal([]byte(signature), []byte(expectedSig)) {
				http.Error(w, `{"error":"invalid HMAC signature"}`, http.StatusUnauthorized)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
