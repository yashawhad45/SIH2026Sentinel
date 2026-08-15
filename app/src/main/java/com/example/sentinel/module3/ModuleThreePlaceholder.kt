package com.example.sentinel.module3

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult

class ModuleThreePlaceholder : ForensicModule {

    override val moduleName = "Module III (TBD)"

    override suspend fun analyze(bitmap: Bitmap): LayerResult {
        throw UnsupportedOperationException("Module 3 has not been defined yet.")
    }
}
