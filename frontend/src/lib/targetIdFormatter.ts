import type { TargetID } from '@/types/api';

const REGION_MAP: Record<string, string> = {
  '05': 'Region V (Bicol)',
};

const PROVINCE_MAP: Record<string, string> = {
  '05': 'Albay',
};

const MUNI_MAP: Record<string, string> = {
  '12': 'Legazpi City',
  '01': 'Legazpi City',
  '02': 'Daraga',
  '03': 'Tabaco City',
  '08': 'Mayon Volcano Sector',
};

const BARANGAY_MAP: Record<string, string> = {
  '01': 'Central Poblacion (Brgy 1)',
  '02': 'Peñaranda Park Zone (Brgy 16)',
  '03': 'San Roque',
  '04': 'Port Area / Embarcadero (Brgy 44)',
  '05': 'Brgy 5 (Rizal Street)',
  '08': 'Mayon 6km Danger Zone',
  '09': 'Daraga Station District',
};

export interface FormattedLocation {
  readable: string;
  subText: string;
  raw: string;
  psgcFull: string;
}

export function formatTargetLocation(targetId: string | TargetID | any, fallbackLat?: number, fallbackLon?: number): FormattedLocation {
  if (!targetId) {
    return {
      readable: 'Unknown Location',
      subText: fallbackLat && fallbackLon ? `GPS: ${fallbackLat.toFixed(4)}, ${fallbackLon.toFixed(4)}` : 'No coordinates available',
      raw: 'N/A',
      psgcFull: 'N/A',
    };
  }

  let rawString = '';
  let regionCode = '05';
  let provinceCode = '05';
  let muniCode = '12';
  let barangayCode = '01';
  let unitCode = '';
  let uuidSnippet = '';

  if (typeof targetId === 'string') {
    rawString = targetId;
    const parts = targetId.split('::');
    const psgcParts = parts[0] ? parts[0].split('-') : [];
    if (psgcParts.length >= 4) {
      regionCode = psgcParts[0] || '05';
      provinceCode = psgcParts[1] || '05';
      muniCode = psgcParts[2] || '12';
      barangayCode = psgcParts[3] || '01';
      if (psgcParts.length >= 5) unitCode = psgcParts[4];
    }
    if (parts[1]) {
      uuidSnippet = parts[1].length > 12 ? parts[1].substring(0, 8) + '...' : parts[1];
    }
  } else if (typeof targetId === 'object') {
    rawString = targetId.raw || '';
    regionCode = targetId.region || '05';
    provinceCode = targetId.province || '05';
    muniCode = targetId.muni || '12';
    barangayCode = targetId.barangay || '01';
    unitCode = targetId.unit || '';
    uuidSnippet = targetId.uuid ? (targetId.uuid.length > 12 ? targetId.uuid.substring(0, 8) + '...' : targetId.uuid) : '';
  }

  const muniName = MUNI_MAP[muniCode] || MUNI_MAP[parseInt(muniCode, 10).toString()] || `Muni ${muniCode}`;
  const barangayName = BARANGAY_MAP[barangayCode] || BARANGAY_MAP[parseInt(barangayCode, 10).toString()] || `Brgy ${barangayCode}`;
  const provinceName = PROVINCE_MAP[provinceCode] || 'Albay';
  const regionName = REGION_MAP[regionCode] || `Region ${regionCode}`;

  const zoneText = unitCode ? ` (Zone ${unitCode})` : '';
  const readable = `${barangayName}${zoneText}, ${muniName}, ${provinceName}`;

  const psgcCodeStr = `${regionCode}-${provinceCode}-${muniCode}-${barangayCode}${unitCode ? `-${unitCode}` : ''}`;
  const subText = `${regionName} | PSGC: ${psgcCodeStr}${uuidSnippet ? ` · Node: #${uuidSnippet}` : ''}`;

  return {
    readable,
    subText,
    raw: rawString,
    psgcFull: rawString || psgcCodeStr,
  };
}
