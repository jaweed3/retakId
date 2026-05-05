# Getting Started — Retak.id

## PC Lab — Zero Setup (sekali aja)

```bash
git clone https://github.com/jaweed3/retakId.git && cd retakId
bash scripts/bootstrap.sh
```

## Quick Reference Card

```bash
# ===== DATA PIPELINE =====
make scrape KW="landslide soil cracks, retakan tanah" LIMIT=150   # Scrape dataset
make validate                                                        # Cek corrupt images
make deduplicate                                                     # Cross-class dedup
make stats                                                           # Dataset statistics
make split                                                           # 70/15/15 train/val/test

# ===== TRAINING =====
make train                                       # Single training run
make grid-gen && make grid-run                   # Grid search (36 experiments, resume-safe)

# ===== EVALUATION =====
make cv                                          # 5-fold cross-validation
make evaluate                                    # Evaluate checkpoint on test set

# ===== MODEL REGISTRY =====
make register RUN_ID=<mlflow_run_id>             # Promote model (check benchmark + compare)
make list-models                                 # List registered models
make deploy-registered ASSETS=app/src/main/assets # Download best model for Android

# ===== MONITORING =====
mlflow ui --backend-store-uri file://$(pwd)/backend/logs/mlruns  # Local MLflow
# Cloud: buka https://dagshub.com/jaweed3/retakId.mlflow

# ===== TESTING =====
make test                                        # 16 pytest tests

# ===== UTILS =====
make lint                                        # Check formatting
make format                                      # Auto-format code
make clean                                       # Remove cache
```

## Full Workflow (Training → Production)

```bash
# 1. Setup data (sekali aja, kalo belum)
make validate && make deduplicate && make split

# 2. Grid search (auto-gen configs + run semua)
make grid-gen && make grid-run

# 3. Cari run ID terbaik dari MLflow dashboard
# Buka https://dagshub.com/jaweed3/retakId.mlflow
# Copy run ID terbaik (misal: abc123def456)

# 4. Cross-validation — cek konsistensi
make cv
# Output: mean ± std per metric. Lolos kalo accuracy std < 3%.

# 5. Register — promote jadi best model
make register RUN_ID=abc123def456
# Cek benchmark: BAHAYA recall ≥72%, accuracy ≥82%, macro F1 ≥75%
# Auto-compare vs champion sebelumnya. Lolos kalo ≥2 primary metrics lebih baik.

# 6. Lihat registered models
make list-models

# 7. Deploy ke app Android
make deploy-registered ASSETS=app/src/main/assets
```

## Model Promotion Rules

Model dipromote ke "Staging" kalo memenuhi:

| Metric | Threshold | Severity |
|--------|-----------|----------|
| BAHAYA recall | ≥ 72% | **CRITICAL** |
| Test accuracy | ≥ 82% | Required |
| Macro F1 | ≥ 75% | Required |
| AMAN recall | ≥ 75% | Required |
| INT8 agreement | ≥ 90% | Required |
| CV accuracy std | < 3% | Required |

Plus: harus beat champion sebelumnya di minimal 2 dari 4 primary metrics.

## DagsHub Cloud Setup

```bash
# Sekali aja — set env vars
export MLFLOW_TRACKING_URI=https://dagshub.com/jaweed3/retakId.mlflow
export MLFLOW_TRACKING_USERNAME=jaweed3
export MLFLOW_TRACKING_PASSWORD=<token_dagshub>

# Training auto-log ke cloud. Dashboard:
# https://dagshub.com/jaweed3/retakId.mlflow
```

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make setup` | Install dependencies via uv |
| `make lab-setup` | Bootstrap fresh PC (uv + Python + deps + data) |
| `make pull-data` | Pull dataset from DagsHub via DVC |
| `make scrape KW="..." LIMIT=100` | Scrape images from DuckDuckGo |
| `make validate` | Check dataset for corrupt/bad images |
| `make deduplicate` | Detect cross-class near-duplicates |
| `make stats` | Print dataset statistics |
| `make split` | Stratified 70/15/15 train/val/test split |
| `make train` | Full training pipeline (train → eval → export) |
| `make grid-gen` | Generate experiment configs from grid |
| `make grid-run` | Run all grid experiments (resume-safe) |
| `make tune` | grid-gen + grid-run |
| `make cv` | 5-fold cross-validation |
| `make register RUN_ID=<id>` | Promote model to MLflow Registry |
| `make list-models` | List registered models |
| `make evaluate` | Evaluate a trained model checkpoint |
| `make export MODEL=path` | Export TFLite from checkpoint |
| `make deploy` | Copy model + labels to Android assets (manual) |
| `make deploy-registered` | Download best registered model (auto) |
| `make test` | Run pytest suite (16 tests) |
| `make docker-build` | Build Docker training image |
| `make docker-train` | Run training in Docker |

## Model Artifacts

After training:
- `backend/models/retak_mobilenetv2.tflite` — INT8 quantized model (<5MB)
- `backend/models/labels.txt` — Class labels (AMAN, WASPADA, BAHAYA)
- `backend/models/checkpoints/best.keras` — Best Keras checkpoint

Registered models also available via:
- `make deploy-registered` — download best from MLflow Registry
- DagsHub MLflow UI → Model Registry tab
