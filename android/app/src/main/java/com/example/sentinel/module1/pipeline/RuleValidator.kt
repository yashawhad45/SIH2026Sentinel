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

    override suspend fun analyze(bitmap: Bitmap) = LayerResult.unavailable(moduleName)

    fun validateWithOcrResult(ocrResult: OcrResult): LayerResult {
        val rawId = ocrResult.idNumber.replace(" ", "").uppercase()
        return when {
            rawId.length == 12 && rawId.all { it.isDigit() } -> validateAadhaar(rawId)
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

    private fun validateAadhaar(digits: String): LayerResult {
        val checksumOk = verhoeffChecksum(digits)
        val passed = checksumOk && digits.length == 12
        return LayerResult(
            layerName = moduleName,
            passed = passed,
            riskLevel = if (passed) RiskLevel.CLEAR else RiskLevel.FORGED,
            score = if (passed) 0f else 0.85f,
            details = buildList {
                add("Document: Aadhaar Card")
                add("Format (12 digits): Valid")
                add("Verhoeff checksum: ${if (checksumOk) "Passed" else "FAILED — digit may be tampered"}")
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
