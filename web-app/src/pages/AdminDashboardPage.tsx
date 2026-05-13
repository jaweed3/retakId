import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, ShieldCheck, Trash2, Pencil, Search, ArrowLeft, ExternalLink } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLaporan } from '../hooks/useLaporan';
import { useToast } from '../context/ToastContext';
import { supabase, requireSupabase } from '../lib/supabase';
import { StatusBadge } from '../components/StatusBadge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EditReportDialog } from '../components/EditReportDialog';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { formatRelativeTime } from '../utils/formatDate';
import type { Laporan, ReportStatus } from '../types/laporan';

export function AdminDashboardPage() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { data, counts, isLoading, error, refetch } = useLaporan({ limit: 500 });
  const [search, setSearch] = useState('');
  const [filterVerif, setFilterVerif] = useState<'all' | 'verified' | 'unverified'>('all');

  // Dialog states
  const [deleteTarget, setDeleteTarget] = useState<Laporan | null>(null);
  const [editTarget, setEditTarget] = useState<Laporan | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const handleSignOut = async () => {
    await signOut();
    navigate('/admin/login');
  };

  const handleVerify = useCallback(async (report: Laporan) => {
    if (!supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const { error } = await (client as any)
      .from('laporan')
      .update({ terverifikasi: report.terverifikasi + 1 })
      .eq('id', report.id);
    setActionLoading(false);

    if (error) {
      toast('error', `Gagal verifikasi: ${error.message}`);
    } else {
      toast('success', `Laporan "${report.nama_lokasi}" berhasil diverifikasi.`);
      refetch();
    }
  }, [refetch, toast]);

  const handleDelete = useCallback(async () => {
    if (!deleteTarget || !supabase) return;
    const client = requireSupabase();
    setActionLoading(true);

    // Insert ke riwayat_penanganan (best effort)
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      await (client as any).from('riwayat_penanganan').insert({
        laporan_id: deleteTarget.id,
        nama_lokasi: deleteTarget.nama_lokasi,
        status: deleteTarget.status,
        ditangani_oleh: user?.email || 'admin',
        tindakan: 'dihapus',
        alasan: 'Laporan sudah ditanggulangi',
        data_sebelumnya: deleteTarget,
      });
    } catch { /* tabel mungkin belum ada */ }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const { error } = await (client as any).from('laporan').delete().eq('id', deleteTarget.id);
    setActionLoading(false);
    setDeleteTarget(null);

    if (error) {
      toast('error', `Gagal menghapus: ${error.message}`);
    } else {
      toast('success', `Laporan "${deleteTarget.nama_lokasi}" berhasil dihapus.`);
      refetch();
    }
  }, [deleteTarget, user, refetch, toast]);

  const handleEditSave = useCallback(async (id: string, updates: { nama_lokasi: string; status: ReportStatus; catatan: string }) => {
    if (!supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const { error } = await (client as any).from('laporan').update(updates).eq('id', id);
    setActionLoading(false);
    setEditTarget(null);

    if (error) {
      toast('error', `Gagal menyimpan: ${error.message}`);
    } else {
      toast('success', 'Laporan berhasil diperbarui.');
      refetch();
    }
  }, [refetch, toast]);

  const filtered = data.filter((r) => {
    if (filterVerif === 'verified' && r.terverifikasi === 0) return false;
    if (filterVerif === 'unverified' && r.terverifikasi > 0) return false;
    if (search && !r.nama_lokasi.toLowerCase().includes(search.toLowerCase()) && !r.pelapor.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  if (isLoading) return <LoadingSpinner text="Memuat data..." />;

  return (
    <div className="min-h-screen bg-surface">
      {/* Header */}
      <header className="bg-card border-b border-divider px-4 sm:px-6 py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link to="/" className="text-text-secondary hover:text-text-primary transition-colors">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-sm sm:text-base font-bold text-text-primary">Panel Admin</h1>
              <p className="text-[10px] sm:text-xs text-text-secondary">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={handleSignOut}
            className="flex items-center gap-2 rounded-lg border border-divider px-3 py-1.5 text-xs text-text-secondary hover:text-bahaya hover:border-bahaya/30 transition-colors"
          >
            <LogOut className="h-3.5 w-3.5" />
            Keluar
          </button>
        </div>
      </header>

      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6">
        {/* Stats */}
        <div className="grid grid-cols-3 gap-4 mb-6">
          <StatBadge label="Belum Diverifikasi" value={counts.total - data.filter((r) => r.terverifikasi > 0).length} color="text-waspada" />
          <StatBadge label="Terverifikasi" value={data.filter((r) => r.terverifikasi > 0).length} color="text-primary" />
          <StatBadge label="Total" value={counts.total} color="text-text-primary" />
        </div>

        {/* Toolbar */}
        <div className="flex flex-col sm:flex-row gap-3 mb-5">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-text-secondary/50" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Cari lokasi atau pelapor..."
              className="w-full rounded-xl border border-divider bg-card py-2.5 pl-10 pr-4 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div className="flex gap-1.5">
            {([
              { key: 'all', label: 'Semua' },
              { key: 'unverified', label: 'Belum Verif' },
              { key: 'verified', label: 'Terverifikasi' },
            ] as const).map((opt) => (
              <button
                key={opt.key}
                onClick={() => setFilterVerif(opt.key)}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                  filterVerif === opt.key
                    ? 'bg-primary text-white shadow-sm'
                    : 'text-text-secondary hover:text-text-primary hover:bg-divider/30'
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* Error */}
        {error && <ErrorState message={error} onRetry={refetch} />}

        {/* Empty */}
        {!error && data.length === 0 && <EmptyState title="Belum ada laporan" description="Laporan dari aplikasi mobile akan muncul di sini." />}

        {/* Table */}
        {!error && data.length > 0 && (
          <div className="rounded-xl border border-divider/60 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-divider/20">
                    <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Status</th>
                    <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Lokasi</th>
                    <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider hidden sm:table-cell">Pelapor</th>
                    <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-secondary uppercase tracking-wider hidden sm:table-cell">Tanggal</th>
                    <th className="px-3 py-2.5 text-center text-[11px] font-semibold text-text-secondary uppercase tracking-wider w-16">Verif</th>
                    <th className="px-3 py-2.5 text-right text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Aksi</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-divider/30">
                  {filtered.map((r) => (
                    <tr key={r.id} className="hover:bg-primary-surface/20 transition-colors">
                      <td className="px-3 py-2.5">
                        <StatusBadge status={r.status} />
                      </td>
                      <td className="px-3 py-2.5 font-medium text-text-primary truncate max-w-[150px] sm:max-w-[200px]">
                        {r.nama_lokasi}
                      </td>
                      <td className="px-3 py-2.5 text-text-secondary text-xs hidden sm:table-cell">{r.pelapor}</td>
                      <td className="px-3 py-2.5 text-text-secondary text-xs whitespace-nowrap hidden sm:table-cell">
                        {formatRelativeTime(r.created_at)}
                      </td>
                      <td className="px-3 py-2.5 text-center">
                        {r.terverifikasi > 0 ? (
                          <span className="inline-flex items-center gap-1 text-xs font-medium text-primary">
                            <ShieldCheck className="h-3.5 w-3.5" /> {r.terverifikasi}
                          </span>
                        ) : (
                          <span className="text-xs text-text-secondary/40">0</span>
                        )}
                      </td>
                      <td className="px-3 py-2.5">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => handleVerify(r)}
                            disabled={actionLoading}
                            className="p-1.5 rounded-lg text-text-secondary/50 hover:text-primary hover:bg-primary-surface transition-colors"
                            title="Verifikasi"
                          >
                            <ShieldCheck className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() => setEditTarget(r)}
                            disabled={actionLoading}
                            className="p-1.5 rounded-lg text-text-secondary/50 hover:text-waspada hover:bg-waspada-bg transition-colors"
                            title="Edit"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <Link
                            to={`/reports/${r.id}`}
                            className="p-1.5 rounded-lg text-text-secondary/50 hover:text-text-primary hover:bg-divider/30 transition-colors"
                            title="Lihat detail"
                          >
                            <ExternalLink className="h-4 w-4" />
                          </Link>
                          <button
                            onClick={() => setDeleteTarget(r)}
                            disabled={actionLoading}
                            className="p-1.5 rounded-lg text-text-secondary/50 hover:text-bahaya hover:bg-bahaya-bg transition-colors"
                            title="Hapus"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {filtered.length === 0 && (
              <p className="text-center text-sm text-text-secondary py-8">Tidak ada laporan dengan filter ini.</p>
            )}
          </div>
        )}
      </div>

      {/* Dialogs */}
      <ConfirmDialog
        open={!!deleteTarget}
        title="Hapus Laporan?"
        message={`Laporan dari "${deleteTarget?.nama_lokasi}" akan dihapus permanen. Tindakan ini tidak bisa dibatalkan.`}
        confirmLabel="Hapus"
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
        loading={actionLoading}
      />

      {editTarget && (
        <EditReportDialog
          open={!!editTarget}
          report={editTarget}
          onSave={handleEditSave}
          onCancel={() => setEditTarget(null)}
          loading={actionLoading}
        />
      )}
    </div>
  );
}

function StatBadge({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="rounded-xl bg-card border border-divider/60 p-3 sm:p-4 text-center">
      <p className={`text-xl sm:text-2xl font-bold tabular-nums ${color}`}>{value}</p>
      <p className="text-[10px] sm:text-xs text-text-secondary mt-0.5">{label}</p>
    </div>
  );
}
