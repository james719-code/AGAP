package domain

import (
	"fmt"
	"strings"
)

type TargetID struct {
	Region       string `json:"region"`
	Province     string `json:"province"`
	Municipality string `json:"municipality"`
	Barangay     string `json:"barangay"`
	Zone         string `json:"zone"`
	UUIDHash     string `json:"uuid_hash"`
	Raw          string `json:"raw"`
}

// ParseTargetID parses strings like "05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111"
func ParseTargetID(raw string) (TargetID, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return TargetID{}, ErrInvalidTargetID
	}

	parts := strings.Split(raw, "::")
	if len(parts) != 2 {
		return TargetID{}, fmt.Errorf("%w: missing '::' delimiter in %s", ErrInvalidTargetID, raw)
	}

	spatialStr := parts[0]
	uuidHash := parts[1]

	spatialParts := strings.Split(spatialStr, "-")
	if len(spatialParts) < 4 || len(spatialParts) > 5 {
		return TargetID{}, fmt.Errorf("%w: invalid spatial component count in %s", ErrInvalidTargetID, raw)
	}

	tid := TargetID{
		Region:       spatialParts[0],
		Province:     spatialParts[1],
		Municipality: spatialParts[2],
		Barangay:     spatialParts[3],
		UUIDHash:     uuidHash,
		Raw:          raw,
	}

	if len(spatialParts) == 5 {
		tid.Zone = spatialParts[4]
	}

	return tid, nil
}

func (t TargetID) String() string {
	if t.Raw != "" {
		return t.Raw
	}
	spatial := fmt.Sprintf("%s-%s-%s-%s", t.Region, t.Province, t.Municipality, t.Barangay)
	if t.Zone != "" {
		spatial = fmt.Sprintf("%s-%s", spatial, t.Zone)
	}
	return fmt.Sprintf("%s::%s", spatial, t.UUIDHash)
}

func (t TargetID) SpatialPrefix() string {
	return fmt.Sprintf("%s-%s-%s-%s", t.Region, t.Province, t.Municipality, t.Barangay)
}
