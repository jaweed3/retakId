import { useState } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { MapView } from '../components/MapView';
import { StatsSummaryCards } from '../components/StatsSummaryCards';
import { FilterStatusBar } from '../components/FilterStatusBar';
import type { StatusFilter } from '../types/laporan';

export function DashboardPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');

  const { data, counts, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: 200,
  });

  return (
    <div className="h-full flex flex-col">
      {/* Map area */}
      <div className="flex-1 relative">
        <MapView
          reports={data}
          isLoading={isLoading}
          error={error}
          onRetry={refetch}
          className="h-full"
        />

        {/* Overlay: Stats + Filter */}
        <div className="absolute top-3 left-3 right-3 z-[1000] pointer-events-none">
          <div className="pointer-events-auto space-y-3 max-w-md">
            <StatsSummaryCards counts={counts} isLoading={isLoading} />
            <FilterStatusBar
              current={statusFilter}
              onChange={setStatusFilter}
              counts={counts}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
