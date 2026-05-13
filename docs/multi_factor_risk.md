# Multi-Factor Environmental Risk Scoring

Dokumen ini menjelaskan arsitektur, formula, dan alur perhitungan Multi-Factor Risk Scoring untuk Retak.id.

---

## 1. Filosofi

Image classification ML doang **tidak cukup** untuk menentukan risiko longsor secara akurat, apalagi dengan dataset terbatas (3.545 gambar). Solusi: gunakan **5 faktor independen** yang masing-masing memberikan sinyal risiko, lalu gabungkan secara weighted.

**Keuntungan:**
- Setiap faktor bisa di-debug secara terpisah
- Bobot bisa disesuaikan tanpa retrain model
- Juri lihat kita pikirannya matang, bukan cuma "AI ajaib"
- Graceful degradation — kalau 1-2 faktor gagal, sistem tetap jalan

---

## 2. Formula Final

```
Score = (ML × 0.50) + (Slope × 0.20) + (Rain × 0.15) + (Elevation × 0.10) + (Soil × 0.05)

Final Risk:
  0.00 - 0.33  → AMAN     (🟢)
  0.34 - 0.66  → WASPADA  (🟡)
  0.67 - 1.00  → BAHAYA   (🔴)
```

### Justifikasi Bobot

| Faktor | Bobot | Alasan |
|--------|-------|--------|
| ML | 50% | Bukti visual langsung dari retakan — sinyal terkuat |
| Slope | 20% | Faktor #1 longsor menurut BNPB — lereng curam = risiko tinggi |
| Rain | 15% | Pemicu longsor #1 — hujan lebat saturasi tanah |
| Elevation | 10% | Korelasi dengan jenis tanah dan potensi pergerakan |
| Soil | 5% | Data paling kasar (API/resolution rendah) — bobot kecil |

### Graceful Degradation

Kalau suatu faktor gagal (network error, API down, timeout), bobotnya didistribusikan proporsional ke faktor lain:

```
Contoh: Slope gagal (20%)
  ML  → 50% + (20% × 50/80) = 62.5%
  Rain → 15% + (20% × 15/80) = 18.75%
  Elev → 10% + (20% × 10/80) = 12.5%
  Soil → 5%  + (20% × 5/80)  = 6.25%
```

---

## 3. Scoring per Faktor

### 3.1 ML Confidence (`weight: 0.50`)

**Sumber:** `MLAnalyzer.analyzeImage()` → softmax output per kelas

**Scoring:**

| Confidence Tertinggi | Score |
|---------------------|-------|
| AMAN ≥ 70% | 0.1 |
| AMAN 50-70% | 0.2 |
| AMAN < 50% | 0.3 |
| WASPADA ≥ 70% | 0.5 |
| WASPADA 50-70% | 0.6 |
| WASPADA < 50% | 0.7 |
| BAHAYA ≥ 70% | 0.8 |
| BAHAYA 50-70% | 0.9 |
| BAHAYA < 50% | 1.0 |

*Logic:* Confidence rendah = model tidak yakin = risiko lebih tinggi (false negative lebih bahaya dari false positive).

### 3.2 Slope (`weight: 0.20`)

**Sumber:** `SlopeCalculator.calculateSlope()` → derajat

**Scoring:**

| Slope | Kategori | Score | Risiko |
|-------|----------|-------|--------|
| 0-8° | Datar | 0.1 | Rendah |
| 8-15° | Landai | 0.4 | Sedang |
| 15-25° | Curam | 0.7 | Tinggi |
| >25° | Sangat Curam | 1.0 | Sangat Tinggi |

**Sumber threshold:** BNPB Zona Kerentanan Longsor + USGS Slope Classification

### 3.3 Rainfall (`weight: 0.15`)

**Sumber:** `WeatherApiService.getCurrentWeather()` → `rain` (mm)

**Scoring:**

| Curah Hujan | Score | Risiko |
|-------------|-------|--------|
| 0 mm | 0.0 | Tidak hujan |
| 0-5 mm | 0.2 | Gerimis |
| 5-15 mm | 0.5 | Hujan sedang |
| 15-30 mm | 0.8 | Hujan lebat |
| >30 mm | 1.0 | Hujan ekstrem |

**Sumber threshold:** BMKG klasifikasi intensitas curah hujan

### 3.4 Elevation (`weight: 0.10`)

**Sumber:** `ElevationService.getElevation()` → meter dari Open-Meteo API

**Scoring:**

| Elevasi | Score | Risiko |
|---------|-------|--------|
| <200 m | 0.1 | Rendah |
| 200-500 m | 0.4 | Sedang |
| 500-1000 m | 0.7 | Tinggi |
| >1000 m | 1.0 | Sangat Tinggi |

**Catatan:** Mayoritas longsor di Indonesia terjadi di elevasi 200-1000m (lereng perbukitan). Ponorogo rata-rata ~400m.

### 3.5 Soil Type (`weight: 0.05`)

