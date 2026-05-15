# Model Details — Retak.id ML Pipeline

> Last updated: 2026-05-15 | Version: v3a (deployed) — v3b/c/d (experimental)

---

## 1. Model Architecture

| Parameter | Value |
|-----------|-------|
| Base Model | **MobileNetV2** (tf.keras.applications) |
| Pre-trained Weights | ImageNet |
| Input Shape | 224 × 224 × 3 (uint8 [0,255]) |
| Classification Head | GlobalAveragePooling2D → Dropout(0.5) → Dense(3, softmax) |
| Input Preprocessing | `Rescaling(1/127.5, offset=-1.0)` — baked into TFLite graph |
| Total Parameters | ~2.26M |
| Trainable Parameters | ~1.8M (24/154 layers unfrozen at layer 130+) |
| Output | 3 logits → softmax → (AMAN, WASPADA, BAHAYA) |
| Framework | TensorFlow 2.19 + TFLite 2.16.1 |
| Model File | `retak_mobilenetv2.tflite` (INT8 quantized) — **2.6 MB** |

### Class Labels

```
AMAN    (0) — Retakan minor, penyusutan alami, tidak perlu tindakan
WASPADA (1) — Retakan signifikan, perlu pemantauan berkala
BAHAYA  (2) — Retakan kritis, indikasi pergerakan tanah besar, evakuasi
```

---

## 2. Dataset

### Current State (on disk, `backend/data/processed/`)

| Class | Count | % of Total | Description |
|-------|-------|------------|-------------|
| AMAN | 400 | 10.4% | Retakan kecil, tanah kering, pola poligonal |
| WASPADA | 1,630 | 42.6% | Retakan signifikan, pola linear, potensi meluas |
| BAHAYA | 1,799 | 47.0% | Retakan kritis, longsor aktif, ancaman tinggi |
| **Total** | **3,829** | **100%** | |

**Note:** Angka di disk berbeda dari model card (3,545) karena dataset telah di-cleanup dan reorganisasi pasca v3a training.

### Data Pipeline

```
Web Scraper (DuckDuckGo, 70+ keywords)
    ↓
  validate_dataset.py       ← Integrity check (PIL verify, min 100px, max 20MB)
    ↓
  deduplicate.py            ← Cross-class perceptual hash (phash, hamming ≤ 6)
    ↓
  dataset_stats.py          ← Distribusi, dimensi, file sizes
    ↓
  split_dataset.py          ← Stratified 70/15/15, seed=42
    ↓
  train.py                  ← Training + evaluation
```

**DVC tracking** di DagsHub (`backend/data/processed.dvc`) — dataset versioned, pull/push via `dvc`. Pipeline reproducibility via `dvc.yaml`.

### HITL Data Augmentation (Stage 3)

Admin-verified training data (koreksi false negative/positive) dapat diinjeksi ke dataset via:

```
Admin verifikasi → Export CSV → make ingest → make split → make train
```

Siklus perbaikan berkelanjutan — setiap false negative yang tertangkap admin langsung menjadi data training untuk model berikutnya.

---

## 3. Training Configuration (v3 — Current Best)

| Parameter | Value |
|-----------|-------|
| Optimizer | Adam |
| Learning Rate | 5 × 10⁻⁶ |
| Loss | Categorical Crossentropy |
| Epochs | 70 (EarlyStopping patience=20) |
| Batch Size | 32 |
| Fine-tune At | Layer 130 (24/154 unfrozen) |
| Dropout | 0.5 |
| Class Weight | `"balanced"` (sklearn auto) |
| ReduceLR Patience | 10 epochs |
| Seed | 42 (Python, NumPy, TensorFlow) |

### Hyperparameter Evolution

