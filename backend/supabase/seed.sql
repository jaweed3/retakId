-- ============================================================
-- Retak.id — Database Seeding Lengkap
--
-- Urutan jalankan:
-- STEP 1: Jalankan script ini di Supabase SQL Editor
-- STEP 2: Buka Authentication > Users > Add User untuk buat akun admin
-- STEP 3: Jalankan query INSERT admin_users (ada di akhir script)
-- ============================================================

-- ============================================================
-- 1. TABEL ADMIN
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_users (
  user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  nama TEXT NOT NULL,
  role TEXT DEFAULT 'admin',
  created_at TIMESTAMPTZ DEFAULT now()
);

-- ============================================================
-- 2. TABEL RIWAYAT PENANGANAN (Audit Trail)
-- ============================================================
CREATE TABLE IF NOT EXISTS riwayat_penanganan (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  laporan_id UUID,
  nama_lokasi TEXT NOT NULL,
  status TEXT NOT NULL,
  ditangani_oleh TEXT NOT NULL,
  tindakan TEXT NOT NULL CHECK (tindakan IN ('diverifikasi', 'diedit', 'dihapus')),
  alasan TEXT,
  detail JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- ============================================================
-- 3. RLS POLICIES
-- ============================================================
ALTER TABLE laporan ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE riwayat_penanganan ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Laporan dapat dibaca publik" ON laporan;
DROP POLICY IF EXISTS "Siapa pun bisa insert laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat update laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat hapus laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat baca admin_users" ON admin_users;
DROP POLICY IF EXISTS "Admin dapat baca riwayat" ON riwayat_penanganan;
DROP POLICY IF EXISTS "Admin dapat insert riwayat" ON riwayat_penanganan;

CREATE POLICY "Laporan dapat dibaca publik" ON laporan
  FOR SELECT USING (true);

CREATE POLICY "Siapa pun bisa insert laporan" ON laporan
  FOR INSERT WITH CHECK (true);

CREATE POLICY "Admin dapat update laporan" ON laporan
  FOR UPDATE USING (auth.role() = 'authenticated');

CREATE POLICY "Admin dapat hapus laporan" ON laporan
  FOR DELETE USING (auth.role() = 'authenticated');

CREATE POLICY "Admin dapat baca admin_users" ON admin_users
  FOR SELECT USING (auth.role() = 'authenticated');

CREATE POLICY "Admin dapat baca riwayat" ON riwayat_penanganan
  FOR SELECT USING (auth.role() = 'authenticated');

CREATE POLICY "Admin dapat insert riwayat" ON riwayat_penanganan
  FOR INSERT WITH CHECK (auth.role() = 'authenticated');

-- ============================================================
-- 4. STORAGE BUCKET FOTO
-- ============================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'laporan-foto',
  'laporan-foto',
  true,
  10485760,
  ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Foto dapat dibaca publik" ON storage.objects;
DROP POLICY IF EXISTS "User dapat upload foto" ON storage.objects;

CREATE POLICY "Foto dapat dibaca publik" ON storage.objects
  FOR SELECT USING (bucket_id = 'laporan-foto');

CREATE POLICY "User dapat upload foto" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'laporan-foto');

-- ============================================================
-- 5. REALTIME
-- ============================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'laporan'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE laporan;
  END IF;
END $$;

