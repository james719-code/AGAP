package cache

import (
	"context"
	"sync"
	"time"
)

type cacheEntry struct {
	expiresAt time.Time
}

type TTLCache struct {
	entries    sync.Map
	defaultTTL time.Duration
}

func NewTTLCache(defaultTTL time.Duration) *TTLCache {
	return &TTLCache{
		defaultTTL: defaultTTL,
	}
}

func (c *TTLCache) Set(key string) {
	c.SetWithTTL(key, c.defaultTTL)
}

func (c *TTLCache) SetWithTTL(key string, ttl time.Duration) {
	c.entries.Store(key, cacheEntry{
		expiresAt: time.Now().Add(ttl),
	})
}

func (c *TTLCache) Exists(key string) bool {
	val, ok := c.entries.Load(key)
	if !ok {
		return false
	}
	entry := val.(cacheEntry)
	if time.Now().After(entry.expiresAt) {
		c.entries.Delete(key)
		return false
	}
	return true
}

func (c *TTLCache) StartCleanup(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			now := time.Now()
			c.entries.Range(func(key, val interface{}) bool {
				entry := val.(cacheEntry)
				if now.After(entry.expiresAt) {
					c.entries.Delete(key)
				}
				return true
			})
		}
	}
}
