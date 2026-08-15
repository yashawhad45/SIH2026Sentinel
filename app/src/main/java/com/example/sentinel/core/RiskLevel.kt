package com.example.sentinel.core

import androidx.compose.ui.graphics.Color

enum class RiskLevel(
    val displayLabel: String,
    val color: Color,
    val backgroundColor: Color,
    val score: Float
) {
    CLEAR(
        displayLabel = "CLEAR",
        color = Color(0xFF00E676),
        backgroundColor = Color(0xFF0D2B1A),
        score = 0.0f
    ),
    SUSPICIOUS(
        displayLabel = "SUSPICIOUS",
        color = Color(0xFFFF9800),
        backgroundColor = Color(0xFF2B1A00),
        score = 0.5f
    ),
    FORGED(
        displayLabel = "FORGED",
        color = Color(0xFFFF3B3B),
        backgroundColor = Color(0xFF2B0D0D),
        score = 1.0f
    )
}
