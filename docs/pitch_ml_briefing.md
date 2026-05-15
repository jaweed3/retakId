# ML Pitch Briefing — Jaweed (ML Engineer)

> Hafalkan. Pahami. Mental.

---

## 1. Core Script — "Cerita ML dalam 60 Detik"

```
"Kami menggunakan MobileNetV2 yang di-fine-tune dengan 3,547 gambar
retakan tanah yang dikumpulkan dari 70+ query pencarian.

Model INT8 quantized — 2.6 MB, inferensi <50ms di HP,
fully offline tanpa internet.

Akurasi test 84.9% — di atas safety threshold kami (≥82%).
BAHAYA recall 84.9% — target minimal 72%.

Setiap laporan diverifikasi staf BPBD → jadi data training.
Model terus belajar. Tidak ada yang statis."
```

---

## 2. Key Numbers (WAJIB HAFAL)

| Metrik | Angka | Threshold | Notes |
|--------|-------|-----------|-------|
| Test Accuracy | **84.9%** | ≥ 82% | |
| BAHAYA Recall | **84.9%** | ≥ 72% | Prioritas #1 — false negative paling bahaya |
| BAHAYA Precision | **86.2%** | ≥ 70% | |
| Macro F1 | **0.83** | ≥ 0.75 | |
| Model Size | **2.6 MB** | — | INT8 quantized |
| Inference | **<50 ms** | — | Pixel 4a, CPU 4 thread |
| Dataset | **3,547** | — | 3 kelas: AMAN 2,009 / WASPADA 768 / BAHAYA 767 |
| FP32→INT8 Agreement | **93.75%** | — | Quantisasi hampir tanpa loss |

---

## 3. Architecture (kalo juri minta diagram)

```
Input: uint8 [1, 224, 224, 3] RGB [0, 255]
  ↓
MobileNetV2 backbone (fine-tuned from ImageNet)
  — Freeze sampai layer 130
  — Fine-tune layer 131+
  ↓
Global Average Pooling
  ↓
Dense(3) → softmax → float32 [1, 3] logits
  ↓
AMAN / WASPADA / BAHAYA

Training:
  Optimizer: Adam, LR 5e-6
  Epochs: 70
  Augmentasi: rot ±45°, zoom ±30%, flip, brightness
  Loss: Sparse Categorical Crossentropy
```

---

## 4. Pipeline (alur dari awal sampai deploy)

```
Scraping → Validasi → Dedup phash → Split 70/15/15
    ↓
Fine-tune MobileNetV2
    ↓
Validation gate (7 checks!)
    ↓
INT8 PTQ quantization
    ↓
TFLite export
    ↓
Model Registry (MLflow DagsHub)
    ↓
Delta OTA (compute_delta.py → .rkd)
    ↓
Deploy ke Supabase Storage + register version
    ↓
Android app check update → download delta → patch → infer
```

---

## 5. Safety Gates (7 Pre-Deployment Checks)

```
1. Load model — file tidak corrupt
2. Input shape — [1, 224, 224, 3] uint8
3. Output dtype — float32
4. Inference runs — tanpa error
5. Multi-class — 3 logits (bukan binary)
6. Confidence — distribusi wajar
7. Cross-validation — ≥ semua threshold benchmark
```

Semua harus lulus. Kalau satu gagal — **model TIDAK masuk registry.**

---

## 6. MultiFactorRiskEngine (ML + Lingkungan)

```
ML hanya 50% dari keputusan akhir!

ML result: AMAN/WASPADA/BAHAYA + confidence
  ├─ ML Score (50%) — dari TFLite
  ├─ Slope Score (20%) — dari 5 titik elevasi Open-Meteo
  ├─ Rain Score (15%) — dari Open-Meteo forecast
  ├─ Elevation Score (10%) — dari SRTM
  └─ Soil Score (5%) — dari ISRIC SoilGrids

Final score = ML(0.5) + Slope(0.2) + Rain(0.15) + Elev(0.1) + Soil(0.05)
AMAN floor = max(0.1, score)

Kalau API timeout (5 detik) → graceful degradation:
  weightedSum / totalWeight (missing factors di-drop)
```

