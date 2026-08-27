package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class TextBlockData(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrResult(
    val name: String,
    val dateOfBirth: String,
    val idNumber: String,
    val rawText: String,
    val documentType: String,
    val textBlocks: List<TextBlockData> = emptyList()
)

class OcrExtractor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val aadhaarPattern = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    private val dobPattern = Regex("""(\d{2}[/-]\d{2}[/-]\d{4,})""")
    private val nameLinePattern = Regex("""(?i)(name| " _ r|nam)\s*[:\-]?\s*(.+)""")
    // Skip mobile number lines during field extraction to avoid confusion with Aadhaar number / DOB
    private val mobileLinePattern = Regex("""(?i)mobile\s*(no\.?|number)?\s*[:\-]?\s*\d+""")

    suspend fun extract(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = Tasks.await(recognizer.process(image))
        val fullText = visionText.text

        val textBlocksData = mutableListOf<TextBlockData>()
        for (block in visionText.textBlocks) { for (line in block.lines) {
            val rect = line.boundingBox
            // Exclude mobile number blocks from forensic analysis to avoid confusion
            val isMobileLine = mobileLinePattern.containsMatchIn(line.text)
            if (rect != null && !isMobileLine) {
                textBlocksData.add(
                    TextBlockData(
                        text = line.text,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom
                    )
                )
            }
        }
        }

        // Strip mobile number lines before pattern matching to prevent them being picked
        // up as Aadhaar number (10-digit mobile can partially match Aadhaar pattern)
        val cleanedText = fullText.lines()
            .filter { !mobileLinePattern.containsMatchIn(it) }
            .joinToString("\n")

        val aadhaarMatch = aadhaarPattern.find(cleanedText)

        // Strict Aadhaar Validation: If no 12-digit Aadhaar number is found, reject the document.
        if (aadhaarMatch == null && !cleanedText.contains("Government of India", ignoreCase = true)) {
            throw IllegalArgumentException("Invalid Document: Only Aadhaar cards are supported.")
        }

        val dobMatch = dobPattern.find(cleanedText)
        val nameMatch = nameLinePattern.find(cleanedText)

        val idNumber = aadhaarMatch?.value?.replace(" ", "") ?: ""
        val documentType = "Aadhaar Card"

        return OcrResult(
            name = nameMatch?.groupValues?.getOrNull(2)?.trim() ?: extractNameFallback(cleanedText),
            dateOfBirth = dobMatch?.value ?: "",
            idNumber = idNumber,
            rawText = cleanedText,
            documentType = documentType,
            textBlocks = textBlocksData
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


