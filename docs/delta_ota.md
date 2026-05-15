# Delta OTA — Model Update over the Air

> **Status:** ✅ End-to-end diverifikasi
> **Delta terakhir:** `delta_v3a_to_v3b.rkd` — 48 KB (98.2% lebih kecil dari full model 2.6 MB)

---

## Kenapa Delta OTA?

Model ML Retak.id (2.6 MB) perlu di-update berkala ketika ada training baru. Kalo 10.000 user masing-masing download 2.6 MB setiap rilis = **26 GB bandwidth per rilis**. Di daerah dengan koneksi 3G/4G lemot, ini berat banget.

**Solusi:** Delta OTA — cuma kirim byte yang **benar-benar berubah** antar versi model.

---

## Cara Kerja

```
Training → export TFLite
         ↓
compute_delta.py ── bandingkan byte model lama vs baru
         │            scan byte-by-byte → cari region berbeda
         │            build .rkd payload → gzip compress level 9
         ↓
.rkd file (48 KB — 98% lebih kecil)
         ↓
deploy_delta.py ── upload ke Supabase Storage bucket "model-deltas"
         │           register versi di tabel model_versions
         ↓
HP warga ── ModelUpdateChecker.checkForUpdate() tiap buka app
         │      POST ke edge function check-model-update
         │      response: { delta_url, full_url, latest_version }
         │
         ├── (A) DeltaModelLoader.applyDelta()
         │      download .rkd → GZIPInputStream decompress
         │      parse header (RKD1 magic + regions)
         │      load bundled model dari assets APK
         │      apply byte patches (in-memory copyInto)
         │      validasi pake TFLite Interpreter
         │      simpan ke internal storage
         │
         └── (B) Fallback: download full model
                kalo delta gagal / ga ada / korup
```

---

## Format .rkd (Retak Delta)

```
┌─────────────────────────────────────┐
│  Magic: "RKD1" (4 bytes)            │
├─────────────────────────────────────┤
│  Number of regions: uint32 LE       │
├─────────────────────────────────────┤
│  Region 0:                          │
│    ├─ offset: uint32 LE             │
│    ├─ length: uint32 LE             │
│    └─ data: raw bytes (length)      │
├─────────────────────────────────────┤
│  Region 1: ...                      │
├─────────────────────────────────────┤
│  ...                                │
└─────────────────────────────────────┘
       ↓ gzip.compress(level=9)
┌─────────────────────────────────────┐
│  .rkd file (gzip compressed)        │
└─────────────────────────────────────┘
```

---

## Hasil End-to-End (Real Run)

### Setup

| Item | File | Ukuran |
|------|------|--------|
| Old model (v3a) | `backend/models/retak_mobilenetv2_v3a.tflite` | 2,710,280 bytes |
| New model (v3b) | `backend/models/retak_mobilenetv2_v3b.tflite` | 2,710,280 bytes |
| Perubahan | Fine-tune simulated: 0.6% bytes di tail region berubah | 16,195 bytes |

### Delta Computation

| Metric | Value | Catatan |
|--------|-------|---------|
| Changed regions | **15,706** | Region kontigu yang berbeda byte-by-byte |
| Total changed | **16,195 bytes** (0.6%) | Cuma layer akhir yang berubah |
| Raw delta size | **141,851 bytes** | Sebelum kompresi |
| Compressed delta | **48,451 bytes** | Gzip level 9 |
| Compression ratio | **65.8%** | Gzip pada payload delta |
| **Savings vs full** | **98.2%** | 2.6 MB → 47 KB |
| **Gates** | ✅ Lolos (savings >50%) | |

### Delta Application (Mobile)

Delta.apply(patched) == new  →  **✅ Bit-exact valid**

Delta dibuka pake `GZIPInputStream`, region bytes di-copy ke array model lama, hasilnya dicek pake `TFLite Interpreter` — kalo lolos, model disimpan ke internal storage. Kalo gagal di tahap mana pun → fallback download full model.

---

## Cara Pakai

### 1. Generate Delta (Local)

```bash
uv run python backend/scripts/training/compute_delta.py \
  --old backend/models/retak_mobilenetv2_v3a.tflite \
  --new backend/models/retak_mobilenetv2_v3b.tflite \
  --out backend/models/delta/delta_v3a_to_v3b.rkd
```

Dry-run (stats doang, ga nulis file):

```bash
uv run python backend/scripts/training/compute_delta.py \
  --old old.tflite --new new.tflite --dry-run
```

### 2. Deploy (Compute + Upload + Register)

```bash
SUPABASE_URL=https://xxx.supabase.co \
SUPABASE_SERVICE_KEY=xxx \
uv run python backend/scripts/deploy_delta.py \
  --old backend/models/retak_mobilenetv2_v3a.tflite \
  --new backend/models/retak_mobilenetv2_v3b.tflite \
  --version v3b \
  --changelog "Improved WASPADA recall by 5%"
```

### 3. Setup Supabase (Sekali Aja)

Jalankan SQL di `backend/supabase/seed.sql` — bagian `model_versions` table + `model-deltas` bucket:

```sql
-- Tabel
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

-- Storage bucket
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('model-deltas', 'model-deltas', true, 5242880, ARRAY['application/octet-stream'])
ON CONFLICT (id) DO NOTHING;
```

### 4. Deploy Edge Function

```bash
supabase functions deploy check-model-update
```

---

## Safety Gates

| Gate | Mechanism | Jika Gagal |
|------|-----------|------------|
| Size check | `len(old) != len(new)` → skip delta | Full model download |
| Savings gate | Delta <50% lebih kecil dari full | Full model download |
| Magic check | Verifikasi "RKD1" di header | Delta discarded |
| Bounds check | `offset + length <= model_size` | Delta discarded |
| TFLite validation | `Interpreter(patched_bytes)` — kalo throw | Delta discarded |
| Fallback | Full model dari Supabase Storage | User tetep dapet update |

---

## File Penting

| File | Role |
|------|------|
| `backend/scripts/training/compute_delta.py` | Algoritma delta — scan byte, build .rkd, gzip |
| `backend/scripts/deploy_delta.py` | Pipeline: compute → upload Supabase → register version |
| `backend/edge-functions/check-model-update/index.ts` | Edge function: cek versi → balikin delta_url / full_url |
| `backend/models/delta/delta_v3a_to_v3b.rkd` | Delta file hasil generate (48 KB) |
| `backend/supabase/seed.sql` | Schema: `model_versions` table + `model-deltas` bucket |
| `mobile-app/.../ml/DeltaModelLoader.kt` | Client-side: download, decompress, patch, validate |
| `mobile-app/.../ml/ModelUpdateChecker.kt` | Client-side: cek update via edge function |
| `backend/models/model_card.md` | Model card dengan version history |

---

## Catatan

- **Ukuran delta real** tergantung seberapa banyak weight berubah antar versi. Fine-tune layer akhir → delta kecil (0.3–0.8 MB). Arsitektur berubah → delta skip, fallback full model.
- Delta cuma berguna kalo `savings > 50%`. Kalo ternyata model baru terlalu berbeda (misal arsitektur gede), lebih murah download full.
- File `.rkd` adalah GZIP compressed — mobile app pake `GZIPInputStream` buat dekompresi.
- Safety: semua patching dilakukan **di memori**, kalo gagal di stage mana pun, file model asli ga kena.
