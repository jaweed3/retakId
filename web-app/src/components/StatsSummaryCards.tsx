import { ShieldCheck, AlertTriangle, Skull } from 'lucide-react';
import { cn } from '../utils/cn';

interface Stats {
  aman: number;
  waspada: number;
  bahaya: number;
}

interface StatsSummaryCardsProps {
  counts: Stats;
  isLoading?: boolean;
}

function SkeletonCard() {
  return (
    <div className="flex items-center gap-1.5 sm:gap-3 rounded-xl bg-card px-2.5 py-2.5 sm:px-4 sm:py-3.5 shadow-sm border border-divider/40 animate-pulse">
      <div className="h-8 w-8 sm:h-10 sm:w-10 rounded-full bg-divider/50" />
      <div className="flex-1 space-y-2">
        <div className="h-5 w-12 rounded bg-divider/50" />
        <div className="h-3 w-16 rounded bg-divider/50" />
      </div>
    </div>
  );
}

export function StatsSummaryCards({ counts, isLoading }: StatsSummaryCardsProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-3 gap-2 sm:gap-3">
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    );
  }

  const total = counts.aman + counts.waspada + counts.bahaya;

  const items = [
    { label: 'Aman', count: counts.aman, icon: ShieldCheck, color: 'text-aman', bg: 'bg-aman-bg', ring: 'ring-aman/20' },
    { label: 'Waspada', count: counts.waspada, icon: AlertTriangle, color: 'text-waspada', bg: 'bg-waspada-bg', ring: 'ring-waspada/20' },
    { label: 'Bahaya', count: counts.bahaya, icon: Skull, color: 'text-bahaya', bg: 'bg-bahaya-bg', ring: 'ring-bahaya/20' },
  ];

  return (
    <div className="space-y-0">
      <div className="grid grid-cols-3 gap-1.5 sm:gap-3">
        {items.map((item) => (
          <div
            key={item.label}
            className={cn(
              'flex items-center gap-1.5 sm:gap-3 rounded-xl bg-card px-2.5 py-2.5 sm:px-4 sm:py-3.5 shadow-sm border border-divider/40',
              'hover:shadow-md transition-shadow',
            )}
          >
            <div className={cn('flex h-8 w-8 sm:h-10 sm:w-10 shrink-0 items-center justify-center rounded-full ring-2', item.bg, item.ring)}>
              <item.icon className={cn('h-4 w-4 sm:h-5 sm:w-5', item.color)} />
            </div>
            <div className="min-w-0">
              <p className="text-base sm:text-xl font-bold text-text-primary tabular-nums leading-tight">{item.count}</p>
              <p className="text-[10px] sm:text-xs text-text-secondary">{item.label}</p>
            </div>
          </div>
        ))}
      </div>
      {total > 0 && (
        <p className="text-[9px] sm:text-[10px] text-text-secondary/60 text-right mt-1 mr-1">{total} total laporan</p>
      )}
    </div>
  );
}
