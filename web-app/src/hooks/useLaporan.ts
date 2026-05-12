import { useCallback, useEffect, useState } from 'react';
import type { Laporan, StatusFilter } from '../types/laporan';
import { supabase, requireSupabase } from '../lib/supabase';

interface UseLaporanOptions {
  status?: StatusFilter;
  limit?: number;
  page?: number;
}

interface UseLaporanReturn {
  data: Laporan[];
  totalCount: number;
  counts: { total: number; aman: number; waspada: number; bahaya: number };
  isLoading: boolean;
  error: string | null;
  refetch: () => void;
}

const PAGE_SIZE = 12;

function mapRow(row: Record<string, unknown>): Laporan {
  return {
    id: row.id as string,
    nama_lokasi: row.nama_lokasi as string,
    status: row.status as Laporan['status'],
    catatan: (row.catatan as string) || '',
    latitude: row.latitude as number,
    longitude: row.longitude as number,
    foto_url: (row.foto_url as string) || null,
    pelapor: (row.pelapor as string) || 'Anonim',
    terverifikasi: (row.terverifikasi as number) || 0,
    created_at: row.created_at as string,
  };
}

const EMPTY_COUNTS = { total: 0, aman: 0, waspada: 0, bahaya: 0 };

export function useLaporan(options: UseLaporanOptions = {}): UseLaporanReturn {
  const { status = 'SEMUA', limit = PAGE_SIZE, page = 0 } = options;
  const [data, setData] = useState<Laporan[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [counts, setCounts] = useState(EMPTY_COUNTS);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (!supabase) { setIsLoading(false); return; }
    const client = requireSupabase();
    setIsLoading(true);
    setError(null);

    try {
      let query = client
        .from('laporan')
        .select('*', { count: 'exact' })
        .order('created_at', { ascending: false })
        .range(page * limit, page * limit + limit - 1);
      if (status !== 'SEMUA') query = query.eq('status', status);

      const { data: rows, count, error: fetchError } = await query;
      if (fetchError) throw fetchError;
      setData((rows || []).map(mapRow));
      setTotalCount(count || 0);

      try {
        const results = await Promise.all([
          client.from('laporan').select('*', { count: 'exact', head: true }).eq('status', 'AMAN'),
          client.from('laporan').select('*', { count: 'exact', head: true }).eq('status', 'WASPADA'),
          client.from('laporan').select('*', { count: 'exact', head: true }).eq('status', 'BAHAYA'),
        ]);
        setCounts({
          total: (results[0].count || 0) + (results[1].count || 0) + (results[2].count || 0),
          aman: results[0].count || 0,
          waspada: results[1].count || 0,
          bahaya: results[2].count || 0,
        });
      } catch {
        const { count: total } = await client.from('laporan').select('*', { count: 'exact', head: true });
        setCounts({ total: total || 0, aman: 0, waspada: 0, bahaya: 0 });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal memuat data');
    } finally {
      setIsLoading(false);
    }
  }, [status, limit, page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  useEffect(() => {
    if (!supabase) return;
    const client = requireSupabase();
    const channel = client
      .channel('laporan-realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'laporan' }, () => fetchData())
      .subscribe();
    return () => { client.removeChannel(channel); };
  }, [fetchData]);

  return { data, totalCount, counts, isLoading, error, refetch: fetchData };
}
