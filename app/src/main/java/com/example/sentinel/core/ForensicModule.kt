package com.example.sentinel.core

import android.graphics.Bitmap

interface ForensicModule {
    val moduleName: String
    suspend fun analyze(bitmap: Bitmap): LayerResult
}
