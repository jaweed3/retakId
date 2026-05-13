import { useState, useMemo } from 'react';
import type { StatusFilter } from '../types/laporan';
import { useLaporan } from '../hooks/useLaporan';
import { FilterStatusBar } from '../components/FilterStatusBar';
import { DateRangeFilter, getDateRange, type DateRange } from '../components/DateRangeFilter';
import { LaporanCard } from '../components/LaporanCard';
import { ReportTable } from '../components/ReportTable';
import { ViewToggle, type ViewMode } from '../components/ViewToggle';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { ChevronLeft, ChevronRight, Search, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { SEOMeta } from '../components/SEOMeta';

const PAGE_SIZE = 12;

export function ReportsPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('SEMUA');
  const [dateRange, setDateRange] = useState<DateRange>('all');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [viewMode, setViewMode] = useState<ViewMode>('card');

  const range = useMemo(() => getDateRange(dateRange), [dateRange]);

  const { data, counts, totalCount, isLoading, error, refetch } = useLaporan({
    status: statusFilter,
    limit: PAGE_SIZE,
    page,
    dateFrom: range?.from || null,
    dateTo: range?.to || null,
  });

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  const filtered = useMemo(
    () =>
      search
        ? data.filter(
            (r) =>
              r.nama_lokasi.toLowerCase().includes(search.toLowerCase()) ||
              r.pelapor.toLowerCase().includes(search.toLowerCase()),
          )
        : data,
    [data, search],
  );

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6">
      <SEOMeta title="Daftar Laporan" description="Semua laporan retakan tanah dari warga Jenangan, Ponorogo. Filter berdasarkan status AMAN, WASPADA, atau BAHAYA." />
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4 sm:mb-6">
        <div>
          <h2 className="text-base sm:text-lg font-bold text-text-primary">Daftar Laporan</h2>
          <p className="text-xs sm:text-sm text-text-secondary">
            Semua laporan retakan tanah dari masyarakat.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            to="/reports/new"
            className="inline-flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2 text-xs font-semibold text-white shadow-sm shadow-primary/20 hover:bg-primary-light transition-colors"
          >
            <Plus className="h-3.5 w-3.5" />
            Laporkan
          </Link>
          <DateRangeFilter current={dateRange} onChange={setDateRange} />
          <ViewToggle mode={viewMode} onChange={setViewMode} />
        </div>
      </div>

      {/* Search + Filter */}
      <div className="space-y-3 mb-4 sm:mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-secondary/50" />
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
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
          {viewMode === 'card' ? (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((report) => (
                <LaporanCard key={report.id} report={report} />
              ))}
            </div>
          ) : (
            <ReportTable data={filtered} />
          )}

          {filtered.length === 0 && search && (
            <p className="text-center text-sm text-text-secondary py-8">
              Tidak ada laporan yang cocok dengan &quot;{search}&quot;.
            </p>
          )}

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