**Contoh kasus:** ML bilang AMAN (confidence 90%, score=0.1), tapi slope 30° (score=1.0), hujan deras (score=1.0). Final score = 0.1×0.5 + 1.0×0.2 + 1.0×0.15 = 0.4 → **WASPADA.** Lingkungan bisa override ML.

---

## 7. Dataset

| Kelas | Jumlah | Sumber |
|-------|--------|--------|
| AMAN | 2,009 | Scraping 70+ query DuckDuckGo |
| WASPADA | 768 | + anotasi manual |
| BAHAYA | 767 | + anotasi manual |
| **Total** | **3,547** | |

Proses:
- DuckDuckGo Image Search (gratis, no API key)
- Filter blur, ukuran, dedup phash (threshold=6)
- Validasi manual oleh tim
- Cross-class leak prevention

**Kelemahan yang diakui:** Data bias ke area yang fotonya banyak di internet. Tapi setiap admin verifikasi nambah data training — model makin baik seiring waktu.

---

## 8. Training Evolution

```
Baseline (MobileNetV2 frozen)     ████████░░░░░░  73.0%
+ Fine-tune all layers            █████████░░░░░  76.7%
+ Conservative FT (layer 130+)    ██████████░░░░  81.8%
+ Clean labels (fix annotation)   ████████████░░  84.9%  ← v3a (PRODUKSI)
```

---

## 9. Delta OTA (Update Model Hemat Kuota)

```
Full model:  2.6 MB
Delta:       0.3–0.8 MB  (70–90% lebih kecil)

Format .rkd:
  - Byte-level diff antara model lama dan baru
  - Gzip compress
  - Hanya region yang berubah disimpan

Flow:
  compute_delta.py → .rkd file
    ↓
  upload ke Supabase Storage
    ↓
  register version di model_versions table
    ↓
  HP: check-model-update → download .rkd → gzip decompress
    → patch byte regions → validate via TFLite Interpreter
    → save ke internal storage

Safety:
  - Patch dari bundled assets (APK), BUKAN dari cached model
  - Validasi Interpreter sebelum save
  - Fallback ke full model download kalo delta gagal
```

Kalau arsitektur model berubah (ukuran file beda) → delta skip → full model download langsung.

---

## 10. HITL → Retrain Loop (Continuous Improvement)

```
LAPORAN → ML PREDICT → DASHBOARD ADMIN
                           ↓
                    VerificationDialog
                    ├─ "Sesuai?" → label = prediksi ML
                    └─ "Tidak" → pilih label benar
                           ↓
                    riwayat_penanganan (DB)
                           ↓
                    Export CSV (label_akhir)
                           ↓
                    ingest_verification.py
                    (download foto + phash dedup)
                           ↓
                    make split && make train
                           ↓
                    Model v3b, v3c, ...
```

**Setiap verifikasi — benar atau salah — jadi data training. Tidak ada yang terbuang.**

---

## 11. Q&A Juri — Semua Kemungkinan Tentang ML

### Q1: "Dataset cuma 3,547 — cukup?"

**Jawaban:**
> MobileNetV2 adalah transfer learning dari ImageNet (1.4M gambar).
> Fine-tuning efektif dengan ribuan gambar — kami buktikan 84.9%.
> Tapi yang lebih penting: **setiap admin verifikasi nambah dataset.**
> Dalam 3 bulan, estimasi kami 500+ data baru terkumpul — model makin baik.
> Kualitas > kuantitas: setiap gambar diverifikasi manual + dedup phash.

**Counter jika didorong:**
> "Model baseline frozen aja 73% — artinya backbone ImageNet sudah sangat relevan
> dengan dataset retakan tanah. Fine-tuning cuma perlu sentuhan akhir."

### Q2: "84.9% itu bagus atau jelek? Untuk safety-critical, harusnya 99%."

