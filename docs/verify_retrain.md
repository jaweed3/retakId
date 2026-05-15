# Verifikasi Staf BPBD → Retraining Loop

> Counter-attack untuk pertanyaan juri: *"Sistem rentan false negative, tanpa human-in-the-loop, bagaimana verifikasi staf BPBD sekaligus jadi pelabelan data untuk retrain?"*

## 1. Jawaban Inti

**Pipeline sudah diimplementasikan sebagai loop tertutup:**

```
Laporan Masuk → ML Predict → Admin Dashboard
                                   ↓
                          VerificationDialog
                          ├─ "Sesuai?" (BENAR)
                          └─ "Tidak Sesuai" → pilih label benar
                                   ↓
                          riwayat_penanganan (DB)
                          ├─ tindakan = 'diverifikasi'
                          ├─ alasan = 'BENAR' | 'SALAH'
                          └─ detail → {ml_status, label_benar, catatan}
                                   ↓
                          Export CSV (tombol di dashboard)
                          ├─ label_akhir (AMAN/WASPADA/BAHAYA)
                          ├─ foto_url, status_ml, status_verifikasi
                          └─ diverifikasi_oleh, catatan_verifikasi
                                   ↓
                    python ingest_verification.py --csv training.csv
                    ├─ Download foto dari Supabase URL
                    ├─ Validasi integritas gambar
                    ├─ Perceptual hash dedup (phash, threshold=6)
                    └─ Simpan ke backend/data/processed/{label_akhir}/
                                   ↓
                          make split && make train
                                   ↓
                          Model v3b, v3c, ...
```

Setiap prediksi ML yang diverifikasi — baik benar maupun salah — **otomatis menjadi data training**. Tidak ada satupun laporan yang diverifikasi yang terbuang.

## 2. Implementasi Kunci

### 2a. VerificationDialog (`web-app/src/components/VerificationDialog.tsx`)

Admin melihat:
- **Foto laporan** ukuran penuh
- **Info lokasi, pelapor, waktu**
- **Hasil prediksi ML** + confidence bar
- **Dua tombol**: "Sesuai" / "Tidak Sesuai"
- Jika "Tidak Sesuai": **pilih label yang benar** (AMAN / WASPADA / BAHAYA)
- Catatan verifikasi opsional

### 2b. Penyimpanan (`AdminDashboardPage.tsx:109-138`)

```typescript
// Data yang disimpan ke riwayat_penanganan.detail (JSONB)
const detail = {
  ml_status: report.status,          // Prediksi ML asli
  label_verifikasi: verif.label_verifikasi,  // 'BENAR' | 'SALAH'
  label_benar: verif.label_benar,    // Label koreksi (jika SALAH)
  catatan: verif.catatan,            // Catatan admin
};
```

### 2c. Export CSV (`web-app/src/utils/exportTrainingData.ts`)

Fungsi `fetchTrainingData()`:
- Query `riwayat_penanganan WHERE tindakan='diverifikasi'`
- Join dengan `laporan` untuk `foto_url`
- Derive `label_akhir`:
  - Jika `alasan='BENAR'` → `label_akhir = report.status` (prediksi ML sesuai)
  - Jika `alasan='SALAH'` → `label_akhir = detail.label_benar` (koreksi admin)

### 2d. Ingestion Script (`backend/scripts/training/ingest_verification.py`)

```
Python pipeline:
  CSV → Download foto → Validasi (size, dimensi, corrupt check)
       → Perceptual hash dedup → Save ke processed/{label_akhir}/
```

- **Perceptual hash dedup**: phash, threshold hamming distance = 6
- **Validasi**: max 20MB, min 100px dimensi, verifikasi integrity via PIL
- **Format nama**: `training_{laporan_id}_{uuid8}.jpg`

### 2e. Retrain

```bash
make split    # Bagi ulang dataset (train/val/test)
make train    # Fine-tune MobileNetV2 dengan data baru
```

## 3. Data Flow Verification

| Langkah | Siapa | Output | Validasi |
|---------|-------|--------|----------|
| ML predict | Sistem | `status` (AMAN/WASPADA/BAHAYA) | Confidence threshold |
| Admin verif | Staf BPBD | `label_verifikasi` + `label_benar` | Wajib isi jika SALAH |
| Simpan DB | Sistem | `riwayat_penanganan` row | Constraint CHECK |
| Export CSV | Admin (klik) | `training-{date}.csv` | Filter hanya yg diverifikasi |
| Ingest | Python script | File di `processed/{label}/` | Dedup + validasi gambar |
| Split/Train | DVC/Makefile | Model baru | Validation gate |

