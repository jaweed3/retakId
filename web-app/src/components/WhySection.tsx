import { Users, Cpu, Globe, ShieldCheck } from 'lucide-react';

const PILLARS = [
  {
    icon: Users,
    title: 'Crowdsourcing',
    description:
      'Warga adalah ujung tombak. Setiap orang bisa melaporkan retakan tanah melalui aplikasi Android, menciptakan jaringan deteksi yang luas hingga ke pelosok desa.',
    accent: 'from-primary to-primary-light',
    iconBg: 'bg-primary-surface',
    iconColor: 'text-primary',
  },
  {
    icon: Cpu,
    title: 'AI Edge Computing',
    description:
      'Kecerdasan buatan berjalan langsung di HP tanpa internet. Model MobileNetV2 mengklasifikasikan retakan dalam waktu kurang dari 50 milidetik.',
    accent: 'from-amber-500 to-orange-500',
    iconBg: 'bg-waspada-bg',
    iconColor: 'text-waspada',
  },
  {
    icon: Globe,
    title: 'Open Data Dashboard',
    description:
      'Data laporan terbuka untuk publik dan BPBD. Pantau sebaran retakan secara real-time, identifikasi zona rawan, dan respons lebih cepat.',
    accent: 'from-blue-500 to-cyan-500',
    iconBg: 'bg-blue-50 dark:bg-blue-950/40',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
];

export function WhySection() {
  return (
    <section id="kenapa" className="scroll-mt-20">
      <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-20 sm:py-28">
        {/* Header */}
        <div className="text-center mb-12 sm:mb-14">
          <span className="text-xs font-semibold text-primary uppercase tracking-widest">
            Misi Kami
          </span>
          <h2 className="text-xl sm:text-2xl lg:text-3xl font-bold text-text-primary mt-2">
            Kenapa Retak.id Dibangun?
          </h2>
          <p className="text-sm text-text-secondary mt-2 max-w-xl mx-auto">
            Jenangan, Ponorogo adalah daerah rawan longsor dengan infrastruktur internet
            terbatas. Retak.id menjembatani warga, teknologi AI, dan BPBD dalam satu
            platform.
          </p>
        </div>

        {/* 3 Pillar Cards — equal height */}
        <div className="grid sm:grid-cols-3 gap-5 lg:gap-6 mb-12 sm:mb-14">
          {PILLARS.map((p) => (
            <div
              key={p.title}
              className="group relative flex flex-col rounded-2xl bg-card border border-divider/60 p-5 sm:p-6 hover:shadow-lg hover:-translate-y-1 transition-all duration-300"
            >
              <div className={`absolute top-0 left-5 right-5 h-1 rounded-b-sm bg-gradient-to-r ${p.accent} opacity-60 group-hover:opacity-100 transition-opacity`} />

              <div className={`flex h-11 w-11 sm:h-12 sm:w-12 items-center justify-center rounded-xl ${p.iconBg} mb-3 sm:mb-4 group-hover:scale-110 transition-transform duration-300`}>
                <p.icon className={`h-5 w-5 sm:h-6 sm:w-6 ${p.iconColor}`} />
              </div>

              <h3 className="text-base sm:text-lg font-bold text-text-primary mb-1.5">
                {p.title}
              </h3>
              <p className="text-sm text-text-secondary leading-relaxed flex-1">
                {p.description}
              </p>
            </div>
          ))}
        </div>

        {/* Bottom mission */}
        <div className="max-w-lg mx-auto text-center rounded-2xl bg-primary-surface/50 dark:bg-primary-surface/10 border border-primary/10 px-5 py-4 sm:px-6 sm:py-5">
          <ShieldCheck className="h-7 w-7 sm:h-8 sm:w-8 text-primary mx-auto mb-2 sm:mb-3" />
          <p className="text-sm text-text-primary font-semibold">
            Satu tujuan: menyelamatkan nyawa melalui deteksi dini.
          </p>
        </div>
      </div>
    </section>
  );
}
