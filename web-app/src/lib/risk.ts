import type { FactorContribution, ReportStatus } from '../types/laporan';

const EDGE_FUNCTION_URL =
  import.meta.env.VITE_SUPABASE_EDGE_FUNCTION_URL ??
  (() => {
    const url = import.meta.env.VITE_SUPABASE_URL;
    if (url) return `${url}/functions/v1/calculate-risk`;
    return null;
  })();

export interface RiskFactorReport {
  mlResult: ReportStatus;
  mlConfidence: number;
  finalScore: number;
  finalResult: ReportStatus;
  factors: FactorContribution[];
  isUpgraded: boolean;
  isDowngraded: boolean;
}

interface CalculateRiskRequest {
  mlResult: ReportStatus;
  mlConfidence: number;
  latitude: number;
  longitude: number;
}

interface CalculateRiskResponse {
  success: boolean;
  data?: RiskFactorReport;
  error?: string;
}

export async function calculateRisk(
  params: CalculateRiskRequest,
): Promise<RiskFactorReport | null> {
  if (!EDGE_FUNCTION_URL) {
    console.warn('[risk] EDGE_FUNCTION_URL tidak dikonfigurasi.');
    return null;
  }

  try {
    const res = await fetch(EDGE_FUNCTION_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
    });

    if (!res.ok) {
      const errBody = await res.json().catch(() => null);
      console.warn('[risk] API error:', res.status, errBody?.error ?? res.statusText);
      return null;
    }

    const json: CalculateRiskResponse = await res.json();
    if (!json.success || !json.data) {
      console.warn('[risk] Response tidak sukses:', json.error);
      return null;
    }

    return json.data;
  } catch (err) {
    console.warn('[risk] Network error:', err);
    return null;
  }
}
