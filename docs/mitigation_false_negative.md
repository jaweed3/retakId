# Mitigasi False Negative: AI Memprediksi AMAN saat Kondisi BAHAYA

> **Skenario kritis**: Model ML mengklasifikasikan foto retakan tanah sebagai "AMAN" (aman/tidak berbahaya), padahal kondisi sebenarnya adalah "BAHAYA" (longsor aktif/ancaman tinggi).
>
> False Negative BAHAYA adalah kegagalan paling berbahaya dalam sistem Retak.id — membuat warga merasa aman padahal ancaman nyata.

## Arsitektur Pertahanan Berlapis (Defense-in-Depth)

Retak.id menggunakan **5 lapis pertahanan** — tidak ada single point of failure. Setiap lapis independen; jika satu lapis gagal, lapis berikutnya tetap menangkap false negative.

---

## LAPIS 1 — Kualitas Dataset & Training

### 1.1 Class Balancing

Dataset ditingkatkan bobotnya untuk kelas minoritas (AMAN: 400 gambar vs BAHAYA: 1798 gambar vs WASPADA: 1630 gambar).

```yaml
# backend/config/training.yaml
training:
  class_weight: "balanced"  # auto upweight minoritas
```

Selisih rasio 4.5:1 (BAHAYA:AMAN) — `compute_class_weights()` otomatis mendeteksi jika rasio >= 2:1 dan mengaktifkan balanced weighting (`train.py:191-215`). Eksperimen eksplisit juga tersedia dengan WASPADA di-3×:

```yaml
# backend/config/experiments/v3c_waspada_boost.yaml
training:
  class_weight:
    AMAN: 1.0
    WASPADA: 3.0    # WASPADA berbobot 3×
    BAHAYA: 1.0
```

### 1.2 Validasi Dataset

Setiap gambar di `backend/data/processed/` melewati:
- **Integritas file**: Pillow `img.verify()` — tolak corrupt/truncated (`validate_dataset.py:77`)
- **Dimensi minimum**: ≥ 100 px di kedua sumbu — tolak gambar terlalu kecil/pecah (`validate_dataset.py:82`)
- **Ukuran file**: ≤ 20 MB — tolak gambar anomali (`validate_dataset.py:20`)
- **Hapus otomatis**: `--delete-invalid` flag untuk cleanup (`validate_dataset.py:146`)

### 1.3 Deduplikasi Antar-Kelas (Cegah Data Leakage)

Deduplikasi menggunakan **perceptual hashing** (`imagehash.phash`) untuk mendeteksi gambar identik/near-identical di kelas berbeda. Jika gambar yang sama muncul di AMAN dan BAHAYA, model akan bingung — false negative risk tinggi.

```python
# backend/scripts/processing/deduplicate.py:33
HAMMING_THRESHOLD = 6  # default, strict
```
- Mendeteksi duplikat antar-kelas saja (AMAN↔BAHAYA, AMAN↔WASPADA, dll)
- Opsi `--delete` untuk auto-hapus dari kelas dengan jumlah lebih besar
- Built-in di `make lab-train` pipeline

### 1.4 Stratified Split

Dataset dibagi 70/15/15 (train/val/test) stratified — proporsi kelas terjaga di setiap split (`split_dataset.py`). Seed tetap (`seed=42` di `training.yaml:14`) → reproducible. Mencegah model overfit ke distribusi tertentu.

### 1.5 Augmentasi Berat

Model melihat variasi ekstrem selama training — mengurangi overfitting:

```yaml
# backend/config/training.yaml
augmentation:
  rotation_range: 45.0     # ±45° rotasi
  translation_range: 0.25   # ±25% geser
  zoom_range: 0.3          # ±30% zoom
  brightness_range: [0.5, 1.5]  # dari gelap ke terang
  contrast_range: [0.5, 1.5]
  random_flip: { horizontal: true, vertical: true }
```

---

## LAPIS 2 — Validasi Model Sebelum Deploy

