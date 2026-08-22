package com.example.sentinel.module3

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult

class ModuleThreePlaceholder : ForensicModule {

    override val moduleName = "Module III (TBD)"

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput): LayerResult {
        if (input !is com.example.sentinel.core.ModuleInput.TransactionInput) throw IllegalArgumentException("Expected TransactionInput")
        throw UnsupportedOperationException("Module 3 API integration not yet implemented.")
    }
}
