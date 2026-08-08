package middleware

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"strings"
)

// HMACVerify validates X-AGAP-Signature against the exact HTTP request body.
// The optional requireSignature argument preserves compatibility with development
// clients while allowing deployments to reject unsigned telemetry explicitly.
func HMACVerify(secretKey string, requireSignature ...bool) func(http.Handler) http.Handler {
	key := []byte(secretKey)
	required := len(requireSignature) > 0 && requireSignature[0]

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			signature := strings.TrimSpace(r.Header.Get("X-AGAP-Signature"))
			if signature == "" {
				if required {
					http.Error(w, `{"error":"X-AGAP-Signature header required"}`, http.StatusUnauthorized)
					return
				}
				next.ServeHTTP(w, r)
				return
			}

			providedSig, err := hex.DecodeString(signature)
			if err != nil || len(providedSig) != sha256.Size {
				http.Error(w, `{"error":"invalid HMAC signature"}`, http.StatusUnauthorized)
				return
			}

			bodyBytes, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, `{"error":"failed to read request body"}`, http.StatusBadRequest)
				return
			}
			// Restore request body for downstream handlers.
			r.Body = io.NopCloser(bytes.NewReader(bodyBytes))

			h := hmac.New(sha256.New, key)
			_, _ = h.Write(bodyBytes)
			expectedSig := h.Sum(nil)

			if !hmac.Equal(providedSig, expectedSig) {
				http.Error(w, `{"error":"invalid HMAC signature"}`, http.StatusUnauthorized)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
