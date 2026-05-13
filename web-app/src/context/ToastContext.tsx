import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';
import { cn } from '../utils/cn';

type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

interface ToastContextValue {
  toast: (type: ToastType, message: string) => void;
}

const ToastContext = createContext<ToastContextValue>({
  toast: () => {},
});

const ICONS = {
  success: CheckCircle,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
};

const STYLES: Record<ToastType, string> = {
  success: 'border-l-primary bg-primary-surface/60 dark:bg-primary-surface/20',
  error: 'border-l-bahaya bg-bahaya-bg/60 dark:bg-bahaya-bg/20',
  warning: 'border-l-waspada bg-waspada-bg/60 dark:bg-waspada-bg/20',
  info: 'border-l-blue-500 bg-blue-50/60 dark:bg-blue-950/20',
};

const ICON_COLORS: Record<ToastType, string> = {
  success: 'text-primary',
  error: 'text-bahaya',
  warning: 'text-waspada',
  info: 'text-blue-500',
};

let nextId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((type: ToastType, message: string) => {
    const id = nextId++;
    setToasts((prev) => [...prev.slice(-4), { id, type, message }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
  }, []);

  const removeToast = (id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <ToastContext.Provider value={{ toast: addToast }}>
      {children}
      {/* Toast container */}
      <div className="fixed bottom-4 right-4 z-[9999] flex flex-col-reverse gap-2 pointer-events-none">
        {toasts.map((t) => {
          const Icon = ICONS[t.type];
          return (
            <div
              key={t.id}
              className={cn(
                'pointer-events-auto flex items-center gap-2.5 rounded-xl border border-divider bg-card shadow-xl px-4 py-3 border-l-4 min-w-[280px] max-w-sm animate-slide-up',
                STYLES[t.type],
              )}
            >
              <Icon className={cn('h-4.5 w-4.5 shrink-0', ICON_COLORS[t.type])} />
              <p className="text-xs text-text-primary flex-1">{t.message}</p>
              <button onClick={() => removeToast(t.id)} className="text-text-secondary/40 hover:text-text-secondary shrink-0">
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}
