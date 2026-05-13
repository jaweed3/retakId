import { MapPin, Github, Mail, ExternalLink } from 'lucide-react';

const TEAM = [
  {
    name: 'Farrel Ghozy',
    role: 'Data Acquisition & Web Dashboard',
    desc: 'Mengumpulkan dan menganotasi 3,547 gambar retakan tanah. Membangun dashboard monitoring berbasis React + Supabase.',
    color: 'from-primary to-primary-light',
  },
  {
    name: 'Adam Nurwahid',
    role: 'Android Development',
    desc: 'Membangun aplikasi Android dengan CameraX, TensorFlow Lite, dan integrasi Supabase — deteksi ML offline.',
    color: 'from-amber-500 to-orange-500',
  },
  {
    name: 'Jaweed (Fatih)',
    role: 'ML Pipeline & Infrastructure',
    desc: 'Melatih model MobileNetV2 dengan INT8 quantization. Membangun pipeline DVC/MLflow untuk reproducible ML.',
    color: 'from-blue-500 to-cyan-500',
  },
];

const TECH = [
  { cat: 'Mobile', items: 'Kotlin, Jetpack Compose, CameraX, TFLite INT8' },
  { cat: 'Web', items: 'React 18, Vite 6, TypeScript, Tailwind CSS, Leaflet' },
  { cat: 'ML', items: 'Python 3.11, TensorFlow 2.15, MobileNetV2, DVC, MLflow' },
  { cat: 'Backend', items: 'Supabase (PostgreSQL, Auth, Storage, Realtime)' },
];

export function AboutPage() {
  return (
    <div>
      {/* Hero */}
      <section className="bg-card border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <div className="text-center max-w-2xl mx-auto">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Tentang
            </span>
            <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-text-primary mt-2 mb-3">
              Retak.id
            </h1>
            <p className="text-sm sm:text-base text-text-secondary leading-relaxed">
              Platform crowdsourcing deteksi dini retakan tanah berbasis AI dan
              partisipasi warga. Dibangun untuk kompetisi IYREF 2026 Semi-Final
              kategori Climate Resilience &amp; Local Wisdom.
            </p>
          </div>
        </div>
      </section>

      {/* Visi Misi */}
      <section className="border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <div className="grid sm:grid-cols-2 gap-8 sm:gap-10">
            <div>
              <h2 className="text-lg sm:text-xl font-bold text-text-primary mb-2">Visi</h2>
              <p className="text-sm text-text-secondary leading-relaxed">
                Menjadi platform crowdsourcing deteksi dini longsor terdepan di Indonesia
                yang memberdayakan warga dan mendukung kesiapsiagaan bencana.
              </p>
            </div>
            <div>
              <h2 className="text-lg sm:text-xl font-bold text-text-primary mb-2">Misi</h2>
              <ul className="text-sm text-text-secondary space-y-2">
                <li className="flex items-start gap-2">
                  <span className="mt-0.5 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Memberdayakan warga dengan teknologi AI mobile yang bisa diakses siapa saja
                </li>
                <li className="flex items-start gap-2">
                  <span className="mt-0.5 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Menyediakan data real-time untuk BPBD dalam pengambilan keputusan
                </li>
                <li className="flex items-start gap-2">
                  <span className="mt-0.5 h-1.5 w-1.5 rounded-full bg-primary shrink-0" />
                  Menyelamatkan nyawa melalui deteksi dini retakan tanah sebelum longsor
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Latar Belakang */}
      <section className="bg-card border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <h2 className="text-xl sm:text-2xl font-bold text-text-primary mb-4">
            Latar Belakang Masalah
          </h2>
          <div className="grid sm:grid-cols-2 gap-6 sm:gap-8">
            <div className="space-y-3 text-sm text-text-secondary leading-relaxed">
              <p>
                Jenangan, Ponorogo merupakan daerah rawan longsor. Tanah retak
                sering menjadi tanda awal — tetapi warga tidak memiliki alat untuk
                mengidentifikasi tingkat bahaya secara mandiri.
              </p>
              <p>
                BPBD juga kesulitan memantau kondisi tanah secara real-time karena
                tidak ada sistem pelaporan terpusat yang bisa diakses warga,
                terutama di daerah dengan infrastruktur internet terbatas.
              </p>
            </div>
            <div className="space-y-3 text-sm text-text-secondary leading-relaxed">
              <p>
                <strong className="text-text-primary">Retak.id hadir sebagai solusi:</strong>
                {' '}aplikasi Android yang bisa mendeteksi retakan secara offline
                menggunakan AI, terhubung ke dashboard web yang bisa dipantau
                BPBD secara real-time.
              </p>
              <p>
                Dengan pendekatan crowdsourcing, setiap warga bisa menjadi
                ujung tombak deteksi dini — menciptakan jaringan pengawasan
                yang luas dan merata hingga ke pelosok desa.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Tim */}
      <section className="border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <div className="text-center mb-8 sm:mb-10">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Tim
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Tim Retak.id
            </h2>
          </div>
          <div className="grid sm:grid-cols-3 gap-5 lg:gap-6">
            {TEAM.map((member) => (
              <div
                key={member.name}
                className="rounded-2xl bg-card border border-divider/60 p-5 sm:p-6 text-center hover:shadow-lg transition-all duration-300"
              >
                {/* Avatar placeholder */}
                <div className={`h-16 w-16 sm:h-20 sm:w-20 mx-auto rounded-full bg-gradient-to-br ${member.color} flex items-center justify-center mb-3 sm:mb-4 shadow-md`}>
                  <span className="text-xl sm:text-2xl font-bold text-white">
                    {member.name.charAt(0)}
                  </span>
                </div>
                <h3 className="text-sm sm:text-base font-bold text-text-primary">
                  {member.name}
                </h3>
                <p className="text-[10px] sm:text-xs text-primary font-medium mt-0.5 mb-2">
                  {member.role}
                </p>
                <p className="text-xs text-text-secondary leading-relaxed">
                  {member.desc}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Tech Stack */}
      <section className="bg-card border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <div className="text-center mb-8 sm:mb-10">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">
              Teknologi
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mt-2">
              Teknologi yang Digunakan
            </h2>
          </div>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {TECH.map((t) => (
              <div
                key={t.cat}
                className="rounded-xl bg-surface border border-divider/50 px-4 py-4 text-center"
              >
                <h3 className="text-sm font-bold text-text-primary mb-2">{t.cat}</h3>
                <p className="text-xs text-text-secondary leading-relaxed">{t.items}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Kontak */}
      <section className="border-b border-divider">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-12 sm:py-16">
          <div className="text-center max-w-lg mx-auto">
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mb-4">
              Kontak &amp; Tautan
            </h2>
            <div className="flex flex-wrap justify-center gap-4 text-sm">
              <a
                href="https://github.com/jaweed3/retakId"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-xl border border-divider bg-card px-4 py-2.5 text-text-secondary hover:text-text-primary hover:border-primary/30 transition-all"
              >
                <Github className="h-4 w-4" />
                GitHub Repository
                <ExternalLink className="h-3 w-3" />
              </a>
              <a
                href="mailto:retak.id@email.com"
                className="inline-flex items-center gap-2 rounded-xl border border-divider bg-card px-4 py-2.5 text-text-secondary hover:text-text-primary hover:border-primary/30 transition-all"
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
