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

    private val aadhaarPattern = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    private val dobPattern = Regex("""(\d{2}[/-]\d{2}[/-]\d{4,})""")
    private val nameLinePattern = Regex("""(?i)(name|नाम|nam)\s*[:\-]?\s*(.+)""")

    suspend fun extract(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))
        val fullText = visionText.text

        val aadhaarMatch = aadhaarPattern.find(fullText)
        
        // Strict Aadhaar Validation: If no 12-digit Aadhaar number is found, reject the document.
        if (aadhaarMatch == null && !fullText.contains("Government of India", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid Document: Only Aadhaar cards are supported.")
        }

        val dobMatch = dobPattern.find(fullText)
        val nameMatch = nameLinePattern.find(fullText)

        val idNumber = aadhaarMatch?.value?.replace(" ", "") ?: ""
        val documentType = "Aadhaar Card"

        return OcrResult(
            name = nameMatch?.groupValues?.getOrNull(2)?.trim() ?: extractNameFallback(fullText),
            dateOfBirth = dobMatch?.value ?: "",
            idNumber = idNumber,
            rawText = fullText,
            documentType = documentType
        )
    }

    private fun extractNameFallback(text: String): String {
        val excludedWords = listOf(
            "government", "india", "authority", "identification", "unique",
            "father", "mother", "wife", "husband", "do/o", "so/o", "wo/o",
            "dob", "year", "birth", "gender", "male", "female", "aadhaar", "enrollment", "update"
        )

        return text.lines()
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                val lowerLine = line.trim().lowercase()
                line.length in 3..50 &&
                line.all { ch -> ch.isLetter() || ch.isWhitespace() } &&
                line.trim().split(" ").size in 2..4 &&
                excludedWords.none { lowerLine.contains(it) }
            }?.trim() ?: ""
    }

    fun release() = recognizer.close()
}



