const OPEN_METEO_FORECAST = 'https://api.open-meteo.com/v1/forecast';
const OPEN_METEO_ELEVATION = 'https://api.open-meteo.com/v1/elevation';
const ISRIC_SOILGRIDS = 'https://rest.isric.org/soilgrids/v2.0/classification/query';
const OFFSET_DEG = 0.0009;
const DEG_TO_M = 111_320;

const SOIL_RISK_MAP: Record<string, number> = {
  Acrisol: 0.7, Albeluvisol: 0.5, Alisol: 0.7, Andosol: 0.3,
  Arenosol: 0.6, Cambisol: 0.4, Chernozem: 0.2, Ferralsol: 0.5,
  Fluvisol: 0.8, Gleysol: 0.9, Histosol: 0.9, Kastanozem: 0.3,
  Leptosol: 0.7, Luvisol: 0.4, Nitisol: 0.3, Phaeozem: 0.3,
  Planosol: 0.7, Plinthosol: 0.6, Podzol: 0.5, Regosol: 0.6,
  Solonchak: 0.8, Solonetz: 0.7, Umbrisol: 0.4, Vertisol: 0.9,
};

const SOIL_NAME_MAP: Record<string, string> = {
  Acrisol: 'Acrisol (tanah masam)',
  Albeluvisol: 'Albeluvisol',
  Alisol: 'Alisol (tanah masam)',
  Andosol: 'Andosol (tanah vulkanik)',
  Arenosol: 'Arenosol (tanah berpasir)',
  Cambisol: 'Cambisol (tanah muda)',
  Chernozem: 'Chernozem (tanah hitam)',
  Ferralsol: 'Ferralsol (tanah merah)',
  Fluvisol: 'Fluvisol (tanah endapan)',
  Gleysol: 'Gleysol (tanah jenuh air)',
  Histosol: 'Histosol (tanah gambut)',
  Kastanozem: 'Kastanozem',
  Leptosol: 'Leptosol (tanah tipis)',
  Luvisol: 'Luvisol (tanah liat)',
  Nitisol: 'Nitisol (tanah subur)',
  Phaeozem: 'Phaeozem',
  Planosol: 'Planosol',
  Plinthosol: 'Plinthosol',
  Podzol: 'Podzol (tanah pasir)',
  Regosol: 'Regosol (tanah longgar)',
  Solonchak: 'Solonchak (tanah asin)',
  Solonetz: 'Solonetz (tanah soda)',
  Umbrisol: 'Umbrisol',
  Vertisol: 'Vertisol (tanah lempung)',
};

export interface WeatherData {
  temperature: number;
  humidity: number;
  precipitation: number;
  rain: number;
  weatherCode: number;
  windSpeed: number;
}

function abortTimeout(ms: number): AbortSignal {
  try { return AbortSignal.timeout(ms); } catch { return new AbortController().signal; }
}

export async function getWeather(lat: number, lon: number): Promise<WeatherData | null> {
  try {
    const params = new URLSearchParams({
      latitude: String(lat),
      longitude: String(lon),
      current: 'temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,wind_speed_10m',
      timezone: 'Asia/Jakarta',
    });
    const res = await fetch(`${OPEN_METEO_FORECAST}?${params}`, { signal: abortTimeout(5000) });
    if (!res.ok) return null;
    const data = await res.json();
    const c = data.current;
    if (!c) return null;
    return {
      temperature: c.temperature_2m,
      humidity: c.relative_humidity_2m,
      precipitation: c.precipitation ?? 0,
      rain: c.rain ?? 0,
      weatherCode: c.weather_code,
      windSpeed: c.wind_speed_10m,
    };
  } catch {
    return null;
  }
}

export async function getElevation(lat: number, lon: number): Promise<number | null> {
  try {
    const params = new URLSearchParams({ latitude: String(lat), longitude: String(lon) });
    const res = await fetch(`${OPEN_METEO_ELEVATION}?${params}`, { signal: abortTimeout(5000) });
    if (!res.ok) return null;
    const data = await res.json();
    return data.elevation?.[0] ?? null;
  } catch {
    return null;
  }
}

export async function getSlope(lat: number, lon: number): Promise<number | null> {
  const center = await getElevation(lat, lon);
  if (center == null) return null;

  const offsets: [number, number][] = [
    [lat + OFFSET_DEG, lon],
    [lat - OFFSET_DEG, lon],
    [lat, lon + OFFSET_DEG],
    [lat, lon - OFFSET_DEG],
  ];

  let maxDeg = 0;
  let successes = 0;

  const results = await Promise.all(
    offsets.map(([pl, pn]) => getElevation(pl, pn))
  );

  for (const elev of results) {
    if (elev == null) continue;
    successes++;
    const elevDiff = Math.abs(elev - center);
    const distance = OFFSET_DEG * DEG_TO_M;
    const slopeRad = Math.atan(elevDiff / distance);
    const slopeDeg = slopeRad * (180 / Math.PI);
    if (slopeDeg > maxDeg) maxDeg = slopeDeg;
  }

  if (successes === 0) return null;
  return Math.round(maxDeg * 10) / 10;
}

export interface SoilData {
  code: string;
  name: string;
  riskScore: number;
}

export async function getSoil(lat: number, lon: number): Promise<SoilData | null> {
  try {
    const params = new URLSearchParams({ lat: String(lat), lon: String(lon) });
    const res = await fetch(`${ISRIC_SOILGRIDS}?${params}`, { signal: abortTimeout(8000) });
    if (!res.ok) return null;
    const data = await res.json();
    const wrb = data.wrb_class_name;
    if (!wrb || wrb === 'No information') return null;
    return {
      code: wrb,
      name: SOIL_NAME_MAP[wrb] ?? wrb,
      riskScore: SOIL_RISK_MAP[wrb] ?? 0.5,
    };
  } catch {
    return null;
  }
}

export interface EnvData {
  weather: WeatherData | null;
  elevation: number | null;
  slope: number | null;
  soil: SoilData | null;
}

export async function fetchAllEnv(lat: number, lon: number): Promise<EnvData> {
  const [weather, elevation, slope, soil] = await Promise.all([
    getWeather(lat, lon),
    getElevation(lat, lon),
    getSlope(lat, lon),
    getSoil(lat, lon),
  ]);
  return { weather, elevation, slope, soil };
}
