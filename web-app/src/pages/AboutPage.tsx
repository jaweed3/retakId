import { MapPin, Github, Mail, ExternalLink, Users, Cpu, Globe } from 'lucide-react';
import { SectionDivider } from '../components/SectionDivider';
import {
  SiKotlin, SiAndroid, SiTensorflow,
  SiReact, SiVite, SiTypescript, SiTailwindcss,
  SiPython, SiSupabase, SiPostgresql,
} from 'react-icons/si';

const TEAM = [
  {
    name: 'Farrel Ghozy',
    role: 'Data Acquisition & Web Dashboard',
    desc: 'Mengumpulkan dan menganotasi 3.547 gambar retakan tanah dari 70+ keyword. Membangun dashboard monitoring berbasis React, Vite, TypeScript, dan Supabase.',
    photo: '/team/farrel.png',
    color: 'from-primary to-primary-light',
  },
  {
    name: 'Adam Nurwahid',
    role: 'Android Development',
    desc: 'Membangun aplikasi Android dengan Kotlin, Jetpack Compose, CameraX, dan TensorFlow Lite INT8. Deteksi ML berjalan offline di HP.',
    photo: '/team/adam.png',
    color: 'from-amber-500 to-orange-500',
  },
  {
    name: 'Jaweed (Fatih)',
    role: 'ML Pipeline & Infrastructure',
    desc: 'Melatih model MobileNetV2 transfer learning dengan INT8 quantization. Membangun pipeline DVC/MLflow untuk reproducible ML research.',
    photo: '/team/jaweed.png',
    color: 'from-blue-500 to-cyan-500',
  },
];

