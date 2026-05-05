# Tugas Jaweed — ML Pipeline & Deploy

## Role
Lo adalah **ML Engineer + Tech Lead**. Tanggung jawab:
1. Pastiin model akurat (≥85% test accuracy, per-class F1 ≥0.75)
2. Pastiin pipeline reproducible (siapa pun bisa re-run)
3. Pastiin deploy ke app Adam lancar (model + labels siap)
4. Final decision maker untuk arsitektur dan trade-off

---

## Checklist Pekerjaan

### Phase 1 — Verifikasi Data (Blocked: nunggu Farrel)
- [ ] `dvc pull` — narik data dari DagsHub
- [ ] Cek hasil anotasi Farrel:
  ```bash
  make validate
  make stats
  make deduplicate
  ```
- [ ] Review 5-10 gambar random per kelas, pastiin konsisten
- [ ] Kalo class imbalance > 3:1, diskusi sama Farrel buat tambah kelas minoritas

### Phase 2 — Training (Issue #5, #6)
- [ ] Split dataset:
  ```bash
  make split
  ```
- [ ] Review config sebelum training:
  - `training.yaml` → `class_weight: "balanced"` (otomatis, udah di-set)
  - Pastiin `learning_rate`, `dropout`, `epochs` reasonable
- [ ] Training:
  ```bash
  make train
  ```
- [ ] Monitor training:
  ```bash
  tensorboard --logdir backend/logs/tensorboard
  ```
- [ ] Cek metrik:
  - Accuracy ≥ 85% target
  - Per-class F1 ≥ 0.75 (terutama AMAN — kelas minoritas)
  - Kalo ga mencapai: tuning hyperparameter (lr, dropout, augmentation intensity)

### Phase 3 — Evaluation & Iteration (Issue #21, #22)
- [ ] Analisis confusion matrix — kelas mana yang paling banyak salah?
- [ ] Analisis ROC curves — AUC per kelas
- [ ] Kalo AMAN sering salah klasifikasi sebagai WASPADA:
  - Naikin `dropout` (0.3 → 0.5) untuk regularisasi
  - Atau tambah data AMAN
- [ ] Kalo BAHAYA sering missed:
  - Naikin `class_weight` untuk BAHAYA secara eksplisit
- [ ] Log tiap eksperimen ke `runs.csv` — bandingin metrik antar run
- [ ] Pilih model terbaik berdasarkan **macro F1**, bukan accuracy

### Phase 4 — Quantization & Export (Issue #6, #7)
- [ ] Udah otomatis dari `make train`. Tapi verifikasi:
  - INT8 model size < 5MB ✓
  - FP32 vs INT8 agreement > 90%
  - `labels.txt` isinya bener (AMAN, WASPADA, BAHAYA)
- [ ] Kalo INT8 accuracy drop > 5%: coba FP16 quantization sebagai fallback

### Phase 5 — Deploy ke App (Issue #10)
- [ ] Deploy model ke assets:
  ```bash
  make deploy ASSETS=app/src/main/assets
  ```
- [ ] Koordinasi sama Adam — pastiin model + labels udah di assets/
- [ ] Test inference di device Android (bareng Adam):
  - Preprocessing: bitmap → resize → RGB ByteBuffer ✓
  - Output: 3 probabilities, argmax sesuai label
  - Latency: <50ms target

### Phase 6 — Model Card (Issue #26)
- [ ] Tulis model card setelah training final:
  - Arsitektur: MobileNetV2 + INT8 PTQ
  - Dataset: size, distribusi kelas, source
  - Metrik: accuracy, per-class F1, confusion matrix
  - Keterbatasan: lighting ekstrim, tanah tertutup vegetasi, bukan untuk aspal/tembok

### Phase 7 — Hardware Profiling (Issue #11)
- [ ] Benchmark latency di 2-3 device (bareng Adam)
- [ ] Catat: device model, OS version, avg latency, memory usage
- [ ] Dokumentasi di model card

---

## Hyperparameter Tuning Guide

### Baseline (dari training.yaml)
```yaml
training:
  learning_rate: 0.0001
  epochs: 50
  early_stopping_patience: 10
model:
  dropout: 0.3
```

### Kalo Overfitting (train acc >> val acc)
```yaml
model:
  dropout: 0.5              # 0.3 → 0.5
augmentation:
  rotation_range: 45.0      # 30 → 45 (lebih agresif)
  zoom_range: 0.3            # 0.2 → 0.3
  brightness_range: [0.5, 1.5]  # lebih ekstrim
```

### Kalo Underfitting (train acc juga rendah)
```yaml
model:
  freeze_base: false         # unfreeze beberapa layer
training:
  learning_rate: 0.001       # 1e-4 → 1e-3 (learning rate lebih tinggi)
```

### Fine-tuning (setelah transfer learning dasar berhasil)
```yaml
model:
  freeze_base: false
training:
  learning_rate: 0.00001     # 10x lebih kecil dari baseline
  epochs: 30
```
> Unfreeze 20-30 layer terakhir MobileNetV2. Jangan unfreeze semua — bakal overfit di dataset kecil.

---

## Tips Biar Menang

### 1. Macro F1 > Accuracy
Di dataset imbalance (AMAN 200, WASPADA 1000), accuracy bisa misleading.
Model yang selalu prediksi WASPADA dapet accuracy 55% tapi F1=0 untuk AMAN.
**Pantau macro F1 sebagai metrik utama.**

### 2. Confusion matrix cerita banyak
Kalo BAHAYA → WASPADA banyak salah, itu "acceptable error" (model masih cautious).
Kalo BAHAYA → AMAN salah, itu **FATAL** (model anggap bahaya sebagai aman).
Prioritas: minimalkan false negative untuk BAHAYA.

### 3. Reproducibility buat juri
Kalo juri tanya "bisa di-reproduce?", lo harus bisa jawab:
```bash
make setup && make split && make train
# Output: model.tflite dengan akurasi X%
```
Docker image juga available: `make docker-build && make docker-train`

### 4. Siapin narasi teknik
Buat bantu Fatih (storytelling #14):
- Kenapa MobileNetV2, bukan ResNet/VGG? (lightweight, depthwise separable conv)
- Kenapa INT8 quantization? (4x size reduction, on-device CPU inference)
- Kenapa on-device, bukan cloud? (zero infrastructure, offline Ponorogo)
- Kenapa class weighting? (imbalanced dataset, minority class AMAN)

### 5. Simpan semua artifact
```
backend/models/retak_mobilenetv2.tflite   ← final model
backend/logs/confusion_matrix.png         ← buat slide deck
backend/logs/roc_curves.png               ← buat slide deck
backend/logs/training_history.png         ← buat slide deck
backend/logs/runs.csv                     ← catetan eksperimen
```
