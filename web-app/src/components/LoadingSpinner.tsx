import { Loader2 } from 'lucide-react';

export function LoadingSpinner({ text = 'Memuat data...' }: { text?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-20">
      <Loader2 className="h-8 w-8 animate-spin text-primary" />
      <p className="text-sm text-text-secondary">{text}</p>
    </div>
  );
}
