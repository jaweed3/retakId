import { useState } from 'react';
import { X, Check, AlertTriangle, Camera, MapPin, User, Calendar } from 'lucide-react';
import { StatusBadge } from './StatusBadge';
import { LoadingSpinner } from './LoadingSpinner';
import { cn } from '../utils/cn';
import { formatRelativeTime } from '../utils/formatDate';
import type { Laporan, ReportStatus, VerifLabel, VerificationData } from '../types/laporan';

interface Props {
  open: boolean;
  report: Laporan | null;
  onSave: (data: VerificationData) => void;
  onCancel: () => void;
  loading?: boolean;
}

export function VerificationDialog({ open, report, onSave, onCancel, loading }: Props) {
  const [verifLabel, setVerifLabel] = useState<VerifLabel | null>(null);
  const [correctLabel, setCorrectLabel] = useState<ReportStatus | null>(null);
  const [note, setNote] = useState('');

  if (!open || !report) return null;

  const handleSubmit = () => {
    if (!verifLabel) return;
    if (verifLabel === 'SALAH' && !correctLabel) return;
    onSave({
      label_verifikasi: verifLabel,
      label_benar: verifLabel === 'SALAH' ? correctLabel! : undefined,
      catatan: note,
    });
  };

  const statusColor = (status: ReportStatus) => {
    switch (status) {
      case 'AMAN': return { bg: 'bg-aman-bg', border: 'border-aman/30', text: 'text-aman' };
      case 'WASPADA': return { bg: 'bg-waspada-bg', border: 'border-waspada/30', text: 'text-waspada' };
      case 'BAHAYA': return { bg: 'bg-bahaya-bg', border: 'border-bahaya/30', text: 'text-bahaya' };
    }
  };

  const mlStatusScore = (status: ReportStatus): number => {
    switch (status) {
      case 'AMAN': return 0.2;
      case 'WASPADA': return 0.5;
      case 'BAHAYA': return 0.85;
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-3 sm:p-4" onClick={onCancel}>
      <div className="w-full max-w-lg max-h-[85vh] overflow-y-auto rounded-2xl bg-card border border-divider shadow-2xl animate-scale-in" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-4 sm:px-5 py-3.5 border-b border-divider">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-full bg-primary-surface flex items-center justify-center">
              <Check className="h-3.5 w-3.5 text-primary" />
            </div>
            <h2 className="text-sm sm:text-base font-bold text-text-primary">Verifikasi Laporan</h2>
          </div>
          <button onClick={onCancel} className="p-1.5 rounded-lg text-text-secondary hover:text-text-primary hover:bg-divider/30 transition-colors">
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="px-4 sm:px-5 py-4 space-y-4">
          {report.foto_url && (
            <div className="rounded-xl overflow-hidden bg-surface border border-divider">
              <img src={report.foto_url} alt="Foto laporan" className="w-full h-48 object-cover" />
            </div>
          )}

          <div className="grid grid-cols-2 gap-2.5 text-[11px] sm:text-xs">
            <div className="flex items-center gap-1.5 text-text-secondary"><MapPin className="h-3.5 w-3.5 shrink-0" />{report.nama_lokasi}</div>
            <div className="flex items-center gap-1.5 text-text-secondary"><User className="h-3.5 w-3.5 shrink-0" />{report.pelapor}</div>
            <div className="flex items-center gap-1.5 text-text-secondary"><Calendar className="h-3.5 w-3.5 shrink-0" />{formatRelativeTime(report.created_at)}</div>
            <div className="flex items-center gap-1.5 text-text-secondary"><Camera className="h-3.5 w-3.5 shrink-0" />Koordinat: {report.latitude.toFixed(4)}, {report.longitude.toFixed(4)}</div>
          </div>

          <div className="rounded-xl bg-surface border border-divider p-3.5 sm:p-4">
            <h3 className="text-xs sm:text-sm font-bold text-text-primary mb-3">Hasil Analisis ML</h3>
            <div className="flex items-center justify-between mb-2.5">
              <span className="text-[11px] sm:text-xs text-text-secondary">Status prediksi:</span>
              <StatusBadge status={report.status} className="text-[10px] sm:text-xs px-2 py-0.5" />
            </div>
            <div className="space-y-1.5">
              <div className="flex justify-between text-[10px] sm:text-[11px] text-text-secondary">
                <span>Keyakinan AI</span>
                <span>{(mlStatusScore(report.status) * 100).toFixed(0)}%</span>
              </div>
              <div className="h-2 rounded-full bg-divider overflow-hidden">
                <div className={cn('h-full rounded-full transition-all', report.status === 'AMAN' ? 'bg-aman' : report.status === 'WASPADA' ? 'bg-waspada' : 'bg-bahaya')}
                  style={{ width: `${mlStatusScore(report.status) * 100}%` }}
                />
              </div>
            </div>
          </div>

          {report.catatan && (
            <div className="rounded-xl bg-surface border border-divider p-3.5 sm:p-4">
              <h3 className="text-[11px] sm:text-xs font-semibold text-text-secondary mb-1.5">Catatan Pelapor</h3>
              <p className="text-xs sm:text-sm text-text-primary">{report.catatan}</p>
            </div>
          )}

          <div className="rounded-xl border-2 border-divider p-3.5 sm:p-4">
            <h3 className="text-xs sm:text-sm font-bold text-text-primary mb-3">Apakah hasil analisis ini sesuai?</h3>
            <div className="grid grid-cols-2 gap-2.5">
              <button onClick={() => { setVerifLabel('BENAR'); setCorrectLabel(null); }}
                className={cn('flex items-center justify-center gap-2 rounded-xl border-2 px-4 py-3 text-xs sm:text-sm font-semibold transition-all',
                  verifLabel === 'BENAR' ? 'border-primary bg-primary-surface text-primary' : 'border-divider text-text-secondary hover:border-primary/30 hover:bg-primary-surface/50'
                )}>
                <Check className="h-4 w-4" /> Sesuai
              </button>
              <button onClick={() => setVerifLabel('SALAH')}
                className={cn('flex items-center justify-center gap-2 rounded-xl border-2 px-4 py-3 text-xs sm:text-sm font-semibold transition-all',
                  verifLabel === 'SALAH' ? 'border-bahaya bg-bahaya-bg text-bahaya' : 'border-divider text-text-secondary hover:border-bahaya/30 hover:bg-bahaya-bg/50'
                )}>
                <AlertTriangle className="h-4 w-4" /> Tidak Sesuai
              </button>
            </div>

            {verifLabel === 'SALAH' && (
              <div className="mt-3">
                <p className="text-[11px] sm:text-xs font-medium text-text-secondary mb-2">Pilih label yang benar:</p>
                <div className="flex gap-2">
                  {(['AMAN', 'WASPADA', 'BAHAYA'] as ReportStatus[]).map((label) => (
                    <button key={label} onClick={() => setCorrectLabel(label)}
                      className={cn('flex-1 rounded-lg border-2 px-3 py-2 text-xs sm:text-sm font-bold text-center transition-all',
                        correctLabel === label
                          ? label === 'AMAN' ? 'border-aman bg-aman-bg text-aman'
                            : label === 'WASPADA' ? 'border-waspada bg-waspada-bg text-waspada'
                            : 'border-bahaya bg-bahaya-bg text-bahaya'
                          : 'border-divider text-text-secondary/50 hover:border-text-secondary/30'
                      )}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div>
            <label className="text-[11px] sm:text-xs font-medium text-text-secondary block mb-1.5">Catatan Verifikasi <span className="text-text-secondary/40">(opsional)</span></label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={3}
              className="w-full rounded-xl border border-divider bg-surface px-3 py-2.5 text-xs sm:text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
              placeholder="Contoh: Retakan sesuai dengan foto, memang perlu diwaspadai..." />
          </div>
        </div>

        <div className="flex items-center justify-end gap-2.5 px-4 sm:px-5 py-3.5 border-t border-divider">
          <button onClick={onCancel} disabled={loading}
            className="px-4 py-2 rounded-xl text-xs sm:text-sm font-medium text-text-secondary hover:text-text-primary hover:bg-divider/30 transition-colors">
            Batal
          </button>
          <button onClick={handleSubmit} disabled={!verifLabel || (verifLabel === 'SALAH' && !correctLabel) || loading}
            className={cn('px-5 py-2 rounded-xl text-xs sm:text-sm font-bold text-white transition-all flex items-center gap-2',
              loading ? 'bg-primary/60 cursor-not-allowed' :
              verifLabel === 'BENAR' ? 'bg-primary hover:bg-primary/90' : 'bg-bahaya hover:bg-bahaya/90'
            )}>
            {loading ? <><LoadingSpinner text="" /> Memproses...</> :
              verifLabel === 'BENAR' ? '✔ Konfirmasi Sesuai' : '✘ Kirim Koreksi'}
          </button>
        </div>
      </div>
    </div>
  );
}
