import type { ReportStatus } from '../types/laporan';

interface StatusColorSet {
  base: string;
  bg: string;
  label: string;
}

export const STATUS_COLORS: Record<ReportStatus, StatusColorSet> = {
  AMAN: { base: 'text-aman bg-aman-bg border-aman', bg: 'bg-aman', label: 'Aman' },
  WASPADA: { base: 'text-waspada bg-waspada-bg border-waspada', bg: 'bg-waspada', label: 'Waspada' },
  BAHAYA: { base: 'text-bahaya bg-bahaya-bg border-bahaya', bg: 'bg-bahaya', label: 'Bahaya' },
} as const;

export function getStatusColor(status: ReportStatus): StatusColorSet {
  return STATUS_COLORS[status];
}
