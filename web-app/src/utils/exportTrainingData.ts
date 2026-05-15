import { supabase, requireSupabase } from '../lib/supabase';
import type { RiwayatPenanganan, ReportStatus } from '../types/laporan';

interface TrainingRow {
  laporan_id: string;
  foto_url: string | null;
  status_ml: string;
  status_verifikasi: string;
  label_akhir: string;
  diverifikasi_oleh: string;
  diverifikasi_pada: string;
  catatan_verifikasi: string;
}

export async function fetchTrainingData(): Promise<TrainingRow[]> {
  if (!supabase) return [];
  const client = requireSupabase();

  const { data: rows, error } = await (client as any)
    .from('riwayat_penanganan')
    .select('*')
    .eq('tindakan', 'diverifikasi')
    .in('alasan', ['BENAR', 'SALAH'])
    .order('created_at', { ascending: false });

  if (error || !rows) return [];

  const laporanIds = [...new Set(rows.map((r: RiwayatPenanganan) => r.laporan_id))];
  const { data: laporanRows } = await (client as any)
    .from('laporan')
    .select('id, foto_url')
    .in('id', laporanIds);

  const fotoMap: Record<string, string | null> = {};
  if (laporanRows) {
    for (const l of laporanRows as Array<{ id: string; foto_url: string | null }>) {
      fotoMap[l.id] = l.foto_url;
    }
  }

  return (rows as RiwayatPenanganan[]).map((r) => {
    const detail = (r.detail || {}) as Record<string, unknown>;
    const isSalah = r.alasan === 'SALAH';
    return {
      laporan_id: r.laporan_id,
      foto_url: fotoMap[r.laporan_id] || null,
      status_ml: (detail.ml_status as string) || r.status || '',
      status_verifikasi: r.alasan || '',
      label_akhir: isSalah ? ((detail.label_benar as string) || r.status || '') : r.status || '',
      diverifikasi_oleh: r.ditangani_oleh,
      diverifikasi_pada: r.created_at,
      catatan_verifikasi: (detail.catatan as string) || '',
    };
  });
}

export function downloadTrainingCSV(data: TrainingRow[]): void {
  const header = [
    'laporan_id',
    'foto_url',
    'status_ml',
    'status_verifikasi',
    'label_akhir',
    'diverifikasi_oleh',
    'diverifikasi_pada',
    'catatan_verifikasi',
  ];

  const rows = data.map((r) =>
    header.map((key) => {
      const val = (r as unknown as Record<string, unknown>)[key];
      if (val === null || val === undefined) return '';
      const str = String(val);
      if (str.includes(',') || str.includes('"') || str.includes('\n')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    }).join(',')
  );

  const csv = [header.join(','), ...rows].join('\n');
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `training-data-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function getVerificationStats(client: ReturnType<typeof requireSupabase>) {
  const { data: allData } = await (client as any)
    .from('riwayat_penanganan')
    .select('alasan')
    .eq('tindakan', 'diverifikasi')
    .in('alasan', ['BENAR', 'SALAH']);

  if (!allData || allData.length === 0) {
    return { total: 0, benar: 0, salah: 0, akurasi: 0 };
  }

  const benar = allData.filter((r: { alasan: string }) => r.alasan === 'BENAR').length;
  const salah = allData.filter((r: { alasan: string }) => r.alasan === 'SALAH').length;
  const total = benar + salah;

  return {
    total,
    benar,
    salah,
    akurasi: total > 0 ? Math.round((benar / total) * 100) : 0,
  };
}
