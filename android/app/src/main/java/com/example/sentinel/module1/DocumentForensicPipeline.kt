package com.example.sentinel.module1

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.sentinel.core.AggregatedReport
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module1.pipeline.OcrExtractor
import com.example.sentinel.module1.pipeline.RuleValidator
import com.example.sentinel.module1.pipeline.ElaAnalyzer
import com.example.sentinel.module1.pipeline.ForensicApiClient
import com.example.sentinel.module1.pipeline.TextBlockData
import com.google.gson.Gson
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
    private val gson = Gson()

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
        val elaDeferred = async(Dispatchers.Default) { elaAnalyzer.analyze(com.example.sentinel.core.ModuleInput.ImageInput(bitmap)) }
        
        // === Layer 4: Forensic Consistency Analysis (Server) ===
        val forensicDeferred = async(Dispatchers.IO) { 
            runForensicAnalysis(bitmap, ocrResult?.textBlocks ?: emptyList()) 
        }

        val elaResult = elaDeferred.await()
        results.add(elaResult)
        onLayerComplete(elaResult)
        
        val forensicResult = forensicDeferred.await()
        results.add(forensicResult)
        onLayerComplete(forensicResult)

        buildReport(results, ocrResult?.documentType ?: "Unknown")
    }
    
    private suspend fun runForensicAnalysis(bitmap: Bitmap, textBlocks: List<TextBlockData>): LayerResult {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val byteArray = stream.toByteArray()

            val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", "upload.jpg", requestFile)
            
            val blocksJson = gson.toJson(textBlocks)
            val blocksBody = blocksJson.toRequestBody("application/json".toMediaTypeOrNull())

            val response = ForensicApiClient.api.analyzeForensics(body, blocksBody)

            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                
                val risk = when (res.risk_level) {
                    "FORGED" -> RiskLevel.FORGED
                    "SUSPICIOUS" -> RiskLevel.SUSPICIOUS
                    else -> RiskLevel.CLEAR
                }
                
                val detailsList = mutableListOf<String>()
                if (res.success) {
                    detailsList.addAll(res.explanations)
                } else {
                    detailsList.add("Analysis failed: ${res.error}")
                }
                
                LayerResult(
                    layerName = "Forensic Consistency Analysis",
                    passed = risk == RiskLevel.CLEAR,
                    riskLevel = risk,
                    score = res.forensic_score,
                    details = detailsList
                )
            } else {
                Log.e("FORENSIC_API", "API Error: ${response.errorBody()?.string()}")
                LayerResult(
                    layerName = "Forensic Consistency Analysis",
                    passed = true,
                    riskLevel = RiskLevel.CLEAR,
                    score = 0.0f,
                    details = listOf("Warning: Backend analysis failed (API Error)")
                )
            }
        } catch (e: Exception) {
            Log.e("FORENSIC_API", "Network Error", e)
            LayerResult(
                layerName = "Forensic Consistency Analysis",
                passed = true,
                riskLevel = RiskLevel.CLEAR,
                score = 0.0f,
                details = listOf("Warning: Could not reach backend server")
            )
        }
    }

    private fun buildReport(results: List<LayerResult>, documentType: String): AggregatedReport {
        // Weights: OCR(5%) Rules(40%) ELA(20%) Forensic Consistency(35%)
        val weights = listOf(0.05f, 0.40f, 0.20f, 0.35f)
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
