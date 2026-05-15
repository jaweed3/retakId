<p align="center">
  <img src="https://img.shields.io/badge/Accuracy-84.9%25-success?style=for-the-badge" alt="Accuracy">
  <img src="https://img.shields.io/badge/Model_Size-2.6MB-blue?style=for-the-badge" alt="Model Size">
  <img src="https://img.shields.io/badge/Inference-<50ms-orange?style=for-the-badge" alt="Inference">
  <img src="https://img.shields.io/badge/Risk_Factors-5-teal?style=for-the-badge" alt="Risk Factors">
  <img src="https://img.shields.io/badge/Platform-Android%20%26%20Web-green?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/Offline-First-black?style=for-the-badge" alt="Offline">
  <img src="https://img.shields.io/badge/Delta_OTA-70--90%25_purple?style=for-the-badge" alt="Delta OTA">
  <img src="https://img.shields.io/badge/PWA-Ready-34A853?style=for-the-badge" alt="PWA">
</p>

<h1 align="center">Retak.id</h1>
<h3 align="center">Crowdsourcing Early Detection of Landslide Soil Cracks</h3>
<p align="center"><strong>IYREF 2026 Semi-Final · Climate Resilience & Local Wisdom</strong></p>

---

## The Problem

**Jenangan, Ponorogo.** 41 landslides in 4 months. Illegal mining strips vegetation, destabilizes slopes. Roads cut off. Communities isolated. BPBD lacks real-time field data.

Existing solutions — IoT sensors, satellite imagery, drone surveys — are **too expensive** and **can't reach** rural villages.

**What if every citizen with a smartphone could detect landslides before they happen?**

---

## Our Solution

**Retak.id** — an Android + Web platform that classifies soil crack severity from a single photo, computes multi-factor risk in real-time, aggregates reports, and feeds verified data back into retraining. **On-device ML. Offline-first. Free.**

<p align="center">
  <strong>Point camera → Take photo → Instant risk level → Live dashboard → Verify → Retrain</strong>
</p>

| Risk Level | Description | Action |
|------------|-------------|--------|
| **AMAN** (Safe) | Minor natural cracks | No immediate action |
| **WASPADA** (Caution) | Significant cracks developing | Monitor + report to RT/RW |
| **BAHAYA** (Danger) | Critical ground displacement | Evacuate + contact BPBD |

### Why This Works

1. **Zero infrastructure.** Runs on smartphones people already own. ML inference works fully offline — no signal needed in deep rural slopes.
2. **Hyperlocal coverage.** Citizens cover every path, every hillside, every day. No static sensor can match that.
3. **Evidence-based advocacy.** Aggregated citizen reports become hard data for BPBD via the web dashboard.
4. **Continuous improvement.** Every admin-verified report becomes training data for the next model version.

---

## System Architecture

```
┌─────────────────────────┐     ┌─────────────────────┐     ┌───────────────────────────┐
│       ANDROID APP       │     │      SUPABASE       │    │       WEB PLATFORM        │
│   (Kotlin / Jetpack)    │     │   (PostgreSQL + EF) │     │   (React / Vite / PWA)    │
│                         │     │                     │     │                           │
│  CameraX → TFLite INT8  │────▶│  laporan            │◀────│  Dashboard (Leaflet map)  │
│  MultiFactorRiskEngine  │     │  riwayat_penanganan │     │  ReportForm (LiteRT Wasm) │
│  Elevation / Slope      │     │  model_versions     │     │  Admin: VerificationDialog│
│  Weather / Soil Type    │     │  auth / storage     │     │  Export Training Data CSV │
│  Delta Model Updater    │     │                     │     │  Riwayat Penanganan       │
│  Offline-first          │     │  5 Edge Functions   │     │  Dark/Light mode          │
└─────────────────────────┘     └─────────────────────┘     └───────────────────────────┘
```

### Multi-Factor Risk Engine

Setiap laporan tidak hanya dinilai dari foto — **5 faktor digabung dalam 300ms:**

