import type { TargetID } from '@/types/api';
import barangayCatalog from '@/assets/data/barangays.json';

interface BarangayCatalogEntry {
  barangay_code: string;
  barangay_name: string;
  city_municipality: string;
  province: string;
  representative_latitude_e7: number;
  representative_longitude_e7: number;
}

export interface FormattedLocation {
  readable: string;
  subText: string;
  raw: string;
  psgcFull: string;
}

const barangays = barangayCatalog as BarangayCatalogEntry[];

function titleCase(value: string): string {
  return value.toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function nearestBarangay(latitude?: number, longitude?: number): BarangayCatalogEntry | undefined {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || barangays.length === 0) return undefined;
  return barangays.reduce((nearest, candidate) => {
    const candidateLat = candidate.representative_latitude_e7 / 1e7;
    const candidateLon = candidate.representative_longitude_e7 / 1e7;
    const nearestLat = nearest.representative_latitude_e7 / 1e7;
    const nearestLon = nearest.representative_longitude_e7 / 1e7;
    const candidateDistance = (candidateLat - latitude!) ** 2 + (candidateLon - longitude!) ** 2;
    const nearestDistance = (nearestLat - latitude!) ** 2 + (nearestLon - longitude!) ** 2;
    return candidateDistance < nearestDistance ? candidate : nearest;
  });
}

function barangayBySpatialCode(code: string): BarangayCatalogEntry | undefined {
  const numericCode = Number.parseInt(code, 10);
  if (!Number.isFinite(numericCode)) return undefined;
  const suffix = String(numericCode).padStart(3, '0');
  return barangays.find((barangay) => barangay.barangay_code.endsWith(`-${suffix}`));
}

export function formatTargetLocation(
  targetId: string | TargetID | null | undefined,
  fallbackLat?: number,
  fallbackLon?: number
): FormattedLocation {
  let raw = typeof targetId === 'string' ? targetId : targetId?.raw || '';
  const rawParts = raw.split('::');
  const spatial = rawParts[0]?.split('-') || [];

  const region = typeof targetId === 'object' && targetId?.region ? targetId.region : spatial[0] || '05';
  const province = typeof targetId === 'object' && targetId?.province ? targetId.province : spatial[1] || '05';
  const municipality = typeof targetId === 'object'
    ? targetId?.municipality || targetId?.muni || spatial[2] || '12'
    : spatial[2] || '12';
  const barangay = typeof targetId === 'object' && targetId?.barangay ? targetId.barangay : spatial[3] || '';
  const zone = typeof targetId === 'object'
    ? targetId?.zone || targetId?.unit || spatial[4] || ''
    : spatial[4] || '';
  const uuid = typeof targetId === 'object'
    ? targetId?.uuid_hash || targetId?.uuid || rawParts[1] || ''
    : rawParts[1] || '';

  // The target's decoded code is authoritative. GPS supplies the same
  // nearest-barangay fallback used by Android when that code is unavailable.
  const codedBarangay = barangayBySpatialCode(barangay);
  const resolved = codedBarangay || nearestBarangay(fallbackLat, fallbackLon);
  const barangayName = resolved?.barangay_name || (barangay ? `Barangay ${barangay}` : 'Barangay unavailable');
  const municipalityName = resolved?.city_municipality
    ? titleCase(resolved.city_municipality)
    : municipality === '12' || municipality === '01' ? 'Legazpi City' : `Municipality ${municipality}`;
  const provinceName = resolved?.province ? titleCase(resolved.province) : province === '05' ? 'Albay' : `Province ${province}`;
  const zoneText = zone ? ` (Zone ${zone})` : '';
  const psgc = `${region}-${province}-${municipality}-${barangay || '??'}${zone ? `-${zone}` : ''}`;
  const uuidSnippet = uuid ? (uuid.length > 12 ? `${uuid.slice(0, 8)}...` : uuid) : '';

  if (!raw) raw = psgc;
  return {
    readable: `${barangayName}${zoneText}, ${municipalityName}, ${provinceName}`,
    subText: `${codedBarangay ? 'Catalog location' : 'Approximate nearest barangay'} · Region ${region} | PSGC: ${psgc}${uuidSnippet ? ` · Node: #${uuidSnippet}` : ''}`,
    raw,
    psgcFull: raw,
  };
}
