import type {
  DetectionResult,
  FactorContribution,
  RiskFactor,
  RiskFactorReport,
  RiskLabel,
} from './types.ts';

const RISK_FACTOR_WEIGHTS: Record<RiskFactor, number> = {
  ML: 0.5,
  SLOPE: 0.2,
  RAIN: 0.15,
  ELEVATION: 0.1,
  SOIL: 0.05,
};

const RISK_FACTOR_DISPLAY: Record<RiskFactor, string> = {
  ML: 'Analisis Visual',
  SLOPE: 'Kemiringan Lereng',
  RAIN: 'Curah Hujan',
  ELEVATION: 'Ketinggian',
  SOIL: 'Jenis Tanah',
};

function labelFromScore(score: number): RiskLabel {
  if (score <= 0.2) return 'RENDAH';
  if (score <= 0.5) return 'SEDANG';
  if (score <= 0.8) return 'TINGGI';
  return 'SANGAT_TINGGI';
}

export function resultFromScore(score: number): DetectionResult {
  if (score <= 0.33) return 'AMAN';
  if (score <= 0.66) return 'WASPADA';
  return 'BAHAYA';
}

function addFactor(
  factors: FactorContribution[],
  factor: RiskFactor,
  rawValue: string,
  score: number,
  weight: number,
): void {
  factors.push({
    factor,
    rawValue,
    score,
    weight,
    weightedScore: Math.round(score * weight * 100) / 100,
    riskLabel: labelFromScore(score),
  });
}

export function mlScore(result: DetectionResult, confidence: number): number {
  switch (result) {
    case 'AMAN':
      if (confidence >= 0.7) return 0.1;
      if (confidence >= 0.5) return 0.2;
      return 0.3;
    case 'WASPADA':
      if (confidence >= 0.7) return 0.5;
      if (confidence >= 0.5) return 0.6;
      return 0.7;
    case 'BAHAYA':
      if (confidence >= 0.7) return 0.8;
      if (confidence >= 0.5) return 0.9;
      return 1.0;
  }
}

function mlFactorRaw(result: DetectionResult, confidence: number): string {
  return `${result} ${Math.round(confidence * 100)}%`;
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

export function soilTypeScore(riskScore: number): number {
  return riskScore;
}

export interface AnalyzeInput {
  mlResult: DetectionResult;
  mlConfidence: number;
  slopeDegrees?: number;
  rainMm?: number;
  elevationMeters?: number;
  soilScore?: number;
  soilName?: string;
}

export function analyze(input: AnalyzeInput): RiskFactorReport {
  const factors: FactorContribution[] = [];
  let totalWeight = 0;
  let weightedSum = 0;
  let anyMissing = false;

  const mlSc = mlScore(input.mlResult, input.mlConfidence);
  addFactor(factors, 'ML', mlFactorRaw(input.mlResult, input.mlConfidence), mlSc, RISK_FACTOR_WEIGHTS.ML);
  totalWeight += RISK_FACTOR_WEIGHTS.ML;
  weightedSum += mlSc * RISK_FACTOR_WEIGHTS.ML;

  if (input.slopeDegrees != null) {
    const s = slopeScore(input.slopeDegrees);
    addFactor(factors, 'SLOPE', `${Math.round(input.slopeDegrees)}°`, s, RISK_FACTOR_WEIGHTS.SLOPE);
    totalWeight += RISK_FACTOR_WEIGHTS.SLOPE;
    weightedSum += s * RISK_FACTOR_WEIGHTS.SLOPE;
  } else {
    anyMissing = true;
  }

  if (input.rainMm != null) {
    const s = rainScore(input.rainMm);
    addFactor(factors, 'RAIN', `${Math.round(input.rainMm)} mm`, s, RISK_FACTOR_WEIGHTS.RAIN);
    totalWeight += RISK_FACTOR_WEIGHTS.RAIN;
    weightedSum += s * RISK_FACTOR_WEIGHTS.RAIN;
  } else {
    anyMissing = true;
  }

  if (input.elevationMeters != null) {
    const s = elevationScore(input.elevationMeters);
    addFactor(factors, 'ELEVATION', `${Math.round(input.elevationMeters)} m`, s, RISK_FACTOR_WEIGHTS.ELEVATION);
    totalWeight += RISK_FACTOR_WEIGHTS.ELEVATION;
    weightedSum += s * RISK_FACTOR_WEIGHTS.ELEVATION;
  } else {
    anyMissing = true;
  }

  if (input.soilScore != null) {
    const s = input.soilScore;
    addFactor(factors, 'SOIL', input.soilName ?? 'Tidak diketahui', s, RISK_FACTOR_WEIGHTS.SOIL);
    totalWeight += RISK_FACTOR_WEIGHTS.SOIL;
    weightedSum += s * RISK_FACTOR_WEIGHTS.SOIL;
  } else {
    anyMissing = true;
  }

  const finalScore = anyMissing && totalWeight > 0
    ? weightedSum / totalWeight
    : weightedSum;

  const clampedScore = Math.max(0, Math.min(1, finalScore));
  const finalResult = resultFromScore(clampedScore);

  const mlOnlyScore = mlScore(input.mlResult, input.mlConfidence);
  const isUpgraded = clampedScore > mlOnlyScore + 0.05;
  const isDowngraded = clampedScore < mlOnlyScore - 0.05;

  return {
    mlResult: input.mlResult,
    mlConfidence: input.mlConfidence,
    finalScore: clampedScore,
    finalResult,
    factors: factors.sort((a, b) => b.weightedScore - a.weightedScore),
    isUpgraded,
    isDowngraded,
  };
}