```
                         ┌─────────────────────────────┐
                         │     MultiFactorRiskEngine    │
                         │        (Kotlin + Deno)       │
                         ├─────────────────────────────┤
                         │ ML Visual Analysis   (50%)  │
                         │    ← TFLite INT8 on-device  │
                         │                             │
                         │ Slope (Kemiringan)   (20%)  │
                         │    ← 5-titik elevasi API    │
                         │                             │
                         │ Rainfall (Curah Hujan)(15%) │
                         │    ← Open-Meteo API         │
                         │                             │
                         │ Elevation (Ketinggian)(10%) │
                         │    ← Open-Meteo API / SRTM  │
                         │                             │
                         │ Soil Type (Jenis Tanah)(5%) │
                         │    ← ISRIC SoilGrids API    │
                         ├─────────────────────────────┤
                         │ AMAN floor score = 0.1      │
                         │ Graceful degradation ✓      │
                         └─────────────────────────────┘
```

### Deployment Pipeline (4 Stages)

```
Stage 1: ML Pipeline
  Scrape → Validate → Dedup → Split → Train MobileNetV2
  → INT8 PTQ → Validate → Registry

Stage 2: Multi-Factor Risk
  MultiFactorRiskEngine (Kotlin + Deno)
  → 5 environmental factors
  → Telegram/Slack alert on BAHAYA

Stage 3: Human-in-the-Loop Verification
  Admin VerificationDialog (Sesuai / Koreksi label)
  → Export Training Data CSV
  → ingest_verification.py (download + dedup + save)
  → make split && make train

Stage 4: Delta OTA Model Updates
  compute_delta.py (byte-level diff + gzip → .rkd)
  deploy_delta.py (upload + register version)
  → check-model-update edge function
  → DeltaModelLoader (download → patch → validate)
```

---

## Key Features

### On-Device ML (Fully Offline)

```
CameraX → Bitmap → Resize 224×224 → uint8 RGB [0, 255]
                ↓
     TFLite INT8 Model (2.6MB, on-device, CPU 4 threads)
                ↓
     float32 [1, 3] logits → softmax → AMAN/WASPADA/BAHAYA
                ↓
     <50ms inference (Pixel 4a) — no internet required
```

### Web Dashboard — ML via Browser

```
Upload/camera foto → Canvas resize → uint8 tensor
                ↓
   LiteRT.js WebAssembly (XNNPack CPU / WebGPU)
                ↓
   Auto-fill status → User override? → Submit report
                ↓
   PWA: model + WASM runtime cached 30 hari (CacheFirst)
```

### Human-in-the-Loop Verification

```
VerificationDialog:
  ├─ Foto laporan + info lokasi/pelapor/waktu
  ├─ Hasil prediksi ML + confidence bar
  ├─ "Apakah hasil ini sesuai?"
  │   ├─ ✅ Sesuai → label_akhir = prediksi ML
  │   └─ ❌ Tidak Sesuai → pilih label benar (AMAN/WASPADA/BAHAYA)
  └─ Submit → riwayat_penanganan (alasan, detail JSONB)

Export CSV → ingest_verification.py (phash dedup)
  → backend/data/processed/{label_akhir}/
  → make split && make train → Model v3b, v3c, ...
```

Setiap verifikasi — benar atau salah — menjadi data training. Tidak ada yang terbuang.

### Delta OTA Model Updates

```
Kompresi delta (byte-level diff + gzip → .rkd):
  Bundled model (v3a) ⋈ New model (v3b)
    → changed_regions + patched_bytes
    → gzip → upload ke Supabase Storage

Android:
  ModelUpdateChecker → check-model-update edge function
    → download .rkd → gzip decompress
    → patch byte regions (dari bundled assets, bukan cache!)
    → validate via TFLite Interpreter → save ke internal storage
```

Estimasi: **0.3–0.8 MB** per update (vs 2.6 MB full model) — **70–90% bandwidth savings**.

---

## Model Performance

