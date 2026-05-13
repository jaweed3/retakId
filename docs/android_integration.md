# Android TFLite Integration — Retak.id

## Quick Check (Adam: pastikan model valid)

Sebelum build APK, model harus lolos validasi dari ML team:
```bash
make validate-model
# ✓ ALL CHECKS PASSED — safe to deploy
```

## Model Spec

| Property | Value |
|----------|-------|
| File | `app/src/main/assets/retak_mobilenetv2.tflite` |
| Size | ~2.6 MB (INT8) |
| Input | uint8 `[1, 224, 224, 3]` RGB |
| Output | float32 `[1, 3]` — raw logits |
| Labels | 0=AMAN, 1=WASPADA, 2=BAHAYA |

## Integration Code (copy-paste ke project)

### build.gradle.kts
```kotlin
implementation("org.tensorflow:tensorflow-lite:2.16.1")
```

### BitmapUtils.kt
```kotlin
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Bitmap.toModelInput(): ByteBuffer {
    val resized = Bitmap.createScaledBitmap(this, 224, 224, true)
    val buffer = ByteBuffer.allocateDirect(224 * 224 * 3)
    buffer.order(ByteOrder.nativeOrder())

    val pixels = IntArray(224 * 224)
    resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

    for (pixel in pixels) {
        buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
        buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
        buffer.put((pixel and 0xFF).toByte())           // B
    }
    buffer.rewind()
    return buffer
}
```

### MLAnalyzer.kt
```kotlin
import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class CrackClassifier(context: Context) {
    private val interpreter: Interpreter
    private val output = Array(1) { FloatArray(3) }
    private val labels = arrayOf("AMAN", "WASPADA", "BAHAYA")

    init {
        val model = context.assets.openFd("retak_mobilenetv2.tflite").use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { ch ->
                ch.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
    }

    fun classify(bitmap: Bitmap): ClassificationResult {
        val input = bitmap.toModelInput()
        interpreter.run(input, output)

        val logits = output[0]
        val maxLogit = logits.max()
        val expSum = logits.sumOf { Math.exp((it - maxLogit).toDouble()) }.toFloat()
        val probs = FloatArray(3) {
            Math.exp((logits[it] - maxLogit).toDouble()).toFloat() / expSum
        }

        val maxIdx = probs.indices.maxBy { probs[it] }
        return ClassificationResult(
            label = labels[maxIdx],
            confidence = probs[maxIdx],
            allProbs = probs.toList(),
        )
    }

    fun close() = interpreter.close()
}

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val allProbs: List<Float>,
)
```

### Usage in ViewModel
```kotlin
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val classifier = CrackClassifier(application)

    fun analyze(bitmap: Bitmap): ClassificationResult {
        return classifier.classify(bitmap)
    }

    override fun onCleared() {
        classifier.close()
    }
}
```

## Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `Cannot create interpreter` | Model file missing or corrupt | Check `assets/retak_mobilenetv2.tflite` exists |
| `java.lang.IllegalArgumentException: Internal error: Failed to run...` | Wrong input format | Use `ByteBuffer` uint8, NOT `FloatBuffer` |
| All predictions same class | Old model with `preprocess_input` bug | Update model via `make deploy-model` |
| `UnsatisfiedLinkError` | Missing TFLite native lib | Add `tensorflow-lite:2.16.1` to build.gradle |
| App crash on `classify()` | Interpreter not initialized | Call `CrackClassifier(context)` ONCE in ViewModel |

## Communication with ML Team

1. **Model update**: Jalankan `make deploy-model` lalu `git pull origin mobile-app`
2. **Validation**: Sebelum build APK, cek `make validate-model` — harus PASS
3. **Bug report**: Kalo model aneh, kirim:
   - Screenshot input (foto yang diambil)
   - Log `probs` output (3 angka)
   - Versi model (cek `git log --oneline mobile-app`)
