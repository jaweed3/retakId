# Technical Architecture — Retak.id

## Overview
Retak.id uses an **Edge-First** approach. All landslide crack detection is performed on-device to ensure functionality in areas with poor internet connectivity (Jenangan, Ponorogo). No cloud dependency for inference.

## Architecture Diagram

```
┌────────────────────────── ANDROID APP ──────────────────────────┐
│                                                                  │
│  CameraX ──→ Bitmap ──→ Preprocessing ──→ TFLite INT8 ──→ UI   │
│                           224x224 RGB         Inference      │
│                           uint8 [0,255]     AMAN/WASPADA/   │
│                                               BAHAYA        │
└──────────────────────────────────────────────────────────────────┘

┌────────────────────────── ML PIPELINE ──────────────────────────┐
│                                                                  │
│  Scrape ──→ Validate ──→ Deduplicate ──→ Split (70/15/15)     │
│    │                                                             │
│    └──→ Augment ──→ MobileNetV2 ──→ INT8 PTQ ──→ .tflite      │
│                      Transfer Learning                          │
│                                                                  │
│  Config: backend/config/training.yaml (single source of truth)  │
│  Output: backend/models/retak_mobilenetv2.tflite (<5MB)        │
└──────────────────────────────────────────────────────────────────┘
```

## Data Flow

1. **Camera Input**: Android CameraX captures real-time frames.
2. **Preprocessing**: Bitmap → resize 224×224 → RGB uint8 buffer [0, 255].
3. **Inference**: TFLite INT8 model processes the frame locally on-device.
4. **Output**: UI displays classification (AMAN, WASPADA, BAHAYA) with confidence score.

## ML Pipeline

### Data Stage
| Step | Script | Description |
|------|--------|-------------|
| Scrape | `scripts/scraping/image_scraper.py` | DDG search, perceptual hash dedup, blur detection, quality filter |
| Validate | `scripts/processing/validate_dataset.py` | Check image integrity, detect corrupt files |
| Deduplicate | `scripts/processing/deduplicate.py` | Cross-class near-duplicate detection |
| Stats | `scripts/processing/dataset_stats.py` | Class distribution, image properties |
| Split | `scripts/processing/split_dataset.py` | Stratified 70/15/15 with reproducible seed |

### Training Stage
| Step | Module | Description |
|------|--------|-------------|
| Config | `config/training.yaml` | All pipeline parameters in one file |
| Augment | `src/training/augment.py` | Extreme augmentation via tf.keras.layers |
| Train | `src/training/train.py` | MobileNetV2 transfer learning + callbacks |
| Evaluate | `src/training/evaluation.py` | Per-class F1, confusion matrix, ROC curves |
| Export | `src/training/export.py` | INT8 PTQ, accuracy comparison, benchmark |

### Deployment
```bash
make train    # Full pipeline: train → evaluate → export
make deploy   # Copy .tflite + labels.txt to app/src/main/assets/
```

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **On-device inference** | Zero infrastructure; works offline in rural Ponorogo |
| **MobileNetV2** | Lightweight depthwise separable convs; proven TFLite support |
| **INT8 PTQ** | 4x size reduction (14MB → 2.6MB) with <3% accuracy loss |
| **Class weights** | Handles imbalanced dataset (e.g., AMAN 200 vs WASPADA 1000) |
| **YAML config** | Single source of truth; exact reproducibility |
| **DVC** | Version-controlled datasets; team collaboration |
| **uv** | Fast, reproducible Python dependency management |
