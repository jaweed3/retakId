import { AlertTriangle, RefreshCw } from 'lucide-react';

export function ErrorState({
  message = 'Terjadi kesalahan saat memuat data.',
  onRetry,
}: {
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-20">
      <AlertTriangle className="h-12 w-12 text-bahaya" />
      <p className="text-sm text-text-secondary text-center max-w-sm">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:opacity-90 transition-opacity">
          <RefreshCw className="h-4 w-4" /> Coba Lagi
        </button>
      )}
    </div>
  );
}
