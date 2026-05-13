import { Camera, Cpu, Send } from 'lucide-react';

const STEPS = [
  {
    icon: Camera,
    title: 'Foto Retakan',
    description:
      'Warga memfoto retakan tanah menggunakan kamera HP lewat aplikasi Android.',
  },
  {
    icon: Cpu,
    title: 'AI Deteksi Tingkat Bahaya',
    description:
      'Model AI di HP mengklasifikasi retakan sebagai AMAN, WASPADA, atau BAHAYA — tanpa koneksi internet.',
  },
  {
    icon: Send,
    title: 'Laporan Terkirim ke BPBD',
    description:
      'Hasil deteksi, foto, dan lokasi GPS otomatis terkirim ke dashboard saat HP terhubung internet.',
  },
];

export function HowItWorks() {
  return (
    <section id="cara-kerja" className="bg-card border-y border-divider scroll-mt-20">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-16 sm:py-20">
        {/* Header */}
        <div className="text-center mb-10 sm:mb-12">
          <span className="text-xs font-semibold text-primary uppercase tracking-widest">
            Cara Kerja
          </span>
          <h2 className="text-xl sm:text-2xl lg:text-3xl font-bold text-text-primary mt-2">
            Tiga Langkah Deteksi Dini Longsor
          </h2>
          <p className="text-sm text-text-secondary mt-2 max-w-lg mx-auto">
            Dari kamera warga ke dashboard BPBD — semuanya otomatis dan akurat.
          </p>
        </div>

        {/* Steps */}
        <div className="grid sm:grid-cols-3 gap-6 lg:gap-8 relative">
          {/* Connector line (desktop) */}
          <div className="hidden sm:block absolute top-12 left-[calc(16.66%+28px)] right-[calc(16.66%+28px)] h-[2px] bg-gradient-to-r from-primary/20 via-primary/40 to-primary/20 z-0" />

          {STEPS.map((step, i) => (
            <div key={step.title} className="relative z-10 flex flex-col items-center text-center group">
              <div className="relative mb-4 sm:mb-5">
                <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-primary-surface ring-2 ring-primary/20 group-hover:ring-primary/40 group-hover:shadow-lg group-hover:shadow-primary/10 transition-all duration-300">
                  <step.icon className="h-6 w-6 sm:h-7 sm:w-7 text-primary" />
                </div>
                <div className="absolute -top-1.5 -right-1.5 flex h-5 w-5 sm:h-6 sm:w-6 items-center justify-center rounded-full bg-primary text-[10px] sm:text-[11px] font-bold text-white shadow-sm">
                  {i + 1}
                </div>
              </div>

              <h3 className="text-base sm:text-lg font-bold text-text-primary mb-1.5">
                {step.title}
              </h3>
              <p className="text-sm text-text-secondary leading-relaxed max-w-xs">
                {step.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
