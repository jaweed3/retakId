import type { ReportStatus } from '../types/laporan';
import type { FactorContribution, RiskFactorReport } from './risk';

function mlScore(label: string, confidence: number): number {
  if (label === 'AMAN') {
    if (confidence >= 0.70) return 0.1;
    if (confidence >= 0.50) return 0.2;
    return 0.3;
  }
  if (label === 'WASPADA') {
    if (confidence >= 0.70) return 0.5;
    if (confidence >= 0.50) return 0.6;
    return 0.7;
  }
  if (label === 'BAHAYA') {
    if (confidence >= 0.70) return 0.8;
    if (confidence >= 0.50) return 0.9;
    return 1.0;
  }
  return 0.5;
}

function slopeScore(degrees: number): number {
  if (degrees < 8) return 0.1;
  if (degrees < 15) return 0.4;
  if (degrees < 25) return 0.7;
  return 1.0;
}

function rainScore(mm: number): number {
  if (mm <= 0) return 0.0;
  if (mm < 5) return 0.2;
  if (mm < 15) return 0.5;
  if (mm < 30) return 0.8;
  return 1.0;
}

function elevationScore(meters: number): number {
  if (meters < 200) return 0.1;
  if (meters < 500) return 0.4;
  if (meters < 1000) return 0.7;
  return 1.0;
}

function resultFromScore(score: number): string {
  if (score <= 0.33) return 'AMAN';
  if (score <= 0.66) return 'WASPADA';
  return 'BAHAYA';
}

export interface AnalyzeParams {
  mlLabel: string;
  mlConfidence: number;
  slopeDeg?: number | null;
  rainMm?: number | null;
  elevationM?: number | null;
  soilRiskScore?: number | null;
  soilName?: string | null;
}

export function analyze(params: AnalyzeParams): RiskFactorReport {
  const factors: FactorContribution[] = [];
  let totalWeight = 0;
  let weightedSum = 0;
  let missingAny = false;

  function addFactor(factor: string, rawValue: string, score: number, weight: number) {
    totalWeight += weight;
    weightedSum += score * weight;
    factors.push({ factor, weight, score, rawValue, weightedScore: Math.round(score * weight * 1000) / 1000 });
  }

  const mlScoreVal = mlScore(params.mlLabel, params.mlConfidence);
  addFactor('ML', `${params.mlLabel} ${Math.round(params.mlConfidence * 100)}%`, mlScoreVal, 0.50);

  if (params.slopeDeg != null) {
    addFactor('SLOPE', `${Math.round(params.slopeDeg)}°`, slopeScore(params.slopeDeg), 0.20);
  } else {
    missingAny = true;
  }

  if (params.rainMm != null) {
    addFactor('RAIN', `${Math.round(params.rainMm)} mm`, rainScore(params.rainMm), 0.15);
  } else {
    missingAny = true;
  }

  if (params.elevationM != null) {
    addFactor('ELEVATION', `${Math.round(params.elevationM)} m`, elevationScore(params.elevationM), 0.10);
  } else {
    missingAny = true;
  }

  if (params.soilRiskScore != null) {
    addFactor('SOIL', params.soilName ?? '?', params.soilRiskScore, 0.05);
  } else {
    missingAny = true;
  }

  const finalScore = missingAny && totalWeight > 0
    ? weightedSum / totalWeight
    : weightedSum;

  const clamped = Math.max(0, Math.min(1, finalScore));
  const finalLabel = resultFromScore(clamped) as ReportStatus;

  const isUpgraded = finalScore > mlScoreVal + 0.05;
  const isDowngraded = finalScore < mlScoreVal - 0.05;

  factors.sort((a, b) => b.weightedScore - a.weightedScore);

  return {
    mlResult: { status: params.mlLabel as ReportStatus, confidence: params.mlConfidence },
    mlConfidence: params.mlConfidence,
    finalScore: clamped,
    finalResult: { status: finalLabel, confidence: 0 },
    factors,
    isUpgraded,
    isDowngraded,
  };
}
