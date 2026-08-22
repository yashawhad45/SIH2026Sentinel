package com.example.sentinel.module3

data class TransactionResponse(
    val risk_score: Int,
    val risk_tier: String,
    val flags: List<String>,
    val explanation: String
)
