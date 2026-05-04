package com.unidagontor.retakid.data.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
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
    private var classifier: ImageClassifier? = null

    private fun setupClassifier() {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(1)
            .build()

        try {
            // "model.tflite" adalah nama default. User perlu menyesuaikan atau menamai filenya model.tflite
            classifier = ImageClassifier.createFromFileAndOptions(
                context,
                "model.tflite",
                options
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun analyzeImage(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.IO) {
        if (classifier == null) {
            setupClassifier()
        }

        // Jika model tetap tidak bisa dimuat (misal file tidak ada), gunakan simulasi agar tidak crash
        if (classifier == null) {
            delay(1000)
            return@withContext DetectionResult.entries[Random.nextInt(DetectionResult.entries.size)]
        }

        val imageProcessor = ImageProcessor.Builder().build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        
        val results = classifier?.classify(tensorImage)
        val topResult = results?.firstOrNull()?.categories?.firstOrNull()?.label?.uppercase()

        when (topResult) {
            "BAHAYA" -> DetectionResult.BAHAYA
            "WASPADA" -> DetectionResult.WASPADA
            else -> DetectionResult.AMAN
        }
    }
}

class MockMLAnalyzer : MLAnalyzer {
    override suspend fun analyzeImage(bitmap: Bitmap): DetectionResult {
        // Simulate processing time
        delay(2000)
        
        // Return a random result for demonstration
        val results = DetectionResult.entries
        return results[Random.nextInt(results.size)]
    }
}
