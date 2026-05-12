import { FileX } from 'lucide-react';

export function EmptyState({
  title = 'Belum ada laporan',
  description = 'Laporan retakan tanah dari aplikasi akan muncul di sini.',
}: {
  title?: string;
  description?: string;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20">
      <FileX className="h-12 w-12 text-text-secondary/40" />
      <p className="text-sm font-medium text-text-secondary">{title}</p>
      <p className="text-xs text-text-secondary/60 text-center max-w-xs">{description}</p>
    </div>
  );
}