**Jawaban:**
> Kami setuju safety-critical butuh akurasi tinggi. Tapi 2 poin:
> 1. **ML hanya 50%** — MultiFactorRiskEngine dengan faktor lingkungan
>    mengurangi dampak false negative. AMAN floor = 0.1.
> 2. **25 layer defense-in-depth** — dari dataset quality, validation gate,
>    multi-factor override, UI confirmation, admin HITL.
> 3. **BAHAYA recall kami 84.9% vs threshold 72%** — model over-performs.
> 4. Model terus improve via retrain loop. Target v3b: >88%.

### Q3: "Kenapa MobileNetV2? Kenapa bukan arsitektur yang lebih modern?"

**Jawaban:**
> 3 alasan:
> 1. **Ukuran** — MobileNetV2 INT8 = 2.6 MB. EfficientNet-B0 INT8 = 7 MB.
>    Di desa terpencil dengan kuota terbatas, setiap MB berharga.
> 2. **Kecepatan** — <50ms di Pixel 4a. Model modern seperti ConvNeXt atau
>    ViT butuh >200ms di CPU — tidak acceptable untuk real-time.
> 3. **Kecocokan** — Untuk klasifikasi 3 kelas retakan tanah, MobileNetV2
>    sudah lebih dari cukup. Tidak perlu arsitektur seberat ViT.

### Q4: "Quantisasi INT8 turunkan akurasi?"

**Jawaban:**
> FP32 → INT8 agreement = 93.75%. Hanya 0.3% accuracy drop.
> Model size turun dari 9.3 MB → 2.6 MB (72% lebih kecil).
> Trade-off yang sangat worth it untuk deployment di HP kelas bawah.

### Q5: "Data bias — gambar retakan di Google beda dengan kondisi lapangan Jenangan."

**Jawaban:**
> Kami sadar. 3 mitigasi:
> 1. **Scraping difokuskan** — 70+ query spesifik (soil crack, retakan tanah,
>    tanah longsor, dll.) bukan gambar retakan abstrak.
> 2. **Anotasi manual** — tiap gambar divalidasi tim sebelum masuk dataset.
> 3. **Retrain loop** — foto dari lapangan (via laporan warga) diverifikasi
>    admin → masuk dataset. Model belajar dari distribusi data asli.
>    Dalam 3 bulan, komposisi dataset bergeser dari "internet" ke "lapangan."

### Q6: "Kalau model di-update via delta, gimana kalau delta corrupt?"

**Jawaban:**
> 3 lapis safety:
> 1. **Validasi Interpreter** — sebelum save, model di-test dengan TFLite
>    Interpreter. Kalau gagal load → delta di-delete, fallback ke bundled.
> 2. **Patch dari bundled assets** — selalu patch dari model asli APK,
>    bukan dari cached model (yang mungkin corrupt).
> 3. **Full model fallback** — kalo delta gagal (corrupt, network error, dll),
>    download full model 2.6 MB dari Supabase Storage.

### Q7: "Kenapa tidak pakai TensorFlow Serving / server-side ML?"

**Jawaban:**
> 2 alasan:
> 1. **Offline-first** — desa terpencil sering tanpa sinyal. ML di server
>    tidak berguna tanpa koneksi. On-device inference = works everywhere.
> 2. **Biaya** — server ML (GPU) mahal, butuh maintenance. Model 2.6 MB
>    di HP gratis. Untuk skala kabupaten, server-side cost × 10 lipat.

### Q8: "Gimana kalau HP lemah — RAM 2 GB, CPU lambat?"

**Jawaban:**
> MobileNetV2 INT8 didesain untuk low-end device.
> - RAM: ~50 MB peak (model 2.6 MB + buffer)
> - CPU: 4 thread, <50ms inference
> - Android 8.0+ (API 26) — mencakup 95% devices
> - Fallback: kalo inference >5 detik, timeout → pake random fallback
>
> Detail lengkap di `docs/minimum_spec.md`.

### Q9: "Kenapa tidak pakai pre-trained model khusus landslide?"

**Jawaban:**
> Tidak ada pre-trained model untuk klasifikasi retakan tanah dari foto
> smartphone dalam 3 kelas (AMAN/WASPADA/BAHAYA) yang publik.
> Yang ada: satellite imagery landslide detection — berbeda domain.
> Jadi kami train dari scratch via transfer learning ImageNet.
> Ini juga jadi **kontribusi ilmiah** kami — model retakan tanah open-source.

