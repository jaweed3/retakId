package com.unidagontor.retakid.data.risk

import com.unidagontor.retakid.data.elevation.SlopeData
import com.unidagontor.retakid.data.ml.DetectionResult
import com.unidagontor.retakid.data.soil.SoilType
import kotlin.math.roundToInt

enum class RiskLabel(val label: String, val emoji: String) {
    RENDAH("Rendah", "\uD83D\uDFE2"),
    SEDANG("Sedang", "\uD83D\uDFE1"),
    TINGGI("Tinggi", "\uD83D\uDD34"),
    SANGAT_TINGGI("Sangat Tinggi", "\uD83D\uDD34\uFE0F")
}

data class FactorContribution(
    val factor: RiskFactor,
    val rawValue: String,
    val score: Double,
    val weight: Double,
    val weightedScore: Double,
    val riskLabel: RiskLabel
)

enum class RiskFactor(val displayName: String) {
    ML("Analisis Visual"),
    SLOPE("Kemiringan Lereng"),
    RAIN("Curah Hujan"),
    ELEVATION("Ketinggian"),
    SOIL("Jenis Tanah")
}

data class RiskFactorReport(
    val mlResult: DetectionResult,
    val mlConfidence: Float,
    val finalScore: Double,
    val finalResult: DetectionResult,
    val factors: List<FactorContribution>,
    val isUpgraded: Boolean,
    val isDowngraded: Boolean
)

object MultiFactorRiskEngine {

    fun analyze(
        mlResult: DetectionResult,
        mlConfidence: Float,
        slopeDegrees: Double? = null,
        rainMm: Double? = null,
        elevationMeters: Double? = null,
        soilType: SoilType? = null
    ): RiskFactorReport {
        val factors = mutableListOf<FactorContribution>()
        var totalWeight = 0.0
        var weightedSum = 0.0
        var anyMissing = false

        addFactor(factors, RiskFactor.ML, mlFactorRaw(mlResult, mlConfidence), mlScore(mlResult, mlConfidence), 0.50)
        totalWeight += 0.50
        weightedSum += mlScore(mlResult, mlConfidence) * 0.50

        if (slopeDegrees != null) {
            val s = slopeScore(slopeDegrees)
            addFactor(factors, RiskFactor.SLOPE, "${slopeDegrees.toInt()}°", s, 0.20)
            totalWeight += 0.20
            weightedSum += s * 0.20
        } else {
            anyMissing = true
        }

        if (rainMm != null) {
            val s = rainScore(rainMm)
            addFactor(factors, RiskFactor.RAIN, "${rainMm.toInt()} mm", s, 0.15)
            totalWeight += 0.15
            weightedSum += s * 0.15
        } else {
            anyMissing = true
        }

        if (elevationMeters != null) {
            val s = elevationScore(elevationMeters)
            addFactor(factors, RiskFactor.ELEVATION, "${elevationMeters.toInt()} m", s, 0.10)
            totalWeight += 0.10
            weightedSum += s * 0.10
        } else {
            anyMissing = true
        }

        if (soilType != null) {
            val s = soilType.riskScore
            addFactor(factors, RiskFactor.SOIL, soilType.indonesianName, s, 0.05)
            totalWeight += 0.05
            weightedSum += s * 0.05
        } else {
            anyMissing = true
        }

        val finalScore = if (anyMissing && totalWeight > 0.0) {
            weightedSum / totalWeight
        } else {
            weightedSum
        }

        val clampedScore = finalScore.coerceIn(0.0, 1.0)
        val finalResult = resultFromScore(clampedScore)

        val mlOnlyScore = mlScore(mlResult, mlConfidence)
        val isUpgraded = clampedScore > mlOnlyScore + 0.05
        val isDowngraded = clampedScore < mlOnlyScore - 0.05

        return RiskFactorReport(
            mlResult = mlResult,
            mlConfidence = mlConfidence,
            finalScore = clampedScore,
            finalResult = finalResult,
            factors = factors.sortedByDescending { it.weightedScore },
            isUpgraded = isUpgraded,
            isDowngraded = isDowngraded
        )
    }

    private fun addFactor(
        factors: MutableList<FactorContribution>,
        factor: RiskFactor,
        rawValue: String,
        score: Double,
        weight: Double
    ) {
        factors.add(
            FactorContribution(
                factor = factor,
                rawValue = rawValue,
                score = score,
                weight = weight,
                weightedScore = (score * weight * 100.0).roundToInt() / 100.0,
                riskLabel = labelFromScore(score)
            )
        )
    }

    private fun mlFactorRaw(result: DetectionResult, confidence: Float): String {
        return "${result.name} ${(confidence * 100).toInt()}%"
    }

    fun mlScore(result: DetectionResult, confidence: Float): Double {
        val c = confidence.toDouble()
        return when (result) {
            DetectionResult.AMAN -> when {
                c >= 0.70 -> 0.1
                c >= 0.50 -> 0.2
                else -> 0.3
            }
            DetectionResult.WASPADA -> when {
                c >= 0.70 -> 0.5
                c >= 0.50 -> 0.6
                else -> 0.7
            }
            DetectionResult.BAHAYA -> when {
                c >= 0.70 -> 0.8
                c >= 0.50 -> 0.9
                else -> 1.0
            }
            DetectionResult.TIDAK_PASTI -> 0.5
        }
    }

    private fun slopeScore(degrees: Double): Double = when {
        degrees < 8.0 -> 0.1
        degrees < 15.0 -> 0.4
        degrees < 25.0 -> 0.7
        else -> 1.0
    }

    private fun rainScore(mm: Double): Double = when {
        mm <= 0.0 -> 0.0
        mm < 5.0 -> 0.2
        mm < 15.0 -> 0.5
        mm < 30.0 -> 0.8
        else -> 1.0
    }

    private fun elevationScore(meters: Double): Double = when {
        meters < 200.0 -> 0.1
        meters < 500.0 -> 0.4
        meters < 1000.0 -> 0.7
        else -> 1.0
    }

    private fun labelFromScore(score: Double): RiskLabel = when {
        score <= 0.2 -> RiskLabel.RENDAH
        score <= 0.5 -> RiskLabel.SEDANG
        score <= 0.8 -> RiskLabel.TINGGI
        else -> RiskLabel.SANGAT_TINGGI
    }

    fun resultFromScore(score: Double): DetectionResult = when {
        score <= 0.33 -> DetectionResult.AMAN
        score <= 0.66 -> DetectionResult.WASPADA
        else -> DetectionResult.BAHAYA
    }
}
