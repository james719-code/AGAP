import barangayCatalog from '@/assets/data/barangays.json';

export interface BarangayMapItem {
  barangay_code: string;
  barangay_name: string;
  city_municipality: string;
  province: string;
  representative_latitude_e7: number;
  representative_longitude_e7: number;
  boundary_geojson: string;
}

export const barangays = barangayCatalog as BarangayMapItem[];
