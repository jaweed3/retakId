import { cn } from '../utils/cn';

export type DateRange = '7d' | '30d' | '90d' | 'all';

interface DateRangeFilterProps {
  current: DateRange;
  onChange: (range: DateRange) => void;
}

const OPTIONS: { key: DateRange; label: string }[] = [
  { key: '7d', label: '7 hari' },
  { key: '30d', label: '30 hari' },
  { key: '90d', label: '90 hari' },
  { key: 'all', label: 'Semua' },
];

export function getDateRange(range: DateRange): { from: string; to: string } | null {
  if (range === 'all') return null;
  const now = new Date();
  const to = now.toISOString();
  const days = range === '7d' ? 7 : range === '30d' ? 30 : 90;
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000).toISOString();
  return { from, to };
}

export function DateRangeFilter({ current, onChange }: DateRangeFilterProps) {
  return (
    <div className="flex gap-1.5">
      {OPTIONS.map((opt) => (
        <button
          key={opt.key}
          onClick={() => onChange(opt.key)}
          className={cn(
            'rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
            current === opt.key
              ? 'bg-primary text-white shadow-sm'
              : 'bg-black/5 dark:bg-white/10 text-text-secondary/60 hover:text-text-primary hover:bg-black/10 dark:hover:bg-white/15',
          )}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}
