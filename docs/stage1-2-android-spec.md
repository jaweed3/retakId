# Stage 1 & 2 — Technical Spec for Android Developer

## Overview

| Stage | What | Data Layer | UI Layer |
|-------|------|------------|----------|
| 1a | Confidence threshold | ✅ `MLAnalyzer.kt` | Minimal — confidence bar already exists |
| 1b | Input validation (blur/brightness) | ✅ `BitmapUtils.kt` | Needs integration |
| 2a | SRTM offline elevation | ✅ `HgtElevationSource.kt`, `ElevationService.kt` | Nothing — fully automatic |
| 2b | Onboarding GPS + download | ✅ `ElevasiOnboardingScreen.kt`, `ViewModel` | Needs minor navigation wiring |

---

## Stage 1 — Confidence Threshold + Input Validation

### 1a. Confidence Threshold (`MLAnalyzer.kt`)

**What runs automatically (no UI needed):**

- `MLAnalyzer` (interface) → `TFLiteMLAnalyzer` does softmax → if `confidence < 0.50` → returns `DetectionResult.TIDAK_PASTI`
- `DetectionResult` enum now has `AMAN`, `WASPADA`, `BAHAYA`, `TIDAK_PASTI`
- `MockMLAnalyzer` — useful for dev/testing (returns random results, 2s delay)

**Key detail — TIDAK_PASTI is a DETECTION STAGE, not a result:**

In `DeteksiViewModel.analyzeImage()`:
```
BitmapUtils.validateImage(bitmap)
  → if blur/gelap: stage = UNCERTAIN, validationError = message, STOP
  
TFLiteMLAnalyzer.analyzeImage(bitmap) → MLResult
  → if TIDAK_PASTI: stage = UNCERTAIN, validationError = "Hasil tidak pasti (X%)", STOP
  → else: stage = ANALYZING_ENV, fetch environmental factors
```

**UIConsiderations:**
- `UncertainView` sudah ada di `MainScreens.kt:291` — menampilkan icon warning + confidence bar + tombol "Foto Ulang"
- Cuma nampilin `validationError` text + `confidence` bar — ✅ kerja tanpa perubahan

### 1b. Image Quality Validation (`BitmapUtils.kt`)

**Checks applied before ML inference:**
| Check | Threshold | Message |
|-------|-----------|---------|
| Terlalu gelap | meanBrightness < 30 | "Foto gelap — aktifkan flash" |
| Terlalu terang | meanBrightness > 220 | "Foto terlalu terang — hindari cahaya langsung" |
| Blur (Laplacian var) | variance < 100 | "Foto buram — dekatkan kamera ke retakan tanah" |

**UIConsiderations:**
- These errors appear as `validationError` text in the existing `UncertainView`
- `UncertainView` already shows the message, confidence bar, and retake button
- No extra UI needed unless you want per-type icons/color

**ConfidenceBar (`MainScreens.kt:344`):**
```
confidence < 0.5  → red   (StatusBahaya)
0.5 ≤ c < 0.7    → orange (StatusWaspada)
c ≥ 0.7          → green  (StatusAman)
```
Already integrated in both `UncertainView` (line 323) and `ResultView` (line 478).

### Stage 1 — Dev Action Items

| # | Task | File | Done? |
|---|------|------|-------|
| 1 | Verify `UncertainView` menerima `validationError` dari blur/gelap/TIDAK_PASTI | `MainScreens.kt:291` | ✅ already handles |
| 2 | Verify `ConfidenceBar` nampil di `UncertainView` | line 323 | ✅ already there |
| 3 | Review `DeteksiViewModel.analyzeImage()` flow | `DeteksiViewModel.kt:73` | ✅ logic done |

---

## Stage 2 — SRTM Offline Elevation

### Architecture

```
ElevationService.initFromAssets(context)   ← dipanggil di RetakIdApplication.onCreate()
  ├── HgtElevationSource.loadFromAssets()  ← tile built-in dari assets/dem/
  └── HgtElevationSource.loadFromDirectory() ← tile dari filesDir/dem/ (download hasil onboarding)

...

SlopeCalculator.calculateSlope(lat, lon)
  └── ElevationService.getElevation()      ← otomatis pake HGT, fallback API
      ├── HgtElevationSource.getElevation()  ← bilinear interpolation dari tile di RAM
      └── Open-Meteo API                      ← fallback kalau tile gak ada
```

**Key data:**
- 1 tile SRTM3 = 2.8 MB (1201×1201, 16-bit elevation)
- 4 tiles (2×2 grid, ~220 km coverage) = ~11 MB RAM saat loaded
- Bilinear interpolation untuk sub-pixel accuracy
- Fallback ke Open-Meteo API kalau tile gak tersedia

### 2a. Files Already Built (review only)

| File | Notes |
|------|-------|
| `HgtElevationSource.kt` | Multi-tile Map, loadFromAssets/loadFromDirectory, bilinear interp |
| `ElevationService.kt` | Offline-first (HGT → API), response cache per coordinate |
| `SlopeCalculator.kt` | Hitung slope dari 4 offset points (N/S/E/W, ~100m) |
| `TileRegionCalculator.kt` | GPS → 4 tile names (2×2 grid) |
| `TileDownloader.kt` | Download .hgt.zip dari kurviger.de, save ke filesDir/dem/ |
| `RetakIdApplication.kt` | Panggil `ElevationService.initFromAssets(this)` di onCreate |

