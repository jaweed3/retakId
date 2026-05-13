import { useState } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { MapView } from '../components/MapView';
import { StatsSummaryCards } from '../components/StatsSummaryCards';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { ChartsSection } from '../components/ChartsSection';
import { DateRangeFilter, getDateRange, type DateRange } from '../components/DateRangeFilter';
import type { StatusFilter } from '../types/laporan';

export function DashboardPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const [dateRange, setDateRange] = useState<DateRange>('30d');
  const range = getDateRange(dateRange);

  const { data, counts, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: 200,
    dateFrom: range?.from || null,
    dateTo: range?.to || null,
  });

  return (
    <div className="h-full flex flex-col">
      {/* Panel Info */}
      <div className="shrink-0 bg-card border-b border-divider px-4 sm:px-6 py-4 sm:py-5">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-3 sm:mb-4">
            <div>
              <h2 className="text-sm sm:text-base font-bold text-text-primary">
                Dashboard Pemantauan
              </h2>
              <p className="text-[11px] sm:text-xs text-text-secondary mt-0.5">
                Retakan tanah di Jenangan, Ponorogo
              </p>
            </div>
            <DateRangeFilter current={dateRange} onChange={setDateRange} />
          </div>

          <div className="space-y-3">
            <StatsSummaryCards counts={counts} isLoading={isLoading} />
            <FilterStatusBar current={statusFilter} onChange={setStatusFilter} counts={counts} />
          </div>

          {/* Charts */}
          <div className="mt-4 sm:mt-5">
            <ChartsSection data={data} counts={counts} isLoading={isLoading} />
          </div>
        </div>
      </div>

      {/* Peta */}
      <div className="flex-1 relative">
        <MapView
          reports={data}
          isLoading={isLoading}
          error={error}
          onRetry={refetch}
          className="h-full"
        />
      </div>
    </div>
  );
}
