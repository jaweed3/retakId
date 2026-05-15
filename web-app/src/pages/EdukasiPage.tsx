import { useState, useRef, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck, AlertTriangle, Skull, Phone, ChevronDown, Smartphone, Camera, MapPin, ExternalLink, ImageIcon, CheckCircle, PhoneCall, ArrowRight } from 'lucide-react';
import { cn } from '../utils/cn';
import { SEOMeta } from '../components/SEOMeta';
import { ScrollReveal } from '../components/ScrollReveal';
import { SectionDivider } from '../components/SectionDivider';

const FAQS = [
  {
    q: 'Apakah aplikasi butuh internet?',
    a: 'Untuk deteksi retakan oleh AI, TIDAK perlu internet — semua berjalan di dalam HP. Internet hanya dibutuhkan saat mengirim laporan ke server. Laporan bisa dikirim nanti begitu ada koneksi.',
  },
  {
    q: 'Bagaimana cara download aplikasi?',
    a: 'Aplikasi Android akan tersedia dalam bentuk file APK yang bisa di-download dari halaman ini. Tim Retak.id akan merilis APK sebelum presentasi IYREF 2026.',
  },
  {
    q: 'Siapa yang bisa melapor?',
    a: 'Semua warga Jenangan, Ponorogo dan sekitarnya bisa melapor. Tidak perlu keahlian khusus — cukup foto retakan dan aplikasi akan otomatis mendeteksi tingkat bahayanya.',
  },
  {
    q: 'Apakah data saya aman?',
    a: 'Ya. Data yang dikirim hanya lokasi GPS dan foto retakan — tidak ada data pribadi sensitif. Semua data tersimpan aman di server Supabase dengan enkripsi.',
  },
  {
    q: 'Berapa akurasi deteksi AI?',
    a: 'Model AI kami memiliki akurasi sekitar 85% dalam uji coba. Meskipun tidak sempurna, ini cukup untuk memberikan peringatan dini. Keputusan akhir tetap ada di BPBD dan ahli geologi.',
  },
];

const RISK_LEVELS = [
  {
    icon: ShieldCheck,
    title: 'AMAN',
    color: 'text-aman',
    bg: 'bg-aman-bg',
    border: 'border-aman/30',
    hoverBorder: 'group-hover:border-aman/60',
    shadowColor: 'shadow-aman/10',
    desc: 'Retakan minor akibat penyusutan alami tanah. Lebar retakan < 1 cm, tidak bertambah lebar, tidak ada rembesan air.',
    action: 'Tidak perlu tindakan khusus. Amati secara berkala setiap minggu.',
    image: '/edukasi/aman-placeholder.svg',
    imageAlt: 'Contoh retakan tanah tingkat AMAN',
  },
  {
    icon: AlertTriangle,
    title: 'WASPADA',
    color: 'text-waspada',
    bg: 'bg-waspada-bg',
    border: 'border-waspada/30',
    hoverBorder: 'group-hover:border-waspada/60',
    shadowColor: 'shadow-waspada/10',
    desc: 'Retakan signifikan, lebar 1-5 cm. Bertambah lebar dalam beberapa hari. Mungkin ada rembesan air kecil.',
    action: 'Laporkan ke ketua RT/RW. Pantau setiap hari. Siapkan rencana evakuasi keluarga.',
    image: '/edukasi/waspada-placeholder.svg',
    imageAlt: 'Contoh retakan tanah tingkat WASPADA',
  },
  {
    icon: Skull,
    title: 'BAHAYA',
    color: 'text-bahaya',
    bg: 'bg-bahaya-bg',
    border: 'border-bahaya/30',
    hoverBorder: 'group-hover:border-bahaya/60',
    shadowColor: 'shadow-bahaya/10',
    desc: 'Retakan kritis, lebar > 5 cm. Bertambah lebar dengan cepat. Ada suara gemuruh atau rembesan air deras.',
    action: 'SEGERA EVAKUASI! Hubungi BPBD Ponorogo. Jauhi area retakan minimal 100 meter.',
    image: '/edukasi/bahaya-placeholder.svg',
    imageAlt: 'Contoh retakan tanah tingkat BAHAYA',
  },
];

