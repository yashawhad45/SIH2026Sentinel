package com.example.sentinel.module2

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult

class AudioForensicModule : ForensicModule {

    override val moduleName = "Audio Deepfake Detector"

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput): LayerResult {
        if (input !is com.example.sentinel.core.ModuleInput.AudioInput) throw IllegalArgumentException("Expected AudioInput")
        throw UnsupportedOperationException(
            "Module 2 is not yet implemented. Audio analysis is under construction."
        )
    }
}
