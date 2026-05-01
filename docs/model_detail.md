# Model Details — Retak.id

## Architecture

| Parameter | Value |
|-----------|-------|
| Base Model | MobileNetV2 |
| Pre-trained Weights | ImageNet |
| Input Shape | 224 × 224 × 3 (uint8) |
| Classification Head | GlobalAveragePooling2D → Dropout(0.3) → Dense(3, softmax) |
| Total Params | ~2.26M |
| Trainable Params | ~3.8K (classification head only) |

## Training Strategy

| Parameter | Value |
|-----------|-------|
| Transfer Learning | Freeze base, train head only |
| Optimizer | Adam (lr=1e-4) |
| Loss | Categorical Crossentropy |
| Epochs | 50 max (EarlyStopping patience=10) |
| Batch Size | 32 |

### Callbacks

| Callback | Config | Purpose |
|----------|--------|---------|
| ModelCheckpoint | `best.keras` (monitor=val_accuracy) | Save best weights |
| EarlyStopping | patience=10 (monitor=val_loss) | Prevent overfitting |
| ReduceLROnPlateau | factor=0.5, patience=5 | Adaptive learning rate |
| TensorBoard | histogram_freq=1 | Visual debugging |
| CSVLogger | per-run timestamp | Structured run comparison |

### Class Weighting

Supports automatic class balancing via `training.class_weight: "balanced"` in config.
Uses sklearn's `compute_class_weight("balanced")`. Automatically skips if imbalance ratio < 2:1.

Example for AMAN(200) vs WASPADA(1000) vs BAHAYA(1000):
- AMAN weight: ~2.5
- WASPADA weight: ~0.5
- BAHAYA weight: ~0.5

## Augmentation

In-graph via `tf.keras.layers` (zero Python I/O overhead):

| Technique | Range | Purpose |
|-----------|-------|---------|
| RandomFlip | Horizontal + Vertical | Crack orientation invariance |
| RandomRotation | ±30° | Camera angle variation |
| RandomZoom | ±20% | Distance variation |
| RandomTranslation | ±20% | Off-center framing |
| RandomBrightness | 0.7–1.3× | Outdoor lighting (dawn to noon) |
| RandomContrast | 0.8–1.2× | Phone camera quality variance |

## Quantization (INT8 PTQ)

| Metric | FP32 | INT8 |
|--------|------|------|
| Model Size | ~8.5 MB | ~2.6 MB (3.3× smaller) |
| Input Type | float32 | uint8 [0, 255] |
| Output Type | float32 | uint8 [0, 255] |
| Calibration | — | 100 representative samples |

Post-training quantization with representative dataset. Input/output are uint8 — no
normalization step needed on Android (raw pixel values in, class probabilities out).

## Evaluation Metrics

- Per-class: Precision, Recall, F1-score
- Overall: Accuracy, Macro-F1, Weighted-F1
- Visualizations: Confusion matrix, ROC curves (one-vs-rest)

## Class Labels

```
0 = AMAN    — Retakan minor, penyusutan alami, tidak perlu tindakan
1 = WASPADA — Retakan signifikan, perlu pemantauan berkala
2 = BAHAYA  — Retakan kritis, indikasi pergerakan tanah besar, evakuasi
```

## Reproducibility

- All random seeds pinned (Python, NumPy, TensorFlow) at seed=42
- Config-driven (single `training.yaml`)
- Dependency lock via `uv.lock`
- Docker training environment available
