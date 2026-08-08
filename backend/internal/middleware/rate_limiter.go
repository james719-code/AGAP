package middleware

import (
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

const rateLimiterVisitorTTL = 10 * time.Minute

type visitor struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

type RateLimiter struct {
	visitors    map[string]*visitor
	mu          sync.Mutex
	rps         rate.Limit
	burst       int
	lastCleanup time.Time
}

func NewRateLimiter(rps float64, burst int) *RateLimiter {
	return &RateLimiter{
		visitors:    make(map[string]*visitor),
		rps:         rate.Limit(rps),
		burst:       burst,
		lastCleanup: time.Now(),
	}
}

func (rl *RateLimiter) getVisitor(ip string) *rate.Limiter {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	if now.Sub(rl.lastCleanup) >= time.Minute {
		for key, v := range rl.visitors {
			if now.Sub(v.lastSeen) > rateLimiterVisitorTTL {
				delete(rl.visitors, key)
			}
		}
		rl.lastCleanup = now
	}

	v, exists := rl.visitors[ip]
	if !exists {
		limiter := rate.NewLimiter(rl.rps, rl.burst)
		rl.visitors[ip] = &visitor{limiter: limiter, lastSeen: now}
		return limiter
	}
	v.lastSeen = now
	return v.limiter
}

func (rl *RateLimiter) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip := requestClientIP(r)
		limiter := rl.getVisitor(ip)
		if !limiter.Allow() {
			w.Header().Set("Retry-After", "1")
			http.Error(w, `{"error":"rate limit exceeded"}`, http.StatusTooManyRequests)
			return
		}

		next.ServeHTTP(w, r)
	})
}

// requestClientIP only trusts proxy headers when the immediate peer is loopback.
// This supports the bundled local Nginx deployment without allowing arbitrary
// direct clients to spoof their source address.
func requestClientIP(r *http.Request) string {
	remoteIP := r.RemoteAddr
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		remoteIP = host
	}

	parsedRemote := net.ParseIP(strings.TrimSpace(remoteIP))
	if parsedRemote != nil && parsedRemote.IsLoopback() {
		if realIP := strings.TrimSpace(r.Header.Get("X-Real-IP")); realIP != "" {
			if parsed := net.ParseIP(realIP); parsed != nil {
				return parsed.String()
			}
		}
		if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
			first := strings.TrimSpace(strings.Split(forwarded, ",")[0])
			if parsed := net.ParseIP(first); parsed != nil {
				return parsed.String()
			}
		}
	}

	if parsedRemote != nil {
		return parsedRemote.String()
	}
	return remoteIP
}
