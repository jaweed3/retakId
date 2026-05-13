<p align="center">
  <img src="https://img.shields.io/badge/Accuracy-84.9%25-success?style=for-the-badge" alt="Accuracy">
  <img src="https://img.shields.io/badge/Model_Size-2.6MB-blue?style=for-the-badge" alt="Model Size">
  <img src="https://img.shields.io/badge/Inference-<50ms-orange?style=for-the-badge" alt="Inference">
  <img src="https://img.shields.io/badge/Platform-Android%20%26%20Web-green?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/Web-Dashboard-teal?style=for-the-badge" alt="Web">
  <img src="https://img.shields.io/badge/Offline-First-black?style=for-the-badge" alt="Offline">
  <img src="https://img.shields.io/badge/Client_ML-LiteRT.js-34A853?style=for-the-badge" alt="LiteRT">
</p>

<h1 align="center">Retak.id</h1>
<h3 align="center">Crowdsourcing Early Detection of Landslide Soil Cracks</h3>
<p align="center"><strong>IYREF 2026 Semi-Final · Climate Resilience & Local Wisdom</strong></p>

---

## The Problem

**Jenangan, Ponorogo.** 41 landslides in 4 months. Illegal mining strips vegetation, destabilizes slopes. Roads cut off. Communities isolated. BPBD lacks real-time field data.

Existing solutions — IoT sensors, satellite imagery — are **too expensive** and **can't reach** rural villages.

**What if every citizen with a smartphone could detect landslides before they happen?**

---

## Our Solution

**Retak.id** — an Android + Web platform that classifies soil crack severity from a single photo, aggregates reports, and displays them on a live dashboard. **On-device ML. Offline-first. Free.**

<p align="center">
  <strong>Point camera → Take photo → Instant risk level → Live dashboard</strong>
</p>

| Risk Level | Description | Action |
|------------|-------------|--------|
| **AMAN** (Safe) | Minor natural cracks | No immediate action |
| **WASPADA** (Caution) | Significant cracks developing | Monitor + report to RT/RW |
| **BAHAYA** (Danger) | Critical ground displacement | Evacuate + contact BPBD |

### Why This Works

1. **Zero infrastructure.** Runs on smartphones people already own. No cell signal needed — works deep in rural slopes.
2. **Hyperlocal coverage.** Citizens cover every path, every hillside, every day. No static sensor can match that.
3. **Evidence-based advocacy.** Aggregated citizen reports become hard data for BPBD via the web dashboard.

---

## System Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌───────────────────┐
│   ANDROID APP   │────▶│    SUPABASE      │◀────│   WEB DASHBOARD   │
│  (Kotlin/TFLite)│     │  (PostgreSQL)    │     │  (React/Vite)     │
│                 │     │  + Auth          │     │                   │
│  CameraX        │     │  + Storage       │     │  Peta interaktif  │
│  TFLite INT8    │     │  + Realtime      │     │  List laporan     │
│  Offline-first  │     │                  │     │  Filter + Search  │
└─────────────────┘     └──────────────────┘     │  ML Auto-Detect    │
                                                  │  (LiteRT Wasm)     │
                                                  └───────────────────┘
```

### Android App (on-device inference)

```
CameraX → Bitmap → Resize 224×224 → uint8 RGB
               ↓
    TFLite INT8 Model (2.6MB, on-device)
               ↓
    AMAN / WASPADA / BAHAYA + Confidence
               ↓
    Kirim laporan ke Supabase (foto + GPS + status)
```

### Web Dashboard (client-side inference)

```
Upload foto → Canvas resize 224×224 → uint8 RGB [0, 255]
               ↓
  LiteRT Wasm (.tflite, 2.6MB, XNNPack CPU/WebGPU)
               ↓
  AMAN / WASPADA / BAHAYA + Confidence
               ↓
  Auto-fill status di form → User override? → Kirim laporan
```

### ML Pipeline (offline training)

```
Scrape → Validate → Deduplicate → Split (70/15/15)
    ↓
MobileNetV2 + Fine-Tuning + Augmentation
    ↓
INT8 PTQ → TFLite Export → Model Registry
    ↓
Deploy → Android assets/ + Web (native .tflite)
```

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
Baseline (frozen)   ████████░░░░░░░░░░  73.0%
+ Fine-tuning       █████████░░░░░░░░░  76.7%
+ Conservative FT   ██████████░░░░░░░░  81.8%
+ Clean Labels      ██████████░░░░░░░░  84.9%  ← Production
```

---

## Tech Stack

| Layer | Teknologi |
|-------|-----------|
| **Mobile** | Kotlin, Jetpack Compose, CameraX, TensorFlow Lite (INT8) |
| **Web** | React 18, Vite 6, TypeScript, Tailwind CSS 3, Leaflet, React Router 6, LiteRT.js (Wasm/WebGPU) |
| **ML** | Python 3.11, TensorFlow 2.15+, MobileNetV2 (transfer learning), INT8 PTQ |
| **Backend (BaaS)** | Supabase — PostgreSQL, Auth, Storage, Realtime |
| **Data Pipeline** | DuckDuckGo Image Scraping, perceptual hashing, OpenCV |
| **Experiment Tracking** | MLflow, DagsHub |
| **Data Versioning** | DVC (remote: DagsHub S3-compatible) |
| **Package Manager** | `uv` (Python), `npm` (Node.js) |
| **Testing** | pytest (16 backend tests), TypeScript strict mode (web) |
| **Deploy** | Vercel (web), Docker (training) |