Setiap model harus melewati **6 gerbang keamanan** sebelum menyentuh HP warga. Jika satu gagal → **jangan deploy**.

### 2.1 Benchmark Thresholds

```yaml
# backend/config/benchmark.yaml
thresholds:
  per_class_recall:
    AMAN: 0.75        # OK kalo false positive
    WASPADA: 0.60     # OK kalo terlewat
    BAHAYA: 0.72      # ⛔ KRITIS: jangan sampai miss! (tertinggi)
  per_class_precision:
    BAHAYA: 0.70      # Jangan terlalu sering teriak "BAHAYA" palsu
  test_accuracy: 0.82
  macro_f1: 0.75
  int8_agreement: 0.90
  cv_max_std: 0.03    # std akurasi antar fold ≤ 3%
```

BAHAYA recall **0.72** adalah threshold recall tertinggi di antara semua kelas — lebih tinggi dari WASPADA (0.60). Filosofi: "BAHAYA recall > accuracy. Missing a danger crack is FATAL." (`benchmark.yaml:17`).

### 2.2 Cross-Validation

5-fold stratified CV → mean accuracy ± std. Jika std > 3% → model inconsistent → **jangan deploy** (`cross_validate.py:195`).

### 2.3 Pre-Deployment Validation (7 Checks)

```python
# scripts/validate_model.py
# Exit code 1 = DO NOT DEPLOY
check_1: Model loads (tidak corrupt)
check_2: Input shape = [1, 224, 224, 3] (sesuai kontrak Android)
check_3: Input dtype = uint8 (sesuai format byte buffer)
check_4: Output shape = [1, 3]
check_5: Inference berjalan tanpa crash
check_6: Model memprediksi ≥ 2 kelas berbeda (cek frozen model)
check_7: Confidence max > 0.45 DAN std > 0.02 (cek flat prediction)
```

Check 6 dan 7 menangkap **frozen model** — kondisi dimana model hanya memprediksi 1 kelas (selalu "AMAN") karena preprocessing mismatch atau bug export.

### 2.4 FP32 vs INT8 Agreement

Setelah TFLite export, FP32 (Keras) vs INT8 (TFLite) dibandingkan:
```python
# backend/src/training/export.py:148
agreement = np.mean(keras_preds == tflite_preds)
# Jika agreement < 90% → warning
```

Quantization error yang berlebihan bisa mengubah prediksi BAHAYA jadi AMAN. Threshold 90% memastikan INT8 setia pada model asli.

### 2.5 Model Registration Gating

Model hanya dipromosikan ke Production jika:
1. **Semua benchmark thresholds terpenuhi** (termasuk BAHAYA recall ≥ 72%)
2. **Mengalahkan champion sebelumnya** pada ≥ 2 dari 4 primary metrics
3. Primary metrics: `test_accuracy`, `macro_f1`, **`per_class_recall.BAHAYA`**, `int8_agreement`

```python
# scripts/register_model.py:149
# Jika tidak memenuhi syarat → status tetap "Archived", tidak ke "Production"
# Force deploy dengan --force flag (log warning)
```

### 2.6 Pipeline Enforcement

```makefile
# Makefile
train-and-deploy: train validate-model deploy-model
#                    ↑ jika gagal (exit 1), deploy-model tidak pernah jalan
deploy-model: validate-model deploy-model
#             ↑ diulang lagi sebelum deploy ke branch Android
```

Ganda: sekali di `train-and-deploy`, sekali di `deploy-model`. Fail-safe.

---

## LAPIS 3 — On-Device Multi-Factor Override (Android)

**Lapisan terpenting saat runtime.** ML tidak pernah menjadi keputusan akhir. Bobotnya hanya **50%**.

### 3.1 MultiFactorRiskEngine — Arsitektur

```kotlin
// mobile-app/.../data/risk/MultiFactorRiskEngine.kt
val weights = mapOf(
    "ml" to 0.50,        // hanya 50%
    "slope" to 0.20,     // kemiringan lereng: 20%
    "rainfall" to 0.15,  // curah hujan: 15%
    "elevation" to 0.10, // elevasi: 10%
    "soil" to 0.05       // jenis tanah: 5%
)
```

