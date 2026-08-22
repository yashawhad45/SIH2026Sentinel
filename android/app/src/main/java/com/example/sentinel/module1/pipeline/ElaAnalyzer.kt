package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import java.io.ByteArrayOutputStream

class ElaAnalyzer : ForensicModule {

    override val moduleName = "Error Level Analysis"

    private val recompressionQuality = 75
    private val amplificationFactor = 10
    private val suspiciousThreshold = 0.15f
    private val forgedThreshold = 0.30f

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput): LayerResult {
        if (input !is com.example.sentinel.core.ModuleInput.ImageInput) throw IllegalArgumentException("Expected ImageInput")
        val bitmap = input.bitmap
        val scaledBitmap = scaleBitmapForProcessing(bitmap)
        val recompressed = recompressBitmap(scaledBitmap)
        val (meanScore, heatmap) = computeHeatmap(scaledBitmap, recompressed)

        ElaResultHolder.lastHeatmap = heatmap

        val riskLevel = when {
            meanScore >= forgedThreshold -> RiskLevel.FORGED
            meanScore >= suspiciousThreshold -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        return LayerResult(
            layerName = moduleName,
            passed = riskLevel == RiskLevel.CLEAR,
            riskLevel = riskLevel,
            score = meanScore.coerceIn(0f, 1f),
            details = buildList {
                add("Recompression quality: ${recompressionQuality}%")
                add("Mean anomaly score: ${"%.4f".format(meanScore)}")
                add("Amplification: ${amplificationFactor}x")
                when (riskLevel) {
                    RiskLevel.CLEAR -> add("No significant compression discontinuities detected")
                    RiskLevel.SUSPICIOUS -> add("Moderate ELA variance — possible minor editing detected")
                    RiskLevel.FORGED -> add("High ELA variance — spliced or altered regions likely present")
                }
            }
        )
    }

    private fun scaleBitmapForProcessing(bitmap: Bitmap): Bitmap {
        val maxDim = 640
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else bitmap
    }

    private fun recompressBitmap(original: Bitmap): Bitmap {
        val stream = ByteArrayOutputStream()
        original.compress(Bitmap.CompressFormat.JPEG, recompressionQuality, stream)
        val bytes = stream.toByteArray()
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return decoded?.let {
            Bitmap.createScaledBitmap(it, original.width, original.height, true)
        } ?: original
    }

    private fun computeHeatmap(original: Bitmap, recompressed: Bitmap): Pair<Float, Bitmap> {
        val width = original.width
        val height = original.height
        val heatmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var totalDiff = 0L

        for (x in 0 until width) {
            for (y in 0 until height) {
                val orig = original.getPixel(x, y)
                val recomp = recompressed.getPixel(x, y)
                val dr = kotlin.math.abs(Color.red(orig) - Color.red(recomp))
                val dg = kotlin.math.abs(Color.green(orig) - Color.green(recomp))
                val db = kotlin.math.abs(Color.blue(orig) - Color.blue(recomp))
                val mean = (dr + dg + db) / 3
                totalDiff += mean
                val amp = (mean * amplificationFactor).coerceAtMost(255)
                heatmap.setPixel(x, y, Color.rgb(amp, (amp * 0.3f).toInt(), 255 - amp))
            }
        }

        val meanScore = (totalDiff.toFloat() / (width.toLong() * height.toLong())) / 255f
        return Pair(meanScore, heatmap)
    }
}

object ElaResultHolder {
    var lastHeatmap: Bitmap? = null
}
