package com.example.sentinel.module1

import android.content.Context
import android.graphics.Bitmap
import com.example.sentinel.core.AggregatedReport
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module1.pipeline.OcrExtractor
import com.example.sentinel.module1.pipeline.RuleValidator
import com.example.sentinel.module1.pipeline.ElaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentForensicPipeline(context: Context) {

    private val ocrExtractor = OcrExtractor()
    private val ruleValidator = RuleValidator()
    private val elaAnalyzer = ElaAnalyzer()

    suspend fun run(
        bitmap: Bitmap,
        onLayerComplete: suspend (LayerResult) -> Unit
    ): AggregatedReport = withContext(Dispatchers.Default) {

        val results = mutableListOf<LayerResult>()

        // === Layer 1: OCR Text Extraction ===
        val ocrResult = try {
            withContext(Dispatchers.IO) { ocrExtractor.extract(bitmap) }
        } catch (e: Exception) { null }

        val ocrLayerResult = LayerResult(
            layerName = "OCR Text Extraction",
            passed = ocrResult?.rawText?.isNotBlank() == true,
            riskLevel = if (ocrResult?.rawText?.isNotBlank() == true) RiskLevel.CLEAR else RiskLevel.SUSPICIOUS,
            score = if (ocrResult?.rawText?.isNotBlank() == true) 0f else 0.4f,
            details = if (ocrResult != null) listOf(
                "Document type: ${ocrResult.documentType}",
                "Name: ${ocrResult.name.ifEmpty { "Not found" }}",
                "Date of birth: ${ocrResult.dateOfBirth.ifEmpty { "Not found" }}",
                "ID number: ${ocrResult.idNumber.ifEmpty { "Not found" }}"
            ) else listOf("OCR failed - image may be too blurry or low contrast")
        )
        results.add(ocrLayerResult)
        onLayerComplete(ocrLayerResult)

        // === Layer 2: Rule-Based Validation ===
        val ruleResult = if (ocrResult != null) {
            ruleValidator.validateWithOcrResult(ocrResult)
        } else {
            LayerResult(
                layerName = "Rule-Based Validation",
                passed = false,
                riskLevel = RiskLevel.SUSPICIOUS,
                score = 0.5f,
                details = listOf("Skipped - OCR result unavailable")
            )
        }
        results.add(ruleResult)
        onLayerComplete(ruleResult)

        // === Layer 3: Error Level Analysis ===
        val elaResult = elaAnalyzer.analyze(com.example.sentinel.core.ModuleInput.ImageInput(bitmap))
        results.add(elaResult)
        onLayerComplete(elaResult)

        buildReport(results, ocrResult?.documentType ?: "Unknown")
    }

    private fun buildReport(results: List<LayerResult>, documentType: String): AggregatedReport {
        // Weights: OCR(10%) Rules(50%) ELA(40%)
        val weights = listOf(0.10f, 0.50f, 0.40f)
        val weightedScore = results.mapIndexed { i, r ->
            r.score * (weights.getOrElse(i) { 0f })
        }.sum().coerceIn(0f, 1f)

        var finalRisk = when {
            weightedScore >= 0.50f -> RiskLevel.FORGED
            weightedScore >= 0.28f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        if (results.any { it.riskLevel == RiskLevel.FORGED && it.layerName == "Rule-Based Validation" }) {
            finalRisk = RiskLevel.FORGED
        }

        val flaggedLayers = results.count { it.riskLevel != RiskLevel.CLEAR }
        if (flaggedLayers >= 2 && finalRisk == RiskLevel.CLEAR) {
            finalRisk = RiskLevel.SUSPICIOUS
        }

        return AggregatedReport(
            documentType = documentType,
            layerResults = results,
            finalScore = weightedScore,
            finalRiskLevel = finalRisk,
            timestamp = System.currentTimeMillis()
        )
    }

    fun release() {
        ocrExtractor.release()
    }
}