const CONTACTS = [
  { label: 'BPBD Kabupaten Ponorogo', phone: '(0352) XXX-XXXX', icon: PhoneCall },
  { label: 'Call Center BNPB', phone: '117', icon: Phone },
  { label: 'Polres Ponorogo', phone: '110', icon: Phone },
];

const STEPS_ANDROID = [
  'Download & install APK Retak.id',
  'Buka aplikasi & izinkan akses kamera',
  'Arahkan kamera ke retakan tanah',
  'Tekan tombol foto — AI akan deteksi otomatis',
  'Isi catatan (opsional) & kirim laporan',
];

const STEPS_WEB = [
  'Buka halaman Laporan di web ini',
  'Klik tombol "Laporkan Retakan"',
  'Upload foto retakan (JPG/PNG)',
  'Lokasi diambil dari GPS foto (EXIF)',
  'Isi form & kirim laporan',
];

function FaqItem({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const [contentHeight, setContentHeight] = useState(0);

  useEffect(() => {
    if (contentRef.current) {
      setContentHeight(contentRef.current.scrollHeight);
    }
  }, [a]);

  return (
    <div className={cn(
      'rounded-2xl border transition-all duration-300',
      open
        ? 'border-primary/30 bg-primary-surface/40 shadow-sm'
        : 'border-divider bg-card hover:border-divider/80 hover:shadow-sm'
    )}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between gap-3 px-5 py-4 sm:px-6 text-left font-medium text-text-primary transition-colors"
      >
        <span className="text-sm sm:text-base">{q}</span>
        <ChevronDown
          className={cn(
            'h-4 w-4 shrink-0 text-text-secondary transition-transform duration-300',
            open && 'rotate-180'
          )}
        />
      </button>
      <div
        className="overflow-hidden transition-all duration-300 ease-in-out"
        style={{ maxHeight: open ? contentHeight : 0 }}
      >
        <div ref={contentRef} className="px-5 sm:px-6 pb-4 sm:pb-5 text-sm text-text-secondary leading-relaxed border-t border-divider pt-3">
          {a}
        </div>
      </div>
    </div>
  );
}

function ImageFrame({ src, alt, className }: { src: string; alt: string; className?: string }) {
  return (
    <div className={cn('relative overflow-hidden rounded-xl bg-surface', className)}>
      <img
        src={src}
        alt={alt}
        className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
        loading="lazy"
      />
      <div className="absolute inset-0 ring-1 ring-inset ring-black/5 rounded-xl pointer-events-none" />
    </div>
  );
}

function ContactCard({ label, phone, icon: Icon }: { label: string; phone: string; icon: typeof Phone }) {
  return (
    <div className="group rounded-xl bg-card border border-divider px-5 py-4 flex items-center justify-between transition-all duration-200 hover:border-bahaya/30 hover:shadow-md hover:shadow-bahaya/5">
      <div className="flex items-center gap-3 min-w-0">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bahaya-bg transition-colors group-hover:bg-bahaya/10">
          <Icon className="h-4 w-4 text-bahaya" />
        </div>
        <span className="text-sm font-medium text-text-primary truncate">{label}</span>
      </div>
      <span className="text-sm font-bold text-bahaya tabular-nums shrink-0 ml-3">{phone}</span>
    </div>
  );
}

export function EdukasiPage() {
  return (
    <div>
      <SEOMeta
        title="Edukasi Bencana"
        description="Kenali retakan tanah: tingkat bahaya AMAN, WASPADA, BAHAYA. Cara melapor, kontak darurat BPBD Ponorogo, dan FAQ."
      />

      {/* ── Hero ── */}
      <section className="relative overflow-hidden bg-gradient-to-b from-primary-surface/60 via-card to-card">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--color-primary)_0%,_transparent_60%)] opacity-[0.07] dark:opacity-[0.12]" />
        <div className="relative max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-16 sm:py-24 lg:py-28">
          <ScrollReveal>
            <div className="text-center max-w-3xl mx-auto">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-primary-surface px-4 py-1.5 text-xs font-semibold text-primary">
                <Camera className="h-3.5 w-3.5" />
                Edukasi Bencana
              </span>
              <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-text-primary mt-5 mb-4 leading-tight">
                Kenali Retakan Tanah,{' '}
                <span className="text-primary">Cegah Longsor</span>
              </h1>
              <p className="text-sm sm:text-base text-text-secondary leading-relaxed max-w-xl mx-auto">
                Informasi lengkap tentang retakan tanah, tingkat bahaya, cara melapor, dan
                langkah-langkah yang harus dilakukan. Disusun khusus untuk warga Jenangan, Ponorogo.
              </p>
            </div>
          </ScrollReveal>
        </div>
        <div className="absolute bottom-0 left-0 right-0 h-8 sm:h-12 bg-gradient-to-t from-surface to-transparent" />
      </section>

      <SectionDivider variant="slant" />

      {/* ── Tingkat Bahaya ── */}
      <section className="bg-surface py-16 sm:py-20 lg:py-24">
        <div className="max-w-6xl mx-auto px-6 sm:px-8 lg:px-10">
          <ScrollReveal>
            <div className="text-center mb-10 sm:mb-14">
              <h2 className="text-2xl sm:text-3xl font-bold text-text-primary">
                Tiga Tingkat Bahaya Retakan Tanah
              </h2>
              <p className="text-sm sm:text-base text-text-secondary mt-3 max-w-lg mx-auto">
                Setiap retakan tanah memiliki tingkat bahaya yang berbeda. Kenali ciri-cirinya dan lakukan tindakan yang tepat.
              </p>
            </div>
          </ScrollReveal>

          <div className="grid md:grid-cols-3 gap-6 lg:gap-8">
            {RISK_LEVELS.map((item, i) => (
              <ScrollReveal key={item.title} delay={i * 100}>
                <div className={cn(
                  'group relative rounded-2xl border-2 overflow-hidden transition-all duration-300',
                  item.border, item.hoverBorder,
                  'bg-card hover:shadow-xl',
                  item.shadowColor
                )}>
                  <div className="aspect-[3/2] overflow-hidden">
                    <ImageFrame src={item.image} alt={item.imageAlt} />
                  </div>
                  <div className="p-5 sm:p-6">
                    <div className={cn('flex h-10 w-10 items-center justify-center rounded-xl mb-3', item.bg)}>
                      <item.icon className={cn('h-5 w-5', item.color)} />
                    </div>
                    <h3 className={cn('text-lg font-bold mb-2', item.color)}>{item.title}</h3>
                    <p className="text-xs sm:text-sm text-text-secondary leading-relaxed mb-4">{item.desc}</p>
                    <div className={cn('rounded-xl px-4 py-3 border', item.bg, item.border)}>
                      <p className={cn('text-xs font-semibold mb-1', item.color)}>Tindakan:</p>
                      <p className="text-xs text-text-secondary leading-relaxed">{item.action}</p>
                    </div>
                  </div>
                </div>
              </ScrollReveal>
            ))}
          </div>
        </div>
      </section>

      <SectionDivider variant="wave" />

      {/* ── Cara Melapor ── */}
      <section className="bg-card py-16 sm:py-20 lg:py-24">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10">
          <ScrollReveal>
            <div className="text-center mb-10 sm:mb-14">
              <h2 className="text-2xl sm:text-3xl font-bold text-text-primary">
                Cara Melaporkan Retakan
              </h2>
              <p className="text-sm sm:text-base text-text-secondary mt-3 max-w-lg mx-auto">
                Dua cara mudah untuk melaporkan retakan tanah yang Anda temukan.
              </p>
            </div>
          </ScrollReveal>

          <div className="grid lg:grid-cols-2 gap-8 lg:gap-12 items-start">
            {/* Android */}
            <ScrollReveal delay={100}>
              <div className="group rounded-2xl bg-surface border border-divider p-6 sm:p-8 transition-all duration-300 hover:shadow-lg hover:border-primary/20">
                <div className="flex items-center gap-4 mb-6">
                  <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-primary-surface transition-colors group-hover:bg-primary/15">
                    <Smartphone className="h-7 w-7 text-primary" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-text-primary">Via Aplikasi Android</h3>
                    <p className="text-xs text-text-secondary">Deteksi offline + kirim online</p>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row gap-6 items-start">
                  {/* Phone mockup placeholder */}
                  <div className="relative shrink-0 mx-auto sm:mx-0">
                    <div className="w-40 h-80 rounded-[2rem] border-4 border-gray-300 dark:border-gray-600 overflow-hidden bg-gray-100 dark:bg-gray-800 relative shadow-lg">
                      <img
                        src="/edukasi/app-mockup-placeholder.svg"
                        alt="Mockup Aplikasi Android"
                        className="h-full w-full object-cover"
                        loading="lazy"
                      />
                      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/40 to-transparent h-16 pointer-events-none" />
                    </div>
                    <div className="flex items-center justify-center gap-1 mt-2 text-[10px] text-text-secondary/60">
                      <ImageIcon className="h-3 w-3" />
                      <span>Ganti dengan screenshot APK</span>
                    </div>
                  </div>

                  {/* Steps */}
                  <div className="flex-1 min-w-0">
                    <ol className="space-y-3">
                      {STEPS_ANDROID.map((step, i) => (
                        <li key={i} className="flex gap-3 items-start">
                          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-surface text-primary text-xs font-bold">
                            {i + 1}
                          </span>
                          <span className="text-xs sm:text-sm text-text-secondary leading-relaxed pt-0.5">{step}</span>
                        </li>
                      ))}
                    </ol>
                    <div className="mt-5 flex flex-wrap items-center gap-3 text-[11px] text-text-secondary/60">
                      <span className="flex items-center gap-1.5">
                        <CheckCircle className="h-3.5 w-3.5 text-aman" />
                        Deteksi offline
                      </span>
                      <span className="flex items-center gap-1.5">
                        <CheckCircle className="h-3.5 w-3.5 text-aman" />
                        Kirim online
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </ScrollReveal>

            {/* Web */}
            <ScrollReveal delay={200}>
              <div className="group rounded-2xl bg-surface border border-divider p-6 sm:p-8 transition-all duration-300 hover:shadow-lg hover:border-primary/20">
                <div className="flex items-center gap-4 mb-6">
                  <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-primary-surface transition-colors group-hover:bg-primary/15">
                    <Camera className="h-7 w-7 text-primary" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-text-primary">Via Website (Form)</h3>
                    <p className="text-xs text-text-secondary">Upload foto dari browser</p>
                  </div>
                </div>

                <div className="mb-6">
                  <div className="relative aspect-[16/9] rounded-xl overflow-hidden bg-gray-100 dark:bg-gray-800 border border-divider">
                    <img
                      src="/edukasi/report-example-placeholder.svg"
                      alt="Contoh form laporan website"
                      className="h-full w-full object-cover"
                      loading="lazy"
                    />
                    <div className="absolute inset-0 ring-1 ring-inset ring-black/5 rounded-xl pointer-events-none" />
                  </div>
                  <div className="flex items-center justify-center gap-1 mt-2 text-[10px] text-text-secondary/60">
                    <ImageIcon className="h-3 w-3" />
                    <span>Ganti dengan screenshot form laporan</span>
                  </div>
                </div>

                <ol className="space-y-3">
                  {STEPS_WEB.map((step, i) => (
                    <li key={i} className="flex gap-3 items-start">
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary-surface text-primary text-xs font-bold">
                        {i + 1}
                      </span>
                      <span className="text-xs sm:text-sm text-text-secondary leading-relaxed pt-0.5">{step}</span>
                    </li>
                  ))}
                </ol>
                <div className="mt-4 flex items-center gap-1.5 text-[11px] text-text-secondary/50">
                  <MapPin className="h-3 w-3" />
                  Pastikan GPS HP aktif saat memfoto
                </div>
              </div>
            </ScrollReveal>
          </div>

          <ScrollReveal delay={300}>
            <div className="mt-10 text-center">
              <Link
                to="/reports/new"
                className="inline-flex items-center gap-2 rounded-xl bg-primary px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-primary/20 transition-all duration-200 hover:bg-primary-light hover:shadow-xl hover:shadow-primary/30 active:scale-[0.97]"
              >
                <ExternalLink className="h-4 w-4" />
                Laporkan Retakan Sekarang
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </ScrollReveal>
        </div>
      </section>

      <SectionDivider variant="fade" />

      {/* ── Galeri Contoh Retakan ── */}
      <section className="bg-surface py-16 sm:py-20 lg:py-24">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10">
          <ScrollReveal>
            <div className="text-center mb-10 sm:mb-14">
              <h2 className="text-2xl sm:text-3xl font-bold text-text-primary">
                Galeri Contoh Retakan
              </h2>
              <p className="text-sm sm:text-base text-text-secondary mt-3 max-w-lg mx-auto">
                Lihat perbandingan visual retakan tanah dari ketiga tingkat bahaya.
              </p>
            </div>
          </ScrollReveal>

          <div className="grid sm:grid-cols-3 gap-4 sm:gap-5">
            {RISK_LEVELS.map((item, i) => (
              <ScrollReveal key={`gallery-${item.title}`} delay={i * 100}>
                <div className="group cursor-pointer rounded-xl overflow-hidden border-2 border-transparent hover:border-primary/20 transition-all duration-300 hover:shadow-lg">
                  <div className="aspect-[4/3] overflow-hidden bg-surface">
                    <img
                      src={item.image}
                      alt={item.imageAlt}
                      className="h-full w-full object-cover transition-all duration-500 group-hover:scale-110"
                      loading="lazy"
                    />
                  </div>
                  <div className={cn('px-4 py-3', item.bg)}>
                    <div className="flex items-center gap-2">
                      <item.icon className={cn('h-4 w-4', item.color)} />
                      <span className={cn('text-sm font-bold', item.color)}>{item.title}</span>
                    </div>
                  </div>
                </div>
              </ScrollReveal>
            ))}
          </div>

          <ScrollReveal delay={300}>
            <p className="text-center text-xs text-text-secondary/50 mt-6">
              Foto di atas adalah placeholder. Ganti dengan foto retakan yang sesuai.
            </p>
          </ScrollReveal>
        </div>
      </section>

      <SectionDivider variant="wave" />

      {/* ── Kontak Darurat ── */}
      <section className="bg-card py-16 sm:py-20 lg:py-24">
        <div className="max-w-3xl mx-auto px-6 sm:px-8 lg:px-10 text-center">
          <ScrollReveal>
            <div className="flex h-16 w-16 mx-auto items-center justify-center rounded-2xl bg-bahaya-bg mb-5">
              <Phone className="h-8 w-8 text-bahaya" />
            </div>
            <h2 className="text-2xl sm:text-3xl font-bold text-text-primary mb-2">Kontak Darurat</h2>
            <p className="text-sm sm:text-base text-text-secondary mb-8 max-w-md mx-auto">
              Jika Anda menemukan retakan dengan status <span className="font-semibold text-bahaya">BAHAYA</span>, segera hubungi:
            </p>
          </ScrollReveal>

          <div className="space-y-3 max-w-lg mx-auto">
            {CONTACTS.map((contact, i) => (
              <ScrollReveal key={contact.label} delay={i * 100}>
                <ContactCard {...contact} />
              </ScrollReveal>
            ))}
          </div>

          <ScrollReveal delay={300}>
            <div className="mt-10 rounded-2xl bg-bahaya-bg border border-bahaya/20 px-6 py-5 inline-block">
              <p className="text-xs sm:text-sm text-bahaya font-semibold flex items-center gap-2">
                <Skull className="h-4 w-4 shrink-0" />
                Jangan panik. Segera evakuasi & hubungi nomor darurat di atas.
              </p>
            </div>
          </ScrollReveal>
        </div>
      </section>

      <SectionDivider variant="slant" />

      {/* ── FAQ ── */}
      <section className="bg-surface py-16 sm:py-20 lg:py-24">
        <div className="max-w-3xl mx-auto px-6 sm:px-8 lg:px-10">
          <ScrollReveal>
            <div className="text-center mb-10">
              <h2 className="text-2xl sm:text-3xl font-bold text-text-primary">
                Pertanyaan Umum
              </h2>
              <p className="text-sm text-text-secondary mt-3">
                Hal-hal yang sering ditanyakan tentang Retak.id
              </p>
            </div>
          </ScrollReveal>

          <div className="space-y-3">
            {FAQS.map((faq, i) => (
              <ScrollReveal key={faq.q} delay={i * 80}>
                <FaqItem {...faq} />
              </ScrollReveal>
            ))}
          </div>

          <ScrollReveal delay={400}>
            <div className="text-center mt-10">
              <p className="text-xs sm:text-sm text-text-secondary">
                Masih punya pertanyaan? Hubungi tim Retak.id melalui halaman{' '}
                <Link to="/about" className="text-primary font-medium hover:underline">
                  Tentang Kami
                </Link>
              </p>
            </div>
          </ScrollReveal>
        </div>
      </section>
    </div>
  );
}
