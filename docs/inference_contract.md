# TFLite Inference Contract — Retak.id

Specification for integrating the trained model into the Android app.

## Model Artifacts

| File | Source | Destination |
|------|--------|-------------|
| `retak_mobilenetv2.tflite` | `backend/models/` | `app/src/main/assets/` |
| `labels.txt` | `backend/models/` | `app/src/main/assets/` |

Deploy via:
```bash
make deploy
```

## Input Specification

```
Tensor:  input_1
Shape:   [1, 224, 224, 3]
Type:    UINT8
Range:   [0, 255] — raw pixel values, NO normalization needed
Format:  RGB (not BGR, not ARGB)
Order:   Channel-last (HWC)
```

**Preprocessing pipeline (Android → TFLite):**
```
CameraX Bitmap (any resolution, ARGB)
  → Resize to 224×224 (bilinear interpolation)
  → Extract R, G, B channels (discard alpha)
  → Pack into ByteBuffer [0, 255] uint8
  → Feed to interpreter
```

## Output Specification

```
Tensor:  Identity
Shape:   [1, 3]
Type:    UINT8
Range:   [0, 255] per class
```

**Postprocessing pipeline (TFLite → UI):**
```
UINT8 output [z0, z1, z2]
  → Convert to float: zi / 255.0
  → Apply softmax → [p(AMAN), p(WASPADA), p(BAHAYA)]
  → argmax → predicted class
```

## Class Labels

```
0 = AMAN    (Safe — minor soil cracks, no action needed)
1 = WASPADA (Caution — significant cracks, monitor and report)
2 = BAHAYA  (Danger — critical cracks, evacuate and contact BPBD)
```

## Quantization Parameters (for reference)

```
Quantization:  INT8 post-training quantization (PTQ)
Input scale:   ~1.0 (no rescaling — uint8 in, uint8 out)
Output scale:  ~1.0
Zero point:    0
```

If using the Android TFLite API directly (not our wrapper), you don't need to
manually quantize/dequantize — the interpreter handles it.

## Android Integration (Kotlin)

See `backend/src/android/CrackClassifier.kt` for the reference implementation.

### Quick Start (Jetpack Compose + CameraX)

```kotlin
// 1. Init in ViewModel
class CameraViewModel : ViewModel() {
    private val classifier = CrackClassifier()

    init {
        classifier.initialize(context)
    }

    fun analyze(imageProxy: ImageProxy): ClassificationResult {
        val bitmap = imageProxy.toBitmap()
        return classifier.classify(bitmap)
    }

    override fun onCleared() {
        classifier.close()
    }
}

// 2. Display in Composable
@Composable
fun CrackDetectionOverlay(result: ClassificationResult) {
    val color = when (result.label) {
        "AMAN" -> Color(0xFF4CAF50)
        "WASPADA" -> Color(0xFFFFC107)
        "BAHAYA" -> Color(0xFFF44336)
        else -> Color.Gray
    }
    Column(modifier = Modifier.background(color.copy(alpha = 0.7f))) {
        Text("${result.label} (${(result.confidence * 100).toInt()}%)")
        Text("${result.latencyMs}ms")
    }
}
```

## Performance Targets

| Metric | Target | Measured |
|--------|--------|----------|
| Model size | <5 MB | ~2.6 MB (INT8) |
| Inference latency | <50 ms | TBD (device-dependent) |
| Memory overhead | <20 MB | TBD |

## Model Update Strategy (Post-MVP)

1. Train new version → produce `retak_mobilenetv2.tflite`
2. Upload to GitHub Releases with version tag
3. App checks version on launch (Firebase Remote Config)
4. Download new model to app internal storage
5. Fallback to bundled model if download fails

For competition MVP: **bundle model in APK.** No network dependency.
