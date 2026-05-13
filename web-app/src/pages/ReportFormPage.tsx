import { useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Send, AlertCircle, Cpu } from 'lucide-react';
import { Link } from 'react-router-dom';
import { supabase, requireSupabase } from '../lib/supabase';
import { ImageUploadPreview } from '../components/ImageUploadPreview';
import { LocationPicker } from '../components/LocationPicker';
import { useModelInference } from '../hooks/useModelInference';
import type { ReportStatus } from '../types/laporan';

interface FormState {
  file: File | null;
  preview: string | null;
  gpsLat: number | null;
  gpsLng: number | null;
  manualLat: number | null;
  manualLng: number | null;
  lokasi: string;
  status: ReportStatus;
  catatan: string;
  pelapor: string;
}

const STATUS_OPTIONS: { value: ReportStatus; label: string; desc: string; detail: string }[] = [
  { value: 'AMAN', label: 'Aman', desc: 'Retakan minor, penyusutan alami', detail: 'Tidak perlu tindakan khusus' },
  { value: 'WASPADA', label: 'Waspada', desc: 'Retakan signifikan, perlu dipantau', detail: 'Laporkan ke ketua RT/RW' },
  { value: 'BAHAYA', label: 'Bahaya', desc: 'Retakan kritis, segera evakuasi', detail: 'Hubungi BPBD segera' },
];

const CONFIDENCE_COLORS: Record<ReportStatus, string> = {
  AMAN: 'bg-aman-bg text-aman border-aman/30',
  WASPADA: 'bg-waspada-bg text-waspada border-waspada/30',
  BAHAYA: 'bg-bahaya-bg text-bahaya border-bahaya/30',
};

import { SEOMeta } from '../components/SEOMeta';

