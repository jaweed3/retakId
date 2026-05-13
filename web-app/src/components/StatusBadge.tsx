import type { ReportStatus } from '../types/laporan';
import { cn } from '../utils/cn';

interface StatusBadgeProps {
  status: ReportStatus;
  className?: string;
}

const LABELS: Record<ReportStatus, string> = {
  AMAN: 'Aman',
  WASPADA: 'Waspada',
  BAHAYA: 'Bahaya',
};

const STYLES: Record<ReportStatus, string> = {
  AMAN: 'text-aman bg-aman-bg border-aman/30',
  WASPADA: 'text-waspada bg-waspada-bg border-waspada/30',
  BAHAYA: 'text-bahaya bg-bahaya-bg border-bahaya/30',
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold',
        STYLES[status],
        className,
      )}
    >
      {LABELS[status]}
    </span>
  );
}
