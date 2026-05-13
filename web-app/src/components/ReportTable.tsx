import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpDown, ArrowUp, ArrowDown, ExternalLink } from 'lucide-react';
import type { Laporan } from '../types/laporan';
import { StatusBadge } from './StatusBadge';
import { formatRelativeTime } from '../utils/formatDate';

type SortField = 'created_at' | 'status' | 'nama_lokasi' | 'terverifikasi';
type SortDir = 'asc' | 'desc';

interface ReportTableProps {
  data: Laporan[];
}

export function ReportTable({ data }: ReportTableProps) {
  const [sortField, setSortField] = useState<SortField>('created_at');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((prev) => (prev === 'desc' ? 'asc' : 'desc'));
    } else {
      setSortField(field);
      setSortDir('desc');
    }
  };

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return <ArrowUpDown className="h-3 w-3 opacity-30" />;
    return sortDir === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />;
  };

  const thClass = (field: SortField) =>
    `px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider cursor-pointer hover:text-text-primary transition-colors select-none`;

  const sorted = [...data].sort((a, b) => {
    let cmp = 0;
    if (sortField === 'created_at') cmp = a.created_at.localeCompare(b.created_at);
    else if (sortField === 'status') cmp = a.status.localeCompare(b.status);
    else if (sortField === 'nama_lokasi') cmp = a.nama_lokasi.localeCompare(b.nama_lokasi);
    else cmp = a.terverifikasi - b.terverifikasi;
    return sortDir === 'asc' ? cmp : -cmp;
  });

  return (
    <div className="rounded-xl border border-divider/60 overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-divider/20">
              <th className={thClass('status')} onClick={() => handleSort('status')}>
                <div className="flex items-center gap-1">Status <SortIcon field="status" /></div>
              </th>
              <th className={thClass('nama_lokasi')} onClick={() => handleSort('nama_lokasi')}>
                <div className="flex items-center gap-1">Lokasi <SortIcon field="nama_lokasi" /></div>
              </th>
              <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider">
                Pelapor
              </th>
              <th className={thClass('created_at')} onClick={() => handleSort('created_at')}>
                <div className="flex items-center gap-1">Tanggal <SortIcon field="created_at" /></div>
              </th>
              <th className={thClass('terverifikasi')} onClick={() => handleSort('terverifikasi')}>
                <div className="flex items-center gap-1">Verif <SortIcon field="terverifikasi" /></div>
              </th>
              <th className="px-3 py-2.5 text-right text-[11px] font-semibold text-text-secondary uppercase tracking-wider">
                Detail
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-divider/30">
            {sorted.map((r) => (
              <tr key={r.id} className="hover:bg-primary-surface/30 transition-colors">
                <td className="px-3 py-2.5">
                  <StatusBadge status={r.status} />
                </td>
                <td className="px-3 py-2.5 font-medium text-text-primary truncate max-w-[180px]">
                  {r.nama_lokasi}
                </td>
                <td className="px-3 py-2.5 text-text-secondary text-xs">{r.pelapor}</td>
                <td className="px-3 py-2.5 text-text-secondary text-xs whitespace-nowrap">
                  {formatRelativeTime(r.created_at)}
                </td>
                <td className="px-3 py-2.5 text-text-secondary text-xs tabular-nums">
                  {r.terverifikasi > 0 ? (
                    <span className="text-primary font-medium">{r.terverifikasi}</span>
                  ) : (
                    <span className="text-text-secondary/50">0</span>
                  )}
                </td>
                <td className="px-3 py-2.5 text-right">
                  <Link
                    to={`/reports/${r.id}`}
                    className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                  >
                    Lihat <ExternalLink className="h-3 w-3" />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
