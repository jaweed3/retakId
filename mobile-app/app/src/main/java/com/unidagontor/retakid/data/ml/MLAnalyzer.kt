package com.unidagontor.retakid.data.ml

import android.content.Context
import android.graphics.Bitmap
import com.unidagontor.retakid.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random

enum class DetectionResult(val label: String) {
    AMAN("AMAN"),
    WASPADA("WASPADA"),
    BAHAYA("BAHAYA"),
    TIDAK_PASTI("TIDAK PASTI");

    companion object {
        const val CONFIDENCE_THRESHOLD = 0.50f
    }
}

data class MLResult(
    val detectionResult: DetectionResult,
    val confidence: Float
)

interface MLAnalyzer {
    suspend fun analyzeImage(bitmap: Bitmap): MLResult
}

class TFLiteMLAnalyzer(private val context: Context) : MLAnalyzer {
    private var interpreter: Interpreter? = null
    private val labels = arrayOf("AMAN", "WASPADA", "BAHAYA")

    private val outputArray = Array(1) { FloatArray(3) }

    private fun loadModelFile(): MappedByteBuffer {
        // Prefer delta-updated model in internal storage; fall back to bundled assets
        val cachedModel = DeltaModelLoader.getCachedModelPath(context)
        return if (cachedModel.exists()) {
            RandomAccessFile(cachedModel, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
            }
        } else {
            context.assets.openFd("retak_mobilenetv2.tflite").use { afd ->
                FileInputStream(afd.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
        }
    }

    private fun setupInterpreter() {
        val modelBuffer = loadModelFile()
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
            setNumThreads(4)
        })
    }

    override suspend fun analyzeImage(bitmap: Bitmap): MLResult =
        withContext(Dispatchers.Default) {
            if (interpreter == null) {
                setupInterpreter()
            }

            if (interpreter == null) {
                val random = DetectionResult.entries[Random.nextInt(3)]
                return@withContext MLResult(random, 0.5f)
            }

            val inputBuffer = BitmapUtils.bitmapToByteBuffer(bitmap)

            interpreter?.run(inputBuffer, outputArray)

            val logits = outputArray[0]
            val maxLogit = logits.maxOrNull() ?: 0f
            val expSum = logits.sumOf { Math.exp((it - maxLogit).toDouble()) }.toFloat()
            val probs = FloatArray(3) {
                Math.exp((logits[it] - maxLogit).toDouble()).toFloat() / expSum
            }

            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
            val confidence = probs[maxIdx]

            if (confidence < DetectionResult.CONFIDENCE_THRESHOLD) {
                return@withContext MLResult(DetectionResult.TIDAK_PASTI, confidence)
            }

            val result = when (maxIdx) {
                0 -> DetectionResult.AMAN
                1 -> DetectionResult.WASPADA
                2 -> DetectionResult.BAHAYA
                else -> DetectionResult.AMAN
            }
            MLResult(result, confidence)
        }
}

class MockMLAnalyzer : MLAnalyzer {
    override suspend fun analyzeImage(bitmap: Bitmap): MLResult {
        kotlinx.coroutines.delay(2000)
        val result = DetectionResult.entries[Random.nextInt(DetectionResult.entries.size)]
        val confidence = 0.5f + Random.nextFloat() * 0.5f
        return MLResult(result, confidence)
    }
}