**Sumber:** `SoilTypeService.getSoilType()` → ISRIC SoilGrids API + fallback hardcode

**Scoring:**

| Tipe Tanah | Score | Risiko |
|------------|-------|--------|
| Sand / Pasir | 0.1 | Sangat Rendah |
| Sandy Loam / Pasir Berlempung | 0.2 | Rendah |
| Loam / Lempung | 0.4 | Sedang |
| Clay Loam / Liat Berpasir | 0.6 | Tinggi |
| Clay / Liat | 0.8 | Sangat Tinggi |
| Peat / Gambut | 1.0 | Ekstrem |

**Catatan:** Tanah liat (clay) mengembang saat basah dan menyusut saat kering — menciptakan retakan. Jenangan, Ponorogo didominasi tanah liat.

---

## 4. Alur Fetch (Cascade)

```
User capture photo
  │
  ├── [INSTANT] ML Analysis (TFLite)     ~100ms
  │
  ├── [PARALLEL] Mulai async fetch:      ~2-3 detik
  │   ├── WeatherApiService.getWeather()
  │   ├── ElevationService.getElevation()
  │   └── SoilTypeService.getSoilType()
  │
  ├── [SEQUENTIAL] SlopeCalculator       ~1 detik
  │   └── butuh elevasi 5 titik
  │
  ├── [TIMEOUT] 5 detik max tunggu
  │   └── kalau lewat → skip faktor yg gagal
  │
  └── [FINAL] MultiFactorRiskEngine.analyze()
      └── UI → ResultView + FactorCard
```

**Total waktu:** ~100ms (ML) + ~3 detik (fetch) = user nunggu ~3 detik.

**Loading state:** Setelah ML selesai, tunjukkin result sementara + spinner "Menganalisis faktor lingkungan...".

---

## 5. Data Model (Kotlin)

```kotlin
// RiskEngine.kt

data class RiskFactorReport(
    val mlResult: DetectionResult,        // AMAN / WASPADA / BAHAYA
    val mlConfidence: Float,               // 0.0 - 1.0
    val finalScore: Double,                // 0.0 - 1.0
    val finalResult: DetectionResult,       // final setelah semua faktor
    val factors: List<FactorContribution>,
    val isUpgraded: Boolean,               // true kalau final > ml (lingkungan naikin risiko)
    val isDowngraded: Boolean              // true kalau final < ml (lingkungan nurunin risiko)
)

data class FactorContribution(
    val factor: RiskFactor,                // ML, SLOPE, RAIN, ELEVATION, SOIL
    val rawValue: String,                  // "22°", "12mm", "423m", "Liat"
    val score: Double,                     // 0.0 - 1.0 (setelah normalisasi)
    val weight: Double,                    // 0.05 - 0.50
    val weightedScore: Double,             // score * weight
    val riskLabel: RiskLabel              // RENDAH, SEDANG, TINGGI, SANGAT_TINGGI
)

enum class RiskFactor { ML, SLOPE, RAIN, ELEVATION, SOIL }
enum class RiskLabel { RENDAH, SEDANG, TINGGI, SANGAT_TINGGI }

data class ElevationData(val elevationMeters: Double)
data class SlopeData(val degrees: Double, val category: SlopeCategory)
enum class SlopeCategory { DATAR, LANDAI, CURAM, SANGAT_CURAM }
data class SoilType(val name: String, val risk: RiskLabel)
```

---

## 6. Contoh Skenario

### Skenario A: Lereng Curam habis hujan

| Faktor | Raw | Score | Weight | Weighted |
|--------|-----|-------|--------|----------|
| ML | WASPADA 65% | 0.60 | 0.50 | 0.300 |
| Slope | 28° (Sangat Curam) | 1.00 | 0.20 | 0.200 |
| Rain | 18mm (Lebat) | 0.80 | 0.15 | 0.120 |
| Elevation | 423m (Sedang) | 0.40 | 0.10 | 0.040 |
| Soil | Liat (Sangat Tinggi) | 0.80 | 0.05 | 0.040 |
| **Final** | | | | **0.700** |

**Hasil:** BAHAYA (↑ dari WASPADA) — lingkungan naikin risiko.

### Skenario B: Tanah lapangan datar, cerah

| Faktor | Raw | Score | Weight | Weighted |
|--------|-----|-------|--------|----------|
| ML | AMAN 85% | 0.10 | 0.50 | 0.050 |
| Slope | 3° (Datar) | 0.10 | 0.20 | 0.020 |
| Rain | 0mm | 0.00 | 0.15 | 0.000 |
| Elevation | 150m (Rendah) | 0.10 | 0.10 | 0.010 |
| Soil | Pasir (Sangat Rendah) | 0.10 | 0.05 | 0.005 |
| **Final** | | | | **0.085** |

**Hasil:** AMAN — semua faktor setuju.

### Skenario C: ML bilang AMAN tapi slope curam