5 faktor independen. ML cuma 50%. Jika 4 faktor lingkungan mengatakan BAHAYA, hasil akhir tetap BAHAYA — bahkan jika ML bilang AMAN.

### 3.2 ML Score Floor (AMAN Never Zero)

Bahkan jika ML sangat yakin "AMAN" (confidence ≥ 70%), skor kontribusinya tetap **0.1 — bukan 0.0**.

```kotlin
// MultiFactorRiskEngine.kt:144-163
fun mlScore(result: DetectionResult, confidence: Float): Double {
    return when (result) {
        DetectionResult.AMAN -> when {
            c >= 0.70 -> 0.1    // floor: confident AMAN tetap dapat 0.1
            c >= 0.50 -> 0.2
            else -> 0.3         // ML bingung → skor lebih tinggi → safety
        }
        DetectionResult.WASPADA -> ...
        DetectionResult.BAHAYA -> ...
    }
}
```

Artinya: ML bilang "AMAN" dengan confidence 95% → skor kontribusi 0.1. Jika slope (20%) + rainfall (15%) + soil (5%) = total 40% dengan nilai 1.0 → kontribusi mereka = 0.4. Final score = 0.05 (ML) + 0.4 = **0.45 → WASPADA**. ML yang confident tetap di-override oleh faktor lingkungan.

### 3.3 Renormalisasi Jika Ada Data Hilang

Jika satu faktor (misal: soil) tidak tersedia, bobotnya didistribusikan ulang ke faktor lain:

```kotlin
// MultiFactorRiskEngine.kt:97-101
val finalScore = if (anyMissing && totalWeight > 0.0) {
    weightedSum / totalWeight
} else {
    weightedSum
}
```

Tidak ada data hilang = skor tetap akurat. Tidak ada alasan "data tidak lengkap" untuk skip peringatan.

### 3.4 Upgrade/Downgrade Detection

Sistem mencatat jika MultiFactor mengubah hasil ML:

```kotlin
// MultiFactorRiskEngine.kt:107-108
val isUpgraded = clampedScore > mlOnlyScore + 0.05  // dinaikkan dari hasil ML
val isDowngraded = clampedScore < mlOnlyScore - 0.05  // diturunkan
```

Informasi ini ditampilkan ke user:
> "↑ Meningkat dari hasil ML karena faktor lingkungan" (`MainScreens.kt:484`)

### 3.5 Faktor Lingkungan Detail

| Faktor | Bobot | Sumber Data | Rentang Skor |
|--------|-------|-------------|--------------|
| **ML** | 50% | TFLite Interpreter | 0.1–1.0 (floor 0.1) |
| **Slope** | 20% | Open-Meteo / Elevation API | 0.0–1.0 |
| **Rainfall** | 15% | BMKG / Open-Meteo | 0.0–1.0 |
| **Elevation** | 10% | Open-Meteo | 0.0–1.0 |
| **Soil Type** | 5% | ISRIC SoilGrids | 0.2–1.0 |

**Soil mapped ke skor risiko** (`SoilTypeService.kt`):
- Vertisols, Planosols → **SANGAT_TINGGI** (1.0)
- Acrisols, Lixisols, Nitisols, Alisols, Luvisols → **TINGGI** (0.8)
- Cambisols, Ferralsols, Fluvisols, Leptosols, Regosols → **SEDANG** (0.5)
- Arenosols, Podzols, Gleysols, Histosols → **RENDAH** (0.2)

### 3.6 Score-to-Result Threshold Konservatif

```kotlin
// MultiFactorRiskEngine.kt:194-198
fun resultFromScore(score: Double): DetectionResult = when {
    score <= 0.33 -> DetectionResult.AMAN
    score <= 0.66 -> DetectionResult.WASPADA
    else -> DetectionResult.BAHAYA
}
```

