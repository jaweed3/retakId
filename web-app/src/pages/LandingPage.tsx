import { Link } from 'react-router-dom';
import { Smartphone, QrCode, BookOpen, ArrowRight } from 'lucide-react';
import { HeroSection } from '../components/HeroSection';
import { HowItWorks } from '../components/HowItWorks';
import { WhySection } from '../components/WhySection';
import { MiniMapPreview } from '../components/MiniMapPreview';
import { SectionDivider } from '../components/SectionDivider';
import { SEOMeta } from '../components/SEOMeta';

export function LandingPage() {
  return (
    <div>
      <SEOMeta title="Pantau Retakan Tanah, Cegah Longsor Bersama" />
      {/* ═══ Section 1: Hero ═══ */}
      <HeroSection />

      {/* Divider: Hero → Cara Kerja */}
      <SectionDivider variant="wave" />

      {/* ═══ Section 2: Cara Kerja ═══ */}
      <HowItWorks />

      {/* Divider: Cara Kerja → Kenapa */}
      <SectionDivider variant="fade" />

      {/* ═══ Section 3: Kenapa + Edukasi ═══ */}
      <WhySection />

      {/* Edukasi Banner — langsung di bawah Misi Kami, sebelum ombak */}
      <section className="bg-surface">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 pt-0 pb-8 sm:pb-10">
          <div className="flex flex-col sm:flex-row items-center gap-5 sm:gap-6 rounded-2xl bg-card border border-divider/60 p-5 sm:p-6">
            <div className="flex h-12 w-12 sm:h-14 sm:w-14 shrink-0 items-center justify-center rounded-xl bg-primary-surface ring-2 ring-primary/20">
              <BookOpen className="h-6 w-6 sm:h-7 sm:w-7 text-primary" />
            </div>
            <div className="flex-1 text-center sm:text-left">
              <h3 className="text-base sm:text-lg font-bold text-text-primary">
                Kenali Retakan Tanah
              </h3>
              <p className="text-sm text-text-secondary mt-1">
                Pahami tingkat bahaya, cara melapor, dan kontak darurat BPBD.
              </p>
            </div>
            <Link
              to="/edukasi"
              className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary/20 hover:bg-primary-light hover:shadow-lg transition-all shrink-0"
            >
              Buka Edukasi
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* Divider: Kenapa+Edukasi → Peta */}
      <SectionDivider variant="wave" className="h-20 sm:h-28" />

      {/* ═══ Section 5: Peta ═══ */}
      <MiniMapPreview />

      {/* Divider: Peta → Download (surface → card) */}
      <SectionDivider variant="fade" className="from-surface to-card" />

      {/* ═══ Section 6: Download CTA ═══ */}
      <section id="download" className="bg-card scroll-mt-20">
        <div className="max-w-3xl mx-auto px-6 sm:px-8 lg:px-10 py-12 sm:py-16">
          <div className="flex flex-col sm:flex-row items-center gap-6 sm:gap-10">
            {/* QR placeholder */}
            <div className="flex h-32 w-32 sm:h-40 sm:w-40 shrink-0 items-center justify-center rounded-2xl bg-surface border-2 border-dashed border-divider">
              <div className="flex flex-col items-center gap-1 text-text-secondary/40">
                <QrCode className="h-14 w-14 sm:h-16 sm:w-16" />
                <span className="text-[9px]">QR Code</span>
              </div>
            </div>

            {/* Text */}
            <div className="text-center sm:text-left">
              <span className="text-xs font-semibold text-primary uppercase tracking-widest">
                Aplikasi Android
              </span>
              <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-1.5">
                Download Retak.id
              </h2>
              <p className="text-sm text-text-secondary mt-2 mb-4 leading-relaxed max-w-md">
                Retak.id tersedia di Android. Foto retakan, deteksi pakai AI, dan
                laporkan ke BPBD — semuanya dari HP Anda. Deteksi tetap jalan
                tanpa koneksi internet.
              </p>
              <div className="inline-flex items-center gap-2 rounded-xl bg-primary-surface px-4 py-2.5 text-sm font-semibold text-primary">
                <Smartphone className="h-4 w-4" />
                Segera di Android
              </div>
              <p className="text-[10px] text-text-secondary/60 mt-2">
                APK akan dirilis sebelum presentasi IYREF.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
