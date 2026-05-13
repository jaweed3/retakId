import { useState } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { MapView } from '../components/MapView';
import { StatsSummaryCards } from '../components/StatsSummaryCards';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { MapPin } from 'lucide-react';
import type { StatusFilter } from '../types/laporan';

export function DashboardPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const { data, counts, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: 200,
  });

  return (
    <div className="h-full flex flex-col">
      {/* ─── Panel Info — solid, bukan overlay ─── */}
      <div className="shrink-0 bg-card border-b border-divider px-4 sm:px-6 py-4">
        <div className="max-w-6xl mx-auto">
          {/* Header */}
          <div className="flex items-center gap-2 mb-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
              <MapPin className="h-4 w-4 text-white" />
            </div>
            <div>
              <h2 className="text-base font-bold text-text-primary leading-tight">
                Dashboard Pemantauan
              </h2>
              <p className="text-xs text-text-secondary">
                Retakan tanah di Jenangan, Ponorogo
              </p>
            </div>
          </div>

          {/* Stats + Filter */}
          <div className="space-y-3">
            <StatsSummaryCards counts={counts} isLoading={isLoading} />
            <FilterStatusBar
              current={statusFilter}
              onChange={setStatusFilter}
              counts={counts}
            />
          </div>
        </div>
      </div>

      {/* ─── Peta — full sisa area ─── */}
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
