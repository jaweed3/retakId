import { useState } from 'react';
import { ShieldCheck, AlertTriangle, Skull, Phone, ChevronDown, ChevronUp, Smartphone, Wifi, WifiOff } from 'lucide-react';
import { cn } from '../utils/cn';

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

function FaqItem({ q, a }: { q: string; a: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded-xl bg-card border border-gray-300 dark:border-gray-600 overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-5 py-4 text-left text-sm font-medium text-text-primary hover:bg-divider/10 transition-colors"
      >
        {q}
        {open ? <ChevronUp className="h-4 w-4 text-text-secondary shrink-0" /> : <ChevronDown className="h-4 w-4 text-text-secondary shrink-0" />}
      </button>
      {open && (
        <div className="px-5 pb-4 text-sm text-text-secondary leading-relaxed border-t border-gray-300 dark:border-gray-600 pt-3">
          {a}
        </div>
      )}
    </div>
  );
}

import { SEOMeta } from '../components/SEOMeta';

export function EdukasiPage() {
  return (
    <div>
      <SEOMeta title="Edukasi Bencana" description="Kenali retakan tanah: tingkat bahaya AMAN, WASPADA, BAHAYA. Cara melapor, kontak darurat BPBD Ponorogo, dan FAQ." />
      {/* Hero */}
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-14 sm:py-20">
          <div className="text-center max-w-2xl mx-auto">
            <span className="text-xs font-semibold text-primary uppercase tracking-widest">Edukasi</span>
            <h1 className="text-2xl sm:text-3xl font-bold text-text-primary mt-2 mb-3">
              Kenali Retakan Tanah, Cegah Longsor
            </h1>
            <p className="text-sm text-text-secondary leading-relaxed">
              Informasi tentang retakan tanah, tingkat bahaya, dan apa yang harus dilakukan.
              Disusun untuk warga Jenangan, Ponorogo.
            </p>
          </div>
        </div>
      </section>

      {/* Tingkat Bahaya */}
      <section className="border-b border-divider">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-14 sm:py-20">
          <h2 className="text-xl sm:text-2xl font-bold text-text-primary text-center mb-8 sm:mb-10">
            Tiga Tingkat Bahaya Retakan Tanah
          </h2>
          <div className="grid sm:grid-cols-3 gap-5 lg:gap-6">
            {[
              {
                icon: ShieldCheck, title: 'AMAN', color: 'text-aman', bg: 'bg-aman-bg', border: 'border-aman/30',
                desc: 'Retakan minor akibat penyusutan alami tanah. Lebar retakan < 1 cm, tidak bertambah lebar, tidak ada rembesan air.',
                action: 'Tidak perlu tindakan khusus. Amati secara berkala setiap minggu.',
              },
              {
                icon: AlertTriangle, title: 'WASPADA', color: 'text-waspada', bg: 'bg-waspada-bg', border: 'border-waspada/30',
                desc: 'Retakan signifikan, lebar 1-5 cm. Bertambah lebar dalam beberapa hari. Mungkin ada rembesan air kecil.',
                action: 'Laporkan ke ketua RT/RW. Pantau setiap hari. Siapkan rencana evakuasi keluarga.',
              },
              {
                icon: Skull, title: 'BAHAYA', color: 'text-bahaya', bg: 'bg-bahaya-bg', border: 'border-bahaya/30',
                desc: 'Retakan kritis, lebar > 5 cm. Bertambah lebar dengan cepat. Ada suara gemuruh atau rembesan air deras.',
                action: 'SEGERA EVAKUASI! Hubungi BPBD Ponorogo. Jauhi area retakan minimal 100 meter.',
              },
            ].map((item) => (
              <div key={item.title} className={cn('rounded-2xl border p-5 sm:p-6', item.bg, item.border)}>
                <div className={cn('flex h-10 w-10 items-center justify-center rounded-xl mb-3', item.bg)}>
                  <item.icon className={cn('h-5 w-5', item.color)} />
                </div>
                <h3 className={cn('text-lg font-bold mb-2', item.color)}>{item.title}</h3>
                <p className="text-xs text-text-secondary leading-relaxed mb-3">{item.desc}</p>
                <div className="rounded-lg bg-card/60 border border-divider px-3 py-2.5">
                  <p className="text-[11px] font-semibold text-text-primary">Tindakan:</p>
                  <p className="text-[11px] text-text-secondary leading-relaxed mt-0.5">{item.action}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Cara Melapor */}
      <section className="bg-card">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-14 sm:py-20">
          <h2 className="text-xl sm:text-2xl font-bold text-text-primary text-center mb-8">
            Cara Melaporkan Retakan
          </h2>
          <div className="grid sm:grid-cols-2 gap-8 sm:gap-10 max-w-3xl mx-auto">
            <div className="rounded-2xl bg-surface border border-divider p-5 sm:p-6 text-center">
              <div className="flex h-12 w-12 mx-auto items-center justify-center rounded-xl bg-primary-surface mb-4">
                <Smartphone className="h-6 w-6 text-primary" />
              </div>
              <h3 className="text-sm font-bold text-text-primary mb-2">Via Aplikasi Android</h3>
              <ol className="text-xs text-text-secondary text-left space-y-1.5">
                <li>1. Download &amp; install APK Retak.id</li>
                <li>2. Buka aplikasi &amp; izinkan akses kamera</li>
                <li>3. Arahkan kamera ke retakan tanah</li>
                <li>4. Tekan tombol foto — AI akan deteksi otomatis</li>
                <li>5. Isi catatan (opsional) &amp; kirim laporan</li>
              </ol>
              <div className="mt-4 flex items-center justify-center gap-3 text-[11px] text-text-secondary/60">
                <span className="flex items-center gap-1"><WifiOff className="h-3 w-3" /> Deteksi offline</span>
                <span className="flex items-center gap-1"><Wifi className="h-3 w-3" /> Kirim online</span>
              </div>
            </div>
            <div className="rounded-2xl bg-surface border border-divider p-5 sm:p-6 text-center">
              <div className="flex h-12 w-12 mx-auto items-center justify-center rounded-xl bg-primary-surface mb-4">
                <Smartphone className="h-6 w-6 text-primary" />
              </div>
              <h3 className="text-sm font-bold text-text-primary mb-2">Via Website (Form)</h3>
              <ol className="text-xs text-text-secondary text-left space-y-1.5">
                <li>1. Buka halaman Laporan di web ini</li>
                <li>2. Klik tombol &quot;Laporkan Retakan&quot;</li>
                <li>3. Upload foto retakan (JPG/PNG)</li>
                <li>4. Lokasi diambil dari GPS foto (EXIF)</li>
                <li>5. Isi form &amp; kirim laporan</li>
              </ol>
              <p className="text-[10px] text-text-secondary/50 mt-3">
                Pastikan GPS HP aktif saat memfoto.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Kontak Darurat */}
      <section className="border-b border-divider">
        <div className="max-w-5xl mx-auto px-6 sm:px-8 lg:px-10 py-14 sm:py-20 text-center">
          <div className="max-w-lg mx-auto">
            <div className="flex h-14 w-14 mx-auto items-center justify-center rounded-2xl bg-bahaya-bg mb-4">
              <Phone className="h-7 w-7 text-bahaya" />
            </div>
            <h2 className="text-xl sm:text-2xl font-bold text-text-primary mb-2">Kontak Darurat</h2>
            <p className="text-sm text-text-secondary mb-6">
              Jika Anda menemukan retakan dengan status BAHAYA, segera hubungi:
            </p>
            <div className="space-y-3">
              <ContactCard label="BPBD Kabupaten Ponorogo" phone="(0352) XXX-XXXX" />
              <ContactCard label="Call Center BNPB" phone="117" />
              <ContactCard label="Polres Ponorogo" phone="110" />
            </div>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="bg-card">
        <div className="max-w-3xl mx-auto px-6 sm:px-8 lg:px-10 py-14 sm:py-20">
          <h2 className="text-xl sm:text-2xl font-bold text-text-primary text-center mb-8">
            Pertanyaan Umum
          </h2>
          <div className="space-y-3">
            {FAQS.map((faq) => (
              <FaqItem key={faq.q} {...faq} />
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

function ContactCard({ label, phone }: { label: string; phone: string }) {
  return (
    <div className="rounded-xl bg-card border border-divider px-5 py-3.5 flex items-center justify-between">
      <span className="text-sm font-medium text-text-primary">{label}</span>
      <span className="text-sm font-bold text-bahaya tabular-nums">{phone}</span>
    </div>
  );
}
