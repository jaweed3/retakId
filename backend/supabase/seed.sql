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
-- 2. TABEL MODEL VERSIONS (Delta OTA)
-- ============================================================
CREATE TABLE IF NOT EXISTS model_versions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  version TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ DEFAULT now(),
  model_size_bytes BIGINT NOT NULL,
  delta_size_bytes BIGINT DEFAULT NULL,
  delta_path TEXT DEFAULT NULL,
  benchmark_accuracy REAL DEFAULT NULL,
  benchmark_f1 REAL DEFAULT NULL,
  changelog TEXT DEFAULT '',
  is_active BOOLEAN DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_model_versions_active ON model_versions(is_active) WHERE is_active = true;

-- ============================================================
-- 3. TABEL RIWAYAT PENANGANAN (Audit Trail)
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
  data_sebelumnya JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

-- ============================================================
-- 4. RLS POLICIES
-- ============================================================
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE riwayat_penanganan ENABLE ROW LEVEL SECURITY;
ALTER TABLE model_versions ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF to_regclass('public.laporan') IS NOT NULL THEN
    ALTER TABLE laporan ENABLE ROW LEVEL SECURITY;

    DROP POLICY IF EXISTS "Laporan dapat dibaca publik" ON laporan;
    DROP POLICY IF EXISTS "Siapa pun bisa insert laporan" ON laporan;
    DROP POLICY IF EXISTS "Admin dapat update laporan" ON laporan;
    DROP POLICY IF EXISTS "Admin dapat hapus laporan" ON laporan;

    CREATE POLICY "Laporan dapat dibaca publik" ON laporan
      FOR SELECT USING (true);

    CREATE POLICY "Siapa pun bisa insert laporan" ON laporan
      FOR INSERT WITH CHECK (true);

    CREATE POLICY "Admin dapat update laporan" ON laporan
      FOR UPDATE USING (auth.role() = 'authenticated');

    CREATE POLICY "Admin dapat hapus laporan" ON laporan
      FOR DELETE USING (auth.role() = 'authenticated');
  END IF;
END $$;

DROP POLICY IF EXISTS "Admin dapat baca admin_users" ON admin_users;
DROP POLICY IF EXISTS "Admin dapat baca riwayat" ON riwayat_penanganan;
DROP POLICY IF EXISTS "Admin dapat insert riwayat" ON riwayat_penanganan;

CREATE POLICY "Admin dapat baca admin_users" ON admin_users
  FOR SELECT USING (auth.role() = 'authenticated');

CREATE POLICY "Admin pertama bisa daftar" ON admin_users
  FOR INSERT WITH CHECK (
    auth.role() = 'authenticated' AND
    (SELECT count(*) FROM admin_users) = 0
  );

CREATE POLICY "Admin dapat insert admin baru" ON admin_users
  FOR INSERT WITH CHECK (
    auth.role() = 'authenticated' AND
    (SELECT count(*) FROM admin_users) > 0
  );

CREATE POLICY "Admin dapat baca riwayat" ON riwayat_penanganan
  FOR SELECT USING (
    EXISTS (
      SELECT 1
      FROM admin_users au
      WHERE au.user_id = auth.uid()
    )
  );

CREATE POLICY "Admin dapat insert riwayat" ON riwayat_penanganan
  FOR INSERT WITH CHECK (
    EXISTS (
      SELECT 1
      FROM admin_users au
      WHERE au.user_id = auth.uid()
    )
  );

-- ============================================================
-- 5. STORAGE BUCKETS: FOTO + MODEL DELTAS
-- ============================================================

-- Bucket for ML model delta files (public — app downloads delta directly)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'model-deltas',
  'model-deltas',
  true,
  5242880,
  ARRAY['application/octet-stream']
)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Model deltas dapat dibaca publik" ON storage.objects;
DROP POLICY IF EXISTS "Admin dapat upload model delta" ON storage.objects;

CREATE POLICY "Model deltas dapat dibaca publik" ON storage.objects
  FOR SELECT USING (bucket_id = 'model-deltas');

CREATE POLICY "Admin dapat upload model delta" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'model-deltas');

