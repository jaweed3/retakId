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
              'flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium whitespace-nowrap transition-colors',
              isActive
                ? f.activeClass
                : 'border-divider text-text-secondary hover:border-primary/40 hover:text-text-primary',
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
