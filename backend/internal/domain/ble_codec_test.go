package domain

import (
	"bytes"
	"testing"
)

func TestEncodeDecodeEmergencyBLEPacket(t *testing.T) {
	token := [6]byte{0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC}
	buf := EncodeEmergencyBLEPacket(
		0,
		"FIRE",
		true,
		"OTHER_PERSON",
		3,
		"MAP_PIN",
		token,
		13.1391,
		123.7438,
		255,
		12345,
	)

	if len(buf) != 21 {
		t.Fatalf("expected length 21, got %d", len(buf))
	}

	pkt, err := DecodeBLEPacket(buf)
	if err != nil {
		t.Fatalf("unexpected error decoding packet: %v", err)
	}

	if pkt.ProtocolVersion != 2 {
		t.Errorf("expected version 2, got %d", pkt.ProtocolVersion)
	}
	if pkt.EmergencyType != "FIRE" {
		t.Errorf("expected FIRE, got %s", pkt.EmergencyType)
	}
	if !pkt.ImmediateDanger {
		t.Errorf("expected immediate danger true")
	}
	if pkt.SubjectRelation != "OTHER_PERSON" {
		t.Errorf("expected OTHER_PERSON, got %s", pkt.SubjectRelation)
	}
	if pkt.LocationSource != "MAP_PIN" {
		t.Errorf("expected MAP_PIN, got %s", pkt.LocationSource)
	}
	if !bytes.Equal(pkt.TransportToken[:], token[:]) {
		t.Errorf("token mismatch")
	}
	if mathAbs(pkt.Latitude-13.1391) > 0.0000001 {
		t.Errorf("lat mismatch: %f vs 13.1391", pkt.Latitude)
	}
	if mathAbs(pkt.Longitude-123.7438) > 0.0000001 {
		t.Errorf("lon mismatch: %f vs 123.7438", pkt.Longitude)
	}
}

func mathAbs(f float64) float64 {
	if f < 0 {
		return -f
	}
	return f
}
