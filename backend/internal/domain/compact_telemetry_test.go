package domain_test

import (
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

func TestCompactUpwardPacket_EncodingAndDecoding(t *testing.T) {
	orig := domain.CompactUpwardPacket{
		Latitude:       13.1391,
		Longitude:      123.7438,
		ReporterID:     101,
		TargetAgencyID: 1,
		AccidentTypeID: 2,
	}

	encoded, err := orig.Encode()
	if err != nil {
		t.Fatalf("failed to encode upward packet: %v", err)
	}

	if len(encoded) != 12 {
		t.Fatalf("expected encoded length 12, got %d", len(encoded))
	}

	decoded, err := domain.DecodeCompactUpward(encoded)
	if err != nil {
		t.Fatalf("failed to decode upward packet: %v", err)
	}

	if decoded.ReporterID != orig.ReporterID {
		t.Errorf("expected ReporterID %d, got %d", orig.ReporterID, decoded.ReporterID)
	}
	if decoded.TargetAgencyID != orig.TargetAgencyID {
		t.Errorf("expected TargetAgencyID %d, got %d", orig.TargetAgencyID, decoded.TargetAgencyID)
	}
	if decoded.AccidentTypeID != orig.AccidentTypeID {
		t.Errorf("expected AccidentTypeID %d, got %d", orig.AccidentTypeID, decoded.AccidentTypeID)
	}
}

func TestCompactDownwardPacket_EncodingAndDecoding(t *testing.T) {
	orig := domain.CompactDownwardPacket{
		ReceiverUserID:  101,
		PredefinedMsgID: 1,
		RescueUnitID:    12,
	}

	encoded, err := orig.Encode()
	if err != nil {
		t.Fatalf("failed to encode downward packet: %v", err)
	}

	if len(encoded) != 8 {
		t.Fatalf("expected encoded length 8, got %d", len(encoded))
	}

	decoded, err := domain.DecodeCompactDownward(encoded)
	if err != nil {
		t.Fatalf("failed to decode downward packet: %v", err)
	}

	if decoded.ReceiverUserID != orig.ReceiverUserID {
		t.Errorf("expected ReceiverUserID %d, got %d", orig.ReceiverUserID, decoded.ReceiverUserID)
	}
	if decoded.PredefinedMsgID != orig.PredefinedMsgID {
		t.Errorf("expected PredefinedMsgID %d, got %d", orig.PredefinedMsgID, decoded.PredefinedMsgID)
	}
	if decoded.RescueUnitID != orig.RescueUnitID {
		t.Errorf("expected RescueUnitID %d, got %d", orig.RescueUnitID, decoded.RescueUnitID)
	}
}

func TestCalculatePacketHash_Deduplication(t *testing.T) {
	pkt1 := domain.CompactUpwardPacket{
		Latitude:       13.1391,
		Longitude:      123.7438,
		ReporterID:     101,
		TargetAgencyID: 1,
		AccidentTypeID: 2,
	}

	pkt2 := domain.CompactUpwardPacket{
		Latitude:       13.1391,
		Longitude:      123.7438,
		ReporterID:     101,
		TargetAgencyID: 1,
		AccidentTypeID: 2,
	}

	hash1 := pkt1.CalculatePacketHash()
	hash2 := pkt2.CalculatePacketHash()

	if hash1 != hash2 {
		t.Errorf("identical packets generated different hashes: %s vs %s", hash1, hash2)
	}
}
