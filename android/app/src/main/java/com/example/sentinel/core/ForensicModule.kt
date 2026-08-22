package com.example.sentinel.core

import android.graphics.Bitmap

sealed class ModuleInput {
    data class ImageInput(val bitmap: Bitmap) : ModuleInput()
    data class TransactionInput(val fields: Map<String, Any>) : ModuleInput()
    data class AudioInput(val filePath: String) : ModuleInput()
}

interface ForensicModule {
    val moduleName: String
    suspend fun analyze(input: ModuleInput): LayerResult
}