-- ============================================================
-- 6. SEED DATA: LAPORAN (12 data contoh area Jenangan)
-- ============================================================
INSERT INTO laporan (nama_lokasi, status, catatan, latitude, longitude, pelapor, terverifikasi, created_at)
VALUES
  ('Desa Jenangan, dekat balai desa', 'AMAN', 'Retakan kecil di tanah kering, lebar sekitar 0.5cm', -7.872, 111.467, 'Budi Santoso', 2, now() - interval '5 days'),
  ('Dusun Krajan, RT 03', 'WASPADA', 'Retakan mulai melebar setelah hujan kemarin, lebar sekitar 2cm', -7.880, 111.474, 'Siti Rahayu', 1, now() - interval '3 days'),
  ('Perbukitan Ngebel', 'BAHAYA', 'Retakan besar > 5cm, ada rembesan air, tanah terlihat bergerak', -7.875, 111.465, 'Agus Widodo', 3, now() - interval '1 day'),
  ('Desa Setono, dekat sungai', 'AMAN', 'Retakan permukaan saja, tidak ada tanda bahaya', -7.869, 111.472, 'Rudi Hartono', 1, now() - interval '7 days'),
  ('Desa Pintu, lereng bukit', 'WASPADA', 'Retakan bertambah panjang dari 1m jadi 3m dalam seminggu', -7.878, 111.478, 'Sumarni', 0, now() - interval '2 days'),
  ('Dusun Paringan, area persawahan', 'BAHAYA', 'Tanah ambles 10cm, retakan melebar cepat, pohon mulai miring', -7.882, 111.469, 'Joko Prasetyo', 2, now() - interval '12 hours'),
  ('Desa Jenangan, perbatasan desa', 'AMAN', 'Retakan kecil tidak berbahaya, sudah dicek mandiri', -7.871, 111.470, 'Rina Marlina', 0, now() - interval '10 days'),
  ('Bukit Gamping, area tambang', 'WASPADA', 'Ada retakan baru setelah ledakan tambang, perlu dipantau', -7.876, 111.480, 'Supardi', 1, now() - interval '4 days'),
  ('Desa Ngebel, dekat pemukiman', 'BAHAYA', 'Retakan mendekati rumah warga, 3 rumah sudah dievakuasi', -7.883, 111.473, 'Kepala Desa Ngebel', 5, now() - interval '6 hours'),
  ('Desa Setono, jalan desa', 'AMAN', 'Retakan kecil di aspal jalan, tidak membahayakan', -7.868, 111.475, 'Tukiman', 0, now() - interval '14 days'),
  ('Dusun Krajan, belakang masjid', 'WASPADA', 'Retakan muncul setelah gempa kecil kemarin, warga diminta waspada', -7.879, 111.471, 'Takmir Masjid', 2, now() - interval '1 day'),
  ('Perbukitan Jenangan, lereng atas', 'BAHAYA', 'Longsor kecil sudah terjadi, retakan besar masih aktif bergerak', -7.874, 111.462, 'Tim BPBD', 4, now() - interval '2 hours')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 7. SEED DATA: RIWAYAT PENANGANAN (contoh audit trail)
-- ============================================================
INSERT INTO riwayat_penanganan (laporan_id, nama_lokasi, status, ditangani_oleh, tindakan, alasan, created_at)
SELECT
  id, nama_lokasi, status, 'Admin BPBD', 'diverifikasi',
  'Laporan valid, retakan terkonfirmasi di lapangan',
  created_at + interval '1 hour'
FROM laporan
WHERE terverifikasi > 0
ON CONFLICT DO NOTHING;

-- ============================================================
-- STEP 2: BUAT AKUN ADMIN (via Supabase Dashboard)
-- ============================================================
-- Buka: Authentication > Users > Add User
-- Isi:
--   Email: admin@retak.id
--   Password: (password kamu)
--   Centang: "Auto Confirm User"
--   Klik: Create User
--
-- Setelah user terbuat, lihat UUID user di tabel users,
-- lalu jalankan query ini (ganti UUID dengan yang asli):
--
-- INSERT INTO admin_users (user_id, nama)
-- VALUES ('<UUID-DARI-AUTH-USER>', 'Admin BPBD');
--
-- Atau jalankan otomatis (ambil dari email):
-- INSERT INTO admin_users (user_id, nama)
-- SELECT id, 'Admin BPBD' FROM auth.users WHERE email = 'admin@retak.id';
-- ============================================================
