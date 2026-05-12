import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, MapPin, User, Calendar, ShieldCheck, ExternalLink } from 'lucide-react';
import { supabase, requireSupabase } from '../lib/supabase';
import type { Laporan } from '../types/laporan';
import { StatusBadge } from '../components/StatusBadge';
import { MapView } from '../components/MapView';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { formatRelativeTime } from '../utils/formatDate';

export function ReportDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [report, setReport] = useState<Laporan | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [imgError, setImgError] = useState(false);

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
    <div className="max-w-3xl mx-auto px-4 py-6">
      {/* Back */}
      <Link
        to="/reports"
        className="inline-flex items-center gap-1 text-sm text-text-secondary hover:text-text-primary mb-4 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" />
        Kembali
      </Link>

      {/* Header */}
      <div className="flex items-start justify-between gap-3 mb-6">
        <div>
          <h1 className="text-lg font-bold text-text-primary flex items-center gap-2">
            <MapPin className="h-5 w-5 text-primary shrink-0" />
            {report.nama_lokasi}
          </h1>
          <p className="text-xs text-text-secondary mt-1">
            Dilaporkan {formatRelativeTime(report.created_at)}
          </p>
        </div>
        <StatusBadge status={report.status} className="text-sm px-3 py-1" />
      </div>

      {/* Photo */}
      {report.foto_url && !imgError && (
        <div className="rounded-xl overflow-hidden bg-divider/20 mb-6">
          <img
            src={report.foto_url}
            alt={`Foto retakan di ${report.nama_lokasi}`}
            onError={() => setImgError(true)}
            className="w-full max-h-96 object-cover"
          />
        </div>
      )}

      {report.foto_url && imgError && (
        <div className="flex flex-col items-center justify-center gap-2 rounded-xl bg-divider/20 py-12 mb-6">
          <p className="text-sm text-text-secondary">Foto tidak dapat dimuat</p>
        </div>
      )}

      {/* Info grid */}
      <div className="grid gap-4 sm:grid-cols-2 mb-6">
        <InfoItem icon={User} label="Pelapor" value={report.pelapor} />
        <InfoItem icon={Calendar} label="Waktu" value={formatRelativeTime(report.created_at)} />
        <InfoItem icon={MapPin} label="Koordinat" value={`${report.latitude.toFixed(5)}, ${report.longitude.toFixed(5)}`} />
        <InfoItem icon={ShieldCheck} label="Verifikasi" value={`${report.terverifikasi} konfirmasi`} />
      </div>

      {/* Catatan */}
      {report.catatan && (
        <div className="rounded-xl bg-card border border-divider/50 p-4 mb-6">
          <h3 className="text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2">
            Catatan
          </h3>
          <p className="text-sm text-text-primary whitespace-pre-wrap">{report.catatan}</p>
        </div>
      )}

      {/* Mini map */}
      <div className="rounded-xl overflow-hidden border border-divider/50 mb-6">
        <div className="flex items-center justify-between px-4 py-2.5 bg-card border-b border-divider/50">
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
    <div className="flex items-center gap-3 rounded-xl bg-card border border-divider/50 px-4 py-3">
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-surface">
        <Icon className="h-4 w-4 text-primary" />
      </div>
      <div className="min-w-0">
        <p className="text-[10px] font-semibold text-text-secondary uppercase tracking-wide">
          {label}
        </p>
        <p className="text-sm font-medium text-text-primary truncate">{value}</p>
      </div>
    </div>
  );
}
