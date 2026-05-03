# Model Card — Retak.id

## Model Summary

**Purpose:** On-device soil crack classification for early landslide detection.
**Model:** MobileNetV2 + fine-tuning + INT8 PTQ
**Version:** v3a (2026-05-04)
**Input:** 224×224×3 uint8 RGB image (raw camera frame, no normalization)
**Output:** 3-class probability: AMAN (safe), WASPADA (caution), BAHAYA (danger)

## Performance

| Metric | Value |
|--------|-------|
| Test Accuracy | **84.9%** |
| Best Val Accuracy | 81.6% |
| INT8 Agreement (vs FP32) | 93.75% |
| Model Size (INT8) | 2.6 MB (target <5 MB) |

### Per-Class Metrics (pending — SSH disconnected, re-run needed)

| Class | Precision | Recall | F1 | Support |
|-------|-----------|--------|-----|---------|
| AMAN | TBD | TBD | TBD | 303 |
| WASPADA | TBD | TBD | TBD | 116 |
| BAHAYA | TBD | TBD | TBD | 116 |

## Training Configuration

| Parameter | Value |
|-----------|-------|
| Base Model | MobileNetV2 (ImageNet weights) |
| Fine-tuning | Layers 130+ unfrozen (24/154) |
| Dropout | 0.5 |
| Learning Rate | 5e-6 (Adam) |
| Epochs | 70 (EarlyStopping patience=20) |
| Class Weighting | Balanced (sklearn) |
| Augmentation | Rotation ±45°, Zoom ±30%, Translation ±25%, Brightness 0.5-1.5x, Contrast 0.5-1.5x, Flip H+V |

## Dataset

| Class | Count | Description |
|-------|-------|-------------|
| AMAN | 2,011 | Minor cracks, natural soil shrinkage, polygonal patterns |
| WASPADA | 766 | Significant cracks, linear patterns, potential expansion |
| **Total** | **3,545** | |

**Source:** DuckDuckGo image search (70+ keywords).
**Annotation:** Manual triage by Farrel. Retakan kemarau (polygonal, shallow) classified as AMAN. Ambiguous/dirty BAHAYA images removed in v2 cleanup.

**Split:** Stratified 70/15/15 (train/val/test), seed=42.

## Quantization

| Metric | Value |
|--------|-------|
| Type | INT8 Post-Training Quantization |
| Calibration | 100 representative samples from training set |
| Output | uint8 [0,255] — softmax applied on-device |

## Intended Use

- **Use Case:** Soil crack classification on Android devices in landslide-prone areas (Jenangan, Ponorogo).
- **Users:** Citizens, BPBD Ponorogo, local RT/RW.
- **Environment:** Outdoor, natural lighting, various phone cameras.
- **Offline:** Inference runs fully on-device, no internet needed.

## Limitations

1. **Not for:** Asphalt, concrete, or wall cracks — model is trained on soil only.
2. **Lighting:** Extreme darkness or overexposure may reduce accuracy.
3. **Vegetation:** Heavy grass/plant cover over cracks may cause misclassification.
4. **Single image:** Does not use temporal data (crack progression over time).
5. **Indonesian soil types:** Primarily trained on images from Indonesian/similar tropical regions.

## Training Reproducibility

```bash
git clone https://github.com/jaweed3/retakId.git
cd retakId
make setup
dvc pull                           # dataset from DagsHub
make split
make train                          # full pipeline → .tflite + labels.txt
```

All seeds fixed at 42. Config at `backend/config/training.yaml`.
Docker image available: `docker build -t retakid-train -f backend/Dockerfile .`

## Version History

| Version | Date | Accuracy | Key Changes |
|---------|------|----------|-------------|
| test1 | 2026-05-02 | 73.0% | Baseline: frozen base, LR 1e-4 |
| test2 | 2026-05-02 | 76.7% | Fine-tune 54 layers, LR 1e-5, dropout 0.5 |
| test3 | 2026-05-03 | 81.8% | Fine-tune 24 layers, LR 5e-6, aggressive augment |
| **v3a** | **2026-05-04** | **84.9%** | Cleaned dataset (BAHAYA noise removed, WASPADA +50%) |
