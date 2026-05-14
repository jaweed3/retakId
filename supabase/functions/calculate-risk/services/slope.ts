import { getElevation } from './elevation.ts';

const OFFSET_DEG = 0.0009;
const METERS_PER_DEG = 111_320;

interface OffsetPoint {
  label: string;
  latitude: number;
  longitude: number;
}

export async function calculateSlope(
  latitude: number,
  longitude: number,
): Promise<number | null> {
  const centerElevation = await getElevation(latitude, longitude);
  if (centerElevation == null) return null;

  const points: OffsetPoint[] = [
    { label: 'N', latitude: latitude + OFFSET_DEG, longitude },
    { label: 'S', latitude: latitude - OFFSET_DEG, longitude },
    { label: 'E', latitude, longitude: longitude + OFFSET_DEG },
    { label: 'W', latitude, longitude: longitude - OFFSET_DEG },
  ];

  const gradients: number[] = [];

  for (const point of points) {
    const pointElevation = await getElevation(point.latitude, point.longitude);
    if (pointElevation == null) continue;

    const elevDiff = Math.abs(pointElevation - centerElevation);
    const distanceMeters = OFFSET_DEG * METERS_PER_DEG;
    const slopeRadians = Math.atan(elevDiff / distanceMeters);
    gradients.push(slopeRadians * (180 / Math.PI));
  }

  if (gradients.length === 0) return null;

  const maxGradient = Math.max(...gradients);
  return Math.round(maxGradient * 10) / 10;
}
