import { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Send, AlertCircle, Cpu, Crosshair, Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { MapContainer, TileLayer, Marker } from 'react-leaflet';
import { supabase, requireSupabase } from '../lib/supabase';
import { ImageUploadPreview } from '../components/ImageUploadPreview';
import { LocationPicker } from '../components/LocationPicker';
import { useModelInference } from '../hooks/useModelInference';
import { fetchAllEnv } from '../lib/envApi';
import { analyze } from '../lib/riskEngine';
import type { RiskFactorReport, FactorContribution } from '../lib/risk';
import type { ReportStatus, PredictionLabel } from '../types/laporan';

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

const CONFIDENCE_THRESHOLD_LOW = 0.4;
const CONFIDENCE_THRESHOLD_MEDIUM = 0.6;

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
  const [predictionLabel, setPredictionLabel] = useState<PredictionLabel | null>(null);
  const [riskReport, setRiskReport] = useState<RiskFactorReport | null>(null);
  const [riskLoading, setRiskLoading] = useState(false);
  const [riskError, setRiskError] = useState<string | null>(null);
  const predictingRef = useRef(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const latitude = form.gpsLat ?? form.manualLat;
  const longitude = form.gpsLng ?? form.manualLng;

  const envDataRef = useRef<{
    slope: number | null; rain: number | null; elevation: number | null;
    soilRisk: number | null; soilName: string | null;
    lat: number; lng: number;
  } | null>(null);

  const runEnvAnalysis = useCallback(async (lat: number, lng: number) => {
    setRiskLoading(true);
    setRiskError(null);

    try {
      const env = await fetchAllEnv(lat, lng);

      envDataRef.current = {
        slope: env.slope, rain: env.weather?.rain ?? null,
        elevation: env.elevation, soilRisk: env.soil?.riskScore ?? null,
        soilName: env.soil?.name ?? null, lat, lng,
      };

      if (predictionLabel && predictionLabel !== 'TIDAK_PASTI' && predictionConfidence) {
        const report = analyze({
          mlLabel: predictionLabel,
          mlConfidence: predictionConfidence,
          slopeDeg: env.slope,
          rainMm: env.weather?.rain,
          elevationM: env.elevation,
          soilRiskScore: env.soil?.riskScore,
          soilName: env.soil?.name,
        });
        setRiskReport(report);
        setForm((prev) => ({ ...prev, status: report.finalResult.status }));
      } else {
        setRiskReport(null);
      }
    } catch {
      setRiskError('Gagal menganalisis faktor lingkungan');
    } finally {
      setRiskLoading(false);
    }
  }, [predictionLabel, predictionConfidence]);

  useEffect(() => {
    const stored = envDataRef.current;
    if (!stored) return;
    if (!predictionLabel || predictionLabel === 'TIDAK_PASTI' || !predictionConfidence) return;

    setRiskLoading(true);
    setRiskError(null);

    const report = analyze({
      mlLabel: predictionLabel,
      mlConfidence: predictionConfidence,
      slopeDeg: stored.slope,
      rainMm: stored.rain,
      elevationM: stored.elevation,
      soilRiskScore: stored.soilRisk,
      soilName: stored.soilName,
    });
    setRiskReport(report);
    setForm((prev) => ({ ...prev, status: report.finalResult.status }));
    setRiskLoading(false);
  }, [predictionLabel]);

  const handleImage = useCallback(async (file: File, preview: string, gps: { latitude: number; longitude: number } | null) => {
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

    setPredictionConfidence(null);
    setPredictionError(null);
    setPredictionLabel(null);
    setValidationError(null);
    setRiskReport(null);
    setRiskLoading(false);
    setRiskError(null);
    envDataRef.current = null;

    // Validate image quality
    try {
      const valid = await validateImageQuality(file);
      if (!valid.valid) {
        setValidationError(valid.message);
        setPredictionLabel('TIDAK_PASTI');
        return;
      }
    } catch {
      // skip validation errors
    }

    if (!isModelReady) return;

    predictingRef.current = true;
    predict(file)
      .then(async (result) => {
        if (!predictingRef.current) return;
        setPredictionLabel(result.status);
        setPredictionConfidence(result.confidence);
        setPredictionError(null);

        if (result.status === 'TIDAK_PASTI') {
          setValidationError(`Hasil tidak pasti (${(result.confidence * 100).toFixed(0)}% yakin) — ambil foto ulang lebih dekat`);
          return;
        }

        setForm((prev) => ({ ...prev, status: result.status as ReportStatus }));

        const photoLat = gps?.latitude ?? null;
        const photoLng = gps?.longitude ?? null;
        if (photoLat != null && photoLng != null) {
          runEnvAnalysis(photoLat, photoLng);
        }
      })
      .catch((err) => {
        if (!predictingRef.current) return;
        setPredictionError(err instanceof Error ? err.message : 'Gagal mendeteksi');
        setPredictionConfidence(null);
      })
      .finally(() => {
        predictingRef.current = false;
      });
  }, [isModelReady, predict, runEnvAnalysis]);

  async function validateImageQuality(file: File): Promise<{ valid: boolean; message: string }> {
    const img = new Image();
    const bitmap = await createImageBitmap(file);
    const canvas = document.createElement('canvas');
    canvas.width = 224;
    canvas.height = 224;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(bitmap, 0, 0, 224, 224);
    const imageData = ctx.getImageData(0, 0, 224, 224);
    const pixels = imageData.data;
    bitmap.close();

    // Brightness check
    let sumBrightness = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      const gray = 0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2];
      sumBrightness += gray;
    }
    const meanBrightness = sumBrightness / (pixels.length / 4);
    if (meanBrightness < 30) return { valid: false, message: 'Foto gelap — aktifkan flash' };
    if (meanBrightness > 220) return { valid: false, message: 'Foto terlalu terang — hindari cahaya langsung' };

    // Blur detection via Laplacian variance
    const gray = new Float32Array(224 * 224);
    for (let y = 0; y < 224; y++) {
      for (let x = 0; x < 224; x++) {
        const i = (y * 224 + x) * 4;
        gray[y * 224 + x] = 0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2];
      }
    }

    let lapSum = 0;
    let count = 0;
    for (let y = 1; y < 223; y++) {
      for (let x = 1; x < 223; x++) {
        const c = gray[y * 224 + x];
        const l = gray[y * 224 + (x - 1)];
        const r = gray[y * 224 + (x + 1)];
        const t = gray[(y - 1) * 224 + x];
        const b = gray[(y + 1) * 224 + x];
        const laplacian = 4 * c - l - r - t - b;
        lapSum += laplacian * laplacian;
        count++;
      }
    }
    const variance = lapSum / count;
    if (variance < 100) return { valid: false, message: 'Foto buram — dekatkan kamera ke retakan tanah' };

    return { valid: true, message: '' };
  }

  const [gpsLoading, setGpsLoading] = useState(false);
  const [gpsError, setGpsError] = useState<string | null>(null);

  const handleLocation = useCallback((lat: number, lng: number) => {
    setForm((prev) => ({ ...prev, manualLat: lat, manualLng: lng }));
    setErrors((prev) => { const { gps, ...r } = prev; return r; });
    runEnvAnalysis(lat, lng);
  }, [runEnvAnalysis]);

  const handleGPSClick = useCallback(() => {
    if (!navigator.geolocation) {
      setGpsError('Browser Anda tidak mendukung GPS.');
      return;
    }
    setGpsLoading(true);
    setGpsError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setForm((prev) => ({ ...prev, manualLat: pos.coords.latitude, manualLng: pos.coords.longitude }));
        setErrors((prev) => { const { gps, ...r } = prev; return r; });
        setGpsLoading(false);
        runEnvAnalysis(pos.coords.latitude, pos.coords.longitude);
      },
      (err) => {
        setGpsError(err.code === 1 ? 'Izin akses lokasi ditolak.' : 'Gagal mendapatkan lokasi.');
        setGpsLoading(false);
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  }, [runEnvAnalysis]);

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

        {/* GPS Device Button */}
        {latitude == null && (
          <div>
            <label className="block text-xs font-medium text-text-secondary mb-2">
              Lokasi <span className="text-bahaya">*</span>
            </label>
            <button
              type="button"
              onClick={handleGPSClick}
              disabled={gpsLoading}
              className="flex items-center gap-2 rounded-xl border border-divider bg-card px-4 py-3 text-sm font-medium text-text-secondary hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-50"
            >
              {gpsLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Crosshair className="h-4 w-4" />
              )}
              {gpsLoading ? 'Mendeteksi lokasi...' : 'Gunakan GPS Perangkat'}
            </button>
            {gpsError && <FieldError msg={gpsError} />}

            {/* Manual coordinate input */}
            <p className="text-[11px] text-text-secondary/60 mt-2">Atau masukkan koordinat manual:</p>
            <div className="grid grid-cols-2 gap-2 mt-1.5">
              <input
                type="number"
                step="any"
                placeholder="Latitude (contoh: -7.876)"
                onChange={(e) => {
                  const v = parseFloat(e.target.value);
                  if (!isNaN(v)) {
                    setForm((prev) => ({ ...prev, manualLat: v }));
                    const lng = form.manualLng ?? form.gpsLng;
                    if (lng != null) runEnvAnalysis(v, lng);
                  }
                }}
                className="rounded-xl border border-divider bg-card px-3 py-2 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <input
                type="number"
                step="any"
                placeholder="Longitude (contoh: 111.470)"
                onChange={(e) => {
                  const v = parseFloat(e.target.value);
                  if (!isNaN(v)) {
                    setForm((prev) => ({ ...prev, manualLng: v }));
                    const lat = form.manualLat ?? form.gpsLat;
                    if (lat != null) runEnvAnalysis(lat, v);
                  }
                }}
                className="rounded-xl border border-divider bg-card px-3 py-2 text-sm text-text-primary placeholder:text-text-secondary/40 focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>
          </div>
        )}

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
          <div>
            <label className="block text-xs font-medium text-text-secondary mb-1.5">
              Lokasi Terpilih <span className="text-bahaya">*</span>
            </label>
            <span className="text-[11px] text-primary font-medium">
              Koordinat: {latitude.toFixed(5)}, {longitude.toFixed(5)}
              {form.gpsLat ? ' (dari foto)' : ' (GPS/map)'}
            </span>
            <div className="rounded-xl overflow-hidden border border-divider h-[180px] mt-2">
              <MapContainer
                center={[latitude, longitude]}
                zoom={15}
                className="h-full w-full z-0"
                scrollWheelZoom={false}
                dragging={false}
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <Marker position={[latitude, longitude]} />
              </MapContainer>
            </div>
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
              {validationError && (
                <div className="flex items-start gap-2 rounded-xl bg-waspada-bg border border-waspada/20 px-3.5 py-2.5 text-xs text-waspada mb-2">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                  <span>{validationError}</span>
                </div>
              )}
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
              ) : predictionConfidence != null && predictionLabel !== 'TIDAK_PASTI' ? (
                <div className="space-y-2">
                  <div className={`flex items-center gap-2 rounded-xl border px-3.5 py-2.5 text-xs ${CONFIDENCE_COLORS[form.status]}`}>
                    <Cpu className="h-3.5 w-3.5 shrink-0" />
                    AI mendeteksi: <strong>{STATUS_OPTIONS.find(s => s.value === form.status)?.label}</strong>
                    {' '}({(predictionConfidence * 100).toFixed(0)}% yakin)
                    <span className="ml-auto text-[10px] opacity-60">Override manual di bawah</span>
                  </div>
                  {predictionConfidence < 0.5 && (
                    <div className="flex items-start gap-2 rounded-xl bg-bahaya-bg/70 border border-bahaya/20 px-3.5 py-2.5 text-xs text-bahaya">
                      <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                      <span>Gambar bukan retakan tanah? Pastikan memotret permukaan tanah</span>
                    </div>
                  )}
                  {predictionConfidence >= 0.5 && predictionConfidence < 0.7 && (
                    <div className="flex items-start gap-2 rounded-xl bg-waspada-bg/70 border border-waspada/20 px-3.5 py-2.5 text-xs text-waspada">
                      <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                      <span>Hasil tidak pasti — ambil foto ulang dengan pencahayaan lebih baik</span>
                    </div>
                  )}
                </div>
              ) : predictionConfidence != null && predictionLabel === 'TIDAK_PASTI' ? (
                <div className="rounded-xl border border-divider bg-card p-3.5">
                  <div className="flex items-center gap-2 text-xs text-waspada font-medium mb-3">
                    <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                    AI tidak yakin ({Math.round(predictionConfidence * 100)}%) — pilih manual atau foto ulang
                  </div>
                  <ConfidenceBar confidence={predictionConfidence} />
                </div>
              ) : !isModelReady && modelError ? (
                <div className="flex items-center gap-2 rounded-xl bg-waspada-bg border border-waspada/20 px-3.5 py-2.5 text-xs text-waspada">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  Model AI tidak tersedia. Pilih manual di bawah.
                </div>
              ) : null}
            </div>
          )}

          {riskLoading && (
            <div className="flex items-center gap-2 rounded-xl bg-primary-surface/60 border border-primary/10 px-3.5 py-2.5 text-xs text-primary mb-3">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Menganalisis faktor lingkungan (kemiringan, curah hujan, elevasi, jenis tanah)...
            </div>
          )}
          {riskError && (
            <div className="flex items-center gap-2 rounded-xl bg-waspada-bg border border-waspada/20 px-3.5 py-2.5 text-xs text-waspada mb-3">
              <AlertCircle className="h-3.5 w-3.5 shrink-0" />
              {riskError} — menggunakan hasil AI saja
            </div>
          )}
          {riskReport && !riskLoading && (
            <div className="rounded-xl border border-divider bg-card overflow-hidden mb-3">
              <div className="px-3.5 py-2.5 border-b border-divider">
                <span className="text-xs font-semibold text-text-primary">Analisis Multi-Faktor</span>
                <span className="text-[10px] text-text-secondary ml-2">
                  Skor akhir: {(riskReport.finalScore * 100).toFixed(0)}% — {riskReport.finalResult.status}
                  {riskReport.isUpgraded && ' ⬆️'}
                  {riskReport.isDowngraded && ' ⬇️'}
                </span>
              </div>
              <div className="divide-y divide-divider">
                {riskReport.factors.map((f: FactorContribution) => (
                  <div key={f.factor} className="flex items-center justify-between px-3.5 py-2">
                    <div className="flex items-center gap-2">
                      <div
                        className="h-1.5 rounded-full bg-primary/20"
                        style={{ width: `${f.weight * 100}px` }}
                      />
                      <span className="text-[11px] text-text-secondary capitalize">
                        {f.factor === 'ML' ? 'Analisis Visual' :
                         f.factor === 'SLOPE' ? 'Kemiringan' :
                         f.factor === 'RAIN' ? 'Curah Hujan' :
                         f.factor === 'ELEVATION' ? 'Elevasi' : 'Tanah'}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-[11px]">
                      <span className="text-text-secondary">{f.rawValue}</span>
                      <div className="flex items-center gap-1.5">
                        <div className="h-1.5 w-12 rounded-full bg-divider overflow-hidden">
                          <div
                            className="h-full rounded-full transition-all"
                            style={{
                              width: `${f.score * 100}%`,
                              backgroundColor:
                                f.score <= 0.2 ? '#22c55e' :
                                f.score <= 0.5 ? '#eab308' :
                                f.score <= 0.8 ? '#f97316' : '#ef4444',
                            }}
                          />
                        </div>
                        <span className="text-text-secondary w-6 text-right">
                          {(f.weightedScore * 100).toFixed(0)}%
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
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
                      : 'border-divider text-text-secondary hover:border-divider'
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

function ConfidenceBar({ confidence }: { confidence: number }) {
  const barColor =
    confidence < 0.5
      ? 'bg-bahaya'
      : confidence < 0.7
        ? 'bg-waspada'
        : 'bg-aman';
  const textColor =
    confidence < 0.5
      ? 'text-bahaya'
      : confidence < 0.7
        ? 'text-waspada'
        : 'text-aman';

  return (
    <div>
      <div className="h-3 rounded-full bg-divider overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${barColor}`}
          style={{ width: `${Math.round(confidence * 100)}%` }}
        />
      </div>
      <div className="flex justify-between mt-1">
        <span className="text-[10px] text-text-secondary">0%</span>
        <span className={`text-[10px] font-bold ${textColor}`}>
          {Math.round(confidence * 100)}%
        </span>
        <span className="text-[10px] text-text-secondary">100%</span>
      </div>
    </div>
  );
}
