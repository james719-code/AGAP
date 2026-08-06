package repository

import (
	"context"

	"github.com/travelingsalesman/agap/backend/internal/domain"
)

type GeoRepository interface {
	ListProvinces(ctx context.Context) ([]*domain.Province, error)
	ListMunicipalities(ctx context.Context, provinceID int64) ([]*domain.Municipality, error)
	ListBarangays(ctx context.Context, municipalityID int64) ([]*domain.Barangay, error)
	ListAddresses(ctx context.Context, barangayID int64) ([]*domain.Address, error)
}