| Version | Date | Accuracy | LR | Fine-tune | Dropout | Augmentasi | Key Change |
|---------|------|----------|----|-----------|---------|------------|------------|
| test1 | 02/05 | 73.0% | 1e-4 | Frozen | — | Ringan | Baseline |
| test2 | 02/05 | 76.7% | 1e-5 | 54 layers | 0.5 | Moderate | Fine-tune awal |
| test3 | 03/05 | 81.8% | 5e-6 | 24 layers | 0.5 | Agresif | LR turun, augmentasi naik |
| **v3a** | **04/05** | **84.9%** | 5e-6 | 24 layers | 0.5 | Agresif | **Dataset cleaned** |
| v3b | exp | — | 5e-6 | 34 layers | 0.5 | Agresif | More layers unfrozen |
| v3c | exp | — | 5e-6 | 24 layers | 0.5 | Agresif | WASPADA weighted 3× |
| v3d | exp | — | 1e-5 | 24 layers | 0.5 | Agresif | Higher LR |

**Strategic insight:** test1→test3 menunjukkan fine-tuning lebih sedikit (54→24 layers) + LR lebih rendah (1e-4→5e-6) + augmentasi lebih agresif meningkatkan akurasi 8.8%. v3a menambahkan **data quality** (cleanup BAHAYA noise, tambah WASPADA) → naik 3.1%.

---

## 4. Augmentation Pipeline

In-graph Keras preprocessing layers (zero Python I/O overhead):

| Augmentation | Value | Tujuan |
|-------------|-------|--------|
| RandomFlip | Horizontal + Vertical | Orientasi retakan tak menentu |
| RandomRotation | ±45° | Variasi sudut kamera |
| RandomZoom | ±30% | Jarak pemotretan |
| RandomTranslation | ±25% | Framing tidak sempurna |
| RandomBrightness | 0.5–1.5× | Pencahayaan outdoor (pagi–siang) |
| RandomContrast | 0.5–1.5× | Kualitas kamera bervariasi |

In-graph = augmentasi jalan di GPU/TPU tanpa I/O bottleneck. `model.fit()` dengan dataset pipeline paralel.

---

## 5. Quantization (INT8 Post-Training)

| Metric | FP32 | INT8 |
|--------|------|------|
| Model Size | ~8.5 MB | **2.6 MB** (3.3× smaller) |
| Input Type | float32 | uint8 [0, 255] |
| Output Type | float32 | float32 logits |
| Calibration | — | 100 representative samples |
| Latency (Mid-range) | ~800 ms | **~450 ms** |

Normalisasi input (`[-1, 1]`) baked into TFLite graph — Android cukup kirim raw pixel (r, g, b). Softmax diterapkan manual di Kotlin via `exp(logit - maxLogit)`.

---

## 6. Safety Gates — Pre-Deployment Validation

Setiap model harus lulus **6 gerbang** sebelum menyentuh HP warga.

### Benchmark Thresholds

| Metric | Threshold | Filosofi |
|--------|-----------|----------|
| Test Accuracy | ≥ 82% | Overall quality floor |
| Macro F1 | ≥ 0.75 | Balanced across all classes |
| BAHAYA Recall | **≥ 72%** | **Jangan sampai miss danger** (tertinggi) |
| BAHAYA Precision | ≥ 70% | Jangan teriak palsu |
| INT8 Agreement | ≥ 90% | Quantization tidak ubah prediksi |
| Model Size | ≤ 5 MB | Deployment constraint |
| CV Std | ≤ 3% | Konsisten antar fold |

### Pre-Deployment Validation (7 checks — `validate_model.py`)

1. Model loads without error
2. Input shape [1, 224, 224, 3], dtype uint8
3. Output shape [1, 3], float32
4. Inference runs on real/synthetic images without crash
5. Predicts ≥ 2 distinct classes (not frozen on one)
6. Confidence max > 0.45, std > 0.02 (not flat 33/33/33)
7. DVC pipeline ensures validation runs before training

**Exit code 1 = DO NOT DEPLOY.** Enforcement di Makefile:
```makefile
train-and-deploy: train validate-model deploy-model
                  # ↑ jika gagal, deploy-model tidak pernah jalan
```

### Model Promotion Gating (`register_model.py`)

Model hanya dipromosikan ke **Production** jika:
1. Lulus SEMUA benchmark thresholds
2. Mengalahkan champion sebelumnya pada ≥ 2 dari 4 primary metrics:
   - `test_accuracy`, `macro_f1`, **`per_class_recall.BAHAYA`**, `int8_agreement`

