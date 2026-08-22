package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import kotlin.math.abs
import kotlin.math.sqrt

class SecurityPatternAnalyzer {

    /**
     * Detects two key forgery indicators:
     * 1. Missing security patterns (flat white boxes where guilloche/micro-text should be)
     * 2. Photo insertion artifacts (harsh rectangular edges around pasted photos)
     */
    suspend fun analyze(bitmap: Bitmap): LayerResult {
        val scaled = scaleBitmap(bitmap, 640)
        val issues = mutableListOf<String>()
        val info = mutableListOf<String>()

        // === Analysis 1: Background Texture (Guilloche/Micro-text detection) ===
        analyzeBackgroundTexture(scaled, issues, info)

        // === Analysis 2: Photo Edge Artifacts ===
        analyzePhotoEdges(scaled, issues, info)

        val score = when {
            issues.size >= 3 -> 0.90f
            issues.size == 2 -> 0.65f
            issues.size == 1 -> 0.40f
            else -> 0f
        }

        return LayerResult(
            layerName = "Security Pattern Analysis",
            passed = issues.isEmpty(),
            riskLevel = when {
                issues.size >= 2 -> RiskLevel.FORGED
                issues.size == 1 -> RiskLevel.SUSPICIOUS
                else -> RiskLevel.CLEAR
            },
            score = score,
            details = buildList {
                addAll(info)
                addAll(issues)
                if (issues.isEmpty()) add("All security patterns appear consistent")
            }
        )
    }

    /**
     * Genuine Aadhaar cards have complex guilloche patterns and micro-text across
     * the entire background. When a forger pastes text or photos over the template,
     * those areas become flat solid-color rectangles.
     * 
     * We split the image into blocks and measure texture variance.
     * Blocks with very low variance = flat/pasted = suspicious.
     */
    private fun analyzeBackgroundTexture(bitmap: Bitmap, issues: MutableList<String>, info: MutableList<String>) {
        val w = bitmap.width
        val h = bitmap.height
        val blockSize = 40
        val variances = mutableListOf<Pair<Float, String>>()

        for (row in 0 until h - blockSize step blockSize) {
            for (col in 0 until w - blockSize step blockSize) {
                val pixels = mutableListOf<Float>()
                for (y in row until row + blockSize) {
                    for (x in col until col + blockSize) {
                        val px = bitmap.getPixel(x, y)
                        val gray = (Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f)
                        pixels.add(gray)
                    }
                }
                val mean = pixels.average().toFloat()
                val variance = pixels.map { (it - mean) * (it - mean) }.average().toFloat()
                val region = "row=${row / blockSize},col=${col / blockSize}"
                variances.add(Pair(variance, region))
            }
        }

        if (variances.isEmpty()) return

        val allVars = variances.map { it.first }
        val medianVar = allVars.sorted()[allVars.size / 2]

        // Count blocks with extremely low texture (flat white/solid boxes)
        val flatThreshold = maxOf(medianVar * 0.1f, 5f)
        val flatBlocks = variances.filter { it.first < flatThreshold && it.first < 15f }
        val flatRatio = flatBlocks.size.toFloat() / variances.size

        // On genuine cards with guilloche, very few blocks should be perfectly flat
        if (flatRatio > 0.25f) {
            issues.add("Missing security patterns: ${(flatRatio * 100).toInt()}% of background is flat/solid (expected complex guilloche)")
        } else if (flatRatio > 0.15f) {
            issues.add("Partial pattern loss: ${(flatRatio * 100).toInt()}% of background lacks expected micro-text")
        } else {
            info.add("Background texture: Security patterns (guilloche/micro-text) detected across ${((1 - flatRatio) * 100).toInt()}% of card")
        }

        // Check for suspiciously uniform rectangular regions (pasted white boxes)
        val consecutiveFlat = findFlatRectangles(variances, w / blockSize, flatThreshold)
        if (consecutiveFlat > 0) {
            issues.add("Detected $consecutiveFlat rectangular flat region(s) - possible pasted overlay")
        }
    }

    /**
     * When a photo is pasted onto an ID card, it creates harsh rectangular edges.
     * Authentic photos have smooth, integrated edges or are printed with the card.
     * We detect this by measuring the gradient strength at potential photo boundaries.
     */
    private fun analyzePhotoEdges(bitmap: Bitmap, issues: MutableList<String>, info: MutableList<String>) {
        val w = bitmap.width
        val h = bitmap.height

        // Aadhaar photo is typically in the left portion of the card
        val photoRegionLeft = 0
        val photoRegionRight = w / 3
        val photoRegionTop = h / 4
        val photoRegionBottom = h * 3 / 4

        // Compute edge strength using Sobel-like gradient
        val edgeStrengths = mutableListOf<Float>()
        val interiorStrengths = mutableListOf<Float>()

        for (y in photoRegionTop until photoRegionBottom) {
            for (x in photoRegionLeft until photoRegionRight) {
                if (x <= 0 || x >= w - 1 || y <= 0 || y >= h - 1) continue

                val gx = getGray(bitmap, x + 1, y) - getGray(bitmap, x - 1, y)
                val gy = getGray(bitmap, x, y + 1) - getGray(bitmap, x, y - 1)
                val gradient = sqrt((gx * gx + gy * gy).toDouble()).toFloat()

                // Check if this pixel is near the boundary of the photo region
                val nearBoundary = (abs(x - photoRegionLeft) < 5 || abs(x - photoRegionRight) < 5 ||
                        abs(y - photoRegionTop) < 5 || abs(y - photoRegionBottom) < 5)

                if (nearBoundary) {
                    edgeStrengths.add(gradient)
                } else if (x > photoRegionLeft + 15 && x < photoRegionRight - 15 &&
                    y > photoRegionTop + 15 && y < photoRegionBottom - 15) {
                    interiorStrengths.add(gradient)
                }
            }
        }

        if (edgeStrengths.isNotEmpty() && interiorStrengths.isNotEmpty()) {
            val avgEdge = edgeStrengths.average()
            val avgInterior = interiorStrengths.average()

            // If edge gradient is much stronger than interior, photo was pasted
            if (avgInterior > 0.1 && avgEdge / avgInterior > 3.0) {
                issues.add("Photo insertion detected: Edge sharpness is ${(avgEdge / avgInterior).toInt()}x stronger than interior")
            } else {
                info.add("Photo edges: No harsh insertion boundaries detected")
            }
        }
    }

    private fun findFlatRectangles(variances: List<Pair<Float, String>>, cols: Int, threshold: Float): Int {
        if (cols <= 0) return 0
        var rectangles = 0
        var consecutiveFlat = 0

        for (v in variances) {
            if (v.first < threshold && v.first < 15f) {
                consecutiveFlat++
                if (consecutiveFlat >= 4) { // 4+ consecutive flat blocks = suspicious rectangle
                    rectangles++
                    consecutiveFlat = 0
                }
            } else {
                consecutiveFlat = 0
            }
        }
        return rectangles
    }

    private fun getGray(bitmap: Bitmap, x: Int, y: Int): Float {
        val px = bitmap.getPixel(x, y)
        return Color.red(px) * 0.299f + Color.green(px) * 0.587f + Color.blue(px) * 0.114f
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
    }
}
