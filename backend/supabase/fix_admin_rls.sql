-- JALANKAN INI DULU DI SQL EDITOR UNTUK PERBAIKI LOGIN
DROP POLICY IF EXISTS "Admin dapat baca admin_users" ON admin_users;
DROP POLICY IF EXISTS "Admin pertama bisa daftar" ON admin_users;
DROP POLICY IF EXISTS "Admin dapat insert admin baru" ON admin_users;

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