Hanya butuh skor 0.67 untuk masuk BAHAYA. Dengan floor ML 0.1 + faktor lingkungan rata-rata 0.5 (kondisi normal) + slope tinggi (0.8) = 0.1×0.5 + 0.5×0.15 + 0.8×0.2 = **0.29** → masih WASPADA. Tapi dengan data lingkungan moderat saja sudah bisa mendorong ke peringatan lebih tinggi.

---

## LAPIS 4 — User Interface Safety & Warnings

### 4.1 Low Confidence Warning

Di layer UI, confidence ML yang rendah memicu peringatan:

```kotlin
// MainScreens.kt:356-398
confidence < 0.4 → Merah: "Gambar bukan retakan tanah? Pastikan memotret permukaan tanah"
confidence < 0.6 → Kuning: "Hasil tidak pasti — ambil foto ulang dengan pencahayaan lebih baik"
```

### 4.2 BAHAYA Emergency Warning

Jika hasil akhir BAHAYA → **peringatan darurat merah tebal**:

```
🚨 PERINGATAN DARURAT:
Segera menjauh dari area lereng dan infokan warga sekitar!
```

(`MainScreens.kt:400-421`)

### 4.3 Environmental Factor Breakdown Card

User melihat kontribusi setiap faktor secara visual — transparan, tidak black box:

```kotlin
// MainScreens.kt:444-509
// Menampilkan:
// - Final risk score sebagai persentase
// - "↑ Meningkat dari hasil ML" (jika di-upgrade)
// - Per-faktor: ikon + bar + kontribusi numerik
```

### 4.4 Weather Risk Notes

Weather service memberikan catatan risiko kontekstual:

```kotlin
// Weatherapiservice.kt
HEAVY_RAIN → "BAHAYA — periksa lereng sekarang"
RAIN → "Tingkat risiko meningkat"
DRIZZLE → "Waspada jika lereng sudah retak"
```

### 4.5 Heavy Rain Warning (Map)

Di peta, jika curah hujan > 5mm:

```
"Curah hujan tinggi — periksa lereng di sekitar Anda" (Petascreen.kt:296)
```

---

## LAPIS 5 — Sistem & Admin

### 5.1 Realtime BAHAYA Alert (Web)

Admin menerima **notifikasi realtime** saat ada laporan BAHAYA baru:

```typescript
// RealtimeAlert.tsx
channel.on('postgres_changes',
  { event: 'INSERT', schema: 'public', table: 'laporan', filter: 'status=eq.BAHAYA' },
  (payload) => {
    toast('error', `Laporan BAHAYA baru di ${nama_lokasi}! Segera cek dashboard.`);
  },
)
```

### 5.2 Admin Verification (HITL — Human-in-the-Loop)

Setiap laporan bisa diverifikasi admin melalui **VerificationDialog**:

```
Admin lihat tabel → klik "Verif. ML" → dialog muncul:
  ├─ Foto laporan + info lokasi/pelapor/waktu
  ├─ Status prediksi ML + confidence bar
  ├─ "Apakah hasil ini sesuai?"
  │   ├─ ✅ Sesuai → alasan = 'BENAR'
  │   └─ ❌ Tidak Sesuai → pilih label benar
  └─ Submit → riwayat_penanganan tercatat
```

(Stage 3 implementation)

### 5.3 Admin Edit & Delete

Admin dapat mengubah status laporan atau menghapus — semua tercatat di `riwayat_penanganan` sebagai audit trail. Data tidak pernah hilang tanpa jejak.

### 5.4 Export Training Data (Retrain Pipeline)

Laporan yang diverifikasi (termasuk koreksi false negative) diexport sebagai dataset training:

```
Admin Verif (SALAH → label benar) → Export CSV
  → make ingest → make split → make train → model baru
```

**Setiap false negative yang tertangkap admin langsung menjadi data training untuk model berikutnya.** Siklus perbaikan berkelanjutan.

### 5.5 Delta Model Updates

Model baru di-push ke HP warga tanpa download full (via delta compression). Update cepat → false negative yang sudah diperbaiki di model baru segera sampai ke pengguna.

---

