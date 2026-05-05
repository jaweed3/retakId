# Tugas Adam — Android App (Kotlin + Jetpack Compose)

## Target
Aplikasi Android **1 screen** yang bisa: buka kamera → foto retakan tanah → tampilkan hasil
klasifikasi (AMAN / WASPADA / BAHAYA) + confidence score. Bekerja **offline**.

> Deadline implisit: sebelum CODE FREEZE (Issue #12). Presentasi ke juri pake app yang jalan.

---

## Checklist Pekerjaan

### Phase 1 — Setup Project (Issue #4)
- [ ] Bikin project Jetpack Compose baru (Empty Activity)
- [ ] Package name: `com.retakid`
- [ ] Min SDK 24 (Android 7.0), Target SDK 34
- [ ] Tambah dependency TFLite:
  ```kotlin
  // build.gradle.kts (app)
  implementation("org.tensorflow:tensorflow-lite:2.16.1")
  ```
- [ ] Struktur direktori:
  ```
  app/src/main/
    assets/                          ← model .tflite + labels.txt disini
    java/com/retakid/
      ml/
        CrackClassifier.kt           ← COPY dari backend/src/android/
      ui/
        CameraScreen.kt              ← UI utama
        ClassificationOverlay.kt     ← Overlay hasil
      MainActivity.kt
  ```

### Phase 2 — Kamera (Issue #9)
- [ ] Setup CameraX di `CameraScreen.kt`:
  ```kotlin
  val imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
      .build()
  ```
- [ ] Bind camera lifecycle ke Composable
- [ ] Tombol capture → convert `ImageProxy` ke `Bitmap`
- [ ] Preprocessing: resize ke 224×224, convert ke RGB ByteBuffer
  > **Gausa nulis dari 0.** Copy `Preprocessing.bitmapToByteBuffer()` dari `backend/src/android/CrackClassifier.kt`.

### Phase 3 — TFLite Inference (Issue #8, #10)
- [ ] Copy 2 file ke project:
  - `backend/src/android/CrackClassifier.kt` → `app/.../ml/CrackClassifier.kt`
- [ ] Init classifier di ViewModel:
  ```kotlin
  class CameraViewModel(application: Application) : AndroidViewModel(application) {
      private val classifier = CrackClassifier()
      
      init { classifier.initialize(application) }
      
      fun classify(bitmap: Bitmap): ClassificationResult = classifier.classify(bitmap)
      
      override fun onCleared() { classifier.close() }
  }
  ```
- [ ] Panggil dari UI setelah foto diambil

### Phase 4 — UI (Issue #10, #11)
- [ ] Tampilkan hasil di overlay dengan warna sesuai severity:
  ```
  AMAN     → #4CAF50 (hijau)  — "Aman"
  WASPADA  → #FFC107 (kuning) — "Waspada"
  BAHAYA   → #F44336 (merah)  — "Bahaya"
  ```
- [ ] Tampilkan confidence score: `"AMAN (87%)"`
- [ ] Tampilkan latency inference: `"42ms"`
- [ ] Tambah loading state saat inference berjalan

### Phase 5 — Build & Polish (Issue #11, #15)
- [ ] Test di minimal 2 device (high-end + low-end)
- [ ] Catat latency di tiap device (target <50ms)
- [ ] Build Release APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`
- [ ] Output APK: `app/build/outputs/apk/release/app-release.apk`

---

## Tips Biar Menang

### 1. UX itu dinilai
Juri liat app lo cuma 1-2 menit. Jangan bikin ribet:
- Buka app → langsung kamera (ga usah splash screen panjang)
- 1 tombol capture aja (ga usah gallery, ga usah settings)
- Hasil langsung muncul di overlay, ga pindah screen

### 2. Offline itu pitch utama lo
```kotlin
// JANGAN pake Firebase/API/network apapun
// Inference HARUS jalan tanpa internet
// Test dengan airplane mode ON
```

### 3. Confidence > 90% berasa "pintar"
Kalo model ngasih confidence rendah, jangan tampilin "UNKNOWN" —
tetep tampilin prediksi terbaik tapi confidence < 60% kasih warna abu-abu:
```kotlin
val displayConfidence = if (result.confidence < 0.6) "Rendah" else "${(result.confidence * 100).toInt()}%"
```

### 4. Siapin screenshot buat slide deck
- 3 screenshot: 1 per severity level (AMAN/WASPADA/BAHAYA)
- Pastiin ada retakan beneran di foto (jangan foto meja)
- Background outdoor (tanah, lereng) biar believable

### 5. Demo video (buat Farrel/Farrel)
- Rekam app jalan di HP beneran (bukan emulator)
- Tunjukin: buka app → foto retakan → hasil AMAN → foto retakan lain → hasil BAHAYA
- Background suara: jelasin apa yang terjadi ("ini retakan kecil, output AMAN...")
- Upload ke Google Drive, kasih link ke Farrel buat slide deck

---

## Command Cepat Buat Lo

```bash
# Narik model dari ML pipeline
make deploy ASSETS=app/src/main/assets
# Udah. Model + labels.txt langsung di assets/ lo.
```
