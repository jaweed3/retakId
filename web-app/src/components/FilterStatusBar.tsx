import type { StatusFilter } from '../types/laporan';
import { cn } from '../utils/cn';

interface FilterStatusBarProps {
  current: StatusFilter;
  onChange: (filter: StatusFilter) => void;
  counts: { aman: number; waspada: number; bahaya: number };
}

const FILTERS: { key: StatusFilter; label: string; activeClass: string }[] = [
  { key: 'SEMUA', label: 'Semua', activeClass: 'bg-primary text-white border-primary' },
  { key: 'AMAN', label: 'Aman', activeClass: 'bg-aman text-white border-aman' },
  { key: 'WASPADA', label: 'Waspada', activeClass: 'bg-waspada text-white border-waspada' },
  { key: 'BAHAYA', label: 'Bahaya', activeClass: 'bg-bahaya text-white border-bahaya' },
];

export function FilterStatusBar({ current, onChange, counts }: FilterStatusBarProps) {
  return (
    <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
      {FILTERS.map((f) => {
        const isActive = current === f.key;
        const count =
          f.key === 'SEMUA'
            ? counts.aman + counts.waspada + counts.bahaya
            : f.key === 'AMAN'
              ? counts.aman
              : f.key === 'WASPADA'
                ? counts.waspada
                : counts.bahaya;

        return (
          <button
            key={f.key}
            onClick={() => onChange(f.key)}
            className={cn(
              'flex items-center gap-1 sm:gap-1.5 rounded-full border px-2.5 sm:px-3 py-1 sm:py-1.5 text-[11px] sm:text-xs font-medium whitespace-nowrap transition-colors',
              isActive
                ? f.activeClass
                : 'bg-black/5 dark:bg-white/10 border-divider dark:border-white/15 text-text-secondary dark:text-white/60 hover:border-primary/40 hover:text-text-primary',
            )}
          >
            {f.label}
            <span className={cn('tabular-nums', isActive ? 'opacity-80' : 'opacity-50')}>
              {count}
            </span>
          </button>
        );
      })}
    </div>
  );
}
