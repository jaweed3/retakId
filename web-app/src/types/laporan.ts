export type ReportStatus = 'AMAN' | 'WASPADA' | 'BAHAYA';
export type PredictionLabel = ReportStatus | 'TIDAK_PASTI';
export type StatusFilter = 'SEMUA' | ReportStatus;

export interface DetectionResult {
  status: ReportStatus;
  confidence: number;
}

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
  mlResult: DetectionResult;
  mlConfidence: number;
  finalScore: number;
  finalResult: DetectionResult;
  factors: FactorContribution[];
  isUpgraded: boolean;
  isDowngraded: boolean;
}

export type VerifLabel = 'BENAR' | 'SALAH';

export interface VerificationData {
  label_verifikasi: VerifLabel;
  label_benar?: ReportStatus;
  catatan: string;
}

export interface TrainingRecord {
  laporan_id: string;
  ml_status: ReportStatus;
  label_verifikasi: VerifLabel;
  label_akhir: ReportStatus;
  foto_url: string | null;
  diverifikasi_oleh: string;
  created_at: string;
}

export interface RiwayatPenanganan {
  id: string;
  laporan_id: string;
  nama_lokasi: string;
  status: string;
  ditangani_oleh: string;
  tindakan: string;
  alasan: string | null;
  detail: Record<string, unknown> | null;
  created_at: string;
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
