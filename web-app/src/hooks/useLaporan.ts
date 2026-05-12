import { useCallback, useEffect, useState } from 'react';
import type { Laporan, StatusFilter } from '../types/laporan';
import { supabase } from '../lib/supabase';

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

export function useLaporan(options: UseLaporanOptions = {}): UseLaporanReturn {
  const { status = 'SEMUA', limit = PAGE_SIZE, page = 0 } = options;

  const [data, setData] = useState<Laporan[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [counts, setCounts] = useState({ total: 0, aman: 0, waspada: 0, bahaya: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      // Hitung per-status sekaligus
      const [countResult] = await Promise.all([
        supabase.rpc('get_laporan_counts').maybeSingle(),
      ]);

      // Fetch data dengan filter
      let query = supabase
        .from('laporan')
        .select('*', { count: 'exact' })
        .order('created_at', { ascending: false })
        .range(page * limit, page * limit + limit - 1);

      if (status !== 'SEMUA') {
        query = query.eq('status', status);
      }

      const { data: rows, count, error: fetchError } = await query;

      if (fetchError) throw fetchError;

      setData((rows || []).map(mapRow));
      setTotalCount(count || 0);

      // Parse counts dari RPC, fallback ke client-side count
      if (countResult?.data) {
        const c = countResult.data as Record<string, number>;
        setCounts({
          total: (c.aman || 0) + (c.waspada || 0) + (c.bahaya || 0),
          aman: c.aman || 0,
          waspada: c.waspada || 0,
          bahaya: c.bahaya || 0,
        });
      } else {
        // Fallback: ambil total dari query tanpa filter
        const { count: total } = await supabase
          .from('laporan')
          .select('*', { count: 'exact', head: true });
        setCounts((prev) => ({ ...prev, total: total || 0 }));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal memuat data');
    } finally {
      setIsLoading(false);
    }
  }, [status, limit, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Realtime subscription
  useEffect(() => {
    const channel = supabase
      .channel('laporan-realtime')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'laporan' },
        () => {
          fetchData();
        },
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [fetchData]);

  return { data, totalCount, counts, isLoading, error, refetch: fetchData };
}
