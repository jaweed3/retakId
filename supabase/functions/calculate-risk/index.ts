import { analyze } from './engine.ts';
import { getElevation } from './services/elevation.ts';
import { calculateSlope } from './services/slope.ts';
import { getRainfall } from './services/rainfall.ts';
import { getSoilType } from './services/soil.ts';
import type { CalculateRiskRequest, CalculateRiskResponse } from './types.ts';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

function errorResponse(status: number, message: string): Response {
  const body: CalculateRiskResponse = { success: false, error: message };
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
  });
}

function corsPreflight(): Response {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

async function handler(req: Request): Promise<Response> {
  if (req.method === 'OPTIONS') return corsPreflight();
  if (req.method !== 'POST') {
    return errorResponse(405, 'Method not allowed. Use POST.');
  }

  let body: CalculateRiskRequest;
  try {
    body = await req.json();
  } catch {
    return errorResponse(400, 'Invalid JSON body.');
  }

  const { mlResult, mlConfidence, latitude, longitude } = body;

  if (!mlResult || mlConfidence == null || latitude == null || longitude == null) {
    return errorResponse(400, 'Missing required fields: mlResult, mlConfidence, latitude, longitude.');
  }

  if (!['AMAN', 'WASPADA', 'BAHAYA'].includes(mlResult)) {
    return errorResponse(400, 'mlResult must be one of: AMAN, WASPADA, BAHAYA.');
  }

  if (mlConfidence < 0 || mlConfidence > 1) {
    return errorResponse(400, 'mlConfidence must be between 0 and 1.');
  }

  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return errorResponse(400, 'Invalid latitude/longitude range.');
  }

  try {
    const [elevationMeters, rainfall, soilType] = await Promise.all([
      getElevation(latitude, longitude),
      getRainfall(latitude, longitude),
      getSoilType(latitude, longitude),
    ]);

    const slopeDegrees = elevationMeters != null
      ? await calculateSlope(latitude, longitude)
      : null;

    const envTimeout = setTimeout(() => {
      console.warn('[calculate-risk] Env fetch exceeded 10s budget');
    }, 10_000);

    const report = analyze({
      mlResult,
      mlConfidence,
      slopeDegrees: slopeDegrees ?? undefined,
      rainMm: rainfall ?? undefined,
      elevationMeters: elevationMeters ?? undefined,
      soilScore: soilType?.score,
      soilName: soilType?.name,
    });

    clearTimeout(envTimeout);

    const response: CalculateRiskResponse = { success: true, data: report };
    return new Response(JSON.stringify(response), {
      status: 200,
      headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Internal server error';
    return errorResponse(500, message);
  }
}

Deno.serve(handler);
