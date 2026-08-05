package domain_test

import (
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

func TestParseTargetID(t *testing.T) {
	raw := "05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111"
	tid, err := domain.ParseTargetID(raw)
	if err != nil {
		t.Fatalf("expected valid target ID, got error: %v", err)
	}

	if tid.Region != "05" {
		t.Errorf("expected Region 05, got %s", tid.Region)
	}
	if tid.Province != "05" {
		t.Errorf("expected Province 05, got %s", tid.Province)
	}
	if tid.Municipality != "12" {
		t.Errorf("expected Municipality 12, got %s", tid.Municipality)
	}
	if tid.Barangay != "01" {
		t.Errorf("expected Barangay 01, got %s", tid.Barangay)
	}
	if tid.Zone != "04" {
		t.Errorf("expected Zone 04, got %s", tid.Zone)
	}
	if tid.UUIDHash != "4a8f9c12-3b4e-4f10-912a-87c2b3f10111" {
		t.Errorf("unexpected UUID hash: %s", tid.UUIDHash)
	}
	if tid.SpatialPrefix() != "05-05-12-01" {
		t.Errorf("expected SpatialPrefix 05-05-12-01, got %s", tid.SpatialPrefix())
	}
}

func TestParseTargetID_Invalid(t *testing.T) {
	invalidIDs := []string{
		"",
		"invalid-string-no-uuid",
		"05-05-12::uuid", // missing barangay
	}

	for _, invalid := range invalidIDs {
		_, err := domain.ParseTargetID(invalid)
		if err == nil {
			t.Errorf("expected error for invalid Target ID %q, got nil", invalid)
		}
	}
}
