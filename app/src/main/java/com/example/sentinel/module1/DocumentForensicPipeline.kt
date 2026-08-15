package com.example.sentinel.module1

import android.content.Context
import android.graphics.Bitmap
import com.example.sentinel.core.AggregatedReport
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module1.pipeline.ElaAnalyzer
import com.example.sentinel.module1.pipeline.OcrExtractor
import com.example.sentinel.module1.pipeline.RuleValidator
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
            ) else listOf("OCR failed — image may be too blurry or low contrast")
        )
        results.add(ocrLayerResult)
        onLayerComplete(ocrLayerResult)

        val ruleResult = if (ocrResult != null) {
            ruleValidator.validateWithOcrResult(ocrResult)
        } else {
            LayerResult(
                layerName = "Rule-Based Validation",
                passed = false,
                riskLevel = RiskLevel.SUSPICIOUS,
                score = 0.5f,
                details = listOf("Skipped — OCR result unavailable")
            )
        }
        results.add(ruleResult)
        onLayerComplete(ruleResult)

        val elaResult = elaAnalyzer.analyze(bitmap)
        results.add(elaResult)
        onLayerComplete(elaResult)

        buildReport(results, ocrResult?.documentType ?: "Unknown")
    }

    private fun buildReport(results: List<LayerResult>, documentType: String): AggregatedReport {
        val weights = listOf(0.30f, 0.30f, 0.40f)
        val weightedScore = results.mapIndexed { i, r ->
            r.score * (weights.getOrElse(i) { 0f })
        }.sum().coerceIn(0f, 1f)

        val finalRisk = when {
            weightedScore >= 0.60f -> RiskLevel.FORGED
            weightedScore >= 0.25f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        return AggregatedReport(
            documentType = documentType,
            layerResults = results,
            finalScore = weightedScore,
            finalRiskLevel = finalRisk,
            timestamp = System.currentTimeMillis()
        )
    }

    fun release() = ocrExtractor.release()
}