---

## Project Structure

```
retakId/
├── web-app/                      # Web Dashboard (React + Vite + TypeScript)
│   ├── src/
│   │   ├── components/           # MapView, LaporanCard, FilterStatusBar, dll
│   │   ├── pages/                # DashboardPage, ReportsPage, ReportFormPage
│   │   ├── hooks/                # useLaporan, useModelInference (LiteRT)
│   │   ├── context/              # ThemeContext (dark/light mode)
│   │   ├── utils/                # preprocess (image → tensor), cn, statusColors
│   │   └── lib/                  # Supabase client
│   └── public/
│       └── models/retak/         # TFLite model (native .tflite)
│
├── mobile-app/                   # Android App (Kotlin + Jetpack Compose)
│   └── app/src/main/
│       ├── assets/               # TFLite model + labels
│       ├── java/.../data/        # SupabaseClient + ViewModels
│       └── java/.../ui/          # CameraX + Compose screens + theme
│
├── backend/
│   ├── config/                   # YAML configs + grid search + benchmark
│   ├── scripts/
│   │   ├── scraping/             # DDG scraper (dedup + blur + quality)
│   │   └── processing/           # Dataset validation + split + stats
│   ├── src/training/             # Train + evaluate + export + augment
│   ├── tests/                    # 16 automated tests
│   └── models/                   # Model artifacts + labels
│
├── docs/                         # Architecture, inference contract, guides
├── scripts/                      # Bootstrap, grid search, CV, registry
├── DOKUMENTASI.md                # Dokumentasi lengkap (Bahasa Indonesia)
├── Makefile                      # 20+ targets
└── pyproject.toml                # Dependencies (uv)
```

> **Catatan**: Android app berada di branch `mobile-app`. Web dashboard di branch `main`.

---

## Quick Start

### Web Dashboard

```bash
cd web-app
cp .env.example .env.local     # Isi VITE_SUPABASE_URL dan VITE_SUPABASE_ANON_KEY
npm install
npm run dev                     # http://localhost:5173
```

> **Client-side ML**: Deteksi otomatis via LiteRT.js (WebAssembly/XNNPack).
> Model `.tflite` di `web-app/public/models/retak/` langsung di-load tanpa
> konversi. Jalankan `bash scripts/deploy_model_web.sh` untuk menyalin model
> terbaru dari Android assets. Wasm & model di-cache PWA selama 30 hari.

### ML Pipeline

```bash
# Clone & bootstrap (auto-installs Python, deps, pulls data)
git clone https://github.com/jaweed3/retakId.git && cd retakId
bash scripts/bootstrap.sh

# Train
make split && make train

# Full pipeline
make train-and-deploy
```

### Android App

```bash
git checkout mobile-app
# Buka di Android Studio, pastikan local.properties berisi kredensial Supabase
# Build & run
```

---

## Engineering Highlights

| Area | What We Built |
|------|--------------|
| **Crowdsourcing Dashboard** | React SPA dengan peta Leaflet, realtime update, dark/light mode, responsive |
| **Offline-First ML** | TFLite INT8 inference di HP tanpa internet — 45ms latency, 2.6MB model |
| **Reproducibility** | Config-driven pipeline, fixed seeds, locked deps, Docker, one-command bootstrap |
| **Experiment Tracking** | MLflow on DagsHub cloud — every run logged with params, metrics, artifacts |
| **Model Registry** | Automated promotion: benchmark thresholds + cross-validation + champion comparison |
| **Data Quality** | Perceptual hash dedup, blur detection, size filtering, cross-class leak prevention |
| **Validation Gate** | Pre-deployment TFLite test mirrors Android inference exactly — broken models blocked |
| **Client-Side ML** | LiteRT.js loads native .tflite via WebAssembly (XNNPack) — photo upload auto-detects crack severity without any server round-trip, ~2.6MB model, optional WebGPU acceleration |
| **Grid Search** | Auto-generated config combinations with resume support for disconnected SSH |
| **Minimal Footprint** | 2.6MB INT8 model. No server. No API. No cloud dependency. |

---

## Team — SAYA AKAN LAWAN

| Role | Member |
|------|--------|
| **ML Engineer** | Jaweed (Fatih) — pipeline, training, quantization, model registry |
| **Data Acquisition & Web** | Farrel Ghozy — scraping, dataset annotation, DVC, web dashboard |
| **Android Developer** | Adam Nurwahid — Kotlin, CameraX, TFLite integration, UI/UX |

Universitas Darussalam Gontor, Ponorogo

---

## Documentation

- [DOKUMENTASI.md](DOKUMENTASI.md) — dokumentasi lengkap seluruh proyek (Bahasa Indonesia)
- [Getting Started](docs/getting_started.md) — full command reference
- [Architecture](docs/architecture.md) — system design & data flow
- [Model Details](docs/model_detail.md) — architecture, training, quantization
- [Inference Contract](docs/inference_contract.md) — Android integration spec
- [Android Integration](docs/android_integration.md) — copy-paste Kotlin guide
- [DVC Workflow](docs/dvc_workflow.md) — dataset versioning
- [Scraping Guide](docs/scraping_guide.md) — data acquisition & annotation
- [Portfolio Blog](docs/portfolio_blog.md) — ML engineering case study

---

## License

MIT · IYREF 2026 Submission
