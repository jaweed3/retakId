const ELEVATION_API = 'https://api.open-meteo.com/v1/elevation';
const TIMEOUT_MS = 3000;

interface OpenMeteoElevationResponse {
  elevation: {
    elevation: number[];
  };
}

const cache = new Map<string, number>();

function cacheKey(lat: number, lon: number): string {
  const k = (v: number) => Math.round(v * 1e4) / 1e4;
  return `${k(lat)},${k(lon)}`;
}

export async function getElevation(
  latitude: number,
  longitude: number,
): Promise<number | null> {
  const key = cacheKey(latitude, longitude);
  const cached = cache.get(key);
  if (cached !== undefined) return cached;

  try {
    const url = `${ELEVATION_API}?latitude=${latitude}&longitude=${longitude}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) return null;

    const json: OpenMeteoElevationResponse = await res.json();
    const elevation = json.elevation.elevation[0];

    if (elevation != null) {
      cache.set(key, elevation);
    }

    return elevation ?? null;
  } catch {
    return null;
  }
}

export function clearCache(): void {
  cache.clear();
}
