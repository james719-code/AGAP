package service

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
	"github.com/travelingsalesman/agap/backend/internal/repository"
)

type GeoService interface {
	ListProvinces(ctx context.Context) ([]*domain.Province, error)
	ListMunicipalities(ctx context.Context, provinceID int64) ([]*domain.Municipality, error)
	ListBarangays(ctx context.Context, municipalityID int64) ([]*domain.Barangay, error)
	ListAddresses(ctx context.Context, barangayID int64) ([]*domain.Address, error)
}

type geoService struct {
	repo repository.GeoRepository
}

func NewGeoService(repo repository.GeoRepository) GeoService {
	return &geoService{repo: repo}
}

func (s *geoService) ListProvinces(ctx context.Context) ([]*domain.Province, error) {
	return s.repo.ListProvinces(ctx)
}

func (s *geoService) ListMunicipalities(ctx context.Context, provinceID int64) ([]*domain.Municipality, error) {
	return s.repo.ListMunicipalities(ctx, provinceID)
}

func (s *geoService) ListBarangays(ctx context.Context, municipalityID int64) ([]*domain.Barangay, error) {
	return s.repo.ListBarangays(ctx, municipalityID)
}

func (s *geoService) ListAddresses(ctx context.Context, barangayID int64) ([]*domain.Address, error) {
	return s.repo.ListAddresses(ctx, barangayID)
}
