import { Smartphone, QrCode } from 'lucide-react';
import { HeroSection } from '../components/HeroSection';
import { HowItWorks } from '../components/HowItWorks';
import { WhySection } from '../components/WhySection';
import { MiniMapPreview } from '../components/MiniMapPreview';

export function LandingPage() {
  return (
    <div>
      <HeroSection />
      <HowItWorks />
      <WhySection />
      <MiniMapPreview />

      {/* CTA Download Android */}
      <section id="download" className="bg-card border-b border-divider scroll-mt-20">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
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
