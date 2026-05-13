import { useCallback, useEffect, useState } from 'react';
import type { Laporan, StatusFilter } from '../types/laporan';
import { supabase, requireSupabase } from '../lib/supabase';

interface UseLaporanOptions {
  status?: StatusFilter;
  dateFrom?: string | null;
  dateTo?: string | null;
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
  const { status = 'SEMUA', dateFrom, dateTo, limit = PAGE_SIZE, page = 0 } = options;

  const [data, setData] = useState<Laporan[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [counts, setCounts] = useState(EMPTY_COUNTS);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (!supabase) {
      setIsLoading(false);
      return;
    }
    const client = requireSupabase();

    setIsLoading(true);
    setError(null);

    try {
      let query = client
        .from('laporan')
        .select('*', { count: 'exact' })
        .order('created_at', { ascending: false });

      if (status !== 'SEMUA') {
        query = query.eq('status', status);
      }
      if (dateFrom) {
        query = query.gte('created_at', dateFrom);
      }
      if (dateTo) {
        query = query.lte('created_at', dateTo);
      }

      query = query.range(page * limit, page * limit + limit - 1);

      const { data: rows, count, error: fetchError } = await query;

      if (fetchError) throw fetchError;

      setData((rows || []).map(mapRow));
      setTotalCount(count || 0);

      // Counts per status (with date filter)
      try {
        const buildCountQuery = (s: string) => {
          let q = client.from('laporan').select('*', { count: 'exact', head: true }).eq('status', s);
          if (dateFrom) q = q.gte('created_at', dateFrom);
          if (dateTo) q = q.lte('created_at', dateTo);
          return q;
        };
        const results = await Promise.all([
          buildCountQuery('AMAN'),
          buildCountQuery('WASPADA'),
          buildCountQuery('BAHAYA'),
        ]);
        const aman = results[0].count || 0;
        const waspada = results[1].count || 0;
        const bahaya = results[2].count || 0;
        setCounts({ total: aman + waspada + bahaya, aman, waspada, bahaya });
      } catch {
        let totalQuery = client.from('laporan').select('*', { count: 'exact', head: true });
        if (dateFrom) totalQuery = totalQuery.gte('created_at', dateFrom);
        if (dateTo) totalQuery = totalQuery.lte('created_at', dateTo);
        const { count: total } = await totalQuery;
        setCounts({ total: total || 0, aman: 0, waspada: 0, bahaya: 0 });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal memuat data');
    } finally {
      setIsLoading(false);
    }
  }, [status, dateFrom, dateTo, limit, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Realtime subscription
  useEffect(() => {
    if (!supabase) return;
    const client = requireSupabase();

    const channel = client
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
      client.removeChannel(channel);
    };
  }, [fetchData]);

  return { data, totalCount, counts, isLoading, error, refetch: fetchData };
}
