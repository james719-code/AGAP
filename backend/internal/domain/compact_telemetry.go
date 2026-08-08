package domain

import (
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"fmt"
)

var AGAPHeader = []byte{0x41, 0x47} // "AG"

type PacketType byte

const (
	PacketTypeUpwardSOS   PacketType = 1
	PacketTypeDownwardAck PacketType = 2
	PacketTypeMeshBeacon  PacketType = 3
)

// Deprecated: CompactUpwardPacket represents legacy v1 12-byte Upward Distress Signal over BLE.
// Protocol v2 uses 21-byte E7 telemetry (BleEmergencyPacket). Retained as backward-compatibility adapter.
// Format: [Header:2B] [PacketType:1B] [Lat/Lon:4B] [ReporterID:2B] [TargetAgencyID:2B] [EmergencyTypeID:1B]
type CompactUpwardPacket struct {
	LatShort        int16   `json:"lat_short"`
	LonShort        int16   `json:"lon_short"`
	Latitude        float64 `json:"latitude"`
	Longitude       float64 `json:"longitude"`
	ReporterID      uint16  `json:"reporter_id"`
	TargetAgencyID  uint16  `json:"target_agency_id"`
	EmergencyTypeID uint8   `json:"emergency_type_id"`
}

// CompactDownwardPacket represents a 6-byte Downward Ack/Dispatch Signal over BLE
// Format: [Header:2B] [PacketType:1B] [ReceiverUserID:2B] [PredefinedMsgID:1B] [RescueUnitID:2B]
type CompactDownwardPacket struct {
	ReceiverUserID  uint16 `json:"receiver_user_id"`
	PredefinedMsgID uint8  `json:"predefined_msg_id"`
	RescueUnitID    uint16 `json:"rescue_unit_id"`
}

func EncodeLat(lat float64) int16 {
	if lat < 4.0 {
		lat = 4.0
	} else if lat > 21.0 {
		lat = 21.0
	}
	return int16((lat - 4.0) * 3000.0)
}

func DecodeLat(latShort int16) float64 {
	raw := uint16(latShort)
	return 4.0 + (float64(raw) / 3000.0)
}

func EncodeLon(lon float64) int16 {
	if lon < 116.0 {
		lon = 116.0
	} else if lon > 127.0 {
		lon = 127.0
	}
	return int16((lon - 116.0) * 3000.0)
}

func DecodeLon(lonShort int16) float64 {
	raw := uint16(lonShort)
	return 116.0 + (float64(raw) / 3000.0)
}

func (pkt *CompactUpwardPacket) Encode() ([]byte, error) {
	buf := new(bytes.Buffer)
	buf.Write(AGAPHeader)
	buf.WriteByte(byte(PacketTypeUpwardSOS))

	if err := binary.Write(buf, binary.BigEndian, EncodeLat(pkt.Latitude)); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.BigEndian, EncodeLon(pkt.Longitude)); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.BigEndian, pkt.ReporterID); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.BigEndian, pkt.TargetAgencyID); err != nil {
		return nil, err
	}
	buf.WriteByte(pkt.EmergencyTypeID)

	return buf.Bytes(), nil
}

func DecodeCompactUpward(data []byte) (*CompactUpwardPacket, error) {
	if len(data) < 12 {
		return nil, fmt.Errorf("compact upward packet too short: expected 12 bytes, got %d", len(data))
	}
	if data[0] != AGAPHeader[0] || data[1] != AGAPHeader[1] {
		return nil, fmt.Errorf("invalid header prefix")
	}
	if data[2] != byte(PacketTypeUpwardSOS) {
		return nil, fmt.Errorf("expected packet type 1 (SOS), got %d", data[2])
	}

	reader := bytes.NewReader(data[3:])
	var pkt CompactUpwardPacket

	if err := binary.Read(reader, binary.BigEndian, &pkt.LatShort); err != nil {
		return nil, err
	}
	if err := binary.Read(reader, binary.BigEndian, &pkt.LonShort); err != nil {
		return nil, err
	}
	if err := binary.Read(reader, binary.BigEndian, &pkt.ReporterID); err != nil {
		return nil, err
	}
	if err := binary.Read(reader, binary.BigEndian, &pkt.TargetAgencyID); err != nil {
		return nil, err
	}
	var emergencyType uint8
	if err := binary.Read(reader, binary.BigEndian, &emergencyType); err != nil {
		return nil, err
	}
	pkt.EmergencyTypeID = emergencyType

	pkt.Latitude = DecodeLat(pkt.LatShort)
	pkt.Longitude = DecodeLon(pkt.LonShort)

	return &pkt, nil
}

func (pkt *CompactDownwardPacket) Encode() ([]byte, error) {
	buf := new(bytes.Buffer)
	buf.Write(AGAPHeader)
	buf.WriteByte(byte(PacketTypeDownwardAck))

	if err := binary.Write(buf, binary.BigEndian, pkt.ReceiverUserID); err != nil {
		return nil, err
	}
	buf.WriteByte(pkt.PredefinedMsgID)
	if err := binary.Write(buf, binary.BigEndian, pkt.RescueUnitID); err != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}

func DecodeCompactDownward(data []byte) (*CompactDownwardPacket, error) {
	if len(data) < 8 {
		return nil, fmt.Errorf("compact downward packet too short: expected 8 bytes, got %d", len(data))
	}
	if data[0] != AGAPHeader[0] || data[1] != AGAPHeader[1] {
		return nil, fmt.Errorf("invalid header prefix")
	}
	if data[2] != byte(PacketTypeDownwardAck) {
		return nil, fmt.Errorf("expected packet type 2 (ACK), got %d", data[2])
	}

	reader := bytes.NewReader(data[3:])
	var pkt CompactDownwardPacket

	if err := binary.Read(reader, binary.BigEndian, &pkt.ReceiverUserID); err != nil {
		return nil, err
	}
	var msgID uint8
	if err := binary.Read(reader, binary.BigEndian, &msgID); err != nil {
		return nil, err
	}
	pkt.PredefinedMsgID = msgID
	if err := binary.Read(reader, binary.BigEndian, &pkt.RescueUnitID); err != nil {
		return nil, err
	}

	return &pkt, nil
}

// CalculatePacketHash generates a deterministic unique hash for emergency deduplication.
func (pkt *CompactUpwardPacket) CalculatePacketHash() string {
	h := sha256.New()
	h.Write([]byte(fmt.Sprintf("%d-%d-%d-%.4f-%.4f", pkt.ReporterID, pkt.TargetAgencyID, pkt.EmergencyTypeID, pkt.Latitude, pkt.Longitude)))
	return fmt.Sprintf("INC-%x", h.Sum(nil)[:8])
}
