const ISRIC_API = 'https://rest.isric.org/soilgrids/v2.0/classification/query';
const TIMEOUT_MS = 5000;

interface SoilInfo {
  score: number;
  name: string;
}

const WRB_MAP: Record<string, { score: number; name: string }> = {
  Acrisol: { score: 0.7, name: 'Acrisol (tanah masam)' },
  Albeluvisol: { score: 0.5, name: 'Albeluvisol' },
  Alisol: { score: 0.7, name: 'Alisol (tanah masam)' },
  Andosol: { score: 0.3, name: 'Andosol (tanah vulkanik)' },
  Arenosol: { score: 0.6, name: 'Arenosol (tanah berpasir)' },
  Cambisol: { score: 0.4, name: 'Cambisol (tanah muda)' },
  Chernozem: { score: 0.2, name: 'Chernozem (tanah hitam)' },
  Ferralsol: { score: 0.5, name: 'Ferralsol (tanah merah)' },
  Fluvisol: { score: 0.8, name: 'Fluvisol (tanah endapan)' },
  Gleysol: { score: 0.9, name: 'Gleysol (tanah jenuh air)' },
  Histosol: { score: 0.9, name: 'Histosol (tanah gambut)' },
  Kastanozem: { score: 0.3, name: 'Kastanozem' },
  Leptosol: { score: 0.7, name: 'Leptosol (tanah tipis)' },
  Luvisol: { score: 0.4, name: 'Luvisol (tanah liat)' },
  Nitisol: { score: 0.3, name: 'Nitisol (tanah subur)' },
  Phaeozem: { score: 0.3, name: 'Phaeozem' },
  Planosol: { score: 0.7, name: 'Planosol' },
  Plinthosol: { score: 0.6, name: 'Plinthosol' },
  Podzol: { score: 0.5, name: 'Podzol (tanah pasir)' },
  Regosol: { score: 0.6, name: 'Regosol (tanah longgar)' },
  Solonchak: { score: 0.8, name: 'Solonchak (tanah asin)' },
  Solonetz: { score: 0.7, name: 'Solonetz (tanah soda)' },
  Umbrisol: { score: 0.4, name: 'Umbrisol' },
  Vertisol: { score: 0.9, name: 'Vertisol (tanah lempung)' },
};

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

  try {
    const url = `${ISRIC_API}?lat=${latitude}&lon=${longitude}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) return null;

    const json = await res.json() as { wrb_class_name?: string };
    const wrb = json.wrb_class_name;
    if (!wrb || wrb === 'No information') return null;

    const mapped = WRB_MAP[wrb] ?? { score: 0.5, name: wrb };
    const result: SoilInfo = { score: mapped.score, name: mapped.name };
    cache.set(key, result);
    return result;
  } catch {
    return null;
  }
}
