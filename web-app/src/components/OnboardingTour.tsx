import { useState, useEffect, useCallback } from 'react';
import { X, ChevronLeft, ChevronRight, Camera, MapPin, BarChart3, Shield, Sparkles } from 'lucide-react';
import { cn } from '../utils/cn';

const STEPS = [
  {
    icon: Sparkles,
    title: 'Selamat Datang di Retak.id',
    description:
      'Platform deteksi dini tanah longsor berbasis komunitas. ' +
      'Laporkan retakan tanah di sekitar Anda dan dapatkan analisis risiko secara real-time ' +
      'berbasis kecerdasan buatan dan data lingkungan.',
  },
  {
    icon: Camera,
    title: 'Laporkan Retakan Tanah',
    description:
      'Ambil foto retakan tanah di sekitar Anda. ' +
      'Pastikan pencahayaan cukup, foto dari jarak ~1 meter, ' +
      'dan hindari bayangan atau silau. ' +
      'Sertakan lokasi untuk analisis yang lebih akurat.',
  },
  {
    icon: MapPin,
    title: 'Analisis Multi-Faktor',
    description:
      'Foto Anda dianalisis dengan model ML (MobileNetV2 INT8) ' +
      'dan digabungkan dengan 4 faktor lingkungan real-time: ' +
      'kemiringan lereng, curah hujan terkini, elevasi, dan jenis tanah. ' +
      'Hasilnya adalah skor risiko yang komprehensif.',
  },
  {
    icon: Shield,
    title: 'Hasil Risiko',
    description:
      '✅ AMAN — Tidak ada indikasi bahaya.\n' +
      '⚠️ WASPADA — Perlu diwaspadai, pantau secara berkala.\n' +
      '🔴 BAHAYA — Berpotensi longsor, segera laporkan ke BPBD.',
  },
  {
    icon: BarChart3,
    title: 'Pantau & Verifikasi',
    description:
      'Lihat semua laporan di dashboard interaktif, ' +
      'pantau sebaran retakan di peta, dan tim BPBD ' +
      'akan memverifikasi laporan untuk tindak lanjut. ' +
      'Bersama kita kurangi risiko bencana.',
  },
];

const STORAGE_KEY = 'retakid_onboarding_seen';

export function OnboardingTour() {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(0);

  useEffect(() => {
    const seen = localStorage.getItem(STORAGE_KEY);
    if (!seen) {
      setOpen(true);
    }
  }, []);

  const handleClose = useCallback(() => {
    localStorage.setItem(STORAGE_KEY, 'true');
    setOpen(false);
  }, []);

  const handleNext = useCallback(() => {
    if (step < STEPS.length - 1) {
      setStep((s) => s + 1);
    } else {
      handleClose();
    }
  }, [step, handleClose]);

  const handlePrev = useCallback(() => {
    if (step > 0) {
      setStep((s) => s - 1);
    }
  }, []);

  if (!open) return null;

  const current = STEPS[step];
  const Icon = current.icon;
  const isFirst = step === 0;
  const isLast = step === STEPS.length - 1;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={handleClose}
      />

      <div
        className="relative w-full max-w-md animate-scale-in rounded-2xl border border-divider bg-card p-6 shadow-2xl sm:p-8"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Tombol tutup */}
        <button
          onClick={handleClose}
          className="absolute right-3 top-3 rounded-lg p-1.5 text-text-secondary transition-colors hover:bg-primary-surface hover:text-primary"
        >
          <X className="h-5 w-5" />
        </button>

        {/* Ikon */}
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-primary-surface sm:h-20 sm:w-20">
          <Icon className="h-8 w-8 text-primary sm:h-10 sm:w-10" />
        </div>

        {/* Judul */}
        <h2 className="mb-2 text-center text-lg font-bold text-text-primary sm:text-xl">
          {current.title}
        </h2>

        {/* Deskripsi */}
        <p className="mb-6 text-center text-sm leading-relaxed text-text-secondary sm:text-base">
          {current.description}
        </p>

        {/* Dot indicators */}
        <div className="mb-6 flex items-center justify-center gap-2">
          {STEPS.map((_, i) => (
            <button
              key={i}
              onClick={() => setStep(i)}
              className={cn(
                'h-2 rounded-full transition-all duration-300',
                i === step
                  ? 'w-6 bg-primary'
                  : 'w-2 bg-divider hover:bg-primary/40',
              )}
            />
          ))}
        </div>

        {/* Navigasi */}
        <div className="flex items-center justify-between gap-3">
          <button
            onClick={isFirst ? handleClose : handlePrev}
            className={cn(
              'flex items-center gap-1.5 rounded-xl px-4 py-2.5 text-sm font-medium transition-all duration-200',
              isFirst
                ? 'border border-divider text-text-secondary hover:bg-surface'
                : 'border border-divider text-text-primary hover:bg-surface',
            )}
          >
            <ChevronLeft className="h-4 w-4" />
            {isFirst ? 'Lewati' : 'Kembali'}
          </button>

          <button
            onClick={handleNext}
            className="flex items-center gap-1.5 rounded-xl bg-primary px-5 py-2.5 text-sm font-medium text-white shadow-md shadow-primary/20 transition-all duration-200 hover:bg-primary-light active:scale-[0.97]"
          >
            {isLast ? 'Mulai' : 'Lanjut'}
            {!isLast && <ChevronRight className="h-4 w-4" />}
          </button>
        </div>
      </div>
    </div>
  );
}
