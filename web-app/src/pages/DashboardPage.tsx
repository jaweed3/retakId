import { useState } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { MapView } from '../components/MapView';
import { StatsSummaryCards } from '../components/StatsSummaryCards';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { useTheme } from '../context/ThemeContext';
import type { StatusFilter } from '../types/laporan';

export function DashboardPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const { theme } = useTheme();
  const { data, counts, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: 200,
  });

  return (
    <div className="h-full flex flex-col">
      <div className="flex-1 relative">
        {/* Peta selalu full area */}
        <MapView
          reports={data}
          isLoading={isLoading}
          error={error}
          onRetry={refetch}
          className="h-full"
        />

        {/* Overlay: Stats + Filter — di atas peta */}
        <div className="absolute top-0 left-0 right-0 z-[1000] pointer-events-none p-3 sm:p-4">
          <div className="pointer-events-auto space-y-3 max-w-lg mx-auto sm:mx-0">
            {/* Welcome text */}
            <div className="hidden sm:block">
              <h2 className="text-base font-bold text-text-primary drop-shadow-sm">
                Dashboard Pemantauan
              </h2>
              <p className="text-xs text-text-secondary drop-shadow-sm">
                Retakan tanah di Jenangan, Ponorogo
              </p>
            </div>
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
