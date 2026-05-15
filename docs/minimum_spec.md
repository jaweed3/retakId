# Minimum Spesifikasi Ponsel — Retak.id ML Inference

## Minimum Recommended

| Komponen | Minimum | Ideal |
|----------|---------|-------|
| **RAM** | 2 GB | 3 GB+ |
| **CPU** | 4× Cortex-A53 @1.3 GHz | 4× Cortex-A73 @2.0 GHz+ |
| **Android** | 8.0 (API 26) | 10+ (API 29) |
| **Storage (free)** | 50 MB | 100 MB+ |
| **Kamera** | 5 MP | 8 MP+ |

## Spesifikasi Model

- Model: MobileNetV2 INT8 quantized
- Size: 2.6 MB
- Input: 224×224 RGB (uint8 [0,255])
- Framework: TensorFlow Lite 2.16.1 (CPU Interpreter, 4 threads)
- Ops: no GPU delegate, no NNAPI (fallback CPU pure)

## Estimasi Performa

| CPU Class | Contoh Chip | RAM | Inference Time |
|-----------|------------|-----|----------------|
| Low-end | Snapdragon 425 (4×A53 @1.4 GHz) | 2 GB | 2,8 dtk — nyaman |
| Mid-range | Snapdragon 665 (4×A73 + 4×A53 @2.0 GHz) | 4 GB | 450 ms — cepat |
| High-end | Snapdragon 8 Gen 1 | 8 GB | 150 ms — instan |

> *Sumber estimasi: MobileNetV2 INT8 benchmark publik (TensorFlow Lite official benchmark, 2024).*

## Analisis Dampak

### Baterai

Sekali inferensi ≈ **15–50 mJ**, setara dengan:
- Scroll 2–3 detik di media sosial
- 0.5 detik streaming video
- 1× pengambilan foto tanpa ML

Dalam satu sesi (foto + analisis + input ke server): ~100 mJ. **Tidak signifikan** — setara dengan nge-chat 10 detik. Aman.

### RAM

Model 2.6 MB di-load sebagai `MappedByteBuffer` — dilevel OS, bukan heap Java. Total alokasi runtime:
- Model buffer (memory-mapped): ~2.6 MB
- Input tensor (224×224×3 uint8): ~150 KB
- Output tensor (3× float32): ~12 B
- Bitmap preprocessing (224×224 ARGB): ~196 KB
- Overhead Interpreter + heap: ~5–10 MB
- **Total: ~30–50 MB**

Di HP 2 GB, sisa RAM setelah OS + app launcher ≈ 800 MB → cukup.

### CPU / Lag

MobileNetV2 INT8 di CPU = **tanpa GPU/NPU**. Di low-end (Cortex-A53):
- Utilisasi CPU: ~1 core penuh selama ~2–3 detik
- Tidak blocking UI (inference di coroutine `Dispatchers.Default`)
- Aman: UI tetap responsif selama ML jalan di background

Di HP Cortex-A7 (Android Go, 1 GB RAM) — **tidak disarankan**:
- Inference > 5 detik
- RAM < 200 MB sisa → risiko OOM/lmkd kill

## Risiko di Bawah Minimum

| Problem | Penyebab | Dampak |
|---------|----------|--------|
| **Lag** | CPU < 4 core atau < 1.2 GHz | Inference 5–10 dtk, user menunggu |
| **Force close** | RAM < 1.5 GB atau < 2 GB + banyak app background | Sistem kill app saat allocate bitmap |
| **Baterai boros** | Inference terlalu lama → CPU thermal throttle naik | 5 dtk inference = 10–15× daya dibanding 300 ms |
| **Prediksi kacau** | Kamera < 5 MP + minim cahaya → input buram | Model lihat noise bukan retakan |

## Mitigasi di Kode

Sudah diimplementasi:
- `Dispatchers.Default` — inference tidak blocking UI
- `MappedByteBuffer` — model tidak full di heap
- Thread count bisa disetel: `Interpreter.Options().setNumThreads(n)`
- Model INT8 — optimal buat CPU tanpa akselerator

Rekomendasi tambahan (belum implementasi):
- Deteksi RAM: `ActivityManager.getMemoryInfo()` → kalo < 2 GB turunkan threads ke 2
- Timeout inference: kalo > 5 dtk, fallback "Ulangi / Analisis Nanti"
- Cooldown 3 dtk antar frame foto beruntun — cegah thermal throttle
- Mode hemat: skip ML, pake rule-based (lokasi + faktor lingkungan aja)
