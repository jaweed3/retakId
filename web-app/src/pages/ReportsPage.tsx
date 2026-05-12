import { useState } from 'react';
import { useLaporan } from '../hooks/useLaporan';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { LaporanCard } from '../components/LaporanCard';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';
import type { StatusFilter } from '../types/laporan';

const PAGE_SIZE = 12;

export function ReportsPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const { data, counts, totalCount, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: PAGE_SIZE,
    page,
  });

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="mb-6">
        <h2 className="text-lg font-bold text-text-primary mb-1">Daftar Laporan</h2>
        <p className="text-sm text-text-secondary">
          Semua laporan retakan tanah dari masyarakat.
        </p>
      </div>

      {/* Search + Filter */}
      <div className="space-y-3 mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-secondary/50" />
          <input
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            placeholder="Cari lokasi atau nama pelapor..."
            className="w-full rounded-xl border border-divider bg-card py-2.5 pl-10 pr-4 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30 transition-shadow"
          />
        </div>
        <FilterStatusBar current={statusFilter} onChange={setStatusFilter} counts={counts} />
      </div>

      {/* Content */}
      {isLoading && <LoadingSpinner />}
      {error && !isLoading && <ErrorState message={error} onRetry={refetch} />}

      {!isLoading && !error && data.length === 0 && (
        <EmptyState
          title={statusFilter === 'SEMUA' ? 'Belum ada laporan' : `Tidak ada laporan ${statusFilter.toLowerCase()}`}
          description={
            statusFilter === 'SEMUA'
              ? 'Laporan dari aplikasi mobile akan muncul di sini.'
              : `Belum ada laporan dengan status "${statusFilter}".`
          }
        />
      )}

      {!isLoading && !error && data.length > 0 && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {data
              .filter(
                (r) =>
                  !search ||
                  r.nama_lokasi.toLowerCase().includes(search.toLowerCase()) ||
                  r.pelapor.toLowerCase().includes(search.toLowerCase()),
              )
              .map((report) => (
                <LaporanCard key={report.id} report={report} />
              ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 mt-6">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-divider text-text-secondary hover:text-text-primary hover:bg-card disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              <span className="text-xs text-text-secondary tabular-nums">
                {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="flex h-8 w-8 items-center justify-center rounded-lg border border-divider text-text-secondary hover:text-text-primary hover:bg-card disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
