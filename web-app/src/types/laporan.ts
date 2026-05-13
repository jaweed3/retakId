export type ReportStatus = 'AMAN' | 'WASPADA' | 'BAHAYA';
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
