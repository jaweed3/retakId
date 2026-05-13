import type { Laporan } from '../types/laporan';
import { TrendChart } from './TrendChart';
import { StatusDistribution } from './StatusDistribution';

interface ChartsSectionProps {
  data: Laporan[];
  counts: { aman: number; waspada: number; bahaya: number };
  isLoading?: boolean;
}

export function ChartsSection({ data, counts, isLoading }: ChartsSectionProps) {
  return (
    <div className="grid sm:grid-cols-3 gap-4 sm:gap-5">
      <div className="sm:col-span-2">
        <TrendChart data={data} isLoading={isLoading} />
      </div>
      <StatusDistribution aman={counts.aman} waspada={counts.waspada} bahaya={counts.bahaya} isLoading={isLoading} />
    </div>
  );
}
