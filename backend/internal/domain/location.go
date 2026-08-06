package domain

import "time"

type Province struct {
	ID        int64     `json:"id"`
	Code      string    `json:"code"`
	Name      string    `json:"name"`
	CreatedAt time.Time `json:"created_at"`
}

type Municipality struct {
	ID         int64     `json:"id"`
	ProvinceID int64     `json:"province_id"`
	Code       string    `json:"code"`
	Name       string    `json:"name"`
	CreatedAt  time.Time `json:"created_at"`
}

type Barangay struct {
	ID             int64     `json:"id"`
	MunicipalityID int64     `json:"municipality_id"`
	Code           string    `json:"code"`
	Name           string    `json:"name"`
	CreatedAt      time.Time `json:"created_at"`
}

type Address struct {
	ID            int64     `json:"id"`
	BarangayID    int64     `json:"barangay_id"`
	StreetAddress string    `json:"street_address"`
	Latitude      float64   `json:"latitude"`
	Longitude     float64   `json:"longitude"`
	CreatedAt     time.Time `json:"created_at"`
}
