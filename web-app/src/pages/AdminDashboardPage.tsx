import { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, ShieldCheck, Trash2, Pencil, Search, ArrowLeft, ExternalLink, History, MoreVertical, Download, CheckCircle, XCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLaporan, type ResolvedFilter } from '../hooks/useLaporan';
import { useToast } from '../context/ToastContext';
import { supabase, requireSupabase } from '../lib/supabase';
import { StatusBadge } from '../components/StatusBadge';
import { ThemeToggle } from '../components/ThemeToggle';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EditReportDialog } from '../components/EditReportDialog';
import { VerificationDialog } from '../components/VerificationDialog';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { formatRelativeTime } from '../utils/formatDate';
import { cn } from '../utils/cn';
import { fetchTrainingData, downloadTrainingCSV, getVerificationStats } from '../utils/exportTrainingData';
import type { Laporan, ReportStatus, VerificationData } from '../types/laporan';

function isAuthError(err: { message?: string; code?: string }): boolean {
  if (!err) return false;
  const msg = (err.message || '').toLowerCase();
  return msg.includes('jwt') || msg.includes('auth') || msg.includes('unauthorized') || msg.includes('session') || err.code === 'PGRST301' || err.code === '401';
}

function ActionsMenu({ report, onVerify, onEdit, onDelete, onResolve, disabled, verified }: {
  report: Laporan; onVerify: (r: Laporan) => void; onEdit: (r: Laporan) => void;
  onDelete: (r: Laporan) => void; onResolve: (r: Laporan) => void; disabled: boolean; verified: boolean;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function close(e: MouseEvent) { if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpen(false); }
    if (open) document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, [open]);
  return (
    <div className="relative" ref={menuRef}>
      <button onClick={() => setOpen(!open)} disabled={disabled} className="p-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-divider/30 transition-colors">
        <MoreVertical className="h-4 w-4" />
      </button>
      {open && (
        <div className="absolute right-0 top-8 z-50 w-40 rounded-xl bg-card border border-divider shadow-xl py-1 animate-scale-in">
          {!verified && (
            <button onClick={() => { onVerify(report); setOpen(false); }} className="flex items-center gap-2.5 w-full px-3.5 py-2 text-xs text-text-secondary hover:text-primary hover:bg-primary-surface transition-colors">
              <ShieldCheck className="h-3.5 w-3.5" /> Verif. ML
            </button>
          )}
          <button onClick={() => { onResolve(report); setOpen(false); }} className="flex items-center gap-2.5 w-full px-3.5 py-2 text-xs text-text-secondary hover:text-primary hover:bg-primary-surface transition-colors">
            {report.is_resolved ? <XCircle className="h-3.5 w-3.5" /> : <CheckCircle className="h-3.5 w-3.5" />} {report.is_resolved ? 'Batal Teratasi' : 'Tandai Teratasi'}
          </button>
          <button onClick={() => { onEdit(report); setOpen(false); }} className="flex items-center gap-2.5 w-full px-3.5 py-2 text-xs text-text-secondary hover:text-waspada hover:bg-waspada-bg transition-colors">
            <Pencil className="h-3.5 w-3.5" /> Edit
          </button>
          <Link to={`/reports/${report.id}`} onClick={() => setOpen(false)} className="flex items-center gap-2.5 w-full px-3.5 py-2 text-xs text-text-secondary hover:text-text-primary hover:bg-divider/30 transition-colors">
            <ExternalLink className="h-3.5 w-3.5" /> Detail
          </Link>
          <div className="border-t border-divider my-1" />
          <button onClick={() => { onDelete(report); setOpen(false); }} className="flex items-center gap-2.5 w-full px-3.5 py-2 text-xs text-text-secondary hover:text-bahaya hover:bg-bahaya-bg transition-colors">
            <Trash2 className="h-3.5 w-3.5" /> Hapus
          </button>
        </div>
      )}
    </div>
  );
}

