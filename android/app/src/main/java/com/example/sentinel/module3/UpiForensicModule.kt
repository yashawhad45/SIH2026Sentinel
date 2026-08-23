package com.example.sentinel.module3

import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.ModuleInput
import com.example.sentinel.core.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpiForensicModule : ForensicModule {
    override val moduleName = "UPI Fraud Analysis"

    override suspend fun analyze(input: ModuleInput): LayerResult {
        if (input !is ModuleInput.TransactionInput) {
            throw IllegalArgumentException("Expected TransactionInput")
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                UpiApiClient.api.checkTransaction(input.fields)
            }

            val riskLevel = when {
                response.risk_score < 50 -> RiskLevel.CLEAR
                response.risk_score < 70 -> RiskLevel.SUSPICIOUS
                else -> RiskLevel.FORGED
            }
            
            val details = buildList {
                add("Explanation: ${response.explanation}")
                if (response.flags.isNotEmpty()) {
                    add("Flags Triggered:")
                    response.flags.forEach { flag -> add(" • $flag") }
                }
            }

            LayerResult(
                layerName = moduleName,
                passed = riskLevel == RiskLevel.CLEAR,
                riskLevel = riskLevel,
                score = response.risk_score.toFloat() / 100f,
                details = details
            )
        } catch (e: Exception) {
            LayerResult(
                layerName = moduleName,
                passed = false,
                riskLevel = RiskLevel.SUSPICIOUS,
                score = 0.5f,
                details = listOf(
                    "Network error during UPI fraud analysis",
                    "Details: ${e.message}"
                )
            )
        }
    }
}
