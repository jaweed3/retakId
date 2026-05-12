import { Link } from 'react-router-dom';
import { MapPin, User, ShieldCheck } from 'lucide-react';
import type { Laporan } from '../types/laporan';
import { StatusBadge } from './StatusBadge';
import { formatRelativeTime } from '../utils/formatDate';
import { cn } from '../utils/cn';

const BORDER_COLORS: Record<string, string> = { AMAN: 'border-l-aman', WASPADA: 'border-l-waspada', BAHAYA: 'border-l-bahaya' };

export function LaporanCard({ report }: { report: Laporan }) {
  return (
    <Link to={`/reports/${report.id}`} className={cn('block rounded-xl bg-card border border-divider/50 border-l-4 shadow-sm hover:shadow-md transition-all', BORDER_COLORS[report.status])}>
      <div className="p-4">
        <div className="flex items-start justify-between gap-2 mb-2">
          <div className="flex items-center gap-1.5 min-w-0">
            <MapPin className="h-4 w-4 shrink-0 text-primary" />
            <h3 className="text-sm font-semibold text-text-primary truncate">{report.nama_lokasi}</h3>
          </div>
          <StatusBadge status={report.status} />
        </div>
        {report.catatan && <p className="text-xs text-text-secondary mb-3 line-clamp-2">{report.catatan}</p>}
        <div className="flex items-center justify-between text-xs text-text-secondary/70">
          <div className="flex items-center gap-1.5"><User className="h-3 w-3" /><span>{report.pelapor}</span></div>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1"><ShieldCheck className="h-3 w-3" /><span className="tabular-nums">{report.terverifikasi}</span></div>
            <span>{formatRelativeTime(report.created_at)}</span>
          </div>
        </div>
      </div>
    </Link>
  );
}
