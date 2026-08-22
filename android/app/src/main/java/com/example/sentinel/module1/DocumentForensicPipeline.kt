package com.example.sentinel.module1

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.sentinel.core.AggregatedReport
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module1.pipeline.ElaAnalyzer
import com.example.sentinel.module1.pipeline.OcrExtractor
import com.example.sentinel.module1.pipeline.RuleValidator
import com.example.sentinel.module1.pipeline.TypographyAnalyzer
import com.example.sentinel.module1.pipeline.SecurityPatternAnalyzer
import com.example.sentinel.module1.pipeline.CnnApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class DocumentForensicPipeline(context: Context) {

    private val ocrExtractor = OcrExtractor()
    private val ruleValidator = RuleValidator()
    private val elaAnalyzer = ElaAnalyzer()
    private val typographyAnalyzer = TypographyAnalyzer()
    private val securityPatternAnalyzer = SecurityPatternAnalyzer()

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

// === Layers 3-6: Run concurrently for speed ===
        val elaDeferred = async(Dispatchers.Default) { elaAnalyzer.analyze(com.example.sentinel.core.ModuleInput.ImageInput(bitmap)) }
        val cnnDeferred = async(Dispatchers.IO) { runCnnBackend(bitmap) }
        val typoDeferred = async(Dispatchers.IO) { typographyAnalyzer.analyze(bitmap) }
        val secDeferred = async(Dispatchers.Default) { securityPatternAnalyzer.analyze(bitmap) }

        // === Layer 3: ELA ===
        val elaResult = elaDeferred.await()
        results.add(elaResult)
        onLayerComplete(elaResult)

        // === Layer 4: DocTamper & SRM (Server) ===
        val cnnResults = cnnDeferred.await()
        results.addAll(cnnResults)
        cnnResults.forEach { onLayerComplete(it) }

        // === Layer 5: Typography Analysis ===
        val typoResult = typoDeferred.await()
        results.add(typoResult)
        onLayerComplete(typoResult)

        // === Layer 6: Security Pattern Analysis ===
        val secResult = secDeferred.await()
        results.add(secResult)
        onLayerComplete(secResult)

        buildReport(results, ocrResult?.documentType ?: "Unknown")
    }

    private suspend fun runCnnBackend(bitmap: Bitmap): List<LayerResult> {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val byteArray = stream.toByteArray()

            val requestFile = byteArray.toRequestBody("image/png".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", "upload.png", requestFile)

            val response = CnnApiClient.api.detectForgery(body)

            if (response.isSuccessful && response.body() != null) {
                val cnnRes = response.body()!!
                val docTamperLayer = LayerResult(
                    layerName = "DocTamper AI Analysis",
                    passed = !cnnRes.is_forged,
                    riskLevel = if (cnnRes.forgery_probability > 0.6f) RiskLevel.FORGED
                               else if (cnnRes.forgery_probability > 0.3f) RiskLevel.SUSPICIOUS
                               else RiskLevel.CLEAR,
                    score = cnnRes.forgery_probability,
                    details = listOf(
                        "Forgery Confidence: ${"%.0f".format(cnnRes.forgery_probability * 100)}%",
                        "Pixels Tampered: ${"%.0f".format(cnnRes.doctamper_pixel_ratio * 100)}%"
                    )
                )
                
                val srmLayer = LayerResult(
                    layerName = "SRM Noise Analysis",
                    passed = cnnRes.srm_score < 0.3f,
                    riskLevel = if (cnnRes.srm_score > 0.6f) RiskLevel.FORGED
                               else if (cnnRes.srm_score > 0.3f) RiskLevel.SUSPICIOUS
                               else RiskLevel.CLEAR,
                    score = cnnRes.srm_score,
                    details = listOf(if (cnnRes.srm_details.isNotEmpty()) cnnRes.srm_details else "No noise inconsistencies detected")
                )
                
                listOf(docTamperLayer, srmLayer)
            } else {
                Log.e("DOCTAMPER_API", "API Error: ${response.errorBody()?.string()}")
                listOf(LayerResult(
                    layerName = "DocTamper AI Analysis",
                    passed = true,
                    riskLevel = RiskLevel.CLEAR,
                    score = 0.0f,
                    details = listOf("Warning: Backend analysis failed (API Error)")
                ))
            }
        } catch (e: Exception) {
            Log.e("DOCTAMPER_API", "Network Error", e)
            listOf(LayerResult(
                layerName = "DocTamper AI Analysis",
                passed = true,
                riskLevel = RiskLevel.CLEAR,
                score = 0.0f,
                details = listOf("Warning: Could not reach backend server")
            ))
        }
    }


    private fun buildReport(results: List<LayerResult>, documentType: String): AggregatedReport {
        // Weights: OCR(5%) Rules(25%) ELA(15%) DocTamper(10%) Typography(20%) Security(25%)
        val weights = listOf(0.05f, 0.25f, 0.15f, 0.10f, 0.20f, 0.25f)
        val weightedScore = results.mapIndexed { i, r ->
            r.score * (weights.getOrElse(i) { 0f })
        }.sum().coerceIn(0f, 1f)

        var finalRisk = when {
            weightedScore >= 0.50f -> RiskLevel.FORGED
            weightedScore >= 0.28f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        // Critical Override: If Rules definitively prove forgery, override everything
        if (results.any { it.riskLevel == RiskLevel.FORGED && it.layerName == "Rule-Based Validation" }) {
            finalRisk = RiskLevel.FORGED
        }

        // Critical Override: If 3+ layers flag FORGED/SUSPICIOUS, escalate
        val flaggedLayers = results.count { it.riskLevel != RiskLevel.CLEAR }
        if (flaggedLayers >= 3 && finalRisk == RiskLevel.CLEAR) {
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
        typographyAnalyzer.release()
    }
}




