const WEATHER_API = 'https://api.open-meteo.com/v1/forecast';
const TIMEOUT_MS = 5000;

interface OpenMeteoWeatherResponse {
  current: {
    rain: number;
  };
}

export async function getRainfall(
  latitude: number,
  longitude: number,
): Promise<number | null> {
  try {
    const url =
      `${WEATHER_API}?latitude=${latitude}&longitude=${longitude}` +
      `&current=rain&timezone=Asia/Jakarta`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) return null;

    const json: OpenMeteoWeatherResponse = await res.json();
    return json.current.rain ?? null;
  } catch {
    return null;
  }
}