export function AdminDashboardPage() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [adminResolvedFilter, setAdminResolvedFilter] = useState<ResolvedFilter>('active');
  const { data, counts, isLoading, error, refetch } = useLaporan({ limit: 500, resolvedFilter: adminResolvedFilter });
  const [search, setSearch] = useState('');
  const [filterVerif, setFilterVerif] = useState<'all' | 'verified' | 'unverified'>('all');
  const [viewMode, setViewMode] = useState<'table' | 'card'>('table');
  const [deleteTarget, setDeleteTarget] = useState<Laporan | null>(null);
  const [editTarget, setEditTarget] = useState<Laporan | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [verifiedIds, setVerifiedIds] = useState<Set<string>>(new Set());
  const [verificationTarget, setVerificationTarget] = useState<Laporan | null>(null);
  const [verifStats, setVerifStats] = useState<{ total: number; benar: number; salah: number; akurasi: number } | null>(null);

  // Load existing verifications by this admin
  useEffect(() => {
    if (!supabase || !user?.email) return;
    const client = requireSupabase();
    (client as any).from('riwayat_penanganan')
      .select('laporan_id')
      .eq('ditangani_oleh', user.email)
      .eq('tindakan', 'diverifikasi')
      .then(({ data: rows }: { data: Array<{ laporan_id: string }> | null }) => {
        if (rows) setVerifiedIds(new Set(rows.map((r: { laporan_id: string }) => r.laporan_id)));
      })
      .catch(() => { /* tabel mungkin belum ada */ });
  }, [user?.email, data]);

  useEffect(() => {
    if (!supabase) return;
    const client = requireSupabase();
    getVerificationStats(client).then(setVerifStats).catch(() => {});
  }, [data]);

  const handleSignOut = async () => { await signOut(); navigate('/admin/login'); };

  const handleResolve = useCallback(async (report: Laporan) => {
    if (!supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    const newVal = !report.is_resolved;
    const { error } = await (client as any).from('laporan').update({ is_resolved: newVal }).eq('id', report.id);
    if (error) { toast('error', `Gagal: ${error.message}`); setActionLoading(false); return; }
    try { await (client as any).from('riwayat_penanganan').insert({ laporan_id: report.id, nama_lokasi: report.nama_lokasi, status: report.status, ditangani_oleh: user?.email || 'admin', tindakan: newVal ? 'ditanggulangi' : 'dipulihkan' }); } catch { /* */ }
    toast('success', newVal ? 'Laporan ditandai teratasi.' : 'Laporan dikembalikan ke aktif.');
    refetch(); setActionLoading(false);
  }, [refetch, toast, user, signOut, navigate]);

  const handleVerifyOpen = useCallback((report: Laporan) => {
    if (verifiedIds.has(report.id)) { toast('info', 'Laporan ini sudah diverifikasi.'); return; }
    setVerificationTarget(report);
  }, [verifiedIds, toast]);

  const handleVerificationSave = useCallback(async (verif: VerificationData) => {
    const report = verificationTarget;
    if (!supabase || !report || !user?.email) return;
    const client = requireSupabase();
    setActionLoading(true);
    const detail = {
      ml_status: report.status,
      label_verifikasi: verif.label_verifikasi,
      label_benar: verif.label_benar || null,
      catatan: verif.catatan,
    };
    const { error } = await (client as any).from('laporan').update({ terverifikasi: report.terverifikasi + 1 }).eq('id', report.id);
    if (error) { if (isAuthError(error)) { signOut(); navigate('/admin/login'); return; } toast('error', `Gagal: ${error.message}`); setActionLoading(false); return; }
    try {
      await (client as any).from('riwayat_penanganan').insert({
        laporan_id: report.id,
        nama_lokasi: report.nama_lokasi,
        status: report.status,
        ditangani_oleh: user.email,
        tindakan: 'diverifikasi',
        alasan: verif.label_verifikasi,
        detail,
      });
    } catch { /* best effort */ }
    setVerifiedIds((prev) => new Set(prev).add(report.id));
    setVerificationTarget(null);
    const statMsg = verif.label_verifikasi === 'BENAR' ? '✅ Hasil ML sesuai' : '✘ Koreksi disimpan untuk training data';
    toast('success', `"${report.nama_lokasi}" diverifikasi. ${statMsg}`);
    refetch(); setActionLoading(false);
  }, [verificationTarget, refetch, toast, user, signOut, navigate]);

  const handleExportTraining = useCallback(async () => {
    if (!supabase) { toast('error', 'Supabase tidak terhubung'); return; }
    toast('info', 'Menyiapkan data training...');
    const data = await fetchTrainingData();
    if (data.length === 0) { toast('error', 'Belum ada data verifikasi untuk diexport'); return; }
    downloadTrainingCSV(data);
    toast('success', `${data.length} record training di-download.`);
  }, [toast]);

  const handleDelete = useCallback(async () => {
    if (!deleteTarget || !supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    try { await (client as any).from('riwayat_penanganan').insert({ laporan_id: deleteTarget.id, nama_lokasi: deleteTarget.nama_lokasi, status: deleteTarget.status, ditangani_oleh: user?.email || 'admin', tindakan: 'dihapus', alasan: 'Laporan sudah ditanggulangi', data_sebelumnya: deleteTarget }); } catch { /* */ }
    const { error } = await (client as any).from('laporan').delete().eq('id', deleteTarget.id);
    setActionLoading(false); setDeleteTarget(null);
    if (error) { if (isAuthError(error)) { signOut(); navigate('/admin/login'); return; } toast('error', `Gagal: ${error.message}`); }
    else { toast('success', `Laporan dihapus.`); refetch(); }
  }, [deleteTarget, user, refetch, toast, signOut, navigate]);

  const handleEditSave = useCallback(async (id: string, updates: { nama_lokasi: string; status: ReportStatus; catatan: string }) => {
    if (!supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    const { error } = await (client as any).from('laporan').update(updates).eq('id', id);
    setActionLoading(false); setEditTarget(null);
    if (error) { if (isAuthError(error)) { signOut(); navigate('/admin/login'); return; } toast('error', `Gagal: ${error.message}`); }
    else { toast('success', 'Laporan diperbarui.'); try { await (client as any).from('riwayat_penanganan').insert({ laporan_id: id, nama_lokasi: updates.nama_lokasi, status: updates.status, ditangani_oleh: user?.email || 'admin', tindakan: 'diedit', detail: updates }); } catch { /* */ } refetch(); }
  }, [refetch, toast, user, signOut, navigate]);

  const filtered = data.filter((r) => {
    if (filterVerif === 'verified' && r.terverifikasi === 0) return false;
    if (filterVerif === 'unverified' && r.terverifikasi > 0) return false;
    if (search && !r.nama_lokasi.toLowerCase().includes(search.toLowerCase()) && !r.pelapor.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  useEffect(() => { if (error && isAuthError({ message: error })) { signOut(); navigate('/admin/login'); } }, [error, signOut, navigate]);

  const isVerified = (id: string) => verifiedIds.has(id);

  if (isLoading) return <LoadingSpinner text="Memuat data..." />;

  return (
    <div className="min-h-screen bg-surface">
      <header className="bg-card border-b border-divider px-3 sm:px-6 py-2.5 sm:py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3 sm:gap-4">
            <Link to="/" className="text-text-secondary hover:text-text-primary transition-colors"><ArrowLeft className="h-4.5 sm:h-5 w-4.5 sm:w-5" /></Link>
            <div>
              <h1 className="text-sm sm:text-base font-bold text-text-primary">Panel Admin</h1>
              <p className="text-[10px] text-text-secondary truncate max-w-[140px] sm:max-w-none">{user?.email}</p>
            </div>
          </div>
          <div className="flex items-center gap-1.5 sm:gap-2">
            <button onClick={handleExportTraining} className="flex items-center gap-1.5 rounded-lg border border-divider px-2.5 sm:px-3 py-1.5 text-[11px] sm:text-xs text-text-secondary hover:text-primary hover:border-primary/30 transition-colors" title="Export data training">
              <Download className="h-3 w-3 sm:h-3.5 sm:w-3.5" /><span className="hidden sm:inline">Export</span>
            </button>
            <Link to="/admin/riwayat" className="flex items-center gap-1.5 rounded-lg border border-divider px-2.5 sm:px-3 py-1.5 text-[11px] sm:text-xs text-text-secondary hover:text-primary hover:border-primary/30 transition-colors">
              <History className="h-3 w-3 sm:h-3.5 sm:w-3.5" /><span className="hidden sm:inline">Riwayat</span>
            </Link>
            <ThemeToggle />
            <button onClick={handleSignOut} className="flex items-center gap-1.5 rounded-lg border border-divider px-2.5 sm:px-3 py-1.5 text-[11px] sm:text-xs text-text-secondary hover:text-bahaya hover:border-bahaya/30 transition-colors">
              <LogOut className="h-3 w-3 sm:h-3.5 sm:w-3.5" /><span className="hidden sm:inline">Keluar</span>
            </button>
          </div>
        </div>
      </header>

      <div className="max-w-6xl mx-auto px-3 sm:px-6 py-4 sm:py-6">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 sm:gap-4 mb-3 sm:mb-4">
          <StatBadge label="Belum Verif" value={counts.total - data.filter((r) => r.terverifikasi > 0).length} color="text-waspada" />
          <StatBadge label="Terverifikasi" value={data.filter((r) => r.terverifikasi > 0).length} color="text-primary" />
          <StatBadge label="Total" value={counts.total} color="text-text-primary" />
          <StatBadge
            label="Akurasi ML"
            value={verifStats ? `${verifStats.akurasi}%` : '-'}
            color={verifStats ? (verifStats.akurasi >= 80 ? 'text-primary' : verifStats.akurasi >= 50 ? 'text-waspada' : 'text-bahaya') : 'text-text-secondary'}
            subtitle={verifStats ? `${verifStats.benar}/${verifStats.total} sesuai` : undefined}
          />
        </div>

        <div className="flex flex-col sm:flex-row gap-2.5 sm:gap-3 mb-4 sm:mb-5">
          <div className="relative flex-1">
            <Search className="absolute left-2.5 sm:left-3 top-1/2 -translate-y-1/2 h-3.5 sm:h-4 w-3.5 sm:w-4 text-text-secondary/50" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Cari lokasi atau pelapor..." className="w-full rounded-xl border border-divider bg-card py-2 sm:py-2.5 pl-8 sm:pl-10 pr-3 sm:pr-4 text-xs sm:text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="flex items-center gap-1.5 justify-between">
            <div className="flex gap-1.5">
              {[{ key: 'all' as const, label: 'Semua', short: 'Semua' }, { key: 'unverified' as const, label: 'Belum Verif', short: 'B.V' }, { key: 'verified' as const, label: 'Terverifikasi', short: 'T.V' }].map((opt) => (
                <button key={opt.key} onClick={() => setFilterVerif(opt.key)} className={cn('rounded-lg px-2 sm:px-3 py-1.5 text-[10px] sm:text-xs font-medium transition-colors', filterVerif === opt.key ? 'bg-primary text-white shadow-sm' : 'bg-black/5 dark:bg-white/10 text-text-secondary/60 hover:text-text-primary hover:bg-black/10 dark:hover:bg-white/15')}>
                  <span className="hidden sm:inline">{opt.label}</span><span className="sm:hidden">{opt.short}</span>
                </button>
              ))}
            </div>
            <div className="flex rounded-lg border border-divider bg-surface p-0.5">
              <button onClick={() => setViewMode('table')} className={cn('rounded-md px-2 py-1 text-[10px] sm:text-xs font-medium transition-colors', viewMode === 'table' ? 'bg-card text-text-primary shadow-sm' : 'text-text-secondary/40 hover:text-text-primary')}>Tabel</button>
              <button onClick={() => setViewMode('card')} className={cn('rounded-md px-2 py-1 text-[10px] sm:text-xs font-medium transition-colors', viewMode === 'card' ? 'bg-card text-text-primary shadow-sm' : 'text-text-secondary/40 hover:text-text-primary')}>Card</button>
            </div>
          </div>
        </div>

        {/* Resolved filter tabs */}
        <div className="flex gap-1.5 mb-4 sm:mb-5">
          {[{ key: 'active' as ResolvedFilter, label: 'Aktif' }, { key: 'resolved' as ResolvedFilter, label: 'Teratasi' }].map((opt) => (
            <button key={opt.key} onClick={() => setAdminResolvedFilter(opt.key)}
              className={cn('rounded-lg px-3 py-1.5 text-xs font-medium transition-colors',
                adminResolvedFilter === opt.key ? 'bg-primary text-white shadow-sm' : 'bg-black/5 dark:bg-white/10 text-text-secondary/60 hover:text-text-primary hover:bg-black/10 dark:hover:bg-white/15')}>
              {opt.label}
            </button>
          ))}
        </div>

        {error && <ErrorState message={error} onRetry={refetch} />}
        {!error && data.length === 0 && <EmptyState title="Belum ada laporan" description="Laporan dari aplikasi mobile akan muncul di sini." />}

        {!error && data.length > 0 && (
          viewMode === 'table' ? (
            <div className="rounded-xl border border-divider overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-xs sm:text-sm">
                  <thead><tr className="bg-black/10 dark:bg-white/10">
                    <th className="px-2 sm:px-3 py-2 sm:py-2.5 text-left text-[10px] sm:text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Status</th>
                    <th className="px-2 sm:px-3 py-2 sm:py-2.5 text-left text-[10px] sm:text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Lokasi</th>
                    <th className="px-2 sm:px-3 py-2 sm:py-2.5 text-left text-[10px] sm:text-[11px] font-semibold text-text-secondary uppercase tracking-wider hidden sm:table-cell">Pelapor</th>
                    <th className="px-2 sm:px-3 py-2 sm:py-2.5 text-center text-[10px] sm:text-[11px] font-semibold text-text-secondary uppercase tracking-wider w-12 sm:w-16">Verif</th>
                    <th className="px-2 sm:px-3 py-2 sm:py-2.5 text-right text-[10px] sm:text-[11px] font-semibold text-text-secondary uppercase tracking-wider">Aksi</th>
                  </tr></thead>
                  <tbody className="divide-y divide-divider/30">
                    {filtered.map((r) => (
                      <tr key={r.id} className="hover:bg-primary-surface/20 transition-colors cursor-pointer" onClick={() => navigate(`/reports/${r.id}`)}>
                        <td className="px-2 sm:px-3 py-2 sm:py-2.5"><StatusBadge status={r.status} className="text-[10px] sm:text-xs px-1.5 sm:px-2.5 py-0.5" /></td>
                        <td className="px-2 sm:px-3 py-2 sm:py-2.5 font-medium text-text-primary truncate max-w-[100px] sm:max-w-[200px] text-xs sm:text-sm">{r.nama_lokasi}</td>
                        <td className="px-2 sm:px-3 py-2 sm:py-2.5 text-text-secondary text-[11px] sm:text-xs hidden sm:table-cell">{r.pelapor}</td>
                        <td className="px-2 sm:px-3 py-2 sm:py-2.5 text-center" onClick={(e) => e.stopPropagation()}>
                          {r.terverifikasi > 0 ? <span className="inline-flex items-center gap-0.5 sm:gap-1 text-[11px] sm:text-xs font-medium text-primary"><ShieldCheck className="h-3 sm:h-3.5 w-3 sm:w-3.5" /> {r.terverifikasi}</span> : <span className="text-[11px] sm:text-xs text-text-secondary/40">0</span>}
                        </td>
                        <td className="px-2 sm:px-3 py-2 sm:py-2.5" onClick={(e) => e.stopPropagation()}>
                          <div className="hidden sm:flex items-center justify-end gap-1">
                            {r.terverifikasi === 0 && !isVerified(r.id) && <button onClick={() => handleVerifyOpen(r)} disabled={actionLoading} className="p-1.5 rounded-lg text-text-secondary/50 hover:text-primary hover:bg-primary-surface transition-colors" title="Verifikasi ML"><ShieldCheck className="h-4 w-4" /></button>}
                            <button onClick={() => handleResolve(r)} disabled={actionLoading} className="p-1.5 rounded-lg text-text-secondary/50 hover:text-primary hover:bg-primary-surface transition-colors" title={r.is_resolved ? 'Batal Teratasi' : 'Tandai Teratasi'}>{r.is_resolved ? <XCircle className="h-4 w-4" /> : <CheckCircle className="h-4 w-4" />}</button>
                            <button onClick={() => setEditTarget(r)} disabled={actionLoading} className="p-1.5 rounded-lg text-text-secondary/50 hover:text-waspada hover:bg-waspada-bg transition-colors" title="Edit"><Pencil className="h-4 w-4" /></button>
                            <Link to={`/reports/${r.id}`} className="p-1.5 rounded-lg text-text-secondary/50 hover:text-text-primary hover:bg-divider/30 transition-colors" title="Detail" onClick={(e) => e.stopPropagation()}><ExternalLink className="h-4 w-4" /></Link>
                            <button onClick={() => setDeleteTarget(r)} disabled={actionLoading} className="p-1.5 rounded-lg text-text-secondary/50 hover:text-bahaya hover:bg-bahaya-bg transition-colors" title="Hapus"><Trash2 className="h-4 w-4" /></button>
                          </div>
                          <div className="sm:hidden flex justify-end"><ActionsMenu report={r} onVerify={handleVerifyOpen} onEdit={setEditTarget} onDelete={setDeleteTarget} disabled={actionLoading} verified={r.terverifikasi > 0 || isVerified(r.id)} onResolve={handleResolve} /></div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {filtered.length === 0 && <p className="text-center text-sm text-text-secondary py-8">Tidak ada laporan dengan filter ini.</p>}
            </div>
          ) : (
            <div className="grid gap-2.5 sm:gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {filtered.map((r) => (
                <div key={r.id} className="rounded-xl bg-card border border-divider p-3.5 sm:p-4 hover:shadow-sm transition-all cursor-pointer" onClick={() => navigate(`/reports/${r.id}`)}>
                  <div className="flex items-start justify-between mb-2">
                    <StatusBadge status={r.status} className="text-[10px] sm:text-xs px-1.5 sm:px-2.5 py-0.5" />
                    <div onClick={(e) => e.stopPropagation()}><ActionsMenu report={r} onVerify={handleVerifyOpen} onEdit={setEditTarget} onDelete={setDeleteTarget} disabled={actionLoading} verified={r.terverifikasi > 0 || isVerified(r.id)} onResolve={handleResolve} /></div>
                  </div>
                  <h3 className="text-xs sm:text-sm font-semibold text-text-primary mb-1.5 truncate">{r.nama_lokasi}</h3>
                  <div className="space-y-1 text-[10px] sm:text-[11px] text-text-secondary">
                    <div className="flex items-center justify-between"><span>Pelapor: {r.pelapor}</span><span>{formatRelativeTime(r.created_at)}</span></div>
                    <div className="flex items-center gap-1"><ShieldCheck className="h-3 w-3" /><span>Verifikasi: {r.terverifikasi > 0 ? r.terverifikasi : 'belum'}</span></div>
                    {r.catatan && <p className="text-text-secondary/70 line-clamp-2 mt-1">{r.catatan}</p>}
                  </div>
                </div>
              ))}
              {filtered.length === 0 && <p className="text-center text-sm text-text-secondary py-8 col-span-full">Tidak ada laporan dengan filter ini.</p>}
            </div>
          )
        )}
      </div>

      <VerificationDialog open={!!verificationTarget} report={verificationTarget} onSave={handleVerificationSave} onCancel={() => setVerificationTarget(null)} loading={actionLoading} />
      <ConfirmDialog open={!!deleteTarget} title="Hapus Laporan?" message={`Laporan dari "${deleteTarget?.nama_lokasi}" akan dihapus permanen.`} confirmLabel="Hapus" variant="danger" onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} loading={actionLoading} />
      {editTarget && <EditReportDialog open={!!editTarget} report={editTarget} onSave={handleEditSave} onCancel={() => setEditTarget(null)} loading={actionLoading} />}
    </div>
  );
}

function StatBadge({ label, value, color, subtitle }: { label: string; value: number | string; color: string; subtitle?: string }) {
  return (
    <div className="rounded-xl bg-card border border-divider p-2.5 sm:p-4 text-center">
      <p className={cn('text-lg sm:text-2xl font-bold tabular-nums', color)}>{value}</p>
      <p className="text-[9px] sm:text-xs text-text-secondary mt-0.5">{label}</p>
      {subtitle && <p className="text-[8px] sm:text-[10px] text-text-secondary/50 mt-0.5">{subtitle}</p>}
    </div>
  );
}
