import { AlertTriangle, X } from 'lucide-react';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
  variant?: 'danger' | 'warning';
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Hapus',
  onConfirm,
  onCancel,
  loading = false,
  variant = 'danger',
}: ConfirmDialogProps) {
  if (!open) return null;

  const confirmStyles =
    variant === 'danger'
      ? 'bg-bahaya hover:bg-red-700 shadow-md shadow-bahaya/20'
      : 'bg-waspada hover:bg-orange-700 shadow-md shadow-waspada/20';

  return (
    <div className="fixed inset-0 z-[9998] flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancel} />

      {/* Dialog */}
      <div className="relative rounded-2xl bg-card border border-divider shadow-2xl p-6 max-w-sm w-full animate-scale-in">
        <button
          onClick={onCancel}
          className="absolute top-4 right-4 text-text-secondary/40 hover:text-text-secondary"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="flex items-center gap-3 mb-4">
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${variant === 'danger' ? 'bg-bahaya-bg' : 'bg-waspada-bg'}`}>
            <AlertTriangle className={`h-5 w-5 ${variant === 'danger' ? 'text-bahaya' : 'text-waspada'}`} />
          </div>
          <h3 className="text-base font-bold text-text-primary">{title}</h3>
        </div>

        <p className="text-sm text-text-secondary leading-relaxed mb-6">{message}</p>

        <div className="flex gap-3 justify-end">
          <button
            onClick={onCancel}
            disabled={loading}
            className="px-4 py-2 rounded-xl border border-divider text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-divider/20 transition-colors disabled:opacity-50"
          >
            Batal
          </button>
          <button
            onClick={onConfirm}
            disabled={loading}
            className={`px-4 py-2 rounded-xl text-sm font-semibold text-white transition-colors disabled:opacity-50 ${confirmStyles}`}
          >
            {loading ? 'Proses...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