| Faktor | Raw | Score | Weight | Weighted |
|--------|-----|-------|--------|----------|
| ML | AMAN 92% | 0.10 | 0.50 | 0.050 |
| Slope | 24° (Curam) | 0.70 | 0.20 | 0.140 |
| Rain | 2mm (Gerimis) | 0.20 | 0.15 | 0.030 |
| Elevation | 850m (Tinggi) | 0.70 | 0.10 | 0.070 |
| Soil | Liat Berpasir (Tinggi) | 0.60 | 0.05 | 0.030 |
| **Final** | | | | **0.320** |

**Hasil:** AMAN (0.32) — nyaris WASPADA. Ini menunjukkan sistem kita "tidak percaya buta" sama ML kalau lingkungan bilang bahaya.

---

## 7. Debugging Guide

### Logging untuk debug
```kotlin
// Setiap faktor di-log
Log.d("RiskEngine", "ML: ${mlResult} (${mlConfidence}) → score=$mlScore")
Log.d("RiskEngine", "Slope: ${slope.degrees}° → score=$slopeScore")
Log.d("RiskEngine", "Rain: ${rain}mm → score=$rainScore")
Log.d("RiskEngine", "Elevation: ${elevation}m → score=$elevScore")
Log.d("RiskEngine", "Soil: ${soilType.name} → score=$soilScore")
Log.d("RiskEngine", "FINAL: score=$finalScore → ${finalResult}")
```

### Test case untuk unit test
```kotlin
// 1. Semua faktor rendah → AMAN
assertEquals(DetectionResult.AMAN, engine.analyze(
    mlResult = AMAN, mlConfidence = 0.9,
    slopeDegrees = 2.0, rainMm = 0.0, elevationM = 50.0, soilType = "Sand"
).finalResult)

// 2. ML AMAN tapi slope >25° → tetap bisa WASPADA
assertEquals(DetectionResult.WASPADA, engine.analyze(
    mlResult = AMAN, mlConfidence = 0.8,
    slopeDegrees = 26.0, rainMm = 10.0, elevationM = 400.0, soilType = "Clay"
).finalResult)

// 3. Semua faktor null (offline) → pakai ML doang
assertEquals(DetectionResult.AMAN, engine.analyze(
    mlResult = AMAN, mlConfidence = 0.9,
    slopeDegrees = null, rainMm = null, elevationM = null, soilType = null
).finalResult)
```

### Firestore data untuk testing
Gunakan data real Jenangan untuk validasi:
```
Lokasi: -7.876, 111.470 (Pusat Jenangan)
Elevasi: ~420m
Slope: ~15-25° (lereng perbukitan)
Rain: 5-15mm/hari (musim hujan)
Soil: Clay Loam
```

---

## 8. EXIF Metadata — Alternatif Offline untuk Elevasi & GPS

### Masalah
RF1 (ElevationService) pake API call ke Open-Meteo — butuh internet, latency ~1-2 detik. Padahal HP modern nyimpen altitude + GPS di EXIF foto JPEG.

### Solusi
Baca `ExifInterface` dari file JPEG sementara SEBELUM dikonversi ke Bitmap:

```kotlin
val exif = ExifInterface(file.absolutePath)

// GPS koordinat (dari satelit — lebih akurat dari GPS chip)
val latLng = exif.latLong  // DoubleArray [lat, lon] atau null

// Altitude (dari GPS — lebih akurat dari SRTM)
val altitude = exif.getAltitude(Double.NaN)  // meter atau NaN

// Arah kamera (opsional)
val direction = exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)

// Waktu pengambilan (buat konteks)
val datetime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
```

### Prioritas Data
```
Elevasi: EXIF GPSAltitude > Open-Meteo Elevation API > null
Koordinat: EXIF GPS > FusedLocationProvider > null
```

### Keuntungan untuk RF1-RF6
- **EXIF GPS →** ganti LocationService (lebih akurat, sesuai titik foto)
- **EXIF Altitude →** ganti ElevationService (no API call, works offline)
- **Lebih cepat →** tidak perlu nunggu API response
- **Lebih akurat →** altitude GPS satelit ±5m, SRTM ±30m

### Lokasi Implementasi
- `MainScreens.kt` — `CameraView.onImageSaved` — baca EXIF sebelum bitmap
- `DeteksiViewModel.kt` — inject EXIF data ke state
- `RF6 fetch cascade` — prioritas EXIF > API

---

## 9. File Reference

| File | Path | Purpose |
|------|------|---------|
| RF1 | `data/elevation/ElevationService.kt` | Fetch elevasi dari API |
| RF2 | `data/elevation/SlopeCalculator.kt` | Hitung kemiringan dari 5 titik |
| RF3 | `data/soil/SoilTypeService.kt` | Fetch tipe tanah dari API |
| RF4 | `data/risk/MultiFactorRiskEngine.kt` | Engine utama scoring |
| RF5 | `ui/screens/MainScreens.kt` | ResultView + EnvironmentFactorCard |
| RF6 | `ui/viewmodel/DeteksiViewModel.kt` | Orchestration fetch + trigger engine |
