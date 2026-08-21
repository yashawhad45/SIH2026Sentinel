package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel

class CopyMoveDetector : ForensicModule {

    override val moduleName = "Copy-Move Detection"

    private val blockSize = 16
    private val hammingDistanceThreshold = 8
    private val suspiciousPairCount = 3
    private val forgedPairCount = 8

    override suspend fun analyze(bitmap: Bitmap): LayerResult {
        val scaledBitmap = scaleBitmapForAnalysis(bitmap)
        val blocks = extractBlocks(scaledBitmap)
        val hashes = blocks.map { block -> Pair(block, computeDctHash(block.pixels)) }
        val duplicatePairs = findDuplicatePairs(hashes)

        val riskLevel = when {
            duplicatePairs >= forgedPairCount -> RiskLevel.FORGED
            duplicatePairs >= suspiciousPairCount -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        return LayerResult(
            layerName = moduleName,
            passed = riskLevel == RiskLevel.CLEAR,
            riskLevel = riskLevel,
            score = computeScore(duplicatePairs),
            details = buildList {
                add("Block size: ${blockSize}x${blockSize}px")
                add("Total blocks analysed: ${hashes.size}")
                add("Duplicate block pairs found: $duplicatePairs")
                add("Hamming distance threshold: $hammingDistanceThreshold")
                when (riskLevel) {
                    RiskLevel.CLEAR -> add("No evidence of copy-move tampering")
                    RiskLevel.SUSPICIOUS -> add("Some duplicate regions detected — review recommended")
                    RiskLevel.FORGED -> add("Significant copy-move detected — likely portrait or digit cloning")
                }
            }
        )
    }

    private fun scaleBitmapForAnalysis(bitmap: Bitmap): Bitmap {
        val maxDim = 512
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

    private data class Block(val x: Int, val y: Int, val pixels: IntArray)

    private fun extractBlocks(bitmap: Bitmap): List<Block> {
        val blocks = mutableListOf<Block>()
        val stride = blockSize / 2
        var y = 0
        while (y + blockSize <= bitmap.height) {
            var x = 0
            while (x + blockSize <= bitmap.width) {
                val pixels = IntArray(blockSize * blockSize)
                bitmap.getPixels(pixels, 0, blockSize, x, y, blockSize, blockSize)
                blocks.add(Block(x, y, pixels))
                x += stride
            }
            y += stride
        }
        return blocks
    }

    private fun computeDctHash(pixels: IntArray): Long {
        val grayscale = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }
        val mean = grayscale.average().toFloat()
        var hash = 0L
        for (i in grayscale.indices.take(64)) {
            if (grayscale[i] >= mean) hash = hash or (1L shl i)
        }
        return hash
    }

    private fun findDuplicatePairs(hashes: List<Pair<Block, Long>>): Int {
        var duplicatePairs = 0
        for (i in hashes.indices) {
            for (j in i + 1 until hashes.size) {
                val blockA = hashes[i].first
                val blockB = hashes[j].first
                val distanceFar = Math.abs(blockA.x - blockB.x) > blockSize ||
                                  Math.abs(blockA.y - blockB.y) > blockSize
                if (distanceFar) {
                    val hammingDistance = java.lang.Long.bitCount(hashes[i].second xor hashes[j].second)
                    if (hammingDistance <= hammingDistanceThreshold) duplicatePairs++
                }
            }
        }
        return duplicatePairs
    }

    private fun computeScore(pairs: Int): Float {
        return (pairs.toFloat() / (forgedPairCount * 2)).coerceIn(0f, 1f)
    }
}
