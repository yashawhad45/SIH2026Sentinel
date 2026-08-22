package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

class QrCrossVerifier : ForensicModule {

    override val moduleName = "QR Cross-Verification"

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput): LayerResult {
        return LayerResult.unavailable(moduleName)
    }

    fun verifyAgainstOcr(bitmap: Bitmap, ocrResult: OcrResult): LayerResult {
        val qrText = decodeQrFromBitmap(bitmap)
            ?: return LayerResult(
                layerName = moduleName,
                passed = true,
                riskLevel = RiskLevel.CLEAR,
                score = 0f,
                details = listOf(
                    "No QR code found on document",
                    "QR cross-verification skipped"
                )
            )

        val qrFields = parseQrContent(qrText)
        val matchResult = crossMatchFields(qrFields, ocrResult)

        val consistencyScore = matchResult.matchedCount.toFloat() / matchResult.totalExpected.toFloat()
        val forgeryScore = 1f - consistencyScore

        val riskLevel = when {
            consistencyScore < 0.5f -> RiskLevel.FORGED
            consistencyScore < 0.8f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        return LayerResult(
            layerName = moduleName,
            passed = riskLevel == RiskLevel.CLEAR,
            riskLevel = riskLevel,
            score = forgeryScore,
            details = buildList {
                add("QR decoded successfully")
                add("Fields matched: ${matchResult.matchedCount} / ${matchResult.totalExpected}")
                add("Consistency score: ${"%.1f".format(consistencyScore * 100)}%")
                matchResult.fieldDetails.forEach { add(it) }
            }
        )
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
            val reader = MultiFormatReader()
            reader.decode(binaryBitmap, hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQrContent(qrText: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        qrText.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                fields[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }
        if (fields.isEmpty()) {
            fields["raw"] = qrText
        }
        return fields
    }

    private data class MatchResult(
        val matchedCount: Int,
        val totalExpected: Int,
        val fieldDetails: List<String>
    )

    private fun crossMatchFields(qrFields: Map<String, String>, ocr: OcrResult): MatchResult {
        val details = mutableListOf<String>()
        var matched = 0
        val total = 3

        val nameKeys = listOf("name", "full name", "holder name")
        val qrName = qrFields.entries.firstOrNull { it.key in nameKeys }?.value
        if (qrName != null) {
            val nameMatch = normalise(qrName).contains(normalise(ocr.name)) ||
                            normalise(ocr.name).contains(normalise(qrName))
            if (nameMatch) matched++
            details.add("Name match: ${if (nameMatch) "Yes" else "No — QR: $qrName | OCR: ${ocr.name}"}")
        } else {
            details.add("Name: not found in QR")
        }

        val dobKeys = listOf("dob", "date of birth", "birth date")
        val qrDob = qrFields.entries.firstOrNull { it.key in dobKeys }?.value
        if (qrDob != null) {
            val dobMatch = normalise(qrDob) == normalise(ocr.dateOfBirth)
            if (dobMatch) matched++
            details.add("DOB match: ${if (dobMatch) "Yes" else "No — QR: $qrDob | OCR: ${ocr.dateOfBirth}"}")
        } else {
            details.add("Date of Birth: not found in QR")
        }

        val idKeys = listOf("id", "uid", "aadhaar", "pan", "number")
        val qrId = qrFields.entries.firstOrNull { it.key in idKeys }?.value
        if (qrId != null) {
            val idMatch = normalise(qrId).replace(" ", "") == normalise(ocr.idNumber).replace(" ", "")
            if (idMatch) matched++
            details.add("ID match: ${if (idMatch) "Yes" else "No — QR: $qrId | OCR: ${ocr.idNumber}"}")
        } else {
            details.add("ID number: not found in QR")
        }

        return MatchResult(matched, total, details)
    }

    private fun normalise(value: String): String = value.trim().lowercase()
}
