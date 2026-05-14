const ISRIC_API = 'https://rest.isric.org/soilgrids/v2.0/classification/query';
const TIMEOUT_MS = 5000;

interface SoilInfo {
  score: number;
  name: string;
}

const wrbRiskMap: Record<string, { score: number; indonesianName: string }> = {
  Vertisols: { score: 1.0, indonesianName: 'Tanah Liat Ekspansif' },
  Planosols: { score: 1.0, indonesianName: 'Tanah Liat Planosol' },
  Acrisols: { score: 0.8, indonesianName: 'Tanah Liat Merah' },
  Lixisols: { score: 0.8, indonesianName: 'Tanah Liat Berpasir' },
  Nitisols: { score: 0.8, indonesianName: 'Tanah Liat Nitrosol' },
  Alisols: { score: 0.8, indonesianName: 'Tanah Liat Alisol' },
  Luvisols: { score: 0.8, indonesianName: 'Tanah Liat Luvisol' },
  Cambisols: { score: 0.5, indonesianName: 'Tanah Lempung' },
  Ferralsols: { score: 0.5, indonesianName: 'Tanah Laterit' },
  Fluvisols: { score: 0.5, indonesianName: 'Tanah Endapan' },
  Leptosols: { score: 0.5, indonesianName: 'Tanah Tipis' },
  Regosols: { score: 0.5, indonesianName: 'Tanah Regosol' },
  Umbrisols: { score: 0.5, indonesianName: 'Tanah Humus' },
  Andosols: { score: 0.5, indonesianName: 'Tanah Vulkanik' },
  Arenosols: { score: 0.2, indonesianName: 'Tanah Pasir' },
  Podzols: { score: 0.2, indonesianName: 'Tanah Podsol' },
  Gleysols: { score: 0.2, indonesianName: 'Tanah Lembab' },
  Histosols: { score: 0.2, indonesianName: 'Tanah Gambut' },
};

interface RegionData {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
  soilName: string;
  soilIndonesian: string;
  score: number;
}

const regions: RegionData[] = [
  { minLat: -8.0, maxLat: -7.7, minLon: 111.3, maxLon: 111.6, soilName: 'Ferralsols', soilIndonesian: 'Tanah Laterit', score: 0.5 },
  { minLat: -8.2, maxLat: -8.0, minLon: 111.3, maxLon: 111.6, soilName: 'Cambisols', soilIndonesian: 'Tanah Lempung', score: 0.5 },
  { minLat: -7.7, maxLat: -7.5, minLon: 111.3, maxLon: 111.6, soilName: 'Acrisols', soilIndonesian: 'Tanah Liat Merah', score: 0.8 },
  { minLat: -8.0, maxLat: -7.7, minLon: 111.6, maxLon: 111.9, soilName: 'Vertisols', soilIndonesian: 'Tanah Liat Ekspansif', score: 1.0 },
];

const cache = new Map<string, SoilInfo>();

function cacheKey(lat: number, lon: number): string {
  const k = (v: number) => Math.round(v * 1e3) / 1e3;
  return `${k(lat)},${k(lon)}`;
}

export async function getSoilType(
  latitude: number,
  longitude: number,
): Promise<SoilInfo | null> {
  const key = cacheKey(latitude, longitude);
  const cached = cache.get(key);
  if (cached) return cached;

  const apiResult = await fetchFromIsric(latitude, longitude);
  const result = apiResult ?? fallbackByRegion(latitude, longitude);

  if (result) cache.set(key, result);
  return result;
}

async function fetchFromIsric(
  latitude: number,
  longitude: number,
): Promise<SoilInfo | null> {
  try {
    const url = `${ISRIC_API}?lat=${latitude}&lon=${longitude}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) return null;

    const json = await res.json() as { wrb_class_name?: string };
    const wrbClass = json.wrb_class_name;
    if (!wrbClass) return null;

    const mapped = wrbRiskMap[wrbClass] ?? { score: 0.5, indonesianName: wrbClass };
    return { score: mapped.score, name: mapped.indonesianName };
  } catch {
    return null;
  }
}

function fallbackByRegion(
  latitude: number,
  longitude: number,
): SoilInfo | null {
  const region = regions.find(
    (r) => latitude >= r.minLat && latitude <= r.maxLat &&
          longitude >= r.minLon && longitude <= r.maxLon,
  );
  if (!region) return null;

  return { score: region.score, name: region.soilIndonesian };
}

export function clearCache(): void {
  cache.clear();
}
