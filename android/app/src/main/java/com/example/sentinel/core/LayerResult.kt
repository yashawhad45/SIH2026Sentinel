package com.example.sentinel.core

data class LayerResult(
    val layerName: String,
    val passed: Boolean,
    val riskLevel: RiskLevel,
    val score: Float,
    val details: List<String>,
    val rawRiskTier: String? = null
) {
    companion object {
        fun unavailable(layerName: String) = LayerResult(
            layerName = layerName,
            passed = true,
            riskLevel = RiskLevel.CLEAR,
            score = 0f,
            details = listOf("Layer not available - skipped"),
            rawRiskTier = null
        )
    }
}