**What's loaded where:**
- `assets/dem/S08E111.hgt` — built-in, covers Ponorogo, always available
- `filesDir/dem/` — downloaded tiles from onboarding, scanned at every app start

**Elevation is a FACTOR in risk analysis — not a screen.**

`DeteksiViewModel.fetchEnvironmentalFactors()` panggil:
```kotlin
val elevation = ElevationService.getElevation(lat, lon)  // offline-first
val slope = SlopeCalculator.calculateSlope(lat, lon)      // via ElevationService
```

These feed into `MultiFactorRiskEngine` yang menghasilkan `RiskFactorReport.factors` → ditampilkan di `ResultView` → `EnvironmentFactorCard` → `FactorRow` sebagai "Ketinggian" dan "Kemiringan Lereng".

**UIConsiderations:**
- No new screens or composable needed for elevation ⚠️
- Elevation dan slope otomatis masuk sebagai baris di `FactorRow` (`MainScreens.kt:636`)
- `FactorContribution.rawValue` menampilkan "102 m" dan "Curam"
- `RiskFactor.SLOPE` → displayName = "Kemiringan Lereng", weight = 20%
- `RiskFactor.ELEVATION` → displayName = "Ketinggian", weight = 10%
- Kalau elevation gagal (HGT null + API null), faktor tersebut dilewat + weight redistributed

**Cuma perlu verify:**
- [ ] `ElevationService.initFromAssets(this)` udah dipanggil di `RetakIdApplication.onCreate()`
- [ ] FactorRow nampilin data elevation & slope dengan benar

### 2b. Onboarding Elevation (UI is already built)

**Navigation flow:**
```
Splash → Onboarding (3 slides) → ElevasiOnboarding → Login → Main

Next launch: skip to Main (via elevasi_ready flag in SharedPreferences)
```

**Files already built:**

| File | Contents |
|------|----------|
| `ElevasiOnboardingScreen.kt` | 6 UI states (Idle → Permission → Detecting → FoundLocation → Downloading → Ready/Error) |
| `ElevasiOnboardingViewModel.kt` | State machine: mLocationservice → tile calculation → sequential download → load tiles |
| `AppNavigation.kt:48-52` | Route `"elevasi-onboarding"` added + skip logic via `prefs.getBoolean("elevasi_ready")` |

**Onboarding UX flow:**
1. "Peta Elevasi Offline" screen → "Deteksi Lokasi Saya" button + "Lewati"
2. Location permission dialog (Android native)
3. "Mendeteksi lokasi..." spinner
4. Location found → shows daerah name + 4 tile names
5. "Download Peta Elevasi" → progress bar per tile (1/4, 2/4...)
6. "Siap!" → tap "Lanjutkan" → login
7. On error → "Coba Lagi" button + "Lewati"

**UIConsiderations:**
- The screen, ViewModel, and navigation are already built ✅
- Just need to verify the screen looks good when you run the app
- Dev can adjust colors, spacing, text as needed

### 2b — Dev Action Items

| # | Task | File | Done? |
|---|------|------|-------|
| 1 | Verify `ElevasiOnboardingScreen` renders correctly | `ElevasiOnboardingScreen.kt` | Need run |
| 2 | Verify GPS permission dialog appears | built-in via `ActivityResultContracts` | Need run |
| 3 | Verify download progress bar animates | `DownloadingContent` composable | Need run |
| 4 | Verify FactorRow shows slope & elevation | `MainScreens.kt:636` | Already works |
| 5 | Confirm error state (GPS off) shows correct message | `ElevasiOnboardingViewModel.kt:46` | Need run |

---

## Files Changed Summary

### New Files (4)
```
data/elevation/TileRegionCalculator.kt
data/elevation/TileDownloader.kt
ui/viewmodel/ElevasiOnboardingViewModel.kt
ui/screens/ElevasiOnboardingScreen.kt
```

### Modified Files (3)
```
data/elevation/HgtElevationSource.kt   ← multi-tile Map (was single tile)
data/elevation/ElevationService.kt      ← +loadFromDirectory for downloaded tiles
ui/screens/AppNavigation.kt            ← route "elevasi-onboarding" + skip logic
```

### Unchanged (already worked before)
```
data/ml/MLAnalyzer.kt                   ← TIDAK_PASTI + threshold
util/BitmapUtils.kt                     ← validateImage()
ui/viewmodel/DeteksiViewModel.kt        ← VALIDATING + UNCERTAIN stages
ui/screens/MainScreens.kt               ← ValidatingView, UncertainView, ConfidenceBar (already exist)
```

---

## Common Pitfalls

1. **TIDAK_PASTI ≠ DetectionResult in risk engine** — `TIDAK_PASTI` di-handle di `DeteksiViewModel` stage, bukan dikirim ke `MultiFactorRiskEngine`. Kalau TIDAK_PASTI, langsung STOP sebelum `fetchEnvironmentalFactors()`.

2. **initFromAssets dipanggil ONCE** — ada `if (initialized) return` guard di `ElevationService.kt:19`. Safe dipanggil berkali-kali.

3. **S08 tile only for Jenangan** — tile built-in `S08E111.hgt` cuma cover Ponorogo. Daerah lain perlu download via onboarding.

4. **Phone restart → tiles masih di filesDir** — `filesDir` persistent across reboots. Tiles gak perlu di-download ulang.

5. **Memory pressure** — 4 tiles ≈ 11 MB in ShortArrays. Android will keep them in memory. If OS needs RAM, app process bisa di-kill. Tiles re-loaded at next `initFromAssets()`.
