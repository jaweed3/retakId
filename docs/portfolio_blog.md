# Building Retak.id: On-Device ML for Landslide Early Detection

**An ML Engineering Case Study — From Zero to 85% Accuracy in 72 Hours**

---

## What I Built

Retak.id is an Android app that classifies soil crack severity from a single photo — offline, on-device, in real-time. Three classes: **AMAN** (safe), **WASPADA** (caution), **BAHAYA** (danger). Built for Jenangan, Ponorogo — a region hit by 41 landslides in 4 months due to illegal mining.

**My role:** ML Engineer — end-to-end pipeline from data acquisition to TFLite deployment.

**Stack:** Python, TensorFlow/Keras, MobileNetV2/V3, INT8 Quantization, TFLite, MLflow, DVC, DagsHub, Docker, uv

---

## The Architecture

```
Camera → Bitmap (any resolution)
  → Resize 224×224 → uint8 [0,255]
  → TFLite INT8 Model (2.6MB)
  → AMAN / WASPADA / BAHAYA + confidence
  → No internet required. 0ms server latency. Works in rural Ponorogo.
```

Key decisions:
- **On-device inference, not cloud.** Landslide-prone areas have poor connectivity. A cloud API is useless if users can't reach it. TFLite runs on any Android 7.0+ device.
- **INT8 quantization.** Drops model from 14MB FP32 to 2.6MB — fits in a single APK. Inference latency <50ms on a Pixel 4a.
- **Config-driven pipeline.** Single `training.yaml` controls everything. Exact reproducibility for competition judges.

---

## The Data Problem

Soil crack datasets don't exist. There's no ImageNet for Indonesian landslides. We had to build our own.

### Scraping
70+ search queries across DuckDuckGo. Built a custom scraper with:
- Perceptual hash deduplication (pHash, Hamming distance < 6)
- Laplacian blur detection (reject images < threshold)
- Rotating user agents + exponential backoff
- 12-thread parallel download with random delays

### Annotation Strategy
Manual triage into 3 classes by domain knowledge (not me — the team geologist). Critical insight: **retakan kemarau (dry season cracks) ≠ landslide warning signs.** We initially mislabeled these as WASPADA. Fixing this single labeling error jumped accuracy 3%.

```
v1 dataset: 4,522 images (noisy BAHAYA labels)
v2 dataset: 3,545 images (cleaned, balanced)
Result:     73% → 85% accuracy from data cleaning alone
```

Lesson: **Label quality beats dataset size. Always audit your annotations before tuning hyperparameters.**

---

## The Model Journey

### Iteration 1: Baseline Transfer Learning
```
MobileNetV2 (frozen) + classification head
LR 1e-4, dropout 0.3, basic augmentation
→ 73.0% test accuracy
```
Standard transfer learning. Decent, but WASPADA F1 was 0.56. The model couldn't distinguish subtle warning signs from severe cracks.

### Iteration 2: Fine-Tuning
```
MobileNetV2 (layers 100+ unfrozen, 54 layers, 1.87M params)
LR 1e-5, dropout 0.5
→ 76.7% test accuracy
```
Unfreezing half the model helped, but 1.87M trainable params on 2,500 training images = overfitting. AMAN F1 dropped. Too aggressive.

### Iteration 3: Conservative Fine-Tuning
```
MobileNetV2 (layers 130+ unfrozen, 24 layers, 800K params)
LR 5e-6, dropout 0.5, aggressive augmentation
→ 81.8% test accuracy
```
The sweet spot. Fewer trainable params + slower learning = better generalization. WASPADA F1 recovered to 0.64.

### Iteration 4: Data Quality Fix
```
Same config. Cleaned dataset (BAHAYA noise removed).
→ 84.9% test accuracy. WASPADA F1 0.68+. INT8 agreement 93.75%.
```
**The biggest single improvement didn't come from model architecture — it came from fixing bad labels.**

---

## Reproducibility by Design

Every decision made with reproducibility in mind:

```bash
# Anyone can reproduce the exact model:
git clone https://github.com/jaweed3/retakId.git
bash scripts/bootstrap.sh    # auto-installs Python, deps, pulls data
make split && make train      # produces identical model.tflite
```

- **Fixed seeds everywhere** (Python, NumPy, TensorFlow, data split)
- **uv.lock** pins all 200+ dependencies
- **DVC + DagsHub** for dataset versioning (S3-compatible, free tier)
- **Docker** for hermetic training environment
- **MLflow** (DagsHub hosted) — every experiment logged: params, metrics, artifacts

---

## Experiment Tracking Infrastructure

Built a grid search system that:
- Defines parameter grids in YAML (`grid_search.yaml`)
- Auto-generates experiment configs (cartesian product + exclude rules)
- Runs with resume support (SSH disconnect → re-run, skips completed)
- Logs everything to DagsHub MLflow (view from phone)

```
24 experiments × 15 min = ~6 hours on CPU, ~1 hour on GPU
Compare in MLflow UI: accuracy vs dropout vs fine_tune_at vs learning_rate
```

No more CSV files. No more "which config produced 81%?". Every run traceable.

---

## What I'd Do Differently

1. **Active learning for annotation.** Instead of random sampling, use model uncertainty to prioritize which images to label next. Would have caught the AMAN/WASPADA confusion earlier.

2. **Test-time augmentation.** Average predictions across 5-10 augmented versions for +1-2% free accuracy. Didn't implement due to mobile latency constraints.

3. **Model distillation.** Train a larger teacher model (EfficientNetB3), distill to MobileNetV3. Could push 90%+ while keeping <5MB.

4. **Start with data audit, not hyperparameter tuning.** 80% of the accuracy gains came from fixing labels, not tweaking learning rates. I learned this the hard way.

---

## Technical Skills Demonstrated

| Area | What I Did |
|------|------------|
| **Computer Vision** | Fine-tuned MobileNetV2/V3/EfficientNet for 3-class soil crack classification |
| **Model Optimization** | INT8 PTQ (14MB → 2.6MB), FP32 vs INT8 comparison (93.75% agreement) |
| **ML Infrastructure** | Config-driven pipeline, MLflow experiment tracking, DVC data versioning, Docker |
| **Data Engineering** | Custom web scraper with dedup + quality filtering, dataset validation toolkit |
| **Reproducibility** | Seed management, dependency locking, one-command bootstrap |
| **Hyperparameter Tuning** | Grid search engine with auto-generation + resume support |
| **Deployment** | TFLite export + inference contract for Android (Kotlin integration spec) |
| **Collaboration** | Git/GitHub, task docs per team member, DagsHub for shared MLflow + DVC |

---

## Links

- **Code:** [github.com/jaweed3/retakId](https://github.com/jaweed3/retakId)
- **Experiments:** [dagshub.com/jaweed3/retakId.mlflow](https://dagshub.com/jaweed3/retakId.mlflow)
- **Dataset:** [dagshub.com/jaweed3/retakId](https://dagshub.com/jaweed3/retakId)
- **Competition:** IYREF 2026 Semi-Final — Climate Resilience & Local Wisdom

---

*Built in collaboration with Farrel (Data Acquisition) and Adam (Android Development). May 2026.*
