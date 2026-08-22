package com.example.sentinel.module1.pipeline

import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel

class RuleValidator : ForensicModule {

    override val moduleName = "Rule-Based Validation"

    private val verhoeffMultiplicationTable = arrayOf(
        intArrayOf(0,1,2,3,4,5,6,7,8,9), intArrayOf(1,2,3,4,0,6,7,8,9,5),
        intArrayOf(2,3,4,0,1,7,8,9,5,6), intArrayOf(3,4,0,1,2,8,9,5,6,7),
        intArrayOf(4,0,1,2,3,9,5,6,7,8), intArrayOf(5,9,8,7,6,0,4,3,2,1),
        intArrayOf(6,5,9,8,7,1,0,4,3,2), intArrayOf(7,6,5,9,8,2,1,0,4,3),
        intArrayOf(8,7,6,5,9,3,2,1,0,4), intArrayOf(9,8,7,6,5,4,3,2,1,0)
    )

    private val verhoeffPermutationTable = arrayOf(
        intArrayOf(0,1,2,3,4,5,6,7,8,9), intArrayOf(1,5,7,6,2,8,3,0,9,4),
        intArrayOf(5,8,0,3,7,9,6,1,4,2), intArrayOf(8,9,1,6,0,4,3,5,2,7),
        intArrayOf(9,4,5,3,1,2,6,8,7,0), intArrayOf(4,2,8,6,5,7,3,9,0,1),
        intArrayOf(2,7,9,3,8,0,6,4,1,5), intArrayOf(7,0,4,6,9,1,3,2,5,8)
    )

    private val panRegex = Regex("""^[A-Z]{5}[0-9]{4}[A-Z]$""")

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput) = LayerResult.unavailable(moduleName)

    fun validateWithOcrResult(ocrResult: OcrResult): LayerResult {
        val rawId = ocrResult.idNumber.replace(" ", "").uppercase()
        return when {
            rawId.length == 12 && rawId.all { it.isDigit() } -> validateAadhaar(rawId, ocrResult)
            panRegex.matches(rawId) -> validatePan(rawId)
            else -> LayerResult(
                layerName = moduleName,
                passed = false,
                riskLevel = RiskLevel.SUSPICIOUS,
                score = 0.5f,
                details = listOf(
                    "No recognisable ID format detected",
                    "Extracted ID: ${rawId.ifEmpty { "None" }}",
                    "Expected: 12-digit Aadhaar or 10-char PAN"
                )
            )
        }
    }

    private fun validateAadhaar(digits: String, ocrResult: OcrResult): LayerResult {
        val checksumOk = verhoeffChecksum(digits)
        val issues = mutableListOf<String>()

        // 1. Verhoeff Checksum
        if (!checksumOk) {
            issues.add("Verhoeff checksum: FAILED - digit tampered")
        }

        // 2. DOB Validation
        val dob = ocrResult.dateOfBirth
        if (dob.isNotEmpty()) {
            val parts = dob.split("/", "-")
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: 0
                val month = parts[1].toIntOrNull() ?: 0
                val yearStr = parts[2]
                val year = yearStr.toIntOrNull() ?: 0

                if (yearStr.length != 4) {
                    issues.add("DOB Year has ${yearStr.length} digits ('$yearStr') instead of 4 - Forgery!")
                } else if (year < 1900 || year > 2025) {
                    issues.add("DOB Year '$year' is out of valid range (1900-2025)")
                }
                if (month < 1 || month > 12) {
                    issues.add("DOB Month '$month' is invalid (expected 1-12)")
                }
                if (day < 1 || day > 31) {
                    issues.add("DOB Day '$day' is invalid (expected 1-31)")
                }
            }
        }

        // 3. Name sanity check
        val name = ocrResult.name
        if (name.isNotEmpty()) {
            if (name.any { it.isDigit() }) {
                issues.add("Name contains digits - likely OCR misread or tampering")
            }
            if (name.length < 2) {
                issues.add("Name too short ('$name') - suspicious")
            }
        }

        val passed = issues.isEmpty()

        return LayerResult(
            layerName = moduleName,
            passed = passed,
            riskLevel = if (passed) RiskLevel.CLEAR else RiskLevel.FORGED,
            score = if (passed) 0f else 0.95f,
            details = buildList {
                add("Document: Aadhaar Card")
                add("Format (12 digits): Valid")
                add("Verhoeff checksum: ${if (checksumOk) "Passed" else "FAILED"}")
                if (dob.isNotEmpty()) add("DOB extracted: $dob")
                addAll(issues)
                if (issues.isEmpty()) add("All validation rules passed")
            }
        )
    }

    private fun validatePan(pan: String): LayerResult {
        val ok = panRegex.matches(pan)
        return LayerResult(
            layerName = moduleName,
            passed = ok,
            riskLevel = if (ok) RiskLevel.CLEAR else RiskLevel.SUSPICIOUS,
            score = if (ok) 0f else 0.6f,
            details = listOf(
                "Document: PAN Card",
                "Pattern [AAAAA0000A]: ${if (ok) "Matched" else "Not matched"}",
                "Extracted: $pan"
            )
        )
    }

    private fun verhoeffChecksum(number: String): Boolean {
        var c = 0
        number.reversed().forEachIndexed { i, ch ->
            c = verhoeffMultiplicationTable[c][verhoeffPermutationTable[i % 8][ch.digitToInt()]]
        }
        return c == 0
    }
}
