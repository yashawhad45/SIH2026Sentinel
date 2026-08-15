package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class OcrResult(
    val name: String,
    val dateOfBirth: String,
    val idNumber: String,
    val rawText: String,
    val documentType: String
)

class OcrExtractor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val aadhaarPattern = Regex("""\b\d{4}\s\d{4}\s\d{4}\b""")
    private val panPattern = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    private val dobPattern = Regex("""\b(\d{2}[/-]\d{2}[/-]\d{4}|\d{4}[/-]\d{2}[/-]\d{2})\b""")
    private val nameLinePattern = Regex("""(?i)(name|नाम)\s*[:\-]?\s*(.+)""")

    suspend fun extract(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))
        val fullText = visionText.text

        val aadhaarMatch = aadhaarPattern.find(fullText)
        val panMatch = panPattern.find(fullText)
        val dobMatch = dobPattern.find(fullText)
        val nameMatch = nameLinePattern.find(fullText)

        val idNumber = when {
            aadhaarMatch != null -> aadhaarMatch.value.replace(" ", "")
            panMatch != null -> panMatch.value
            else -> Regex("""\d{8,}""").find(fullText)?.value ?: ""
        }

        val documentType = when {
            aadhaarMatch != null -> "Aadhaar Card"
            panMatch != null -> "PAN Card"
            else -> "Unknown Document"
        }

        return OcrResult(
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

    fun release() = recognizer.close()
}