### Q10: "Model sekarang v3a — kapan v3b?"

**Jawaban:**
> Setelah data verifikasi terkumpul cukup (estimasi 200+ sampel dari lapangan).
> Pipeline sudah siap:
> - `make split && make train` — retrain dengan data baru
> - Validation gate — auto-check threshold
> - `make deploy-delta` — compute delta + upload + register
>
> Tidak ada jadwal fixed. Retrain didasarkan pada kebutuhan,
> bukan kalender. Kalau admin lihat agreement rate turun → trigger retrain.

### Q11: "Agreement rate admin di dashboard 100% — berarti model sempurna?"

**Jawaban:**
> Itu bukan akurasi model. Itu **persetujuan admin** terhadap prediksi ML
> (= berapa kali admin klik "Sesuai").
>
> Akurasi model sesungguhnya 84.9% — diukur dari test set,
> bukan dari opini admin.
>
> Agreement rate tinggi berarti admin percaya sama model —
> bagus untuk kepercayaan, tapi bukan ukuran performa.
>
> (Label sudah kami fix — sebelumnya tertulis "Akurasi ML", sekarang
> "Sesuai Verifikasi" biar tidak misleading.)

### Q12: "Kenapa confidence bar di dialog verifikasi pake nilai dummy?"

```kotlin
// Di VerificationDialog.tsx:
private val mlStatusScore = when (status) {
    AMAN -> 0.2; WASPADA -> 0.5; BAHAYA -> 0.85
}
```

**Jawaban:**
> Itu nilai aproximasi untuk UI — bukan confidence asli dari model.
> Model outputnya float32 logits, perlu softmax dulu baru jadi confidence.
> Di Android, confidence asli ditampilkan.
> Di web (LiteRT.js), confidence asli juga — bar di dialog verifikasi
> itu placeholder yang harusnya diganti dengan output real model.
>
> **Kelemahan yang diakui:** VerificationDialog di web belum integrate
> confidence asli — pakai nilai mapping kasar. Sudah di backlog.

---

## 12. Red Flags — Kelemahan yang Harus Kamu Akui

| Kelemahan | Akui | Tapi... |
|-----------|------|---------|
| Dataset kecil (3,547) | ✅ | Transfer learning + retrain loop |
| Data bias internet | ✅ | Data lapangan masuk via retrain |
| 84.9% bukan 99% | ✅ | Cuma 50% bobot, 25 defense layers |
| Confidence bar dummy di web | ✅ | Backlog, Android sudah pakai asli |
| Weather API hardcoded (sekarang fix) | ✅ | Baru fix — lat/lon dari GPS |
| Belum automated CI/CD | ✅ | Manual trigger — safety feature |
| Peta Android masih dummy | ✅ | TODO — prioritas setelah pitch |

---

## 13. Final Words — Closing Statement

**Kalau juri bilang:** "Terima kasih, ada pertanyaan?"

**Kamu jawab:**

```
"Terima kasih. Satu hal yang ingin saya tekankan:

Kami bukan akademisi yang publish paper.
Kami mahasiswa Gontor yang turun ke desa, ngomong sama BPBD,
dan bikin sesuatu yang WORK di lapangan.

Model 84.9% — bukan yang terbaik di atas kertas.
Tapi ini jalan di HP Rp 1 jutaan, tanpa internet,
dan bisa di-retrain dengan data dari warga sendiri.

It's not perfect. But it's here, it works, and it gets better every day."
```

---

## Referensi File

| Doc | Untuk |
|-----|-------|
| `docs/model_detail.md` | Detail arsitektur, training config, gates |
| `docs/pitch_outline.md` | Full pitch outline 12 slide |
| `docs/verify_retrain.md` | HITL → retrain counter-attack |
| `docs/coverage_bias.md` | Blind spot + drone |
| `docs/adoption_access.md` | App vs WhatsApp |
| `docs/mitigation_false_negative.md` | 25-layer defense |
| `docs/minimum_spec.md` | Low-end device specs |
| `README.md` | Project overview |
