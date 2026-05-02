# Getting Started

## Prerequisites (laptop sendiri)

- Python 3.10–3.12 (TensorFlow doesn't support 3.13+ yet)
- [uv](https://github.com/astral-sh/uv) installed
- Android Studio (for app development)

## PC Lab — Zero Setup Training

Kalo di PC lab dan males setup Python/TF/DVC manual, pake bootstrap script:

```bash
git clone https://github.com/jaweed3/retakId.git && cd retakId
bash scripts/bootstrap.sh
make train
```

**Yang dilakukan `bootstrap.sh`:**
1. Install `uv` (Python package manager) — zero system dependency
2. Install Python 3.11 via uv (standalone, ga ganggu system Python)
3. Install semua dependencies (~2GB, sekali aja)
4. Pull dataset dari DagsHub via DVC

Setelah selesai, langsung bisa `make train`. Ga perlu install apa-apa manual.

## PC Lab — Alternative: Docker

```bash
docker build -t retakid-train -f backend/Dockerfile .
docker run retakid-train
```

Docker image includes: Python 3.11, TF 2.19, DVC, all deps. Auto-pulls data from DagsHub.

## Backend Setup (laptop sendiri)

1. Clone the repository
2. `make setup` to install dependencies via `uv`
3. `make pull-data` to download dataset from DagsHub DVC
4. Pipeline siap

## Full ML Pipeline

```bash
# Data (kalo belum ada di DagsHub)
make scrape KW="landslide soil cracks, retakan tanah longsor, ground fissure, soil surface cracks" LIMIT=150
# ... manual annotation into data/processed/AMAN|WASPADA|BAHAYA ...

# Validation
make validate       # Check dataset integrity
make deduplicate    # Cross-class near-duplicate detection
make stats          # Dataset statistics & class distribution
make split          # Stratified 70/15/15 train/val/test split

# Training
make train          # Full pipeline: train → evaluate → export TFLite INT8
make test           # 16 tests

# Monitor
tensorboard --logdir backend/logs/tensorboard

# Export from checkpoint
make export MODEL=backend/models/checkpoints/best.keras

# Deploy to Android app
make deploy ASSETS=app/src/main/assets
```

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make setup` | Install dependencies via uv |
| `make lab-setup` | Bootstrap fresh PC (install uv + Python + deps + data) |
| `make pull-data` | Pull dataset from DagsHub via DVC |
| `make lab-train` | Full pipeline: pull data → validate → dedup → split → train |
| `make scrape KW="..." LIMIT=100` | Scrape images from DuckDuckGo |
| `make validate` | Check dataset for corrupt/bad images |
| `make deduplicate` | Detect cross-class near-duplicates |
| `make stats` | Print dataset statistics |
| `make split` | Stratified 70/15/15 train/val/test split |
| `make train` | Full training pipeline (train → eval → export) |
| `make evaluate` | Evaluate a trained model checkpoint |
| `make export MODEL=path` | Export TFLite from checkpoint |
| `make deploy` | Copy model + labels to Android assets |
| `make test` | Run pytest suite (16 tests) |
| `make docker-build` | Build Docker training image |
| `make docker-train` | Run training in Docker |
| `make lint` | Check code formatting |
| `make format` | Auto-format code with black |

## Model Artifacts

After training:
- `backend/models/retak_mobilenetv2.tflite` — INT8 quantized model (<5MB)
- `backend/models/retak_mobilenetv2_int8.tflite` — INT8 variant
- `backend/models/labels.txt` — Class labels (AMAN, WASPADA, BAHAYA)
- `backend/models/checkpoints/best.keras` — Best Keras checkpoint

## Model Integration
```bash
make deploy ASSETS=app/src/main/assets
```
