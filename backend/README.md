# Retak.id Backend & ML Pipeline

End-to-end ML pipeline for on-device soil crack classification (AMAN, WASPADA, BAHAYA).

## Architecture

```
Scraper → Validate → Deduplicate → Split → Train (MobileNetV2) → Quantize (INT8) → Export (.tflite)
```

All steps configurable via `backend/config/training.yaml`.

## Structure

```
backend/
  config/
    training.yaml                # Single source of truth for all pipeline params
  data/
    raw/                          # Scraped images (gitignored)
    processed/                    # Annotated images: AMAN/, WASPADA/, BAHAYA/ (gitignored)
    splits/                       # Generated train/val/test/ (gitignored)
  models/
    retak_mobilenetv2.tflite      # INT8 quantized model (<5MB)
    labels.txt                    # Class labels
    checkpoints/                  # Training checkpoints
  logs/
    tensorboard/                  # TensorBoard logs
    runs.csv                      # Experiment tracking log
  scripts/
    scraping/
      image_scraper.py            # DDG scraper with dedup + quality filter
    processing/
      validate_dataset.py         # Image integrity checker
      split_dataset.py            # Stratified train/val/test split
      dataset_stats.py            # Dataset statistics & EDA
      deduplicate.py              # Cross-class perceptual hash dedup
  src/training/
    train.py                      # Main training script
    augment.py                    # Extreme augmentation pipeline
    evaluation.py                 # Metrics, confusion matrix, ROC curves
    export.py                     # TFLite INT8 export + benchmark
    config_loader.py              # YAML config parser
  tests/
    test_data.py                  # Data pipeline tests
    test_model.py                 # Model tests
    test_export.py                # Export tests
  Dockerfile                      # Hermetic training environment
```

## Quick Start

```bash
make setup           # Install dependencies
make scrape KW="landslide cracks, soil fissure" LIMIT=150
# ... manually annotate images into data/processed/AMAN|WASPADA|BAHAYA ...
make validate        # Check dataset
make split           # Create train/val/test
make train           # Full pipeline: train → evaluate → export
make test            # Run 16 tests
tensorboard --logdir backend/logs/tensorboard
```

## Tech Stack
- **ML**: TensorFlow 2.19, MobileNetV2, INT8 PTQ
- **Data**: DuckDuckGo search, perceptual hash dedup (imagehash)
- **Augmentation**: In-graph via tf.keras.layers (zero I/O overhead)
- **Infra**: uv, Docker, pytest (16 tests)
