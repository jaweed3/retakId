import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Clock, CheckCircle, Pencil, Trash2 } from 'lucide-react';
import { supabase, requireSupabase } from '../lib/supabase';
import { useAuth } from '../context/AuthContext';
import { ProtectedRoute } from '../components/ProtectedRoute';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import { formatRelativeTime } from '../utils/formatDate';

interface RiwayatEntry {
  id: string;
  laporan_id: string | null;
  nama_lokasi: string;
  status: string;
  ditangani_oleh: string;
  tindakan: 'diverifikasi' | 'diedit' | 'dihapus';
  alasan: string | null;
  detail: Record<string, unknown> | null;
  created_at: string;
}

const TINDAKAN_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  diverifikasi: CheckCircle,
  diedit: Pencil,
  dihapus: Trash2,
};

const TINDAKAN_LABEL: Record<string, string> = {
  diverifikasi: 'Verifikasi',
  diedit: 'Edit',
  dihapus: 'Hapus',
};

const TINDAKAN_COLOR: Record<string, string> = {
  diverifikasi: 'text-primary bg-primary-surface',
  diedit: 'text-waspada bg-waspada-bg',
  dihapus: 'text-bahaya bg-bahaya-bg',
};

export function RiwayatPenangananPage() {
  return (
    <ProtectedRoute>
      <RiwayatContent />
    </ProtectedRoute>
  );
}

function RiwayatContent() {
  const { user } = useAuth();
  const [data, setData] = useState<RiwayatEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    if (!supabase) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const client = requireSupabase();
      const { data: rows, error: fetchError } = await (client as any)
        .from('riwayat_penanganan')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(100);

      if (fetchError) throw fetchError;
      setData(rows || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Gagal memuat riwayat.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  if (isLoading) return <LoadingSpinner text="Memuat riwayat..." />;
  if (error) return <ErrorState message={error} onRetry={fetchData} />;

  return (
    <div className="min-h-screen bg-surface">
      {/* Header */}
      <header className="bg-card border-b border-divider px-4 sm:px-6 py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link to="/admin" className="text-text-secondary hover:text-text-primary transition-colors">
              <ArrowLeft className="h-5 w-5" />
            </Link>
            <div>
              <h1 className="text-sm sm:text-base font-bold text-text-primary">Riwayat Penanganan</h1>
              <p className="text-[10px] sm:text-xs text-text-secondary">Audit trail semua aksi admin</p>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-6">
        {data.length === 0 ? (
          <EmptyState
            title="Belum ada riwayat"
            description="Riwayat penanganan akan muncul setelah admin melakukan aksi verifikasi, edit, atau hapus laporan."
          />
        ) : (
          <div className="space-y-3">
            {data.map((entry) => {
              const Icon = TINDAKAN_ICON[entry.tindakan] || Clock;
              return (
                <div
                  key={entry.id}
                  className="rounded-xl bg-card border border-divider/60 p-4 sm:p-5 flex items-start gap-4"
                >
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${TINDAKAN_COLOR[entry.tindakan]}`}>
                    <Icon className="h-4 w-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span className="text-sm font-semibold text-text-primary">
                        {entry.nama_lokasi}
                      </span>
                      <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${TINDAKAN_COLOR[entry.tindakan]}`}>
                        {TINDAKAN_LABEL[entry.tindakan]}
                      </span>
                    </div>
                    <p className="text-xs text-text-secondary">
                      Oleh: {entry.ditangani_oleh} &middot; {formatRelativeTime(entry.created_at)}
                    </p>
                    {entry.alasan && (
                      <p className="text-xs text-text-secondary/70 mt-1">
                        Alasan: {entry.alasan}
                      </p>
                    )}
                    {entry.detail && entry.tindakan === 'diedit' && (
                      <p className="text-[10px] text-text-secondary/50 mt-1">
                        Diubah: {JSON.stringify(entry.detail)}
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
