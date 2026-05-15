import { useCallback, useEffect, useRef, useState } from 'react';
import type { Laporan, StatusFilter } from '../types/laporan';
import { supabase, requireSupabase } from '../lib/supabase';

export type ResolvedFilter = 'active' | 'resolved' | 'all';

interface UseLaporanOptions {
  status?: StatusFilter;
  dateFrom?: string | null;
  dateTo?: string | null;
  resolvedFilter?: ResolvedFilter;
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
    is_resolved: (row.is_resolved as boolean) || false,
  };
}

const EMPTY_COUNTS = { total: 0, aman: 0, waspada: 0, bahaya: 0 };

function buildQuery(
  client: ReturnType<typeof requireSupabase>,
  opts: { status: StatusFilter; dateFrom?: string | null; dateTo?: string | null; resolvedFilter: ResolvedFilter; limit: number; page: number },
) {
  let q = client.from('laporan').select('*', { count: 'exact' }).order('created_at', { ascending: false });
  if (opts.status !== 'SEMUA') q = q.eq('status', opts.status);
  if (opts.dateFrom) q = q.gte('created_at', opts.dateFrom);
  if (opts.dateTo) q = q.lte('created_at', opts.dateTo);
  if (opts.resolvedFilter === 'active') q = q.not('is_resolved', 'is', 'true');
  else if (opts.resolvedFilter === 'resolved') q = q.eq('is_resolved', true);
  return q.range(opts.page * opts.limit, opts.page * opts.limit + opts.limit - 1);
}

function buildCountQuery(
  client: ReturnType<typeof requireSupabase>,
  s: string,
  opts: { dateFrom?: string | null; dateTo?: string | null; resolvedFilter: ResolvedFilter },
) {
  let q = client.from('laporan').select('*', { count: 'exact', head: true }).eq('status', s);
  if (opts.dateFrom) q = q.gte('created_at', opts.dateFrom);
  if (opts.dateTo) q = q.lte('created_at', opts.dateTo);
  if (opts.resolvedFilter === 'active') q = q.not('is_resolved', 'is', 'true');
  else if (opts.resolvedFilter === 'resolved') q = q.eq('is_resolved', true);
  return q;
}

export function useLaporan(options: UseLaporanOptions = {}): UseLaporanReturn {
  const { status = 'SEMUA', dateFrom, dateTo, resolvedFilter = 'active', limit = PAGE_SIZE, page = 0 } = options;
  const [data, setData] = useState<Laporan[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [counts, setCounts] = useState(EMPTY_COUNTS);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    if (!supabase) { setIsLoading(false); return; }
    const client = requireSupabase();
    setIsLoading(true); setError(null);

    const opts = { status, dateFrom, dateTo, resolvedFilter, limit, page };

    try {
      // Main query
      let countOpts = opts;
      let query = buildQuery(client, opts);
      let result = await query;

      // If is_resolved column doesn't exist, handle gracefully
      if (result.error && ((result.error.message || '').includes('is_resolved') || result.error.code === '42703')) {
        // If filtering for resolved, return empty — nothing is resolved yet
        if (opts.resolvedFilter === 'resolved') {
          setData([]);
          setTotalCount(0);
          setCounts(EMPTY_COUNTS);
          setIsLoading(false);
          return;
        }
        // If filtering for active, retry without filter (all data is active)
        countOpts = { ...opts, resolvedFilter: 'all' as ResolvedFilter };
        result = await buildQuery(client, countOpts);
      }

      if (result.error) throw result.error;
      setData((result.data || []).map(mapRow));
      setTotalCount(result.count || 0);

      // Count queries — use same fallback opts as main query
      try {
        const results = await Promise.all([
          buildCountQuery(client, 'AMAN', countOpts),
          buildCountQuery(client, 'WASPADA', countOpts),
          buildCountQuery(client, 'BAHAYA', countOpts),
        ]);
        const aman = results[0].count || 0;
        const waspada = results[1].count || 0;
        const bahaya = results[2].count || 0;
        setCounts({ total: aman + waspada + bahaya, aman, waspada, bahaya });
      } catch {
        setCounts(EMPTY_COUNTS);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal memuat data');
    } finally {
      setIsLoading(false);
    }
  }, [status, dateFrom, dateTo, resolvedFilter, limit, page]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const fetchRef = useRef(fetchData); fetchRef.current = fetchData;
  const channelId = useRef(`laporan-rt-${Math.random().toString(36).slice(2, 8)}`);
  useEffect(() => {
    if (!supabase) return;
    const client = requireSupabase();
    const channel = client.channel(channelId.current)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'laporan' }, () => { fetchRef.current(); })
      .subscribe();
    return () => { client.removeChannel(channel); };
  }, []);

  return { data, totalCount, counts, isLoading, error, refetch: fetchData };
}
