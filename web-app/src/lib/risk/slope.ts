import { getElevation } from './elevation';

const OFFSET_DEG = 0.0009;
const METERS_PER_DEG = 111_320;

export async function calculateSlope(
  latitude: number,
  longitude: number,
): Promise<number | null> {
  const center = await getElevation(latitude, longitude);
  if (center == null) return null;

  const points = [
    { lat: latitude + OFFSET_DEG, lon: longitude },
    { lat: latitude - OFFSET_DEG, lon: longitude },
    { lat: latitude, lon: longitude + OFFSET_DEG },
    { lat: latitude, lon: longitude - OFFSET_DEG },
  ];

  const gradients: number[] = [];
  for (const p of points) {
    const elev = await getElevation(p.lat, p.lon);
    if (elev == null) continue;

    const diff = Math.abs(elev - center);
    const dist = OFFSET_DEG * METERS_PER_DEG;
    const rad = Math.atan(diff / dist);
    gradients.push(rad * (180 / Math.PI));
  }

  if (gradients.length === 0) return null;
  return Math.round(Math.max(...gradients) * 10) / 10;
}
