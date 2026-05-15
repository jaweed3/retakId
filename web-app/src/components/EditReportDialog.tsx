import { useState } from 'react';
import { X } from 'lucide-react';
import type { Laporan, ReportStatus } from '../types/laporan';

interface EditReportDialogProps {
  open: boolean;
  report: Laporan;
  onSave: (id: string, updates: { nama_lokasi: string; status: ReportStatus; catatan: string }) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

const STATUS_OPTIONS: { value: ReportStatus; label: string; color: string }[] = [
  { value: 'AMAN', label: 'Aman', color: 'text-aman' },
  { value: 'WASPADA', label: 'Waspada', color: 'text-waspada' },
  { value: 'BAHAYA', label: 'Bahaya', color: 'text-bahaya' },
];

export function EditReportDialog({
  open, report, onSave, onCancel, loading = false,
}: EditReportDialogProps) {
  const [lokasi, setLokasi] = useState(report.nama_lokasi);
  const [status, setStatus] = useState<ReportStatus>(report.status);
  const [catatan, setCatatan] = useState(report.catatan);

  if (!open) return null;

  const handleSave = () => {
    onSave(report.id, { nama_lokasi: lokasi, status, catatan });
  };

  return (
    <div className="fixed inset-0 z-[9998] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onCancel} />
      <div className="relative rounded-2xl bg-card border border-divider shadow-2xl p-6 max-w-md w-full animate-scale-in">
        <button
          onClick={onCancel}
          className="absolute top-4 right-4 text-text-secondary/40 hover:text-text-secondary"
        >
          <X className="h-4 w-4" />
        </button>

        <h3 className="text-base font-bold text-text-primary mb-5">Edit Laporan</h3>

        <div className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">Nama Lokasi</label>
            <input
              type="text"
              value={lokasi}
              onChange={(e) => setLokasi(e.target.value)}
              className="w-full rounded-xl border border-divider bg-surface px-3.5 py-2.5 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">Status</label>
            <div className="flex gap-2">
              {STATUS_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setStatus(opt.value)}
                  className={`flex-1 rounded-lg border px-3 py-2 text-xs font-semibold transition-colors ${
                    status === opt.value
                      ? 'border-primary bg-primary-surface text-primary'
                      : 'border-divider text-text-secondary hover:border-divider'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">Catatan</label>
            <textarea
              value={catatan}
              onChange={(e) => setCatatan(e.target.value)}
              rows={3}
              className="w-full rounded-xl border border-divider bg-surface px-3.5 py-2.5 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
            />
          </div>
        </div>

        <div className="flex gap-3 justify-end mt-6">
          <button
            onClick={onCancel}
            disabled={loading}
            className="px-4 py-2 rounded-xl border border-divider text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-divider/20 transition-colors disabled:opacity-50"
          >
            Batal
          </button>
          <button
            onClick={handleSave}
            disabled={loading || !lokasi.trim()}
            className="px-4 py-2 rounded-xl bg-primary text-sm font-semibold text-white hover:bg-primary-light transition-colors disabled:opacity-50"
          >
            {loading ? 'Menyimpan...' : 'Simpan'}
          </button>
        </div>
      </div>
    </div>
  );
}