export function ReportFormPage() {
  const navigate = useNavigate();
  const { isModelReady, isPredicting, modelError, predict } = useModelInference();
  const [form, setForm] = useState<FormState>({
    file: null, preview: null, gpsLat: null, gpsLng: null,
    manualLat: null, manualLng: null, lokasi: '', status: 'AMAN', catatan: '', pelapor: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [predictionConfidence, setPredictionConfidence] = useState<number | null>(null);
  const [predictionError, setPredictionError] = useState<string | null>(null);
  const predictingRef = useRef(false);

  const latitude = form.gpsLat ?? form.manualLat;
  const longitude = form.gpsLng ?? form.manualLng;

  const handleImage = useCallback((file: File, preview: string, gps: { latitude: number; longitude: number } | null) => {
    setForm((prev) => ({
      ...prev,
      file,
      preview,
      gpsLat: gps?.latitude ?? null,
      gpsLng: gps?.longitude ?? null,
      manualLat: null,
      manualLng: null,
    }));
    if (gps) setErrors((prev) => { const { gps, ...r } = prev; return r; });

    // Auto-detect via model
    setPredictionConfidence(null);
    setPredictionError(null);
    if (!isModelReady) return;

    predictingRef.current = true;
    predict(file)
      .then((result) => {
        if (!predictingRef.current) return;
        setForm((prev) => ({ ...prev, status: result.status }));
        setPredictionConfidence(result.confidence);
        setPredictionError(null);
      })
      .catch((err) => {
        if (!predictingRef.current) return;
        setPredictionError(err instanceof Error ? err.message : 'Gagal mendeteksi');
        setPredictionConfidence(null);
      })
      .finally(() => {
        predictingRef.current = false;
      });
  }, [isModelReady, predict]);

  const handleLocation = useCallback((lat: number, lng: number) => {
    setForm((prev) => ({ ...prev, manualLat: lat, manualLng: lng }));
    setErrors((prev) => { const { gps, ...r } = prev; return r; });
  }, []);

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!form.file) errs.foto = 'Foto wajib diunggah.';
    if (!form.lokasi.trim() || form.lokasi.trim().length < 3) errs.lokasi = 'Nama lokasi wajib diisi (min 3 karakter).';
    if (latitude == null || longitude == null) errs.gps = 'Lokasi wajib diisi (dari foto GPS atau pilih manual).';
    if (!form.pelapor.trim() || form.pelapor.trim().length < 2) errs.pelapor = 'Nama pelapor wajib diisi.';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    if (!validate()) return;
    if (!supabase) { setSubmitError('Database belum dikonfigurasi.'); return; }

    setSubmitting(true);
    try {
      const client = requireSupabase();

      // Upload foto
      const fileExt = form.file!.name.split('.').pop();
      const fileName = `${Date.now()}-${Math.random().toString(36).slice(2)}.${fileExt}`;
      const { error: uploadError } = await client.storage
        .from('laporan-foto')
        .upload(fileName, form.file!);

      let fotoUrl: string | null = null;
      if (!uploadError) {
        const { data: urlData } = client.storage.from('laporan-foto').getPublicUrl(fileName);
        fotoUrl = urlData.publicUrl;
      }

      // Insert laporan
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const { error: insertError } = await (client as any).from('laporan').insert({
        nama_lokasi: form.lokasi.trim(),
        status: form.status,
        catatan: form.catatan.trim(),
        latitude,
        longitude,
        foto_url: fotoUrl,
        pelapor: form.pelapor.trim(),
        terverifikasi: 0,
      });

      if (insertError) throw insertError;

      navigate('/reports');
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Gagal mengirim laporan.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
      <SEOMeta title="Laporkan Retakan Tanah" description="Laporkan retakan tanah yang Anda temukan. Upload foto, lokasi diambil dari GPS foto (EXIF), dan kirim ke BPBD." />
      <Link to="/reports" className="inline-flex items-center gap-1.5 text-sm text-text-secondary hover:text-text-primary mb-4 sm:mb-6 transition-colors">
        <ArrowLeft className="h-4 w-4" /> Kembali
      </Link>

      <h1 className="text-xl sm:text-2xl font-bold text-text-primary mb-1">Laporkan Retakan Tanah</h1>
      <p className="text-sm text-text-secondary mb-6 sm:mb-8">
        Isi form di bawah untuk melaporkan retakan tanah yang Anda temukan.
      </p>

      <form onSubmit={handleSubmit} className="space-y-5 sm:space-y-6">
        {/* Foto */}
        <ImageUploadPreview onImageSelect={handleImage} />
        {errors.foto && <FieldError msg={errors.foto} />}

        {/* Lokasi (GPS warning or manual picker) */}
        {form.preview && latitude == null && (
          <div className="rounded-xl bg-waspada-bg/60 border border-waspada/20 px-4 py-3">
            <p className="text-xs text-waspada font-medium mb-3">
              Foto tidak memiliki data lokasi GPS. Silakan pilih lokasi manual di peta.
            </p>
            <LocationPicker onLocationSelect={handleLocation} />
          </div>
        )}
        {latitude != null && longitude != null && (
          <div className="flex items-center gap-2 rounded-xl bg-primary-surface/60 border border-primary/10 px-4 py-3">
            <span className="text-[11px] text-primary font-medium">
              Koordinat: {latitude.toFixed(5)}, {longitude.toFixed(5)}
              {form.gpsLat ? ' (dari foto)' : ' (manual)'}
            </span>
          </div>
        )}
        {errors.gps && <FieldError msg={errors.gps} />}

        {/* Nama Lokasi */}
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1.5">
            Nama Lokasi <span className="text-bahaya">*</span>
          </label>
          <input
            type="text"
            value={form.lokasi}
            onChange={(e) => setForm({ ...form, lokasi: e.target.value })}
            placeholder="Contoh: Desa Jenangan, dekat balai desa"
            className="w-full rounded-xl border border-divider bg-card px-3.5 py-2.5 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.lokasi && <FieldError msg={errors.lokasi} />}
        </div>

        {/* Status */}
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-2">
            Tingkat Bahaya <span className="text-bahaya">*</span>
          </label>

          {/* Prediction indicator */}
          {form.preview && (
            <div className="mb-3">
              {isPredicting ? (
                <div className="flex items-center gap-2 rounded-xl bg-primary-surface/60 border border-primary/10 px-3.5 py-2.5 text-xs text-primary">
                  <Cpu className="h-3.5 w-3.5 animate-pulse" />
                  Mendeteksi tingkat bahaya dari foto...
                </div>
              ) : predictionError ? (
                <div className="flex items-center gap-2 rounded-xl bg-bahaya-bg border border-bahaya/20 px-3.5 py-2.5 text-xs text-bahaya">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  Deteksi otomatis gagal. Pilih manual di bawah.
                </div>
              ) : predictionConfidence != null ? (
                <div className={`flex items-center gap-2 rounded-xl border px-3.5 py-2.5 text-xs ${CONFIDENCE_COLORS[form.status]}`}>
                  <Cpu className="h-3.5 w-3.5 shrink-0" />
                  AI mendeteksi: <strong>{STATUS_OPTIONS.find(s => s.value === form.status)?.label}</strong>
                  {' '}({(predictionConfidence * 100).toFixed(0)}% yakin)
                  <span className="ml-auto text-[10px] opacity-60">Override manual di bawah</span>
                </div>
              ) : !isModelReady && modelError ? (
                <div className="flex items-center gap-2 rounded-xl bg-waspada-bg border border-waspada/20 px-3.5 py-2.5 text-xs text-waspada">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  Model AI tidak tersedia. Pilih manual di bawah.
                </div>
              ) : null}
            </div>
          )}

          <div className="grid grid-cols-3 gap-2">
            {STATUS_OPTIONS.map((opt) => {
              const isSelected = form.status === opt.value;
              return (
                <button
                  key={opt.value}
                  type="button"
                  disabled={isPredicting}
                  onClick={() => {
                    setForm({ ...form, status: opt.value });
                    setPredictionConfidence(null);
                  }}
                  className={`rounded-xl border px-3 py-3 text-center transition-colors ${
                    isSelected
                      ? 'border-primary bg-primary-surface text-primary'
                      : 'border-divider text-text-secondary hover:border-divider/80'
                  } ${isPredicting ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <div className="text-sm font-bold">{opt.label}</div>
                  <div className="text-[10px] mt-0.5 opacity-70">{opt.desc}</div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Catatan */}
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1.5">Catatan (opsional)</label>
          <textarea
            value={form.catatan}
            onChange={(e) => setForm({ ...form, catatan: e.target.value })}
            rows={3}
            placeholder="Deskripsikan kondisi retakan, lebar, panjang, apakah ada air rembesan, dll."
            className="w-full rounded-xl border border-divider bg-card px-3.5 py-2.5 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
          />
        </div>

        {/* Nama Pelapor */}
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1.5">
            Nama Pelapor <span className="text-bahaya">*</span>
          </label>
          <input
            type="text"
            value={form.pelapor}
            onChange={(e) => setForm({ ...form, pelapor: e.target.value })}
            placeholder="Nama Anda"
            className="w-full rounded-xl border border-divider bg-card px-3.5 py-2.5 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.pelapor && <FieldError msg={errors.pelapor} />}
        </div>

        {/* Error */}
        {submitError && (
          <div className="flex items-center gap-2 rounded-xl bg-bahaya-bg border border-bahaya/20 px-4 py-3 text-xs text-bahaya">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {submitError}
          </div>
        )}

        {/* Submit */}
        <button
          type="submit"
          disabled={submitting}
          className="w-full flex items-center justify-center gap-2 rounded-xl bg-primary py-3 text-sm font-semibold text-white shadow-md shadow-primary/20 hover:bg-primary-light transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
        >
          <Send className="h-4 w-4" />
          {submitting ? 'Mengirim...' : 'Kirim Laporan'}
        </button>
      </form>
    </div>
  );
}

function FieldError({ msg }: { msg: string }) {
  return (
    <p className="text-xs text-bahaya mt-1.5 flex items-center gap-1">
      <AlertCircle className="h-3 w-3" /> {msg}
    </p>
  );
}