-- ============================================================
-- 6. STORAGE BUCKET FOTO
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
-- 7. REALTIME
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
-- 8. SEED DATA: LAPORAN (12 data contoh area Jenangan)
-- ============================================================
-- 6. TAMBAH KOLOM is_resolved (jika belum ada)
-- ============================================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'laporan' AND column_name = 'is_resolved'
  ) THEN
    ALTER TABLE laporan ADD COLUMN is_resolved BOOLEAN DEFAULT false;
  END IF;
END $$;

-- ============================================================
-- 7. SEED DATA: LAPORAN (12 data contoh seluruh Indonesia)
-- ============================================================
-- Hapus data seeding lama dulu (biar bisa dijalankan ulang)
DELETE FROM laporan WHERE pelapor IN ('Budi Santoso', 'Siti Rahayu', 'Agus Widodo', 'Rudi Hartono', 'Sumarni', 'Joko Prasetyo', 'Rina Marlina', 'Supardi', 'Kepala Desa Padalarang', 'Tukiman', 'Takmir Masjid', 'Tim BPBD');

INSERT INTO laporan (nama_lokasi, status, catatan, latitude, longitude, pelapor, terverifikasi, is_resolved, created_at)
VALUES
  ('Desa Cipanas, Puncak', 'AMAN', 'Retakan kecil di tanah kering, lebar sekitar 0.5cm', -6.720, 107.010, 'Budi Santoso', 2, false, now() - interval '5 days'),
  ('Desa Sembalun, Lombok', 'WASPADA', 'Retakan mulai melebar setelah hujan deras, lebar sekitar 2cm', -8.350, 116.530, 'Siti Rahayu', 1, true, now() - interval '3 days'),
  ('Kecamatan Cisolok, Sukabumi', 'BAHAYA', 'Retakan besar > 5cm, ada rembesan air, tanah terlihat bergerak', -6.950, 106.450, 'Agus Widodo', 3, false, now() - interval '1 day'),
  ('Desa Kemuning, Karanganyar', 'AMAN', 'Retakan permukaan saja, tidak ada tanda bahaya', -7.650, 111.080, 'Rudi Hartono', 1, true, now() - interval '7 days'),
  ('Lereng Gunung Merapi, Sleman', 'WASPADA', 'Retakan bertambah panjang dari 1m jadi 3m dalam seminggu', -7.550, 110.430, 'Sumarni', 0, false, now() - interval '2 days'),
  ('Desa Sumberejo, Batu', 'BAHAYA', 'Tanah ambles 10cm, retakan melebar cepat, pohon mulai miring', -7.870, 112.530, 'Joko Prasetyo', 2, false, now() - interval '12 hours'),
  ('Kampung Naga, Tasikmalaya', 'AMAN', 'Retakan kecil tidak berbahaya, sudah dicek mandiri', -7.350, 108.100, 'Rina Marlina', 0, true, now() - interval '10 days'),
  ('Desa Tomohon, Sulawesi Utara', 'WASPADA', 'Retakan muncul setelah gempa kecil, perlu dipantau', 1.320, 124.840, 'Supardi', 1, false, now() - interval '4 days'),
  ('Desa Padalarang, Bandung Barat', 'BAHAYA', 'Retakan mendekati rumah warga, 3 rumah sudah dievakuasi', -6.840, 107.480, 'Kepala Desa Padalarang', 5, false, now() - interval '6 hours'),
  ('Desa Ubud, Gianyar', 'AMAN', 'Retakan kecil di aspal jalan, tidak membahayakan', -8.510, 115.260, 'Tukiman', 0, true, now() - interval '14 days'),
  ('Kecamatan Meureudu, Pidie Jaya', 'WASPADA', 'Retakan muncul setelah gempa kecil kemarin, warga diminta waspada', 5.250, 96.250, 'Takmir Masjid', 2, false, now() - interval '1 day'),
  ('Desa Pacet, Mojokerto', 'BAHAYA', 'Longsor kecil sudah terjadi, retakan besar masih aktif bergerak', -7.670, 112.540, 'Tim BPBD', 4, false, now() - interval '2 hours')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 9. SEED DATA: RIWAYAT PENANGANAN (contoh audit trail)
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
-- STEP 3: BUAT AKUN ADMIN (via Supabase Dashboard)
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
