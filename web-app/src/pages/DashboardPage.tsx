import { useState, useMemo } from 'react';
import { useLaporan, type ResolvedFilter } from '../hooks/useLaporan';
import { MapView } from '../components/MapView';
import { StatsSummaryCards } from '../components/StatsSummaryCards';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { ChartsSection } from '../components/ChartsSection';
import { DateRangeFilter, getDateRange, type DateRange } from '../components/DateRangeFilter';
import { SEOMeta } from '../components/SEOMeta';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { cn } from '../utils/cn';
import type { StatusFilter } from '../types/laporan';

const RESOLVED_OPTIONS: { key: ResolvedFilter; label: string }[] = [
  { key: 'active', label: 'Aktif' },
  { key: 'all', label: 'Semua' },
  { key: 'resolved', label: 'Tertangani' },
];

export function DashboardPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const [dateRange, setDateRange] = useState<DateRange>('30d');
  const [chartsOpen, setChartsOpen] = useState(true);
  const [resolvedFilter, setResolvedFilter] = useState<ResolvedFilter>('active');
  const range = useMemo(() => getDateRange(dateRange), [dateRange]);

  const { data, counts, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: 200,
    dateFrom: range?.from || null,
    dateTo: range?.to || null,
    resolvedFilter,
  });

  return (
    <div className="sm:flex sm:flex-col">
      <SEOMeta title="Peta Pemantauan" description="Peta interaktif sebaran laporan retakan tanah di Jenangan, Ponorogo. Monitoring real-time oleh BPBD." />

      <div className="shrink-0 bg-card border-b border-divider px-4 sm:px-6 py-3 sm:py-4">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-3 mb-2 sm:mb-3">
            <div>
              <h2 className="text-sm sm:text-base font-bold text-text-primary">Peta Pemantauan</h2>
              <p className="text-[11px] sm:text-xs text-text-secondary mt-0.5">Retakan tanah di Jenangan, Ponorogo</p>
            </div>
            <div className="flex items-center gap-2">
              <DateRangeFilter current={dateRange} onChange={setDateRange} />
              <button onClick={() => setChartsOpen(!chartsOpen)} className="hidden sm:flex items-center gap-1 rounded-lg border border-divider px-2.5 py-1.5 text-xs text-text-secondary hover:text-text-primary hover:bg-divider/20 transition-colors">
                {chartsOpen ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />} Grafik
              </button>
            </div>
          </div>

          <div className="space-y-2">
            <StatsSummaryCards counts={counts} isLoading={isLoading} />
            <FilterStatusBar current={statusFilter} onChange={setStatusFilter} counts={counts} />
          </div>

          {chartsOpen && (
            <div className="mt-3 sm:mt-4">
              <ChartsSection data={data} counts={counts} isLoading={isLoading} />
            </div>
          )}
        </div>
      </div>

      {/* Resolved toggle */}
      <div className="flex justify-center gap-1.5 py-2 bg-surface border-b border-divider">
        {RESOLVED_OPTIONS.map((opt) => (
          <button key={opt.key} onClick={() => setResolvedFilter(opt.key)}
            className={cn('rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
              resolvedFilter === opt.key ? 'bg-primary text-white shadow-sm' : 'text-text-secondary hover:text-text-primary hover:bg-divider/30')}>
            {opt.label}
          </button>
        ))}
      </div>

      <div className="w-full px-0 lg:px-[100px]">
        <MapView reports={data} isLoading={isLoading} error={error} onRetry={refetch}
          className="h-[50vh] sm:h-[60vh] lg:h-[1000px] w-full" />
      </div>
    </div>
  );
}
