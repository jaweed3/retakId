import { Link } from 'react-router-dom';
import { MapPin } from 'lucide-react';
import type { Laporan } from '../types/laporan';
import { StatusBadge } from './StatusBadge';
import { formatRelativeTime } from '../utils/formatDate';

interface LaporanMapPopupProps {
  report: Laporan;
}

export function LaporanMapPopup({ report }: LaporanMapPopupProps) {
  return (
    <div className="min-w-[200px] max-w-[260px]">
      <div className="flex items-start justify-between gap-2 mb-1.5">
        <div className="flex items-center gap-1 min-w-0">
          <MapPin className="h-3.5 w-3.5 shrink-0 text-primary" />
          <span className="text-sm font-semibold text-text-primary truncate">
            {report.nama_lokasi}
          </span>
        </div>
        <StatusBadge status={report.status} />
      </div>
      {report.catatan && (
        <p className="text-xs text-text-secondary mb-2 line-clamp-2">{report.catatan}</p>
      )}
      <div className="flex items-center justify-between text-xs text-text-secondary/70">
        <span>{report.pelapor}</span>
        <span>{formatRelativeTime(report.created_at)}</span>
      </div>
      <Link
        to={`/reports/${report.id}`}
        className="mt-2 block text-center text-xs font-medium text-primary hover:underline"
      >
        Lihat detail →
      </Link>
    </div>
  );
}