---

## 7. On-Device Inference (Android)

### Two-Layer Architecture

```
── Layer 1: TFLite ML ────────────────────────────
  Input: Bitmap (224×224) → ByteBuffer (uint8)
  Inference: Interpreter (4 threads, CPU)
  Output: FloatArray[3] logits → softmax → MLResult

── Layer 2: MultiFactorRiskEngine ─────────────────
  ML Result (50%) + Slope (20%) + Rain (15%)
    + Elevation (10%) + Soil Type (5%)
    ↓
  Final Risk Score → AMAN (≤0.33) / WASPADA (≤0.66) / BAHAYA (>0.66)
```

### ML Score Floor: "AMAN never zero"

Bahkan jika ML sangat yakin "AMAN" (confidence ≥ 70%), skor kontribusinya tetap **0.1 — bukan 0.0**.

```kotlin
fun mlScore(AMAN, confidence=0.95) = 0.1  // floor
```

Artinya: faktor lingkungan (slope 20% + rain 15% + soil 5% = 40% weight) bisa meng-override ML yang confident sekalipun.

### Inference Performance

| CPU Class | Contoh Chip | RAM | Inference |
|-----------|------------|-----|-----------|
| Low-end | Snapdragon 425 (4×A53 @1.4 GHz) | 2 GB | ~2.8 dtk |
| Mid-range | Snapdragon 665 (4×A73 + 4×A53 @2.0 GHz) | 4 GB | ~450 ms |
| High-end | Snapdragon 8 Gen 1 | 8 GB | ~150 ms |

Inference di background coroutine (`Dispatchers.Default`) — UI tetap responsif.

---

## 8. Deployment Strategy (4 Stages)

| Stage | Status | Description |
|-------|--------|-------------|
| **1** ✅ | Done | Dataset pipeline, training, TFLite export, model card v3a (84.9%) |
| **2** ✅ | Done | Android integration (MLAnalyzer, MultiFactorRiskEngine, CameraX) |
| **3** ✅ | Done | HITL: Admin verification dialog, training data export, retrain loop |
| **4** ✅ | Done | **Delta compression**: OTA model updates via .rkd byte-level patching |

### Stage 4 — Delta Compression (Latest ✅ End-to-End Verified)

| Aspek | Detail |
|-------|--------|
| Format | `.rkd` — custom binary (gzip + byte region patches) |
| Status | ✅ **End-to-end diverifikasi**: compute → file .rkd → apply → validasi |
| Full model | 2,710,280 bytes (2.6 MB) — MobileNetV2 INT8 |
| Delta file | **48,451 bytes (47 KB)** — hasil `compute_delta.py` real |
| Savings | **98.2% lebih kecil** dari full model (2.6 MB → 47 KB) |
| Changed bytes | 16,195 bytes (0.6% dari total) — hanya layer akhir yang berubah |
| Pipeline | `make deploy-delta OLD=v3a.tflite NEW=v3b.tflite VERSION=v3b` |
| Server | Supabase Storage (`model-deltas` bucket) + `model_versions` table |
| Client | `DeltaModelLoader.applyDelta()` — download → gzip decompress → patch → Interpreter validasi → save |
| Safety | TFLite Interpreter validation sebelum commit; fallback ke full model |
| Gate kualitas | Lewati delta jika savings <50% atau size mismatch |
| Delta file | `backend/models/delta/delta_v3a_to_v3b.rkd` — siap deploy |

### Deployment Flow Diagram

