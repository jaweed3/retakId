<p align="center">
  <img src="https://img.shields.io/badge/Accuracy-84.9%25-success?style=for-the-badge" alt="Accuracy">
  <img src="https://img.shields.io/badge/Model_Size-2.6MB-blue?style=for-the-badge" alt="Model Size">
  <img src="https://img.shields.io/badge/Inference-<50ms-orange?style=for-the-badge" alt="Inference">
  <img src="https://img.shields.io/badge/Platform-Android-green?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/Offline-First-black?style=for-the-badge" alt="Offline">
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

**Retak.id** — an Android app that classifies soil crack severity from a single photo. **On-device. Offline. Free.**

<p align="center">
  <strong>Point camera → Take photo → Instant risk level</strong>
</p>

| Risk Level | Description | Action |
|------------|-------------|--------|
| **AMAN** (Safe) | Minor natural cracks | No immediate action |
| **WASPADA** (Caution) | Significant cracks developing | Monitor + report to RT/RW |
| **BAHAYA** (Danger) | Critical ground displacement | Evacuate + contact BPBD |

### Why This Works

1. **Zero infrastructure.** Runs on smartphones people already own. No cell signal needed — works deep in rural slopes.
2. **Hyperlocal coverage.** Citizens cover every path, every hillside, every day. No static sensor can match that.
3. **Evidence-based advocacy.** Aggregated citizen reports become hard data to push for illegal mining enforcement.

---

## Architecture

```
┌────────────────── ANDROID APP ──────────────────┐
│                                                   │
│  CameraX → Bitmap → Resize 224×224 → uint8 RGB   │
│                ↓                                  │
│     TFLite INT8 Model (2.6MB, on-device)         │
│                ↓                                  │
│     AMAN / WASPADA / BAHAYA + Confidence         │
│                                                   │
└───────────────────────────────────────────────────┘

┌────────────────── ML PIPELINE ──────────────────┐
│                                                   │
│  Scrape → Validate → Deduplicate → Split          │
│     ↓                                              │
│  MobileNetV2/V3 + Fine-Tuning + Augmentation     │
│     ↓                                              │
│  INT8 PTQ → TFLite Export → Model Registry       │
│     ↓                                              │
│  Deploy → Android assets/                         │
│                                                   │
└───────────────────────────────────────────────────┘
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
| AMAN | 2,011 | Scraped + manually annotated |
| WASPADA | 766 | Scraped + manually annotated |
| BAHAYA | 768 | Scraped + manually annotated |
| **Total** | **3,545** | 70+ DDG search queries |

### Training Evolution

```
Baseline (frozen)   ████████░░░░░░░░░░  73.0%
+ Fine-tuning       █████████░░░░░░░░░  76.7%
+ Conservative FT   ██████████░░░░░░░░  81.8%
+ Clean Labels      ██████████░░░░░░░░  84.9%  ← Production
```

---

## Quick Start

```bash
# Clone & bootstrap (auto-installs Python, deps, pulls data)
git clone https://github.com/jaweed3/retakId.git && cd retakId
bash scripts/bootstrap.sh

# Train
make split && make train

# Validate model before release
make validate-model

# Deploy to Android
make deploy-model

# Full pipeline
make train-and-deploy
```

---

## Project Structure

```
retakId/
├── mobile-app/                  # Android App (Kotlin + Jetpack Compose)
│   └── app/src/main/
│       ├── assets/              # TFLite model + labels
│       ├── java/.../data/ml/    # TFLite Interpreter + preprocessing
│       └── java/.../ui/         # CameraX + Compose screens
│
├── backend/
│   ├── config/                  # YAML configs + grid search + benchmark
│   ├── scripts/
│   │   ├── scraping/            # DDG scraper (dedup + blur + quality)
│   │   └── processing/          # Dataset validation + split + stats
│   ├── src/training/            # Train + evaluate + export + augment
│   ├── tests/                   # 16 automated tests
│   └── models/                  # Model artifacts + model card
│
├── docs/                        # Architecture, inference contract, guides
├── scripts/                     # Bootstrap, grid search, CV, registry
├── Makefile                     # 20+ targets
└── pyproject.toml               # Dependencies (uv)
```

---

## Engineering Highlights

| Area | What We Built |
|------|--------------|
| **Reproducibility** | Config-driven pipeline, fixed seeds, locked deps, Docker, one-command bootstrap |
| **Experiment Tracking** | MLflow on DagsHub cloud — every run logged with params, metrics, artifacts |
| **Model Registry** | Automated promotion: benchmark thresholds + cross-validation + champion comparison |
| **Data Quality** | Perceptual hash dedup, blur detection, size filtering, cross-class leak prevention |
| **Validation Gate** | Pre-deployment TFLite test mirrors Android inference exactly — broken models blocked |
| **Grid Search** | Auto-generated config combinations with resume support for disconnected SSH |
| **Minimal Footprint** | 2.6MB INT8 model. No server. No API. No cloud dependency. |

---

## Team — SAYA AKAN LAWAN

| Role | Member |
|------|--------|
| **ML Engineer** | Jaweed — pipeline, training, quantization, model registry |
| **Data Acquisition** | Farrel — scraping infrastructure, dataset annotation, DVC |
| **Android Developer** | Adam — Kotlin, CameraX, TFLite integration, UI/UX |

Universitas Darussalam Gontor, Ponorogo

---

## Documentation

- [Getting Started](docs/getting_started.md) — full command reference
- [Architecture](docs/architecture.md) — system design & data flow
- [Model Details](docs/model_detail.md) — architecture, training, quantization
- [Inference Contract](docs/inference_contract.md) — Android integration spec
- [Android Integration](docs/android_integration.md) — copy-paste Kotlin guide
- [Model Card](backend/models/model_card.md) — performance & limitations
- [DVC Workflow](docs/dvc_workflow.md) — dataset versioning
- [Portfolio Blog](docs/portfolio_blog.md) — ML engineering case study

---

## License

MIT · IYREF 2026 Submission
