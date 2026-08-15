package service

import (
	"context"
	"sync"

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
	repo           repository.GeoRepository
	mu             sync.RWMutex
	provincesCache []*domain.Province
	munisCache     map[int64][]*domain.Municipality
	barangaysCache map[int64][]*domain.Barangay
	addressesCache map[int64][]*domain.Address
}

func NewGeoService(repo repository.GeoRepository) GeoService {
	return &geoService{
		repo:           repo,
		munisCache:     make(map[int64][]*domain.Municipality),
		barangaysCache: make(map[int64][]*domain.Barangay),
		addressesCache: make(map[int64][]*domain.Address),
	}
}

func (s *geoService) ListProvinces(ctx context.Context) ([]*domain.Province, error) {
	s.mu.RLock()
	if s.provincesCache != nil {
		cached := s.provincesCache
		s.mu.RUnlock()
		return cached, nil
	}
	s.mu.RUnlock()

	provinces, err := s.repo.ListProvinces(ctx)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	s.provincesCache = provinces
	s.mu.Unlock()

	return provinces, nil
}

func (s *geoService) ListMunicipalities(ctx context.Context, provinceID int64) ([]*domain.Municipality, error) {
	s.mu.RLock()
	if munis, exists := s.munisCache[provinceID]; exists {
		s.mu.RUnlock()
		return munis, nil
	}
	s.mu.RUnlock()

	munis, err := s.repo.ListMunicipalities(ctx, provinceID)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	s.munisCache[provinceID] = munis
	s.mu.Unlock()

	return munis, nil
}

func (s *geoService) ListBarangays(ctx context.Context, municipalityID int64) ([]*domain.Barangay, error) {
	s.mu.RLock()
	if brgys, exists := s.barangaysCache[municipalityID]; exists {
		s.mu.RUnlock()
		return brgys, nil
	}
	s.mu.RUnlock()

	brgys, err := s.repo.ListBarangays(ctx, municipalityID)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	s.barangaysCache[municipalityID] = brgys
	s.mu.Unlock()

	return brgys, nil
}

func (s *geoService) ListAddresses(ctx context.Context, barangayID int64) ([]*domain.Address, error) {
	s.mu.RLock()
	if addrs, exists := s.addressesCache[barangayID]; exists {
		s.mu.RUnlock()
		return addrs, nil
	}
	s.mu.RUnlock()

	addrs, err := s.repo.ListAddresses(ctx, barangayID)
	if err != nil {
		return nil, err
	}

	s.mu.Lock()
	s.addressesCache[barangayID] = addrs
	s.mu.Unlock()

	return addrs, nil
}