```
Retak.id Model Pipeline
════════════════════════

DATA LAYER                    TRAINING LAYER              DEPLOYMENT LAYER
┌──────────────┐             ┌──────────────────┐        ┌──────────────────┐
│ DuckDuckGo   │──scrape──→  │ training.yaml    │        │ backend/models/  │
│ Scraper      │             │ augment.py       │        │  .tflite (2.6MB) │
└──────────────┘             │ train.py         │──exp──→│  labels.txt      │
       ↓                    └──────────────────┘        └────────┬─────────┘
┌──────────────┐                    ↓                           │
│ Validate     │             ┌──────────────────┐               │
│ Deduplicate  │             │ evaluate.py      │     ┌─────────┴──────────┐
│ Split        │             │ benchmark.yaml   │     │ v. validate_model  │
└──────────────┘             │ cross_validate    │     │ v. deploy-model    │
       ↓                    └──────────────────┘     │ v. deploy-delta    │
┌──────────────┐                    ↓                 └─────────┬──────────┘
│ processed/   │             ┌──────────────────┐               │
│   AMAN/      │────dvc──→  │ Model Registry   │               ↓
│   WASPADA/   │             │ (MLflow)         │     ┌──────────────────┐
│   BAHAYA/    │             │ Staging/Prod     │     │ HP Warga         │
└──────────────┘             └──────────────────┘     │ MLAnalyzer       │
                                                      │ ModelUpdateChecker│
FEEDBACK LOOP (Stage 3 + 4)                           └──────────────────┘
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Admin Verif  │────→│ Export CSV   │────→│ make ingest  │
│ HITL         │     │ Training Data│     │ + split+train│
└──────────────┘     └──────────────┘     └──────────────┘
       ↑                                          │
       └────────── siklus perbaikan berkelanjutan ──┘
```

---

## 9. Safety in Production (25 Defense Layers)

False negative (ML bilang AMAN, padahal BAHAYA) dicegah oleh **5 lapis pertahanan**:

| Layer | Mekanisme | Coverage |
|-------|-----------|----------|
| **Training** | Balanced class weights, dedup, augmentasi, stratified split | Data quality |
| **Validation** | Benchmark thresholds, CV, 7 pre-deploy checks, promotion gating | Model quality |
| **On-Device** | ML hanya 50% bobot, AMAN floor=0.1, renormalisasi, score threshold | Runtime safety |
| **UI** | Low confidence warning, BAHAYA emergency, upgrade indicator, weather notes | User awareness |
| **System** | Admin HITL, realtime alert, retrain loop, delta update | Operational |

Detail lengkap: [`docs/mitigation_false_negative.md`](./mitigation_false_negative.md)

---

## 10. Model Card — v3a (Production)

| Metric | Value |
|--------|-------|
| Test Accuracy | **84.9%** |
| Best Val Accuracy | 81.6% |
| INT8 Agreement (vs FP32) | 93.75% |
| Model Size | 2.6 MB (target < 5 MB) |
| Training Data | 3,545 images (70/15/15 split) |
| Classes | AMAN, WASPADA, BAHAYA |

### Intended Use

- **Use Case:** Soil crack classification on Android, landslide early warning
- **Users:** Warga + BPBD Ponorogo + admin desa
- **Environment:** Outdoor, berbagai kondisi cahaya, kamera HP beragam
- **Offline:** Inference full on-device, tanpa internet

### Limitations

1. **Tidak untuk:** Retakan aspal, beton, atau tembok — model khusus tanah
2. **Pencahayaan:** Gelap ekstrem atau terlalu terang kurangi akurasi
3. **Vegetasi:** Rumput/daun nutup retakan bisa bikin misklasifikasi
4. **Waktu:** Single image — belum pake data temporal (perkembangan retakan)
5. **Region:** Dominan tanah tropis Indonesia

---

## 11. Reproducibility

```bash
git clone https://github.com/jaweed3/retakId.git
cd retakId
make setup                              # uv sync + Python 3.11
dvc pull                                # dataset dari DagsHub
make split                              # stratified 70/15/15
make train                              # training → .tflite
make validate-model                     # 7 safety checks
make deploy-delta OLD=... NEW=...       # OTA update ke HP
```

- Semua seed fixed (Python, NumPy, TF) = 42
- Config-driven: single `backend/config/training.yaml`
- Deps lock via `uv.lock`
- Docker: `docker build -t retakid-train -f backend/Dockerfile .`
- DVC: dataset + pipeline versioning via DagsHub
- MLflow: experiment tracking (`backend/logs/mlruns`)
