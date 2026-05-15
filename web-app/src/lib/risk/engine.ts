import type { ReportStatus } from '../../types/laporan';

export type RiskFactor = 'ML' | 'SLOPE' | 'RAIN' | 'ELEVATION' | 'SOIL';
export type RiskLabel = 'RENDAH' | 'SEDANG' | 'TINGGI' | 'SANGAT_TINGGI';

export interface FactorContribution {
  factor: RiskFactor;
  rawValue: string;
  score: number;
  weight: number;
  weightedScore: number;
  riskLabel: RiskLabel;
}

export interface RiskFactorReport {
  mlResult: ReportStatus;
  mlConfidence: number;
  finalScore: number;
  finalResult: ReportStatus;
  factors: FactorContribution[];
  isUpgraded: boolean;
  isDowngraded: boolean;
}

interface AnalyzeInput {
  mlResult: ReportStatus;
  mlConfidence: number;
  slopeDegrees?: number;
  rainMm?: number;
  elevationMeters?: number;
  soilScore?: number;
  soilName?: string;
}

const WEIGHTS: Record<RiskFactor, number> = {
  ML: 0.5,
  SLOPE: 0.2,
  RAIN: 0.15,
  ELEVATION: 0.1,
  SOIL: 0.05,
};

function labelFromScore(score: number): RiskLabel {
  if (score <= 0.2) return 'RENDAH';
  if (score <= 0.5) return 'SEDANG';
  if (score <= 0.8) return 'TINGGI';
  return 'SANGAT_TINGGI';
}

export function resultFromScore(score: number): ReportStatus {
  if (score <= 0.33) return 'AMAN';
  if (score <= 0.66) return 'WASPADA';
  return 'BAHAYA';
}

export function mlScore(result: ReportStatus, confidence: number): number {
  if (result === 'AMAN') {
    if (confidence >= 0.7) return 0.1;
    if (confidence >= 0.5) return 0.2;
    return 0.3;
  }
  if (result === 'WASPADA') {
    if (confidence >= 0.7) return 0.5;
    if (confidence >= 0.5) return 0.6;
    return 0.7;
  }
  if (result === 'BAHAYA') {
    if (confidence >= 0.7) return 0.8;
    if (confidence >= 0.5) return 0.9;
    return 1.0;
  }
  return 0.5;
}

export function slopeScore(degrees: number): number {
  if (degrees < 8) return 0.1;
  if (degrees < 15) return 0.4;
  if (degrees < 25) return 0.7;
  return 1.0;
}

export function rainScore(mm: number): number {
  if (mm <= 0) return 0.0;
  if (mm < 5) return 0.2;
  if (mm < 15) return 0.5;
  if (mm < 30) return 0.8;
  return 1.0;
}

export function elevationScore(meters: number): number {
  if (meters < 200) return 0.1;
  if (meters < 500) return 0.4;
  if (meters < 1000) return 0.7;
  return 1.0;
}

export function analyze(input: AnalyzeInput): RiskFactorReport {
  const factors: FactorContribution[] = [];
  let totalWeight = 0;
  let weightedSum = 0;
  let anyMissing = false;

  const addFactor = (
    factor: RiskFactor, raw: string, score: number, weight: number,
  ) => {
    totalWeight += weight;
    weightedSum += score * weight;
    factors.push({
      factor,
      rawValue: raw,
      score,
      weight,
      weightedScore: Math.round(score * weight * 100) / 100,
      riskLabel: labelFromScore(score),
    });
  };

  const mlSc = mlScore(input.mlResult, input.mlConfidence);
  addFactor('ML', `${input.mlResult} ${Math.round(input.mlConfidence * 100)}%`, mlSc, WEIGHTS.ML);

  if (input.slopeDegrees != null) {
    addFactor('SLOPE', `${Math.round(input.slopeDegrees)}°`, slopeScore(input.slopeDegrees), WEIGHTS.SLOPE);
  } else anyMissing = true;

  if (input.rainMm != null) {
    addFactor('RAIN', `${Math.round(input.rainMm)} mm`, rainScore(input.rainMm), WEIGHTS.RAIN);
  } else anyMissing = true;

  if (input.elevationMeters != null) {
    addFactor('ELEVATION', `${Math.round(input.elevationMeters)} m`, elevationScore(input.elevationMeters), WEIGHTS.ELEVATION);
  } else anyMissing = true;

  if (input.soilScore != null) {
    addFactor('SOIL', input.soilName ?? 'Tidak diketahui', input.soilScore, WEIGHTS.SOIL);
  } else anyMissing = true;

  const finalScore = anyMissing && totalWeight > 0
    ? weightedSum / totalWeight
    : weightedSum;

  const clamped = Math.max(0, Math.min(1, finalScore));
  const finalResult = resultFromScore(clamped);

  const mlOnly = mlScore(input.mlResult, input.mlConfidence);
  const isUpgraded = clamped > mlOnly + 0.05;
  const isDowngraded = clamped < mlOnly - 0.05;

  factors.sort((a, b) => b.weightedScore - a.weightedScore);

  return {
    mlResult: input.mlResult,
    mlConfidence: input.mlConfidence,
    finalScore: clamped,
    finalResult,
    factors,
    isUpgraded,
    isDowngraded,
  };
}
