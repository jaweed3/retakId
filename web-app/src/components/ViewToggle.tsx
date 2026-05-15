import { LayoutGrid, List } from 'lucide-react';
import { cn } from '../utils/cn';

export type ViewMode = 'card' | 'table';

interface ViewToggleProps {
  mode: ViewMode;
  onChange: (mode: ViewMode) => void;
}

export function ViewToggle({ mode, onChange }: ViewToggleProps) {
  return (
    <div className="flex rounded-lg border border-divider bg-surface p-0.5">
      <button
        onClick={() => onChange('card')}
        className={cn(
          'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
          mode === 'card' ? 'bg-card text-text-primary shadow-sm' : 'text-text-secondary/40 hover:text-text-primary',
        )}
      >
        <LayoutGrid className="h-3.5 w-3.5" />
        Card
      </button>
      <button
        onClick={() => onChange('table')}
        className={cn(
          'flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
          mode === 'table' ? 'bg-card text-text-primary shadow-sm' : 'text-text-secondary/40 hover:text-text-primary',
        )}
      >
        <List className="h-3.5 w-3.5" />
        Tabel
      </button>
    </div>
  );
}