| Metric | Value |
|--------|-------|
| **Test Accuracy** | **84.9%** |
| Best Val Accuracy | 81.6% |
| Model Size (INT8) | 2.6 MB |
| FP32 → INT8 Agreement | 93.75% |
| Inference Latency | <50ms (Pixel 4a) |
| Input | uint8 [1, 224, 224, 3] RGB |
| Output | float32 [1, 3] logits |
| Classes | AMAN / WASPADA / BAHAYA |

### Dataset

| Class | Samples | Source |
|-------|---------|--------|
| AMAN | 2,009 | Scraped + manually annotated |
| WASPADA | 768 | Scraped + manually annotated |
| BAHAYA | 767 | Scraped + manually annotated |
| **Total** | **3,547** | 70+ DDG search queries |

### Training Evolution

```
Baseline (frozen)         ████████░░░░░░░░░░  73.0%
+ Fine-tuning             █████████░░░░░░░░░  76.7%
+ Conservative FT         ██████████░░░░░░░░  81.8%
+ Clean Labels            ████████████░░░░░░  84.9%  ← Production (v3a)
```

### Validation Gate (Pre-Deployment)

7 checks sebelum model masuk registry:
1. Load (no corruption) ✓
2. Input shape [1, 224, 224, 3] ✓
3. Output dtype float32 ✓
4. Inference runs without error ✓
5. Multi-class output (3 logits) ✓
6. Confidence distribution reasonable ✓
7. Cross-validation ≥ benchmark thresholds ✓

### Key Thresholds

| Metric | Minimum | Current |
|--------|---------|---------|
| BAHAYA Recall | ≥ 72% | **84.9%** |
| BAHAYA Precision | ≥ 70% | **86.2%** |
| Test Accuracy | ≥ 82% | **84.9%** |
| Macro F1 | ≥ 0.75 | **0.83** |

---

## Tech Stack

| Layer | Teknologi |
|-------|-----------|
| **Mobile** | Kotlin, Jetpack Compose, CameraX, TensorFlow Lite (INT8) |
| **Web** | React 18, Vite 6, TypeScript, Tailwind CSS 3, Leaflet, React Router 6, LiteRT.js (Wasm/WebGPU) |
| **ML** | Python 3.11, TensorFlow 2.15+, MobileNetV2 (transfer learning), INT8 PTQ, imagehash |
| **Backend (BaaS)** | Supabase — PostgreSQL, Auth, Storage, Realtime, Edge Functions (Deno) |
| **Risk Engine** | MultiFactorRiskEngine — slope, rainfall, elevation, soil type (Open-Meteo + ISRIC) |
| **Data Pipeline** | DuckDuckGo Image Scraping, perceptual hashing (phash), OpenCV, PIL |
| **Experiment Tracking** | MLflow, DagsHub |
| **Data Versioning** | DVC (remote: DagsHub S3-compatible) |
| **Package Manager** | `uv` (Python), `npm` (Node.js) |
| **Testing** | pytest (16 backend tests), TypeScript strict mode (web), Delta round-trip tests |
| **Deploy** | Vercel (web), Docker (training), Supabase (edge functions) |
| **Delta OTA** | Custom `.rkd` format (byte-region patches + gzip), Supabase Storage |
| **Notifications** | Telegram Bot — BAHAYA alerts + daily summary |
| **Telegram Bot** | Python (python-telegram-bot v20+), ConversationHandler, TFLite runtime, httpx |

---

## Project Structure

