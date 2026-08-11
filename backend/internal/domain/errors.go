package domain

import "errors"

var (
	ErrDuplicatePacket           = errors.New("duplicate telemetry packet")
	ErrInvalidHMAC               = errors.New("HMAC signature verification failed")
	ErrInvalidTargetID           = errors.New("invalid spatial target ID format")
	ErrPacketExpired             = errors.New("telemetry packet TTL expired")
	ErrNotFound                  = errors.New("resource not found")
	ErrUnauthorized              = errors.New("unauthorized request")
	ErrInvalidToken              = errors.New("invalid or expired JWT token")
	ErrInvalidCredentials        = errors.New("invalid authentication credentials")
	ErrAccountUnverified         = errors.New("account is pending admin verification")
	ErrAccountRejected           = errors.New("account registration was rejected")
	ErrAccountSuspended          = errors.New("account is suspended")
	ErrInvalidRole               = errors.New("invalid role")
	ErrInvalidAccountStatus      = errors.New("invalid account status")
	ErrInvalidStatusTransition   = errors.New("invalid incident status transition")
	ErrInvalidDispatchTransition = errors.New("invalid dispatch status transition")
	ErrInvalidDispatchAgency     = errors.New("responder unit agency type does not match incident emergency type")
	ErrResponderBusy             = errors.New("responder unit already has an active dispatch")
	ErrResponderOffline          = errors.New("responder unit is offline")
	ErrInvalidResponderStatus    = errors.New("invalid responder unit status")
	ErrInvalidEmergencyType      = errors.New("invalid emergency type")
	ErrInvalidMessageCatalog     = errors.New("invalid predefined message catalog entry")
	ErrInvalidMeshDeliveryEvent  = errors.New("invalid mesh delivery event")
	ErrAlreadyExists             = errors.New("resource already exists")
)
