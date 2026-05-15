import { useState, useMemo } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { DateRangeFilter, getDateRange, type DateRange } from '../components/DateRangeFilter';
import { TrendChart } from '../components/TrendChart';
import { StatusDistribution } from '../components/StatusDistribution';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import type { Laporan } from '../types/laporan';
import { MapPin, AlertTriangle, ShieldCheck, TrendingUp } from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';

function TopLocationsChart({ data }: { data: Laporan[] }) {
  const locations = useMemo(() => {
    const map = new Map<string, { name: string; total: number; aman: number; waspada: number; bahaya: number }>();
    data.forEach((r) => {
      const name = r.nama_lokasi;
      if (!map.has(name)) map.set(name, { name, total: 0, aman: 0, waspada: 0, bahaya: 0 });
      const entry = map.get(name)!;
      entry.total++;
      if (r.status === 'AMAN') entry.aman++;
      else if (r.status === 'WASPADA') entry.waspada++;
      else entry.bahaya++;
    });
    return Array.from(map.values())
      .sort((a, b) => b.total - a.total)
      .slice(0, 10);
  }, [data]);

  if (locations.length === 0) return null;

  return (
    <div className="rounded-2xl bg-card dark:bg-black border border-divider dark:border-white/10 p-5 sm:p-6">
      <h3 className="text-sm font-semibold text-text-primary mb-4">Top 10 Lokasi Terbanyak Laporan</h3>
      <ResponsiveContainer width="100%" height={Math.max(200, locations.length * 32)}>
        <BarChart data={locations} layout="vertical" margin={{ top: 0, right: 10, left: 10, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-text-secondary)" strokeOpacity={0.15} horizontal={false} />
          <XAxis type="number" tick={{ fontSize: 11, fill: 'var(--color-text-secondary)' }} tickLine={false} axisLine={{ stroke: 'var(--color-divider)' }} allowDecimals={false} />
          <YAxis type="category" dataKey="name" tick={{ fontSize: 11, fill: 'var(--color-text-secondary)' }} tickLine={false} axisLine={{ stroke: 'var(--color-divider)' }} width={120} />
          <Tooltip
            contentStyle={{
              background: 'var(--color-card)',
              border: '1px solid var(--color-divider)',
              borderRadius: 8,
              fontSize: 12,
              color: 'var(--color-text-primary)',
            }}
          />
          <Bar dataKey="total" radius={[0, 4, 4, 0]}>
            {locations.map((loc) => (
              <Cell
                key={loc.name}
                fill={loc.bahaya > 0 ? 'var(--color-bahaya)' : loc.waspada > 0 ? 'var(--color-waspada)' : 'var(--color-aman)'}
                fillOpacity={0.8}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

import { SEOMeta } from '../components/SEOMeta';

export function StatisticsPage() {
  const [dateRange, setDateRange] = useState<DateRange>('30d');
  const range = useMemo(() => getDateRange(dateRange), [dateRange]);

  const { data, counts, isLoading, error, refetch } = useLaporan({
    limit: 1000,
    dateFrom: range?.from || null,
    dateTo: range?.to || null,
  });

  const metrics = useMemo(() => {
    if (!data.length) return null;
    const uniqueDays = new Set(data.map((r) => r.created_at.slice(0, 10))).size;
    const verified = data.filter((r) => r.terverifikasi > 0).length;
    const uniqueLocations = new Set(data.map((r) => r.nama_lokasi.toLowerCase().trim())).size;
    return {
      total: data.length,
      avgPerDay: uniqueDays > 0 ? (data.length / uniqueDays).toFixed(1) : '0',
      verified,
      locations: uniqueLocations,
    };
  }, [data]);

  if (isLoading) return <LoadingSpinner text="Memuat statistik..." />;
  if (error) return <ErrorState message={error} onRetry={refetch} />;

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6 sm:py-8">
      <SEOMeta title="Statistik & Analitik" description="Analisis data laporan retakan tanah: tren harian, distribusi status, top lokasi, dan statistik real-time." />
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6 sm:mb-8">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold text-text-primary">Statistik & Analitik</h2>
          <p className="text-sm text-text-secondary mt-1">
            Analisis data laporan retakan tanah di Jenangan, Ponorogo.
          </p>
        </div>
        <DateRangeFilter current={dateRange} onChange={setDateRange} />
      </div>

      {!data.length ? (
        <EmptyState title="Belum ada data" description="Tidak ada laporan dalam rentang waktu ini." />
      ) : (
        <>
          {/* Metrics */}
          {metrics && (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 mb-6 sm:mb-8">
              <MetricCard icon={MapPin} label="Total Laporan" value={metrics.total.toLocaleString('id-ID')} color="text-primary" bg="bg-primary-surface" />
              <MetricCard icon={TrendingUp} label="Rata-rata/Hari" value={metrics.avgPerDay} color="text-waspada" bg="bg-waspada-bg" />
              <MetricCard icon={ShieldCheck} label="Terverifikasi" value={metrics.verified.toString()} color="text-primary" bg="bg-primary-surface" />
              <MetricCard icon={AlertTriangle} label="Daerah Terdampak" value={metrics.locations.toString()} color="text-bahaya" bg="bg-bahaya-bg" />
            </div>
          )}

          {/* Charts */}
          <div className="grid lg:grid-cols-3 gap-5 sm:gap-6 mb-6 sm:mb-8">
            <div className="lg:col-span-2">
              <TrendChart data={data} />
            </div>
            <StatusDistribution aman={counts.aman} waspada={counts.waspada} bahaya={counts.bahaya} />
          </div>

          <TopLocationsChart data={data} />
        </>
      )}
    </div>
  );
}

function MetricCard({
  icon: Icon, label, value, color, bg,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  color: string;
  bg: string;
}) {
  return (
    <div className="rounded-xl bg-card dark:bg-black border border-divider dark:border-white/10 p-4 sm:p-5">
      <div className={`flex h-9 w-9 sm:h-10 sm:w-10 items-center justify-center rounded-lg ${bg} mb-3`}>
        <Icon className={`h-4 w-4 sm:h-5 sm:w-5 ${color}`} />
      </div>
      <p className="text-xl sm:text-2xl font-bold text-text-primary tabular-nums">{value}</p>
      <p className="text-[10px] sm:text-xs text-text-secondary mt-0.5">{label}</p>
    </div>
  );
}