```
retakId/
├── web-app/                      # Web Dashboard (React + Vite + TypeScript)
│   ├── src/
│   │   ├── components/           # MapView, VerificationDialog, OnboardingTour, etc.
│   │   ├── pages/                # AdminDashboard, RiwayatPenanganan, ReportForm
│   │   ├── hooks/                # useLaporan, useModelInference (LiteRT)
│   │   ├── context/              # ThemeContext, AuthContext, ToastContext
│   │   ├── utils/                # exportTrainingData, preprocess, cn
│   │   └── lib/                  # risk.ts (edge function client), Supabase
│   └── public/
│       └── models/retak/         # TFLite model (native .tflite)
│
├── mobile-app/                   # Android App (Kotlin + Jetpack Compose)
│   └── app/src/main/
│       ├── assets/               # TFLite model + labels
│       ├── java/.../data/
│       │   ├── ml/               # MLAnalyzer, DeltaModelLoader, ModelUpdateChecker
│       │   ├── risk/             # MultiFactorRiskEngine, ElevationService
│       │   ├── weather/          # WeatherApiService (Open-Meteo)
│       │   ├── soil/             # SoilTypeService (ISRIC + fallback)
│       │   └── elevation/        # ElevationService, SlopeCalculator
│       └── java/.../ui/          # CameraX + Compose screens + theme
│
├── bot/                          # Telegram Bot (Python)
│   ├── src/
│   │   ├── handlers/             # lapor.py (wizard), admin.py, start.py
│   │   ├── ml/                   # TFLite inference
│   │   ├── risk/                 # MultiFactorRiskEngine
│   │   ├── services/             # weather, elevation, slope, soil, supabase
│   │   └── middleware/           # Rate limiter
│   ├── Dockerfile
│   └── README.md
│
├── backend/
│   ├── config/                   # training.yaml, benchmark.yaml, grid search
│   ├── scripts/
│   │   ├── scraping/             # DDG scraper (dedup + blur + quality)
│   │   ├── training/             # ingest_verification, compute_delta, deploy_delta
│   │   └── processing/           # Dataset validation + split + stats
│   ├── src/training/             # Train + evaluate + export + augment
│   ├── edge-functions/           # check-model-update, notify-bahaya, daily-summary
│   ├── supabase/                 # seed.sql, rls_policies.sql
│   ├── tests/                    # 16 automated tests + delta round-trip
│   └── models/                   # Model artifacts + labels
│
├── supabase/functions/           # Supabase Edge Functions (Deno)
│   └── calculate-risk/           # MultiFactorRiskEngine (Deno)
│       ├── index.ts              # HTTP handler
│       ├── engine.ts             # Risk scoring engine
│       ├── types.ts              # TypeScript types
│       └── services/             # elevation, slope, rainfall, soil
│
├── docs/                         # Pitch preparation & technical docs
│   ├── model_detail.md           # ML pipeline (335 lines, pitch-ready)
│   ├── verify_retrain.md         # HITL → retrain loop Q&A
│   ├── coverage_bias.md          # Blind spot & drone Q&A
│   ├── adoption_access.md        # App vs WhatsApp Q&A
│   ├── mitigation_false_negative.md  # 25-layer defense-in-depth
│   ├── minimum_spec.md           # Minimum device specs (2GB RAM, Android 8+)
│   └── multi_factor_risk.md      # Risk engine architecture
│
├── scripts/                      # Bootstrap, validation, registry
├── DOKUMENTASI.md                # Dokumentasi lengkap (Bahasa Indonesia)
├── Makefile                      # 20+ targets
└── pyproject.toml                # Dependencies (uv)
```

---

## Quick Start

### Web Dashboard

```bash
cd web-app
cp .env.example .env.local     # Isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY
npm install
npm run dev                     # http://localhost:5173
```

**Client-side ML:** LiteRT.js WebAssembly — model `.tflite` langsung dari `public/models/`.
**PWA:** Install ke home screen, model + WASM cached 30 hari.

### ML Pipeline

```bash
# Bootstrap (auto-installs Python deps, pulls data)
git clone https://github.com/jaweed3/retakId.git && cd retakId
bash scripts/bootstrap.sh

# Train
make split && make train

# Ingest verified training data
python backend/scripts/training/ingest_verification.py --csv training_data.csv
make split && make train

# Delta OTA
python backend/scripts/training/compute_delta.py --base model_v3a.tflite --new model_v3b.tflite
make deploy-delta
```

### Android App

```bash
git checkout mobile-app
# Buka di Android Studio, pastikan local.properties berisi kredensial Supabase
# Build & run
```

---

## Telegram Bot

Bot Telegram untuk pelaporan via chat — `/lapor` wizard dengan 3 langkah:

| Langkah | State | Aksi |
|---------|-------|------|
| 1 | `PHOTO` | Kirim foto retakan → ML inference |
| 2 | `LOCATION` | Kirim lokasi → ambil 4 data lingkungan (paralel) |
| 3 | `CONFIRM` | Review hasil → Simpan / Ulangi / Batal |