const PILLARS = [
  {
    icon: Users,
    title: 'Crowdsourcing',
    desc: 'Warga adalah ujung tombak pelaporan. Setiap orang bisa memfoto dan melaporkan retakan tanah di sekitarnya melalui aplikasi Android.',
    accent: 'from-primary to-primary-light',
    iconBg: 'bg-primary-surface',
    iconColor: 'text-primary',
  },
  {
    icon: Cpu,
    title: 'AI Edge Computing',
    desc: 'Kecerdasan buatan berjalan langsung di HP. Model MobileNetV2 mengklasifikasi retakan dalam <50ms tanpa koneksi internet.',
    accent: 'from-amber-500 to-orange-500',
    iconBg: 'bg-waspada-bg',
    iconColor: 'text-waspada',
  },
  {
    icon: Globe,
    title: 'Open Data Dashboard',
    desc: 'Semua data laporan terbuka untuk publik dan BPBD. Pantau sebaran retakan secara real-time untuk respons yang lebih cepat.',
    accent: 'from-blue-500 to-cyan-500',
    iconBg: 'bg-blue-50 dark:bg-blue-950/40',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
];

const TECH_CATEGORIES = [
  {
    cat: 'Mobile',
    icon: SiAndroid,
    iconColor: '#34A853',
    items: [
      { icon: SiKotlin, name: 'Kotlin', color: '#7F52FF' },
      { icon: SiAndroid, name: 'Jetpack Compose', color: '#34A853' },
      { icon: SiTensorflow, name: 'TFLite INT8', color: '#FF6F00' },
    ],
  },
  {
    cat: 'Web',
    icon: SiReact,
    iconColor: '#61DAFB',
    items: [
      { icon: SiReact, name: 'React 18', color: '#61DAFB' },
      { icon: SiVite, name: 'Vite 6', color: '#646CFF' },
      { icon: SiTypescript, name: 'TypeScript', color: '#3178C6' },
      { icon: SiTailwindcss, name: 'Tailwind CSS', color: '#06B6D4' },
    ],
  },
  {
    cat: 'ML',
    icon: SiPython,
    iconColor: '#3776AB',
    items: [
      { icon: SiPython, name: 'Python 3.11', color: '#3776AB' },
      { icon: SiTensorflow, name: 'TensorFlow 2.15', color: '#FF6F00' },
    ],
  },
  {
    cat: 'Backend',
    icon: SiSupabase,
    iconColor: '#3ECF8E',
    items: [
      { icon: SiSupabase, name: 'Supabase', color: '#3ECF8E' },
      { icon: SiPostgresql, name: 'PostgreSQL', color: '#4169E1' },
    ],
  },
];

import { SEOMeta } from '../components/SEOMeta';

export function AboutPage() {
  return (
    <div>
      <SEOMeta title="Tentang" description="Retak.id adalah proyek IYREF 2026 Semi-Final. Tim: Farrel Ghozy, Adam Nurwahid, Jaweed. Teknologi: AI, Android, Supabase." />
      {/* ═══ Hero ═══ */}
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center max-w-2xl mx-auto">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Tentang
            </span>
            <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-text-primary mt-2 mb-3">
              Retak.id
            </h1>
            <p className="text-sm sm:text-base text-text-secondary leading-relaxed">
              Platform crowdsourcing deteksi dini retakan tanah berbasis AI dan
              partisipasi warga. Dibangun untuk kompetisi{' '}
              <strong className="text-text-primary">IYREF 2026 Semi-Final</strong>{' '}
              kategori Climate Resilience &amp; Local Wisdom.
            </p>
          </div>
        </div>
      </section>

      <SectionDivider variant="fade" />

      {/* ═══ Visi & Misi ═══ */}
      <section>
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center mb-10 sm:mb-12">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Visi &amp; Misi
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Apa yang Kami Perjuangkan
            </h2>
          </div>
          <div className="grid sm:grid-cols-2 gap-8 sm:gap-10">
            <div className="rounded-2xl bg-card border border-divider/60 p-6 sm:p-7">
              <h3 className="text-lg sm:text-xl font-bold text-text-primary mb-3">Visi</h3>
              <p className="text-sm text-text-secondary leading-relaxed">
                Menjadi platform crowdsourcing deteksi dini longsor terdepan di Indonesia
                yang memberdayakan warga dan mendukung kesiapsiagaan bencana melalui
                teknologi AI dan data terbuka.
              </p>
            </div>
            <div className="rounded-2xl bg-card border border-divider/60 p-6 sm:p-7">
              <h3 className="text-lg sm:text-xl font-bold text-text-primary mb-3">Misi</h3>
              <ul className="text-sm text-text-secondary space-y-2.5">
                <li className="flex items-start gap-2.5">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Memberdayakan warga dengan teknologi AI mobile yang bisa diakses siapa saja, kapan saja
                </li>
                <li className="flex items-start gap-2.5">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Menyediakan data real-time untuk BPBD dalam pengambilan keputusan dan mitigasi bencana
                </li>
                <li className="flex items-start gap-2.5">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Menyelamatkan nyawa melalui deteksi dini retakan tanah sebelum longsor terjadi
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* ═══ Latar Belakang (surface→card) ═══ */}
      <SectionDivider variant="fade" className="from-surface to-card" />
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center mb-10 sm:mb-12">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Latar Belakang
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Kenapa Proyek Ini Dibangun
            </h2>
          </div>
          <div className="grid sm:grid-cols-2 gap-8 sm:gap-10">
            <div className="space-y-4 text-sm text-text-secondary leading-relaxed">
              <p>
                <strong className="text-text-primary">Jenangan, Ponorogo</strong> merupakan
                daerah rawan longsor akibat kondisi geografis perbukitan dan curah hujan
                tinggi. Tanah retak sering menjadi tanda awal longsor — tetapi warga tidak
                memiliki alat untuk mengidentifikasi tingkat bahaya secara mandiri.
              </p>
              <p>
                BPBD Ponorogo kesulitan memantau kondisi tanah secara real-time karena
                tidak ada sistem pelaporan terpusat. Infrastruktur internet yang terbatas
                di daerah pelosok semakin mempersulit koordinasi penanganan bencana.
              </p>
            </div>
            <div className="space-y-4 text-sm text-text-secondary leading-relaxed">
              <p>
                <strong className="text-text-primary">Retak.id hadir sebagai solusi:</strong>
                {' '}aplikasi Android yang bisa mendeteksi retakan secara offline menggunakan
                AI, terhubung ke dashboard web yang bisa dipantau BPBD secara real-time.
              </p>
              <p>
                Dengan pendekatan crowdsourcing, setiap warga bisa menjadi ujung tombak
                deteksi dini — menciptakan jaringan pengawasan yang luas dan merata hingga
                ke pelosok desa tanpa bergantung pada infrastruktur internet.
              </p>
              <p className="text-xs text-text-secondary/60">
                Sumber data: BPBD Kabupaten Ponorogo, BMKG, BNPB
              </p>
            </div>
          </div>
        </div>
      </section>

      <SectionDivider variant="fade" />

      {/* ═══ 3 Pilar Solusi ═══ */}
      <section>
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center mb-10 sm:mb-12">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Solusi
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Tiga Pilar Retak.id
            </h2>
            <p className="text-sm text-text-secondary mt-2 max-w-xl mx-auto">
              Pendekatan menyeluruh yang menggabungkan partisipasi warga, kecerdasan
              buatan, dan keterbukaan data.
            </p>
          </div>
          <div className="grid sm:grid-cols-3 gap-5 lg:gap-6">
            {PILLARS.map((p) => (
              <div
                key={p.title}
                className="group relative flex flex-col rounded-2xl bg-card border border-divider/60 p-6 sm:p-7 hover:shadow-lg hover:-translate-y-1 transition-all duration-300"
              >
                <div className={`absolute top-0 left-5 right-5 h-1 rounded-b-sm bg-gradient-to-r ${p.accent} opacity-60 group-hover:opacity-100 transition-opacity`} />
                <div className={`flex h-11 w-11 sm:h-12 sm:w-12 items-center justify-center rounded-xl ${p.iconBg} mb-4 group-hover:scale-110 transition-transform duration-300`}>
                  <p.icon className={`h-5 w-5 sm:h-6 sm:w-6 ${p.iconColor}`} />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-text-primary mb-2">{p.title}</h3>
                <p className="text-sm text-text-secondary leading-relaxed flex-1">{p.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <SectionDivider variant="fade" className="from-surface to-card" />

      {/* ═══ Tim ═══ */}
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center mb-10 sm:mb-12">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Tim
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Tim Retak.id
            </h2>
            <p className="text-sm text-text-secondary mt-2 max-w-lg mx-auto">
              Tiga anggota dengan keahlian berbeda, satu tujuan: menyelamatkan nyawa
              melalui deteksi dini longsor.
            </p>
          </div>
          <div className="grid sm:grid-cols-3 gap-5 lg:gap-6">
            {TEAM.map((member) => (
              <div
                key={member.name}
                className="group relative rounded-2xl bg-surface border border-divider/40 p-6 sm:p-7 text-center hover:shadow-xl hover:-translate-y-1 transition-all duration-300"
              >
                {/* Top accent bar */}
                <div className={`absolute top-0 left-8 right-8 h-1 rounded-b-sm bg-gradient-to-r ${member.color} opacity-70 group-hover:opacity-100 transition-opacity`} />

                {/* Photo */}
                <div className="relative mx-auto mb-5 sm:mb-6 w-fit">
                  <img
                    src={member.photo}
                    alt={member.name}
                    className={`h-20 w-20 sm:h-24 sm:w-24 rounded-full object-cover bg-gradient-to-br ${member.color} shadow-lg ring-4 ring-card dark:ring-card`}
                  />
                  <div className="absolute -bottom-1 -right-1 flex h-6 w-6 items-center justify-center rounded-full bg-card border-2 border-divider">
                    <div className={`h-2.5 w-2.5 rounded-full bg-gradient-to-br ${member.color}`} />
                  </div>
                </div>

                {/* Name */}
                <h3 className="text-sm sm:text-base font-bold text-text-primary mb-1.5">
                  {member.name}
                </h3>

                {/* Role badge */}
                <div className="inline-flex items-center rounded-full bg-primary-surface/60 border border-primary/10 px-3 py-1 text-[11px] sm:text-xs font-medium text-primary mb-3">
                  {member.role}
                </div>

                {/* Description */}
                <p className="text-xs text-text-secondary leading-relaxed max-w-xs mx-auto">
                  {member.desc}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <SectionDivider variant="fade" />

      {/* ═══ Teknologi ═══ */}
      <section>
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center mb-10 sm:mb-12">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Teknologi
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Teknologi yang Digunakan
            </h2>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {TECH_CATEGORIES.map((cat) => (
              <div
                key={cat.cat}
                className="rounded-xl bg-card border border-divider/60 px-5 py-5 hover:border-primary/20 hover:shadow-sm transition-all"
              >
                <div className="flex items-center gap-2.5 mb-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface border border-divider/40">
                    <cat.icon className="h-4 w-4" style={{ color: cat.iconColor }} />
                  </div>
                  <h3 className="text-sm font-bold text-text-primary">{cat.cat}</h3>
                </div>
                <div className="space-y-1.5">
                  {cat.items.map((item) => (
                    <div key={item.name} className="flex items-center gap-2 text-xs text-text-secondary">
                      <item.icon className="h-3.5 w-3.5 shrink-0" style={{ color: item.color }} />
                      <span>{item.name}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <SectionDivider variant="fade" className="from-surface to-card" />

      {/* ═══ Kontak ═══ */}
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-20">
          <div className="text-center max-w-lg mx-auto">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Kontak
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2 mb-6">
              Kontak &amp; Tautan
            </h2>
            <div className="flex flex-wrap justify-center gap-3">
              <a
                href="https://github.com/jaweed3/retakId"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-xl border border-divider bg-surface px-4 py-2.5 text-sm text-text-secondary hover:text-text-primary hover:border-primary/30 transition-all"
              >
                <Github className="h-4 w-4" />
                GitHub Repository
                <ExternalLink className="h-3 w-3" />
              </a>
              <a
                href="mailto:retak.id@email.com"
                className="inline-flex items-center gap-2 rounded-xl border border-divider bg-surface px-4 py-2.5 text-sm text-text-secondary hover:text-text-primary hover:border-primary/30 transition-all"
              >
                <Mail className="h-4 w-4" />
                retak.id@email.com
              </a>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
