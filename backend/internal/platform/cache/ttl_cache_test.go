package cache_test

import (
	"testing"
	"time"

	"github.com/travelingsalesman/agap/backend/internal/platform/cache"
)

func TestTTLCache(t *testing.T) {
	c := cache.NewTTLCache(100 * time.Millisecond)

	key := "packet-hash-123"
	if c.Exists(key) {
		t.Fatalf("expected key to not exist initially")
	}

	c.Set(key)
	if !c.Exists(key) {
		t.Fatalf("expected key to exist after Set")
	}

	time.Sleep(150 * time.Millisecond)
	if c.Exists(key) {
		t.Fatalf("expected key to expire after TTL")
	}
}