**Tidak ada single point of data loss** — setiap langkah memiliki validasi, dan data mentah (CSV + foto) tetap ada meskipun proses ingest gagal.

## 4. Counter-Attack: Antisipasi Pertanyaan Juri

### Q: "Bagaimana kalau staf BPBD malas verifikasi?"
**Jawaban:**
- Dashboard menunjukkan **stats real-time**: "12 dari 45 laporan belum diverifikasi"
- Label verifikasi diperlukan untuk **export data training** — tanpa verifikasi, tidak ada retrain
- Admin punya insentif langsung: **model yang lebih akurat = lebih sedikit false positive yang merepotkan**
- Riwayat diverifikasi tercatat dengan `ditangani_oleh` (email) — **accountability penuh**

### Q: "Bagaimana kalau staf salah verifikasi?"
**Jawaban:**
- Setiap verifikasi yang `SALAH` menyimpan *kedua* label (ML asli + koreksi admin)
- `EditReportDialog` memungkinkan koreksi oleh admin lain
- **Perceptual hash dedup** (threshold=6) mencegah duplikasi jika foto yang sama di-verifikasi ulang
- Retrain loop bersifat **asinkron** — tidak ada efek langsung ke produksi sampai model baru lulus `validate-model` gate

### Q: "Berapa banyak data yang dibutuhkan untuk retrain yang efektif?"
**Jawaban:**
- MobileNetV2 fine-tune efektif dengan **20-50 gambar per kelas** untuk transfer learning
- Dengan estimasi 10-20 laporan/hari, target terkumpul dalam **3-7 hari**
- Data diverifikasi adalah **high-quality ground truth** (label divalidasi staf BPBD langsung)
- Model saat ini 84.9% test accuracy dengan dataset awal; penambahan data edge cases (false negative historis) adalah **strategi paling efisien** untuk menutup gap

### Q: "Kenapa tidak otomatis (retrain triggered otomatis dari DB)?"
**Jawaban:**
- **Sengaja desain semi-manual** untuk safety: admin review dulu sebelum retrain
- Model baru harus lewat `validate-model` (BAHAYA recall ≥ 72%, dll.) sebelum deploy
- Admin bisa memilih **kapan** retrain — tidak mengganggu operasional
- Flow: `Export CSV → Review → make split → make train → validate → deploy` — setiap tahap ada human gate

### Q: "Bagaimana dengan false negative yang tidak terdeteksi?" (misal: ML bilang AMAN, tapi sebenarnya BAHAYA, dan laporan tidak pernah diverifikasi)
**Jawaban:**
- **MultiFactorRiskEngine** tetap memberi skor risiko berdasarkan faktor lingkungan (curah hujan, soil type, kepadatan penduduk) — **ML hanya 50% bobot**
- Laporan dengan skor tinggi otomatis **di-flag** di dashboard untuk prioritas verifikasi
- **AMAN score floor = 0.1**: lingkungan tidak pernah diabaikan
- Safety net: laporan yang tidak diverifikasi dalam 7 hari **masuk antrian prioritas** verifikasi

## 5. Self-Correction: Kelemahan yang Diakui

1. **Tidak ada retrain scheduler otomatis** — tergantung admin menjalankan pipeline
   - *Mitigasi:* Dokumentasi runbook di `Makefile`; proses manual ini adalah **safety feature**
2. **Coverage verifikasi tergantung staf BPBD** — laporan yang tidak diverifikasi = data training potensial hilang
   - *Mitigasi:* Flagging otomatis untuk laporan prioritas; dashboard stats untuk monitoring coverage
3. **Label noise** — staf bisa salah verifikasi
   - *Mitigasi:* Audit trail dengan email; edit dialog untuk koreksi; dedup mencegah duplikasi

## 6. Files Referenced

| File | Peran |
|------|-------|
| `web-app/src/components/VerificationDialog.tsx` | Dialog verifikasi dengan foto + koreksi label |
| `web-app/src/pages/AdminDashboardPage.tsx` (lines 104-145, 304) | Open dialog, save verifikasi, export CSV |
| `web-app/src/utils/exportTrainingData.ts` | Fetch verified data, generate CSV dengan label_akhir |
| `backend/scripts/training/ingest_verification.py` | CSV → download foto → dedup → processed/ |
| `backend/supabase/seed.sql` (riwayat_penanganan table) | Schema with tindakan, alasan, detail JSONB |
| `Makefile` | `make split && make train` |
