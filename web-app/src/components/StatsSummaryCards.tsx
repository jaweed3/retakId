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
    <div className="flex items-center gap-3 rounded-xl bg-card px-4 py-3 shadow-sm animate-pulse">
      <div className="h-10 w-10 rounded-full bg-divider" />
      <div className="flex-1 space-y-2">
        <div className="h-4 w-16 rounded bg-divider" />
        <div className="h-3 w-20 rounded bg-divider" />
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

  const items = [
    {
      label: 'Aman',
      count: counts.aman,
      icon: ShieldCheck,
      color: 'text-aman',
      bg: 'bg-aman-bg',
    },
    {
      label: 'Waspada',
      count: counts.waspada,
      icon: AlertTriangle,
      color: 'text-waspada',
      bg: 'bg-waspada-bg',
    },
    {
      label: 'Bahaya',
      count: counts.bahaya,
      icon: Skull,
      color: 'text-bahaya',
      bg: 'bg-bahaya-bg',
    },
  ];

  return (
    <div className="grid grid-cols-3 gap-2 sm:gap-3">
      {items.map((item) => (
        <div
          key={item.label}
          className={cn(
            'flex items-center gap-2 sm:gap-3 rounded-xl bg-card px-3 py-3 sm:px-4 shadow-sm border border-divider/50',
          )}
        >
          <div className={cn('flex h-9 w-9 sm:h-10 sm:w-10 items-center justify-center rounded-full', item.bg)}>
            <item.icon className={cn('h-4 w-4 sm:h-5 sm:w-5', item.color)} />
          </div>
          <div className="min-w-0">
            <p className="text-lg sm:text-xl font-bold text-text-primary tabular-nums">{item.count}</p>
            <p className="text-xs text-text-secondary">{item.label}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
