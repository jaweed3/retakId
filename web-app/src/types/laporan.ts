export type ReportStatus = 'AMAN' | 'WASPADA' | 'BAHAYA';
export type PredictionLabel = ReportStatus | 'TIDAK_PASTI';
export type StatusFilter = 'SEMUA' | ReportStatus;

export interface Laporan {
  id: string;
  nama_lokasi: string;
  status: ReportStatus;
  catatan: string;
  latitude: number;
  longitude: number;
  foto_url: string | null;
  pelapor: string;
  terverifikasi: number;
  created_at: string;
}

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

export interface Database {
  public: {
    Tables: {
      laporan: {
        Row: {
          id: string;
          nama_lokasi: string;
          status: string;
          catatan: string;
          latitude: number;
          longitude: number;
          foto_url: string | null;
          pelapor: string;
          terverifikasi: number;
          created_at: string;
        };
        Insert: Omit<Database['public']['Tables']['laporan']['Row'], 'id' | 'created_at'>;
        Update: Partial<Database['public']['Tables']['laporan']['Row']>;
      };
    };
  };
}
