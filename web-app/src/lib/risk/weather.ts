const WEATHER_API = 'https://api.open-meteo.com/v1/forecast';
const TIMEOUT_MS = 5000;

interface WeatherResult {
  rain: number;
  temperature: number | null;
  humidity: number | null;
}

export async function getWeather(
  latitude: number,
  longitude: number,
): Promise<WeatherResult | null> {
  try {
    const url =
      `${WEATHER_API}?latitude=${latitude}&longitude=${longitude}` +
      `&current=rain,temperature_2m,relative_humidity_2m&timezone=Asia/Jakarta`;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) return null;

    const json = await res.json() as {
      current: { rain: number; temperature_2m: number; relative_humidity_2m: number };
    };

    return {
      rain: json.current.rain ?? 0,
      temperature: json.current.temperature_2m ?? null,
      humidity: json.current.relative_humidity_2m ?? null,
    };
  } catch {
    return null;
  }
}
