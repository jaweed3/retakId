import { useEffect, useRef, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, ArrowRight, ShieldCheck, Skull } from 'lucide-react';
import { useLaporan } from '../hooks/useLaporan';

function useCountUp(target: number, duration = 1400, start = false) {
  const [value, setValue] = useState(0);
  const raf = useRef<number>(0);

  useEffect(() => {
    if (!start || target === 0) {
      setValue(0);
      return;
    }
    const startTime = performance.now();
    const step = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(eased * target));
      if (progress < 1) {
        raf.current = requestAnimationFrame(step);
      }
    };
    raf.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf.current);
  }, [target, duration, start]);

  return value;
}

function StatCard({
  icon: Icon,
  label,
  value,
  color,
  bg,
  textColor,
  ringColor,
  visible,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  color: string;
  bg: string;
  textColor: string;
  ringColor: string;
  visible: boolean;
}) {
  const displayed = useCountUp(value, 1400, visible);

  return (
    <div className="relative group">
      <div className={`absolute inset-0 ${bg} rounded-2xl blur-sm opacity-0 group-hover:opacity-100 transition-opacity duration-500`} />
      <div className="relative rounded-2xl bg-card border border-divider/50 p-4 sm:p-5 text-center hover:border-divider hover:shadow-lg transition-all duration-300">
        <div className={`flex h-10 w-10 sm:h-12 sm:w-12 mx-auto items-center justify-center rounded-xl ${bg} ring-2 ${ringColor} mb-2.5 sm:mb-3`}>
          <Icon className={`h-5 w-5 sm:h-6 sm:w-6 ${color}`} />
        </div>
        <p className={`text-xl sm:text-2xl lg:text-3xl font-extrabold tabular-nums tracking-tight ${textColor}`}>
          {displayed.toLocaleString('id-ID')}
        </p>
        <p className="text-[10px] sm:text-xs text-text-secondary mt-0.5">{label}</p>
      </div>
    </div>
  );
}

export function HeroSection() {
  const { data, counts, isLoading } = useLaporan({ limit: 500 });
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setVisible(true), 300);
    return () => clearTimeout(timer);
  }, []);

  const daerahTerpantau = useMemo(() => {
    if (!data || data.length === 0) return 0;
    const unique = new Set(data.map((r) => r.nama_lokasi.toLowerCase().trim()));
    return unique.size;
  }, [data]);

  const stats = [
    {
      icon: MapPin,
      label: 'Total Laporan',
      value: isLoading ? 0 : counts.total,
      color: 'text-primary',
      bg: 'bg-primary-surface',
      textColor: 'text-primary',
      ringColor: 'ring-primary/20',
    },
    {
      icon: MapPin,
      label: 'Daerah Terpantau',
      value: daerahTerpantau,
      color: 'text-waspada',
      bg: 'bg-waspada-bg',
      textColor: 'text-waspada',
      ringColor: 'ring-waspada/20',
    },
    {
      icon: Skull,
      label: 'Laporan Bahaya',
      value: isLoading ? 0 : counts.bahaya,
      color: 'text-bahaya',
      bg: 'bg-bahaya-bg',
      textColor: 'text-bahaya',
      ringColor: 'ring-bahaya/20',
    },
  ];

  return (
    <section id="beranda" className="relative overflow-hidden scroll-mt-20">
      {/* Dot pattern */}
      <div
        className="absolute inset-0 opacity-[0.07] dark:opacity-[0.08] pointer-events-none"
        style={{
          backgroundImage: 'radial-gradient(circle, currentColor 1px, transparent 1px)',
          backgroundSize: '24px 24px',
        }}
      />
      {/* Top gradient */}
      <div className="absolute top-0 inset-x-0 h-72 bg-gradient-to-b from-primary-surface/60 to-transparent dark:from-primary-surface/20 pointer-events-none" />

      <div className="relative max-w-6xl mx-auto px-6 sm:px-8 lg:px-10 pt-20 sm:pt-28 lg:pt-32 pb-16 sm:pb-20">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-12 sm:mb-14">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary/40 bg-primary-surface px-3.5 py-1.5 text-xs font-semibold text-primary shadow-sm mb-5 sm:mb-6">
            <ShieldCheck className="h-3.5 w-3.5" />
            IYREF 2026 &mdash; Climate Resilience &amp; Local Wisdom
          </div>

          <h1 className="text-2xl sm:text-4xl lg:text-5xl font-extrabold text-text-primary leading-tight tracking-tight">
            Pantau Retakan Tanah,{' '}
            <span className="text-primary">Cegah Longsor</span>{' '}
            Bersama
          </h1>

          <p className="mt-5 sm:mt-6 text-sm sm:text-base text-text-secondary max-w-xl mx-auto leading-relaxed">
            Platform crowdsourcing deteksi dini retakan tanah di Jenangan, Ponorogo.
            Warga foto retakan lewat Android, AI deteksi tingkat bahaya, BPBD
            pantau dashboard secara real-time.
          </p>

          <div className="flex flex-wrap justify-center gap-3 mt-7 sm:mt-8">
            <Link
              to="/dashboard"
              className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 sm:px-6 py-2.5 sm:py-3 text-sm font-semibold text-white shadow-md shadow-primary/20 hover:bg-primary-light hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200"
            >
              Lihat Dashboard
              <ArrowRight className="h-4 w-4" />
            </Link>
            <a
              href="#kenapa"
              className="inline-flex items-center gap-2 rounded-xl border border-divider bg-card px-5 sm:px-6 py-2.5 sm:py-3 text-sm font-semibold text-text-secondary hover:text-text-primary hover:border-primary/30 transition-all duration-200"
            >
              Kenapa Retak.id?
              <ShieldCheck className="h-4 w-4" />
            </a>
          </div>
        </div>

        {/* Stats */}
        <div className="grid sm:grid-cols-3 gap-4 sm:gap-5 lg:gap-6 max-w-2xl mx-auto">
          {stats.map((stat) => (
            <StatCard key={stat.label} {...stat} visible={visible && !isLoading} />
          ))}
        </div>

      </div>
    </section>
  );
}
