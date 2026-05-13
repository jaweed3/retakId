import { MapPin } from 'lucide-react';

interface EmptyStateProps {
  title?: string;
  description?: string;
}

export function EmptyState({
  title = 'Belum ada laporan',
  description = 'Laporan retakan tanah dari aplikasi akan muncul di sini.',
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16">
      <div className="relative">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary-surface">
          <MapPin className="h-8 w-8 text-primary/40" />
        </div>
        <div className="absolute -bottom-1 -right-1 flex h-6 w-6 items-center justify-center rounded-full bg-card border-2 border-divider">
          <div className="h-2 w-2 rounded-full bg-primary/30" />
        </div>
      </div>
      <p className="text-sm font-semibold text-text-primary">{title}</p>
      <p className="text-xs text-text-secondary text-center max-w-xs leading-relaxed">{description}</p>
    </div>
  );
}
