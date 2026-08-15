package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrResult(
    val name: String,
    val dateOfBirth: String,
    val idNumber: String,
    val rawText: String,
    val documentType: String
)

class OcrExtractor {

    // TODO: Initialize PaddleOCR Engine here once JNI and .onnx models are placed in assets
    // private val paddleOcr = NativePaddleOcr()

    suspend fun extract(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        
        // PADDLE OCR STUB
        // The ML Kit dependency has been removed as requested.
        // You must drop the ONNX models into assets/ and load them via JNI.
        val fullText = "PaddleOCR Integration Pending Models..."

        OcrResult(
            name = "PENDING PADDLE OCR",
            dateOfBirth = "01/01/1990",
            idNumber = "0000000000",
            rawText = fullText,
            documentType = "Unknown"
        )
    }

    fun release() {
        // Release PaddleOCR native resources
    }
}