# Getting Started

## Prerequisites
- Python 3.10–3.12 (TensorFlow doesn't support 3.13+ yet)
- [uv](https://github.com/astral-sh/uv) installed.
- Android Studio (for app development).

## Backend Setup
1. Clone the repository.
2. Run `make setup` to install dependencies using `uv`.
3. The full pipeline is driven from the project root.

## Full ML Pipeline

```bash
# 1. Scrape dataset (5-10 keywords recommended, 150 images each)
make scrape KW="landslide soil cracks, retakan tanah longsor, ground fissure, soil surface cracks" LIMIT=150

# 2. Manually annotate images into data/processed/AMAN/, WASPADA/, BAHAYA/

# 3. Validate dataset integrity
make validate

# 4. Check for cross-class duplicates
make deduplicate

# 5. View dataset statistics
make stats

# 6. Stratified train/val/test split
make split

# 7. Train the model (includes evaluation + TFLite export)
make train

# 8. Monitor training
tensorboard --logdir backend/logs/tensorboard

# 9. (Optional) Export from checkpoint
make export MODEL=backend/models/checkpoints/best.keras

# 10. Run tests
make test
```

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make setup` | Install dependencies via uv |
| `make scrape KW="..." LIMIT=100` | Scrape images from DuckDuckGo |
| `make validate` | Check dataset for corrupt/bad images |
| `make deduplicate` | Detect cross-class near-duplicates |
| `make stats` | Print dataset statistics |
| `make split` | Stratified 70/15/15 train/val/test split |
| `make train` | Full training pipeline (train → eval → export) |
| `make evaluate` | Evaluate a trained model checkpoint |
| `make export MODEL=path` | Export TFLite from checkpoint |
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
Copy `retak_mobilenetv2.tflite` and `labels.txt` to the Android app's `app/src/main/assets/` directory.
