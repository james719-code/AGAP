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

func TestEncodeDecodeResponseBLEPacket(t *testing.T) {
	token := [6]byte{0xAB, 0xCD, 0xEF, 0x12, 0x34, 0x56}
	buf := EncodeResponseBLEPacket(
		2, // ACK
		1, // hopCount
		token,
		6, // INCIDENT_RESOLVED
		54321,
	)

	if len(buf) != 13 {
		t.Fatalf("expected length 13, got %d", len(buf))
	}

	pkt, err := DecodeBLEPacket(buf)
	if err != nil {
		t.Fatalf("unexpected error decoding response packet: %v", err)
	}

	if pkt.ProtocolVersion != 2 {
		t.Errorf("expected version 2, got %d", pkt.ProtocolVersion)
	}
	if pkt.PacketType != 2 {
		t.Errorf("expected PacketType 2 (ACK), got %d", pkt.PacketType)
	}
	if pkt.HopCount != 1 {
		t.Errorf("expected HopCount 1, got %d", pkt.HopCount)
	}
	if pkt.StatusCode != 6 {
		t.Errorf("expected StatusCode 6, got %d", pkt.StatusCode)
	}
	if !bytes.Equal(pkt.TransportToken[:], token[:]) {
		t.Errorf("token mismatch")
	}
}

func TestSharedHexVectorsWithAndroid(t *testing.T) {
	token := [6]byte{0x12, 0x34, 0x56, 0x78, 0x9A, 0xBC}
	buf := EncodeEmergencyBLEPacket(
		0, // SOS
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

	pkt, err := DecodeBLEPacket(buf)
	if err != nil {
		t.Fatalf("failed to decode shared vector: %v", err)
	}

	encodedAgain := EncodeEmergencyBLEPacket(
		pkt.PacketType,
		pkt.EmergencyType,
		pkt.ImmediateDanger,
		pkt.SubjectRelation,
		pkt.HopCount,
		pkt.LocationSource,
		pkt.TransportToken,
		pkt.Latitude,
		pkt.Longitude,
		pkt.AccuracyMeters,
		pkt.CreationTime,
	)

	if !bytes.Equal(buf, encodedAgain) {
		t.Fatalf("roundtrip re-encode mismatch")
	}
}

func mathAbs(f float64) float64 {
	if f < 0 {
		return -f
	}
	return f
}
