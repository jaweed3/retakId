import { analyze } from './engine';
import { getElevation } from './elevation';
import { calculateSlope } from './slope';
import { getWeather } from './weather';
import { getSoilType } from './soil';
import type { RiskFactorReport } from './engine';
import type { ReportStatus } from '../../types/laporan';

interface CalculateRiskParams {
  mlResult: ReportStatus;
  mlConfidence: number;
  latitude: number;
  longitude: number;
}

export async function calculateRisk(
  params: CalculateRiskParams,
): Promise<RiskFactorReport | null> {
  const { mlResult, mlConfidence, latitude, longitude } = params;

  try {
    const [weather, elevationMeters, soil] = await Promise.all([
      getWeather(latitude, longitude),
      getElevation(latitude, longitude),
      getSoilType(latitude, longitude),
    ]);

    const slopeDegrees = elevationMeters != null
      ? await calculateSlope(latitude, longitude)
      : null;

    const rainMm = weather?.rain ?? null;

    return analyze({
      mlResult,
      mlConfidence,
      slopeDegrees: slopeDegrees ?? undefined,
      rainMm: rainMm ?? undefined,
      elevationMeters: elevationMeters ?? undefined,
      soilScore: soil?.score,
      soilName: soil?.name,
    });
  } catch (err) {
    console.warn('[risk] Gagal mengumpulkan data lingkungan:', err);
    return null;
  }
}

export type { RiskFactorReport, FactorContribution } from './engine';