## Ringkasan: 25 Lapisan Pertahanan

| # | Lapisan | Mekanisme | Lokasi |
|---|---------|-----------|--------|
| 1 | **Training** | Balanced class weights (auto + eksplisit) | `train.py:191` / `training.yaml:39` |
| 2 | **Dataset** | Image integrity validation (corrupt check) | `validate_dataset.py:77` |
| 3 | **Dataset** | Cross-class perceptual hash dedup | `deduplicate.py:33` |
| 4 | **Dataset** | Stratified split (seed=42, reproducible) | `split_dataset.py` |
| 5 | **Dataset** | Extreme augmentation (rot, zoom, brightness) | `training.yaml` augmentation |
| 6 | **Benchmark** | BAHAYA recall ≥ 72% (threshold tertinggi) | `benchmark.yaml:23` |
| 7 | **Benchmark** | BAHAYA precision ≥ 70% | `benchmark.yaml:27` |
| 8 | **Benchmark** | Cross-validation std ≤ 3% | `benchmark.yaml:36` |
| 9 | **Registry** | Semua threshold harus lulus | `register_model.py:149` |
| 10 | **Registry** | BAHAYA recall = primary promotion metric | `benchmark.yaml:43` |
| 11 | **Export** | FP32 vs INT8 agreement ≥ 90% | `export.py:148` |
| 12 | **Pre-deploy** | 7 checks (load, shape, dtype, run, multi-class, confidence) | `validate_model.py` |
| 13 | **Pre-deploy** | Frozen model detection (≥ 2 kelas / confidence std) | `validate_model.py` |
| 14 | **App: ML** | ML hanya 50% bobot final | `MultiFactorRiskEngine.kt` |
| 15 | **App: ML** | AMAN score floor = 0.1 (bukan 0.0) | `MultiFactorRiskEngine.kt:148` |
| 16 | **App: Multi** | Slope 20%, rain 15%, elevation 10%, soil 5% | `MultiFactorRiskEngine.kt` |
| 17 | **App: Multi** | Renormalisasi jika data hilang | `MultiFactorRiskEngine.kt:97` |
| 18 | **App: Multi** | Score-to-result threshold konservatif (0.67 → BAHAYA) | `MultiFactorRiskEngine.kt:194` |
| 19 | **App: UI** | Low confidence warning (< 40% / < 60%) | `MainScreens.kt:356` |
| 20 | **App: UI** | BAHAYA emergency warning merah tebal | `MainScreens.kt:400` |
| 21 | **App: UI** | Upgrade indicator (+ faktor breakdown) | `MainScreens.kt:484` |
| 22 | **App: UI** | Weather risk notes (kontekstual) | `Weatherapiservice.kt` |
| 23 | **System** | Admin verification (HITL — koreksi false negative) | `AdminDashboardPage.tsx` |
| 24 | **System** | Realtime BAHAYA alert (admin dashboard) | `RealtimeAlert.tsx` |
| 25 | **System** | Retrain loop (false negative → data training → model baru) | `ingest_verification.py` |

## Contoh Skenario: ML Gagal Total

Skenario terburuk: model false negative — melihat retakan besar BAHAYA tapi memprediksi AMAN dengan confidence 95%.

### Tanpa mitigasi
```
ML: AMAN (95%) → User tenang → tidak evakuasi → longsor → korban jiwa ❌
```

### Dengan mitigasi
```
ML: AMAN (95%) → kontribusi skor = 0.1 (floor)
                    + Slope terjal (0.9 × 20% = 0.18)
                    + Hujan deras (1.0 × 15% = 0.15)
                    + Tanah Vertisols (1.0 × 5% = 0.05)
                    = Final score: 0.48 → WASPADA ⚠️

User lihat: "WASPADA — faktor lingkungan meningkatkan risiko"
           + "Curah hujan tinggi — periksa lereng di sekitar Anda"
Admin: Notifikasi realtime → verifikasi → ubah ke BAHAYA ✅
Data: False negative tertangkap → masuk retrain pipeline ✅
```
