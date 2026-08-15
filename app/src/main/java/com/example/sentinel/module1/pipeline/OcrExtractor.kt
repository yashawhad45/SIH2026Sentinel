package com.example.sentinel.module1.pipeline

import android.content.Context
import android.graphics.Bitmap
import io.github.hzkitty.rapidocr4j.RapidOCR
import io.github.hzkitty.rapidocr4j.OcrConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrResult(
    val name: String,
    val dateOfBirth: String,
    val idNumber: String,
    val rawText: String,
    val documentType: String
)

class OcrExtractor(context: Context) {

    // IMPORTANT: Requires ONNX models in assets folder:
    // ch_PP-OCRv4_det_infer.onnx, ch_ppocr_mobile_v2.0_cls_train.onnx, ch_PP-OCRv4_rec_infer.onnx
    private val ocr: RapidOCR = RapidOCR.create(context)

    private val aadhaarPattern = Regex(""\"\b\d{4}\s\d{4}\s\d{4}\b""\")
    private val panPattern = Regex(""\"\b[A-Z]{5}[0-9]{4}[A-Z]\b""\")
    private val dobPattern = Regex(""\"\b(\d{2}[/-]\d{2}[/-]\d{4}|\d{4}[/-]\d{2}[/-]\d{2})\b""\")
    private val nameLinePattern = Regex(""\"(?i)(name| " _ r)\s*[:\-]?\s*(.+)""\")

    suspend fun extract(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        // Run PaddleOCR
        val result = ocr.run(bitmap)
        
        // Extract raw text by concatenating recognized lines
        val fullTextBuilder = StringBuilder()
        result?.textBlocks?.forEach { block ->
            fullTextBuilder.append(block.text).append("\n")
        }
        val fullText = fullTextBuilder.toString()

        val aadhaarMatch = aadhaarPattern.find(fullText)
        val panMatch = panPattern.find(fullText)
        val dobMatch = dobPattern.find(fullText)
        val nameMatch = nameLinePattern.find(fullText)

        val idNumber = when {
            aadhaarMatch != null -> aadhaarMatch.value.replace(" ", "")
            panMatch != null -> panMatch.value
            else -> Regex(""\"\d{8,}""\").find(fullText)?.value ?: ""
        }

        val documentType = when {
            aadhaarMatch != null -> "Aadhaar Card"
            panMatch != null -> "PAN Card"
            else -> "Unknown Document"
        }

        OcrResult(
            name = nameMatch?.groupValues?.getOrNull(2)?.trim() ?: extractNameFallback(fullText),
            dateOfBirth = dobMatch?.value ?: "",
            idNumber = idNumber,
            rawText = fullText,
            documentType = documentType
        )
    }

    private fun extractNameFallback(text: String): String {
        return text.lines()
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                line.length in 3..50 &&
                line.all { ch -> ch.isLetter() || ch.isWhitespace() } &&
                line.trim().split(" ").size in 2..4
            }?.trim() ?: ""
    }

    fun release() {
        // rapidocr4j may not need manual release, but keeping the signature
    }
}