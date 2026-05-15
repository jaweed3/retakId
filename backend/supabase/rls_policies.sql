-- ============================================================
-- Retak.id — RLS (Row Level Security) Policies
-- Jalankan di Supabase SQL Editor
-- ============================================================

-- ============================================================
-- 1. ENABLE RLS
-- ============================================================
ALTER TABLE laporan ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE riwayat_penanganan ENABLE ROW LEVEL SECURITY;
ALTER TABLE model_versions ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 2. HAPUS POLICY LAMA (supaya script bisa dijalankan ulang)
-- ============================================================
DROP POLICY IF EXISTS "Laporan dapat dibaca publik" ON laporan;
DROP POLICY IF EXISTS "Siapa pun bisa insert laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat update laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat hapus laporan" ON laporan;
DROP POLICY IF EXISTS "Admin dapat baca admin_users" ON admin_users;
DROP POLICY IF EXISTS "Admin dapat baca riwayat" ON riwayat_penanganan;
DROP POLICY IF EXISTS "Admin dapat insert riwayat" ON riwayat_penanganan;
DROP POLICY IF EXISTS "Anyone can read model_versions" ON model_versions;
DROP POLICY IF EXISTS "Admin dapat insert model_versions" ON model_versions;
DROP POLICY IF EXISTS "Admin dapat update model_versions" ON model_versions;

-- ============================================================
-- 3. POLICIES: TABEL LAPORAN
-- ============================================================

-- Semua orang (termasuk tanpa login) bisa melihat laporan
CREATE POLICY "Laporan dapat dibaca publik" ON laporan
  FOR SELECT
  USING (true);

-- Siapa pun bisa insert laporan baru (dari Android atau web form)
CREATE POLICY "Siapa pun bisa insert laporan" ON laporan
  FOR INSERT
  WITH CHECK (true);

-- Hanya user yang sudah login (admin) yang bisa update laporan
CREATE POLICY "Admin dapat update laporan" ON laporan
  FOR UPDATE
  USING (auth.role() = 'authenticated');

-- Hanya user yang sudah login (admin) yang bisa hapus laporan
CREATE POLICY "Admin dapat hapus laporan" ON laporan
  FOR DELETE
  USING (auth.role() = 'authenticated');

-- ============================================================
-- 4. POLICIES: TABEL ADMIN_USERS
-- ============================================================

-- Hanya authenticated user yang bisa lihat daftar admin
CREATE POLICY "Admin dapat baca admin_users" ON admin_users
  FOR SELECT
  USING (auth.role() = 'authenticated');

-- ============================================================
-- 5. POLICIES: TABEL RIWAYAT PENANGANAN
-- ============================================================

CREATE POLICY "Admin dapat baca riwayat" ON riwayat_penanganan
  FOR SELECT
  USING (auth.role() = 'authenticated');

CREATE POLICY "Admin dapat insert riwayat" ON riwayat_penanganan
  FOR INSERT
  WITH CHECK (auth.role() = 'authenticated');

-- ============================================================
-- 6. POLICIES: STORAGE
-- ============================================================
DROP POLICY IF EXISTS "Foto dapat dibaca publik" ON storage.objects;
DROP POLICY IF EXISTS "User dapat upload foto" ON storage.objects;

CREATE POLICY "Foto dapat dibaca publik" ON storage.objects
  FOR SELECT
  USING (bucket_id = 'laporan-foto');

CREATE POLICY "User dapat upload foto" ON storage.objects
  FOR INSERT
  WITH CHECK (bucket_id = 'laporan-foto');

-- ============================================================
-- 7. POLICIES: TABEL MODEL VERSIONS
-- ============================================================

CREATE POLICY "Anyone can read model_versions" ON model_versions
  FOR SELECT USING (true);

CREATE POLICY "Admin dapat insert model_versions" ON model_versions
  FOR INSERT WITH CHECK (
    EXISTS (
      SELECT 1
      FROM admin_users au
      WHERE au.user_id = auth.uid()
    )
  );

CREATE POLICY "Admin dapat update model_versions" ON model_versions
  FOR UPDATE USING (
    EXISTS (
      SELECT 1
      FROM admin_users au
      WHERE au.user_id = auth.uid()
    )
  );
