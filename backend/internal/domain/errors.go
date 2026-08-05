package domain

import "errors"

var (
	ErrDuplicatePacket   = errors.New("duplicate telemetry packet")
	ErrInvalidHMAC       = errors.New("HMAC signature verification failed")
	ErrInvalidTargetID   = errors.New("invalid spatial target ID format")
	ErrPacketExpired     = errors.New("telemetry packet TTL expired")
	ErrNotFound          = errors.New("resource not found")
	ErrUnauthorized      = errors.New("unauthorized request")
	ErrInvalidToken      = errors.New("invalid or expired JWT token")
	ErrInvalidCredentials = errors.New("invalid authentication credentials")
	ErrAlreadyExists     = errors.New("resource already exists")
)
