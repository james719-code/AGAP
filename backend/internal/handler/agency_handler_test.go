package handler_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/handler"
)

type mockAgencyService struct{}

func (m *mockAgencyService) CreateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	agency.ID = 10
	return agency, nil
}
func (m *mockAgencyService) GetAgencyByID(ctx context.Context, id int64) (*domain.Agency, error) {
	return &domain.Agency{ID: id, Code: "POLICE-LEGAZPI", Name: "Legazpi PNP"}, nil
}
func (m *mockAgencyService) ListAgencies(ctx context.Context) ([]*domain.Agency, error) {
	return []*domain.Agency{{ID: 10, Code: "POLICE-LEGAZPI", Name: "Legazpi PNP"}}, nil
}
func (m *mockAgencyService) UpdateAgency(ctx context.Context, agency *domain.Agency) (*domain.Agency, error) {
	return agency, nil
}
func (m *mockAgencyService) DeleteAgency(ctx context.Context, id int64) error {
	return nil
}
func (m *mockAgencyService) CreateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	responder.ID = 20
	return responder, nil
}
func (m *mockAgencyService) GetResponderByID(ctx context.Context, id int64) (*domain.ResponderUnit, error) {
	return &domain.ResponderUnit{ID: id, UnitCallsign: "POLICE-PATROL-1"}, nil
}
func (m *mockAgencyService) ListResponders(ctx context.Context, agencyID *int64) ([]*domain.ResponderUnit, error) {
	return []*domain.ResponderUnit{{ID: 20, UnitCallsign: "POLICE-PATROL-1"}}, nil
}
func (m *mockAgencyService) UpdateResponder(ctx context.Context, responder *domain.ResponderUnit) (*domain.ResponderUnit, error) {
	return responder, nil
}
func (m *mockAgencyService) UpdateResponderLocation(ctx context.Context, id int64, lat, lon float64, status string) error {
	return nil
}
func (m *mockAgencyService) DeleteResponder(ctx context.Context, id int64) error {
	return nil
}

func TestAgencyHandler_CreateAndList(t *testing.T) {
	mockSvc := &mockAgencyService{}
	h := handler.NewAgencyHandler(mockSvc)

	agency := domain.Agency{
		Code:         "POLICE-LEGAZPI",
		Name:         "Legazpi City Police Station",
		Type:         domain.AgencyPolice,
		RegionCode:   "05",
		ProvinceCode: "05",
		MuniCode:     "12",
		BarangayCode: "01",
	}

	body, _ := json.Marshal(agency)
	req := httptest.NewRequest("POST", "/api/v1/agencies", bytes.NewBuffer(body))
	rec := httptest.NewRecorder()

	h.Create(rec, req)

	if rec.Code != http.StatusCreated {
		t.Fatalf("expected 201 Created, got %d: %s", rec.Code, rec.Body.String())
	}

	// Test List
	reqList := httptest.NewRequest("GET", "/api/v1/agencies", nil)
	recList := httptest.NewRecorder()
	h.List(recList, reqList)

	if recList.Code != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", recList.Code)
	}
}
