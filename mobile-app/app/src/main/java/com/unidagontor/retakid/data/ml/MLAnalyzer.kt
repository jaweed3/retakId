package com.unidagontor.retakid.data.ml

import android.content.Context
import android.graphics.Bitmap
import com.unidagontor.retakid.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random

enum class DetectionResult(val label: String) {
    AMAN("AMAN"),
    WASPADA("WASPADA"),
    BAHAYA("BAHAYA")
}

interface MLAnalyzer {
    suspend fun analyzeImage(bitmap: Bitmap): DetectionResult
}

class TFLiteMLAnalyzer(private val context: Context) : MLAnalyzer {
    private var interpreter: Interpreter? = null
    private val labels = arrayOf("AMAN", "WASPADA", "BAHAYA")

    // Output: float32 [1, 3] — raw logits, apply softmax
    private val outputArray = Array(1) { FloatArray(3) }

    private fun loadModelFile(): MappedByteBuffer {
        return context.assets.openFd("retak_mobilenetv2.tflite").use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
    }

    private fun setupInterpreter() {
        val modelBuffer = loadModelFile()
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(4)
        })
    }

    override suspend fun analyzeImage(bitmap: Bitmap): DetectionResult =
        withContext(Dispatchers.Default) {
            if (interpreter == null) {
                setupInterpreter()
            }

            if (interpreter == null) {
                return@withContext DetectionResult.entries[Random.nextInt(3)]
            }

            // 1. Preprocess: resize 224x224 → RGB ByteBuffer uint8 [0,255]
            val inputBuffer = BitmapUtils.bitmapToByteBuffer(bitmap)

            // 2. Run inference
            interpreter?.run(inputBuffer, outputArray)

            // 3. Softmax on float output
            val logits = outputArray[0]
            val maxLogit = logits.maxOrNull() ?: 0f
            val expSum = logits.sumOf { Math.exp((it - maxLogit).toDouble()) }.toFloat()
            val probs = FloatArray(3) {
                Math.exp((logits[it] - maxLogit).toDouble()).toFloat() / expSum
            }

            // 4. Get predicted class
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
            val confidence = probs[maxIdx]

            // 5. If confidence < 40%, default to AMAN (conservative)
            if (confidence < 0.4f) {
                return@withContext DetectionResult.AMAN
            }

            when (maxIdx) {
                0 -> DetectionResult.AMAN
                1 -> DetectionResult.WASPADA
                2 -> DetectionResult.BAHAYA
                else -> DetectionResult.AMAN
            }
        }
}

class MockMLAnalyzer : MLAnalyzer {
    override suspend fun analyzeImage(bitmap: Bitmap): DetectionResult {
        kotlinx.coroutines.delay(2000)
        return DetectionResult.entries[Random.nextInt(DetectionResult.entries.size)]
    }
}
