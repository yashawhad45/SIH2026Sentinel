package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.atan2

data class TextBlockInfo(
    val text: String,
    val boundingBox: Rect,
    val slope: Double,
    val centerY: Int,
    val confidence: Float
)

class TypographyAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val maxSlopeDeviation = 2.5
    private val maxSpacingVariance = 0.35

    suspend fun analyze(bitmap: Bitmap): LayerResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))

        val blocks = mutableListOf<TextBlockInfo>()
        val lineConfidences = mutableListOf<Float>()

        for (block in visionText.textBlocks) {
            if (block.boundingBox == null || block.text.isBlank()) continue

            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val corners = line.cornerPoints

                val slope = if (corners != null && corners.size >= 2) {
                    val dx = (corners[1].x - corners[0].x).toDouble()
                    val dy = (corners[1].y - corners[0].y).toDouble()
                    Math.toDegrees(atan2(dy, dx))
                } else 0.0

                // Extract per-element confidence as font consistency proxy
                val elementConfidences = line.elements.mapNotNull { elem ->
                    elem.confidence
                }
                val avgConf = if (elementConfidences.isNotEmpty())
                    elementConfidences.average().toFloat() else 0.95f

                lineConfidences.add(avgConf)

                blocks.add(TextBlockInfo(
                    text = line.text,
                    boundingBox = box,
                    slope = slope,
                    centerY = (box.top + box.bottom) / 2,
                    confidence = avgConf
                ))
            }
        }

        if (blocks.size < 3) {
            return LayerResult(
                layerName = "Typography Analysis",
                passed = true,
                riskLevel = RiskLevel.CLEAR,
                score = 0f,
                details = listOf("Not enough text lines for layout analysis")
            )
        }

        val issues = mutableListOf<String>()
        val info = mutableListOf<String>()

        // === Check 1: Slope Alignment ===
        val slopes = blocks.map { it.slope }
        val medianSlope = slopes.sorted()[slopes.size / 2]
        val misaligned = blocks.filter { abs(it.slope - medianSlope) > maxSlopeDeviation }

        if (misaligned.isNotEmpty()) {
            misaligned.forEach { block ->
                val dev = abs(block.slope - medianSlope)
                issues.add("Text '${block.text.take(25)}' tilted %.1f degrees off-axis".format(dev))
            }
        } else {
            info.add("Text alignment: All lines properly aligned")
        }

        // === Check 2: Line Spacing Consistency ===
        val sorted = blocks.sortedBy { it.centerY }
        val spacings = mutableListOf<Int>()
        for (i in 1 until sorted.size) {
            spacings.add(sorted[i].centerY - sorted[i - 1].centerY)
        }

        if (spacings.size >= 2) {
            val avgSpacing = spacings.average()
            val irregular = spacings.count { abs(it - avgSpacing) / avgSpacing > maxSpacingVariance }
            if (irregular > spacings.size / 3) {
                issues.add("Irregular line spacing: $irregular of ${spacings.size} gaps deviate >35%")
            } else {
                info.add("Line spacing: Uniform across text blocks")
            }
        }

        // === Check 3: Font Consistency (OCR Confidence Proxy) ===
        // Official UIDAI fonts are well-recognized by OCR (high confidence).
        // Generic/mismatched fonts cause OCR uncertainty (low confidence).
        if (lineConfidences.size >= 3) {
            val avgConfidence = lineConfidences.average()
            val lowConfLines = blocks.filter { it.confidence < 0.7f }

            if (lowConfLines.isNotEmpty() && avgConfidence > 0.8) {
                // Some lines have drastically lower confidence = inconsistent fonts
                lowConfLines.forEach { line ->
                    issues.add("Font anomaly on '${line.text.take(25)}' (confidence: ${(line.confidence * 100).toInt()}%)")
                }
            }

            // Check confidence variance (mixed fonts = high variance)
            val confVariance = lineConfidences.map { (it - avgConfidence) * (it - avgConfidence) }.average()
            if (confVariance > 0.02) {
                issues.add("Inconsistent font rendering detected across text (variance: %.3f)".format(confVariance))
            } else {
                info.add("Font consistency: Uniform OCR confidence across all text")
            }
        }

        // === Check 4: Left Margin Consistency ===
        val leftMargins = blocks.map { it.boundingBox.left }
        val marginGroups = leftMargins.groupBy { it / 15 }
            .values.sortedByDescending { it.size }

        if (marginGroups.isNotEmpty()) {
            val dominant = marginGroups[0].size
            val outliers = blocks.size - dominant
            if (outliers > blocks.size / 3) {
                issues.add("Left margin inconsistency: $outliers of ${blocks.size} lines misaligned")
            } else {
                info.add("Left margins: Consistent alignment")
            }
        }

        val score = when {
            issues.size >= 4 -> 0.95f
            issues.size == 3 -> 0.75f
            issues.size == 2 -> 0.55f
            issues.size == 1 -> 0.35f
            else -> 0f
        }

        return LayerResult(
            layerName = "Typography Analysis",
            passed = issues.isEmpty(),
            riskLevel = when {
                issues.size >= 3 -> RiskLevel.FORGED
                issues.size >= 1 -> RiskLevel.SUSPICIOUS
                else -> RiskLevel.CLEAR
            },
            score = score,
            details = buildList {
                add("Analyzed ${blocks.size} text lines")
                addAll(info)
                addAll(issues)
            }
        )
    }

    fun release() = recognizer.close()
}
