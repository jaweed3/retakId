export type DetectionResult = 'AMAN' | 'WASPADA' | 'BAHAYA';

export interface FactorContribution {
  factor: RiskFactor;
  rawValue: string;
  score: number;
  weight: number;
  weightedScore: number;
  riskLabel: RiskLabel;
}

export type RiskFactor = 'ML' | 'SLOPE' | 'RAIN' | 'ELEVATION' | 'SOIL';

export type RiskLabel = 'RENDAH' | 'SEDANG' | 'TINGGI' | 'SANGAT_TINGGI';

export interface RiskFactorReport {
  mlResult: DetectionResult;
  mlConfidence: number;
  finalScore: number;
  finalResult: DetectionResult;
  factors: FactorContribution[];
  isUpgraded: boolean;
  isDowngraded: boolean;
}

export interface CalculateRiskRequest {
  mlResult: DetectionResult;
  mlConfidence: number;
  latitude: number;
  longitude: number;
}

export interface CalculateRiskResponse {
  success: boolean;
  data?: RiskFactorReport;
  error?: string;
}
