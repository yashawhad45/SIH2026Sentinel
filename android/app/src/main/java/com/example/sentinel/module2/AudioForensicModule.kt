package com.example.sentinel.module2

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult

class AudioForensicModule : ForensicModule {

    override val moduleName = "Audio Deepfake Detector"

    override suspend fun analyze(bitmap: Bitmap): LayerResult {
        throw UnsupportedOperationException(
            "Module 2 is not yet implemented. Audio analysis requires a different input type."
        )
    }
}
