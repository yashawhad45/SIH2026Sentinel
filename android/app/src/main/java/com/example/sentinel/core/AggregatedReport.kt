package com.example.sentinel.core

data class AggregatedReport(
    val documentType: String,
    val layerResults: List<LayerResult>,
    val finalScore: Float,
    val finalRiskLevel: RiskLevel,
    val timestamp: Long
)
