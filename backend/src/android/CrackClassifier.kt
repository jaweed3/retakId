package com.retakid.ml

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis

/**
 * On-device soil crack classifier using TFLite INT8 model.
 *
 * Usage:
 *   val classifier = CrackClassifier(context)
 *   val result = classifier.classify(bitmap)
 *   // result.label = "AMAN", result.confidence = 0.87
 *
 * Model spec:
 *   - Input:  uint8 [1, 224, 224, 3], range [0, 255]
 *   - Output: uint8 [1, 3], raw logits → apply softmax for probabilities
 *   - Labels: AMAN=0, WASPADA=1, BAHAYA=2
 */
class CrackClassifier(
    private val modelPath: String = "retak_mobilenetv2.tflite",
    private val labelsPath: String = "labels.txt",
    private val numThreads: Int = 4
) {
    private var interpreter: Interpreter? = null
    private val labels: List<String> by lazy { loadLabels() }

    // Input: 224x224x3 uint8
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(224 * 224 * 3)
        .apply { order(ByteOrder.nativeOrder()) }

    // Output: 1x3 uint8
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(3)
        .apply { order(ByteOrder.nativeOrder()) }

    /**
     * Initialize TFLite interpreter. Call once, preferably in ViewModel.init{}.
     */
    fun initialize(context: android.content.Context) {
        if (interpreter != null) return

        val modelFile = loadModelFile(context, modelPath)
        interpreter = Interpreter(modelFile, Interpreter.Options().apply {
            setNumThreads(numThreads)
        })
    }

    /**
     * Classify a camera frame. Thread-safe after initialize().
     *
     * @param bitmap Any resolution RGB bitmap. Will be resized to 224x224.
     * @return ClassificationResult with label, confidence [0.0-1.0], and latency.
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        checkNotNull(interpreter) { "Must call initialize() first" }

        var result: ClassificationResult
        val latencyMs = measureTimeMillis {
            // 1. Preprocess: resize → uint8 buffer
            Preprocessing.bitmapToByteBuffer(
                Bitmap.createScaledBitmap(bitmap, 224, 224, true),
                inputBuffer
            )

            // 2. Run inference
            interpreter?.run(inputBuffer, outputBuffer) ?: throw IllegalStateException("Interpreter not initialized")

            // 3. Postprocess: uint8 → softmax probabilities
            val probabilities = Postprocessing.softmax(outputBuffer)

            val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            result = ClassificationResult(
                label = labels.getOrElse(maxIdx) { "UNKNOWN" },
                confidence = probabilities[maxIdx],
                allProbabilities = probabilities.toList(),
                latencyMs = latencyMs
            )
        }
        result = result.copy(latencyMs = latencyMs)
        return result
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(context: android.content.Context, path: String): java.nio.MappedByteBuffer {
        return context.assets.openFd(path).use { afd: AssetFileDescriptor ->
            FileInputStream(afd.fileDescriptor).channel.use { channel: FileChannel ->
                channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
    }

    private fun loadLabels(): List<String> {
        // Labels are bundled in assets/labels.txt, one per line
        return javaClass.classLoader
            ?.getResourceAsStream("assets/$labelsPath")
            ?.bufferedReader()
            ?.readLines()
            ?.filter { it.isNotBlank() }
            ?: listOf("AMAN", "WASPADA", "BAHAYA")
    }
}

/**
 * Result of a single classification.
 */
data class ClassificationResult(
    val label: String,
    val confidence: Float,          // 0.0 - 1.0
    val allProbabilities: List<Float> = emptyList(),  // [P(AMAN), P(WASPADA), P(BAHAYA)]
    val latencyMs: Long = 0,
)

/**
 * Bitmap → TFLite input buffer conversion.
 */
object Preprocessing {
    fun bitmapToByteBuffer(bitmap: Bitmap, buffer: ByteBuffer): ByteBuffer {
        buffer.rewind()

        val pixels = IntArray(224 * 224)
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)

        for (pixel in pixels) {
            // Android Bitmap: ARGB, extract R, G, B
            // INT8 model expects uint8 [0, 255] in RGB order
            buffer.put(((pixel shr 16) and 0xFF).toByte())  // R
            buffer.put(((pixel shr 8) and 0xFF).toByte())   // G
            buffer.put((pixel and 0xFF).toByte())            // B
        }
        buffer.rewind()
        return buffer
    }
}

/**
 * Postprocessing: uint8 output → probabilities.
 */
object Postprocessing {
    /**
     * Apply softmax to raw uint8 logits.
     * Model output is uint8 in [0, 255]; we treat values as scaled logits.
     */
    fun softmax(buffer: ByteBuffer): FloatArray {
        buffer.rewind()
        val logits = FloatArray(3) { (buffer.get(it).toInt() and 0xFF).toFloat() }
        val maxLogit = logits.maxOrNull() ?: 0f
        val expSum = logits.sumOf { Math.exp((it - maxLogit).toDouble()) }.toFloat()
        return FloatArray(3) { Math.exp((logits[it] - maxLogit).toDouble()).toFloat() / expSum }
    }
}