**Komponen:** `bot/src/handlers/lapor.py` — ConversationHandler (python-telegram-bot v20+).

Detail lengkap: [`bot/README.md`](bot/README.md)

## Edge Functions

| Function | Trigger | Purpose |
|----------|---------|---------|
| **calculate-risk** | HTTP POST | MultiFactorRiskEngine: ML + slope + rain + elev + soil |
| **check-model-update** | HTTP POST | Delta OTA version check → delta/full model URL |
| **notify-bahaya** | DB INSERT | Telegram alert for BAHAYA reports |
| **daily-summary** | Cron (24h) | Daily stats to Telegram admin chat |
| **moderate-spam** | DB INSERT | Rate-limit: >5 reports/min from same reporter |
| **auto-cleanup** | Cron (24h) | Archive unverified reports >90 days old |

---

## Engineering Highlights

| Area | What We Built |
|------|--------------|
| **Multi-Factor Risk** | 5-factor risk engine (ML 50%, slope 20%, rain 15%, elevation 10%, soil 5%) — graceful degradation, AMAN floor 0.1. Implemented identically in Kotlin + Deno. |
| **HITL Verification** | VerificationDialog with photo + ML result + label correction → export CSV → ingest script → retrain loop. Every verification becomes training data. |
| **Delta OTA Updates** | Custom `.rkd` format (byte-region patches + gzip). 70–90% bandwidth savings. Python compute + Kotlin apply — round-trip verified bit-exact. |
| **Offline-First ML** | TFLite INT8 inference on-device — <50ms, 2.6MB, no internet. Fallback gracefully when environmental APIs timeout. |
| **Client-Side ML (Web)** | LiteRT.js loads native .tflite via WebAssembly XNNPack — no server round-trip. PWA cached 30 days. |
| **Crowdsourcing Dashboard** | Leaflet map, realtime, dark/light mode, filter by risk, VerificationDialog, Export CSV, Riwayat Penanganan. |
| **Validation Gate** | 7 pre-deployment checks (load, shape, dtype, run, multi-class, confidence, CV). All-or-nothing promotion. |
| **Reproducibility** | Config-driven pipeline, fixed seeds, locked deps, Docker, one-command bootstrap, MLflow tracking. |
| **Data Quality** | Perceptual hash dedup (phash, threshold=6), blur detection, size filtering, cross-class leak prevention. |
| **Experiment Tracking** | MLflow on DagsHub cloud — every run logged with params, metrics, artifacts. |
| **PWA** | Service worker with Workbox: API (NetworkFirst, 5min), tiles (CacheFirst, 1d), model + WASM (CacheFirst, 30d). Standalone installable. |

---

## Documentation for Pitch

| Doc | For |
|-----|-----|
| `docs/model_detail.md` | ML pipeline: architecture, dataset, training, safety gates, 4-stage strategy |
| `docs/mitigation_false_negative.md` | 25-layer defense-in-depth against false negatives |
| `docs/minimum_spec.md` | Minimum device specs (2GB RAM, 4×A53 1.3GHz, Android 8+) |
| `docs/multi_factor_risk.md` | Risk engine architecture & scoring logic |
| `docs/verify_retrain.md` | HITL → retrain loop Q&A counter-attack |
| `docs/coverage_bias.md` | Blind spot & drone Q&A counter-attack |
| `docs/adoption_access.md` | App vs WhatsApp Q&A counter-attack |

---

## Team

| Role | Member |
|------|--------|
| **ML Engineer** | Jaweed (Fatih) — pipeline, training, quantization, model registry, delta OTA |
| **Data Acquisition & Web** | Farrel Ghozy — scraping, dataset annotation, DVC, web dashboard, edge functions |
| **Android Developer** | Adam Nurwahid — Kotlin, CameraX, TFLite integration, risk engine, UI/UX |

Universitas Darussalam Gontor, Ponorogo

---

## License

MIT · IYREF 2026 Submission
