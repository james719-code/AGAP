package domain

import (
	"encoding/binary"
	"errors"
	"math"
)

var (
	ErrInvalidLength = errors.New("invalid ble packet length")
	ErrInvalidCRC    = errors.New("crc checksum verification failed")
	ErrInvalidVer    = errors.New("unsupported protocol version")
)

type BLEPacket struct {
	ProtocolVersion uint8
	PacketType      uint8 // 0: SOS, 1: INCIDENT_REPORT, 2: ACK, 3: STATUS
	EmergencyType   string
	ImmediateDanger bool
	SubjectRelation string
	HopCount        uint8
	LocationSource  string
	TransportToken  [6]byte
	Latitude        float64
	Longitude       float64
	AccuracyMeters  uint8
	CreationTime    uint16
	StatusCode      uint8
}

func CRC16CCITT(data []byte) uint16 {
	var crc uint16 = 0xFFFF
	for _, b := range data {
		crc ^= uint16(b) << 8
		for i := 0; i < 8; i++ {
			if (crc & 0x8000) != 0 {
				crc = (crc << 1) ^ 0x1021
			} else {
				crc <<= 1
			}
		}
	}
	return crc
}

func EncodeEmergencyBLEPacket(
	packetType uint8,
	emergencyType string,
	immediateDanger bool,
	subjectRelation string,
	hopCount uint8,
	locationSource string,
	token [6]byte,
	lat, lon float64,
	accuracy uint8,
	timeSlot uint16,
) []byte {
	buf := make([]byte, 21)

	eCode := emergencyTypeToCode(emergencyType)
	dangerBit := byte(0)
	if immediateDanger {
		dangerBit = 1
	}
	buf[0] = (2 << 6) | ((packetType & 0x03) << 4) | ((eCode & 0x07) << 1) | dangerBit

	relCode := subjectRelationToCode(subjectRelation)
	locCode := locationSourceToCode(locationSource)
	buf[1] = ((relCode & 0x03) << 6) | ((hopCount & 0x0F) << 2) | (locCode & 0x03)

	copy(buf[2:8], token[:])

	binary.BigEndian.PutUint32(buf[8:12], uint32(int32(math.Round(lat*10000000.0))))
	binary.BigEndian.PutUint32(buf[12:16], uint32(int32(math.Round(lon*10000000.0))))

	buf[16] = accuracy
	binary.BigEndian.PutUint16(buf[17:19], timeSlot)

	crc := CRC16CCITT(buf[0:19])
	binary.BigEndian.PutUint16(buf[19:21], crc)

	return buf
}

func EncodeResponseBLEPacket(
	packetType uint8,
	hopCount uint8,
	token [6]byte,
	statusCode uint8,
	timeSlot uint16,
) []byte {
	buf := make([]byte, 13)
	buf[0] = (2 << 6) | ((packetType & 0x03) << 4)
	buf[1] = ((hopCount & 0x0F) << 4) | (statusCode & 0x0F)
	copy(buf[2:8], token[:])
	buf[8] = statusCode
	binary.BigEndian.PutUint16(buf[9:11], timeSlot)

	crc := CRC16CCITT(buf[0:11])
	binary.BigEndian.PutUint16(buf[11:13], crc)
	return buf
}

func DecodeBLEPacket(data []byte) (*BLEPacket, error) {
	if len(data) == 21 {
		crcCalc := CRC16CCITT(data[0:19])
		crcExp := binary.BigEndian.Uint16(data[19:21])
		if crcCalc != crcExp {
			return nil, ErrInvalidCRC
		}

		ver := (data[0] >> 6) & 0x03
		if ver != 2 {
			return nil, ErrInvalidVer
		}

		pType := (data[0] >> 4) & 0x03
		eCode := (data[0] >> 1) & 0x07
		danger := (data[0] & 0x01) == 1

		relCode := (data[1] >> 6) & 0x03
		hop := (data[1] >> 2) & 0x0F
		locCode := data[1] & 0x03

		var token [6]byte
		copy(token[:], data[2:8])

		latE7 := int32(binary.BigEndian.Uint32(data[8:12]))
		lonE7 := int32(binary.BigEndian.Uint32(data[12:16]))

		acc := data[16]
		tSlot := binary.BigEndian.Uint16(data[17:19])

		return &BLEPacket{
			ProtocolVersion: ver,
			PacketType:      pType,
			EmergencyType:   codeToEmergencyType(eCode),
			ImmediateDanger: danger,
			SubjectRelation: codeToSubjectRelation(relCode),
			HopCount:        hop,
			LocationSource:  codeToLocationSource(locCode),
			TransportToken:  token,
			Latitude:        float64(latE7) / 10000000.0,
			Longitude:       float64(lonE7) / 10000000.0,
			AccuracyMeters:  acc,
			CreationTime:    tSlot,
		}, nil
	} else if len(data) == 13 {
		crcCalc := CRC16CCITT(data[0:11])
		crcExp := binary.BigEndian.Uint16(data[11:13])
		if crcCalc != crcExp {
			return nil, ErrInvalidCRC
		}

		ver := (data[0] >> 6) & 0x03
		if ver != 2 {
			return nil, ErrInvalidVer
		}

		pType := (data[0] >> 4) & 0x03
		hop := (data[1] >> 4) & 0x0F
		var token [6]byte
		copy(token[:], data[2:8])
		sCode := data[8]
		tSlot := binary.BigEndian.Uint16(data[9:11])

		return &BLEPacket{
			ProtocolVersion: ver,
			PacketType:      pType,
			EmergencyType:   "UNKNOWN",
			HopCount:        hop,
			TransportToken:  token,
			StatusCode:      sCode,
			CreationTime:    tSlot,
		}, nil
	}
	return nil, ErrInvalidLength
}

func emergencyTypeToCode(t string) uint8 {
	switch t {
	case "MEDICAL":
		return 1
	case "ACCIDENT":
		return 2
	case "FIRE":
		return 3
	case "DISASTER":
		return 4
	case "CRIME":
		return 5
	default:
		return 0
	}
}

func codeToEmergencyType(c uint8) string {
	switch c {
	case 1:
		return "MEDICAL"
	case 2:
		return "ACCIDENT"
	case 3:
		return "FIRE"
	case 4:
		return "DISASTER"
	case 5:
		return "CRIME"
	default:
		return "UNKNOWN"
	}
}

func subjectRelationToCode(r string) uint8 {
	switch r {
	case "SELF":
		return 0
	case "OTHER_PERSON":
		return 1
	case "PUBLIC_OR_UNKNOWN":
		return 2
	default:
		return 0
	}
}

func codeToSubjectRelation(c uint8) string {
	switch c {
	case 0:
		return "SELF"
	case 1:
		return "OTHER_PERSON"
	case 2:
		return "PUBLIC_OR_UNKNOWN"
	default:
		return "SELF"
	}
}

func locationSourceToCode(l string) uint8 {
	switch l {
	case "GPS":
		return 0
	case "MAP_PIN":
		return 1
	case "BARANGAY_REFERENCE":
		return 2
	case "UNKNOWN":
		return 3
	default:
		return 3
	}
}

func codeToLocationSource(c uint8) string {
	switch c {
	case 0:
		return "GPS"
	case 1:
		return "MAP_PIN"
	case 2:
		return "BARANGAY_REFERENCE"
	default:
		return "UNKNOWN"
	}
}
