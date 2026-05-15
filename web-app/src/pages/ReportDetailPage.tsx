import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, MapPin, User, Calendar, ShieldCheck, ExternalLink, Trash2, Pencil } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { supabase, requireSupabase } from '../lib/supabase';
import type { Laporan, ReportStatus } from '../types/laporan';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { StatusBadge } from '../components/StatusBadge';
import { MapView } from '../components/MapView';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EditReportDialog } from '../components/EditReportDialog';
import { formatRelativeTime } from '../utils/formatDate';

export function ReportDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { isAdmin, user } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();
  const [report, setReport] = useState<Laporan | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [imgError, setImgError] = useState(false);
  const [showDelete, setShowDelete] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [verifiedIds, setVerifiedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!supabase || !user?.email || !report) return;
    (requireSupabase() as any).from('riwayat_penanganan')
      .select('laporan_id').eq('ditangani_oleh', user.email).eq('tindakan', 'diverifikasi')
      .then(({ data: rows }: { data: Array<{ laporan_id: string }> | null }) => {
        if (rows) setVerifiedIds(new Set(rows.map((r: { laporan_id: string }) => r.laporan_id)));
      }).catch(() => {});
  }, [user?.email, report?.id]);

  const handleVerify = async () => {
    if (!report || !supabase || verifiedIds.has(report.id)) return;
    const client = requireSupabase();
    setActionLoading(true);
    const { error: err } = await (client as any).from('laporan').update({ terverifikasi: report.terverifikasi + 1 }).eq('id', report.id);
    if (err) { toast('error', `Gagal: ${err.message}`); setActionLoading(false); return; }
    try { await (client as any).from('riwayat_penanganan').insert({ laporan_id: report.id, nama_lokasi: report.nama_lokasi, status: report.status, ditangani_oleh: user?.email || 'admin', tindakan: 'diverifikasi' }); } catch { /* */ }
    setVerifiedIds((prev) => new Set(prev).add(report.id));
    setReport({ ...report, terverifikasi: report.terverifikasi + 1 });
    toast('success', 'Laporan diverifikasi.');
    setActionLoading(false);
  };

  const handleDelete = async () => {
    if (!report || !supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    try { await (client as any).from('riwayat_penanganan').insert({ laporan_id: report.id, nama_lokasi: report.nama_lokasi, status: report.status, ditangani_oleh: user?.email || 'admin', tindakan: 'dihapus', alasan: 'Laporan sudah ditanggulangi' }); } catch { /* */ }
    const { error: err } = await (client as any).from('laporan').delete().eq('id', report.id);
    if (err) { toast('error', `Gagal: ${err.message}`); setActionLoading(false); return; }
    toast('success', 'Laporan dihapus.');
    navigate('/admin');
  };

  const handleEditSave = async (rid: string, updates: { nama_lokasi: string; status: ReportStatus; catatan: string }) => {
    if (!supabase) return;
    const client = requireSupabase();
    setActionLoading(true);
    const { error: err } = await (client as any).from('laporan').update(updates).eq('id', rid);
    if (err) { toast('error', `Gagal: ${err.message}`); setActionLoading(false); return; }
    setReport({ ...report!, ...updates });
    toast('success', 'Laporan diperbarui.');
    setShowEdit(false); setActionLoading(false);
  };

  useEffect(() => {
    if (!id) return;
    if (!supabase) {
      setError('Koneksi database belum dikonfigurasi.');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    const fetchReport = async () => {
      const client = requireSupabase();
      const { data, error: fetchError } = await client
        .from('laporan')
        .select('*')
        .eq('id', id)
        .single();

      if (fetchError) {
        setError(fetchError.message);
      } else if (!data) {
        setError('Laporan tidak ditemukan.');
      } else {
        const row = data as Record<string, unknown>;
        setReport({
          id: row.id as string,
          nama_lokasi: row.nama_lokasi as string,
          status: row.status as Laporan['status'],
          catatan: (row.catatan as string) || '',
          latitude: row.latitude as number,
          longitude: row.longitude as number,
          foto_url: (row.foto_url as string) || null,
          pelapor: (row.pelapor as string) || 'Anonim',
          terverifikasi: (row.terverifikasi as number) || 0,
          created_at: row.created_at as string,
        });
      }
      setIsLoading(false);
    };

    fetchReport().catch((err: Error) => {
      setError(err.message || 'Gagal memuat laporan.');
      setIsLoading(false);
    });
  }, [id]);

  const miniMapReport = useMemo(() => (report ? [report] : []), [report]);

  if (isLoading) return <LoadingSpinner text="Memuat laporan..." />;
  if (error) return <ErrorState message={error} />;
  if (!report) return <ErrorState message="Laporan tidak ditemukan." />;

  return (
    <div className="max-w-3xl mx-auto px-3 py-4 sm:px-4 sm:py-6">
      {/* Back + Admin actions */}
      <div className="flex items-center justify-between mb-3 sm:mb-4">
        <Link to={isAdmin ? "/admin" : "/reports"} className="inline-flex items-center gap-1 text-sm text-text-secondary hover:text-text-primary transition-colors">
          <ArrowLeft className="h-4 w-4" /> Kembali
        </Link>
        {isAdmin && report && (
          <div className="flex items-center gap-1.5">
            {!verifiedIds.has(report.id) && (
              <button onClick={handleVerify} disabled={actionLoading} className="flex items-center gap-1.5 rounded-xl bg-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-primary-light transition-colors disabled:opacity-50">
                <ShieldCheck className="h-3.5 w-3.5" /> Verifikasi
              </button>
            )}
            <button onClick={() => setShowEdit(true)} disabled={actionLoading} className="flex items-center gap-1.5 rounded-xl border border-divider bg-card px-3 py-1.5 text-xs font-medium text-text-secondary hover:text-waspada transition-colors disabled:opacity-50">
              <Pencil className="h-3.5 w-3.5" /> Edit
            </button>
            <button onClick={() => setShowDelete(true)} disabled={actionLoading} className="flex items-center gap-1.5 rounded-xl border border-divider bg-card px-3 py-1.5 text-xs font-medium text-text-secondary hover:text-bahaya transition-colors disabled:opacity-50">
              <Trash2 className="h-3.5 w-3.5" /> Hapus
            </button>
          </div>
        )}
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-2 sm:gap-3 mb-4 sm:mb-6">
        <div>
          <h1 className="text-base sm:text-lg font-bold text-text-primary flex items-center gap-2">
            <MapPin className="h-4 w-4 sm:h-5 sm:w-5 text-primary shrink-0" />
            {report.nama_lokasi}
          </h1>
          <p className="text-[11px] sm:text-xs text-text-secondary mt-1">
            Dilaporkan {formatRelativeTime(report.created_at)}
          </p>
        </div>
        <StatusBadge status={report.status} className="text-xs sm:text-sm px-2.5 py-0.5 sm:px-3 sm:py-1" />
      </div>

      {/* Photo */}
      {report.foto_url && !imgError && (
        <div className="rounded-xl overflow-hidden bg-divider/20 mb-4 sm:mb-6">
          <img
            src={report.foto_url}
            alt={`Foto retakan di ${report.nama_lokasi}`}
            onError={() => setImgError(true)}
            className="w-full max-h-96 object-cover"
          />
        </div>
      )}

      {report.foto_url && imgError && (
        <div className="flex flex-col items-center justify-center gap-2 rounded-xl bg-divider/20 py-10 sm:py-12 mb-4 sm:mb-6">
          <p className="text-sm text-text-secondary">Foto tidak dapat dimuat</p>
        </div>
      )}

      {/* Info grid */}
      <div className="grid gap-3 sm:gap-4 sm:grid-cols-2 mb-4 sm:mb-6">
        <InfoItem icon={User} label="Pelapor" value={report.pelapor} />
        <InfoItem icon={Calendar} label="Waktu" value={formatRelativeTime(report.created_at)} />
        <InfoItem icon={MapPin} label="Koordinat" value={`${report.latitude.toFixed(5)}, ${report.longitude.toFixed(5)}`} />
        <InfoItem icon={ShieldCheck} label="Verifikasi" value={`${report.terverifikasi} konfirmasi`} />
      </div>

      {/* Catatan */}
      {report.catatan && (
        <div className="rounded-xl bg-card border border-divider p-3 sm:p-4 mb-4 sm:mb-6">
          <h3 className="text-[11px] sm:text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">
            Catatan
          </h3>
          <p className="text-sm text-text-primary whitespace-pre-wrap">{report.catatan}</p>
        </div>
      )}

      {/* Mini map */}
      <div className="rounded-xl overflow-hidden border border-divider mb-4 sm:mb-6">
        <div className="flex items-center justify-between px-4 py-2.5 bg-card border-b border-divider">
          <h3 className="text-xs font-semibold text-text-secondary uppercase tracking-wide">
            Lokasi
          </h3>
          <a
            href={`https://www.openstreetmap.org/?mlat=${report.latitude}&mlon=${report.longitude}&zoom=17`}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
          >
            Buka di peta
            <ExternalLink className="h-3 w-3" />
          </a>
        </div>
        <MapView
          reports={miniMapReport}
          center={[report.latitude, report.longitude]}
          zoom={16}
          className="h-48"
        />
      </div>

      {/* Admin dialogs */}
      <ConfirmDialog open={showDelete} title="Hapus Laporan?" message={`Laporan dari "${report.nama_lokasi}" akan dihapus permanen.`} confirmLabel="Hapus" variant="danger" onConfirm={handleDelete} onCancel={() => setShowDelete(false)} loading={actionLoading} />
      {showEdit && <EditReportDialog open={showEdit} report={report} onSave={handleEditSave} onCancel={() => setShowEdit(false)} loading={actionLoading} />}
    </div>
  );
}

function InfoItem({
  icon: Icon,
  label,
  value,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-2.5 sm:gap-3 rounded-xl bg-card border border-divider px-3 py-2.5 sm:px-4 sm:py-3">
      <div className="flex h-8 w-8 sm:h-9 sm:w-9 shrink-0 items-center justify-center rounded-full bg-primary-surface">
        <Icon className="h-3.5 w-3.5 sm:h-4 sm:w-4 text-primary" />
      </div>
      <div className="min-w-0">
        <p className="text-[9px] sm:text-[10px] font-semibold text-text-secondary uppercase tracking-wide">
          {label}
        </p>
        <p className="text-xs sm:text-sm font-medium text-text-primary truncate">{value}</p>
      </div>
    </div>
  );
}
