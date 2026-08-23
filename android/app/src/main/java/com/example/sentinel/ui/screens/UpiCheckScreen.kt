/*
 * MODULE 3 — UPI FRAUD ANALYSIS SCREEN
 *
 * Full QR → DB → ML Pipeline:
 * 1) User taps "Scan QR Code"
 * 2) ZXing launches the camera QR scanner
 * 3) On scan, the UPI ID is extracted from the QR payload (pa=<user_id>@sentinel)
 * 4) UpiDbRepository fetches that user's transactions from the local simulation SQLite DB
 * 5) Each transaction is sent to the FastAPI ML server (/check-transaction)
 * 6) The highest risk result across all transactions is shown on screen
 *
 * DEMO CHECKLIST:
 * 1) Start the FastAPI server on your laptop:
 *    cd ml-server/module3/ && uvicorn src.api:app --host 0.0.0.0 --port 8000 --reload
 * 2) Copy upi_simulation_database.db into android/app/src/main/assets/
 * 3) Update BASE_URL in UpiApiClient.kt to your laptop's local IP (e.g. 192.168.x.x:8000)
 * 4) Scan any QR code from the qr_codes/ folder OR use the quick-fill buttons for demo
 */
package com.example.sentinel.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.ModuleInput
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module3.UpiDbRepository
import com.example.sentinel.module3.UpiForensicModule
import com.example.sentinel.ui.theme.BgCard
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.StrokeColor
import com.example.sentinel.ui.theme.TextHint
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiCheckScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val upiModule = remember { UpiForensicModule() }

    var upiId by remember { mutableStateOf("") }
    var scannedUserId by remember { mutableStateOf<String?>(null) }
    var isUpiIdFocused by remember { mutableStateOf(false) }
    var scanStatusMsg by remember { mutableStateOf<String?>(null) }

    // Editable fields for manual/demo use
    var amount by remember { mutableStateOf("450") }
    var receiverAccountAge by remember { mutableStateOf("420") }
    var transactionTimeOfDay by remember { mutableFloatStateOf(15f) }
    var sessionSource by remember { mutableStateOf("app") }
    var handleVerificationStatus by remember { mutableStateOf("verified") }

    var result by remember { mutableStateOf<LayerResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // ── ZXing QR Scanner Launcher ──────────────────────────────────────────────
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { scanResult ->
        if (scanResult.contents != null) {
            val rawQr = scanResult.contents
            // Parse UPI string: upi://pay?pa=user_id@sentinel&pn=...
            val userId = try {
                val decoded = URLDecoder.decode(rawQr, "UTF-8")
                val paMatch = Regex("pa=([^&]+)").find(decoded)
                val pa = paMatch?.groupValues?.get(1) ?: ""
                pa.substringBefore("@")
            } catch (e: Exception) { "" }

            if (userId.isNotBlank()) {
                scannedUserId = userId
                upiId = "$userId@sentinel"
                scanStatusMsg = "✅ Scanned: $userId — fetching transactions..."

                // Auto-trigger ML analysis using transactions from DB
                isLoading = true
                coroutineScope.launch {
                    val transactions = withContext(Dispatchers.IO) {
                        UpiDbRepository.getTransactionsByUserId(context, userId)
                    }

                    if (transactions.isEmpty()) {
                        scanStatusMsg = "⚠️ No transactions found for user '$userId' in simulation DB."
                        isLoading = false
                        return@launch
                    }

                    scanStatusMsg = "🔍 Found ${transactions.size} transactions — running ML model..."

                    // Evaluate each transaction and keep the highest-risk result
                    var highestRiskResult: LayerResult? = null
                    for (txn in transactions) {
                        val input = ModuleInput.TransactionInput(txn)
                        val layerResult = upiModule.analyze(input)
                        if (highestRiskResult == null ||
                            layerResult.score > (highestRiskResult?.score ?: 0f)) {
                            highestRiskResult = layerResult
                        }
                    }

                    result = highestRiskResult
                    scanStatusMsg = null
                    isLoading = false
                }
            } else {
                scanStatusMsg = "⚠️ Could not parse UPI ID from QR. Raw: $rawQr"
            }
        }
    }

    // ── Default Transaction Base Map ───────────────────────────────────────────
    val defaultTransaction = mapOf<String, Any>(
        "transaction_id" to "txn-default",
        "user_id" to "user_default",
        "merchant_id" to "merch_default",
        "session_duration" to 180,
        "authentication_attempts" to 1,
        "receiver_transaction_history" to 25,
        "transaction_amount_vs_sender_history" to 0.95,
        "geographic_disparity" to 12.5,
        "merchant_category_code" to "food",
        "time_between_link_click_and_transaction" to 0,
        "unusual_device_flag" to 0,
        "unusual_ip_flag" to 0,
        "unusual_location_flag" to 0,
        "dns_lookup_age" to 0,
        "recent_app_installs" to "[]",
        "input_timing_consistency" to 0.95,
        "app_switching_frequency" to 0,
        "keyboard_input_speed" to 1.2,
        "input_pause_patterns" to 0.05,
        "permissions_granted" to "[]",
        "screen_active_time" to 195,
        "geographic_location_vs_ip" to 12.5,
        "background_data_usage" to 0.15,
        "recognized_screen_sharing_apps" to "[]",
        "authentication_attempt_count" to 1,
        "time_between_otp_generation_and_input" to 0,
        "pin_entry_method" to "manual",
        "pin_entry_speed" to 1.3,
        "unusual_transaction_amount_flag" to 0,
        "otp_request_frequency" to 0,
        "otp_request_device_consistency" to 1,
        "transaction_velocity" to 1,
        "failed_transaction_count" to 0,
        "authorization_method" to "pin",
        "transaction_type" to "payment",
        "request_description_keywords" to "[]",
        "request_amount_roundness" to 1.0,
        "request_frequency" to 0,
        "request_acceptance_rate" to 0.0,
        "time_to_respond_to_request" to 0,
        "time_pressure_indicators" to 0,
        "requester_account_age" to 0,
        "handle_similarity_score" to 0.0,
        "handle_typo_analysis" to "none",
        "handle_transaction_history" to 15,
        "business_name_match" to "match",
        "handle_registration_pattern" to "standard",
        "handle_to_description_consistency" to 1
    )

    fun loadSafeExample() {
        scannedUserId = null
        upiId = "shopkeeper@okhdfcbank"
        amount = "450"
        receiverAccountAge = "420"
        transactionTimeOfDay = 15f
        sessionSource = "app"
        handleVerificationStatus = "verified"
        scanStatusMsg = null
        result = null
    }

    fun loadRiskyExample() {
        scannedUserId = null
        upiId = "newpay1234@upi"
        amount = "75000"
        receiverAccountAge = "0"
        transactionTimeOfDay = 3f
        sessionSource = "link"
        handleVerificationStatus = "unverified"
        scanStatusMsg = null
        result = null
    }

    fun runManualCheck() {
        val amountVal = amount.toFloatOrNull() ?: 450f
        val ageVal = receiverAccountAge.toIntOrNull() ?: 420
        val timeVal = transactionTimeOfDay.toInt()

        val fields = defaultTransaction.toMutableMap()
        fields["amount"] = amountVal
        fields["receiver_account_age"] = ageVal
        fields["transaction_time_of_day"] = timeVal
        fields["session_source"] = sessionSource
        fields["handle_verification_status"] = handleVerificationStatus

        if (sessionSource == "link") fields["time_between_link_click_and_transaction"] = 6
        if (amountVal > 50000) fields["unusual_transaction_amount_flag"] = 1
        if (handleVerificationStatus == "unverified") fields["business_name_match"] = "none"

        val input = ModuleInput.TransactionInput(fields)
        isLoading = true
        coroutineScope.launch {
            result = upiModule.analyze(input)
            isLoading = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "UPI Fraud Analysis",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Module 3 — Real-Time Check",
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {

                // ── QR SCAN BUTTON ─────────────────────────────────────────────
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan UPI QR Code")
                            setBeepEnabled(true)
                            setBarcodeImageEnabled(false)
                        }
                        qrScanLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan UPI QR Code", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                // Scan status / feedback message
                scanStatusMsg?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (msg.startsWith("✅") || msg.startsWith("🔍")) CyanAccent else Color(0xFFFFB74D),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── UPI ID DISPLAY ─────────────────────────────────────────────
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it; scannedUserId = null },
                    label = { Text("UPI ID") },
                    colors = textFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState -> isUpiIdFocused = focusState.isFocused }
                )
                if (scannedUserId == null && (upiId.isNotEmpty() || isUpiIdFocused)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Demo note: manual UPI ID won't auto-fetch from DB — use the fields below.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // ── MANUAL FIELDS (only shown if NOT using QR scan) ────────────
                if (scannedUserId == null) {
                    // AMOUNT
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Transaction Amount (INR)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // RECEIVER ACCOUNT AGE
                    OutlinedTextField(
                        value = receiverAccountAge,
                        onValueChange = { receiverAccountAge = it },
                        label = { Text("Receiver Account Age (Days)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = textFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // TRANSACTION HOUR
                    Text(
                        text = "Time of Day: ${transactionTimeOfDay.toInt()}:00 hrs",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = transactionTimeOfDay,
                        onValueChange = { transactionTimeOfDay = it },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccent,
                            activeTrackColor = CyanAccent,
                            inactiveTrackColor = StrokeColor
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // SESSION SOURCE
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Source: ", color = TextPrimary, modifier = Modifier.width(80.dp))
                        OutlinedActionButton(
                            label = "App",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier
                                .weight(1f)
                                .border(if (sessionSource == "app") 2.dp else 1.dp, if (sessionSource == "app") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                            onClick = { sessionSource = "app" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedActionButton(
                            label = "Link",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier
                                .weight(1f)
                                .border(if (sessionSource == "link") 2.dp else 1.dp, if (sessionSource == "link") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                            onClick = { sessionSource = "link" }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // HANDLE VERIFICATION
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Handle: ", color = TextPrimary, modifier = Modifier.width(80.dp))
                        OutlinedActionButton(
                            label = "Verified",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier
                                .weight(1f)
                                .border(if (handleVerificationStatus == "verified") 2.dp else 1.dp, if (handleVerificationStatus == "verified") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                            onClick = { handleVerificationStatus = "verified" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedActionButton(
                            label = "Unverified",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier
                                .weight(1f)
                                .border(if (handleVerificationStatus == "unverified") 2.dp else 1.dp, if (handleVerificationStatus == "unverified") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                            onClick = { handleVerificationStatus = "unverified" }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // QUICK FILL BUTTONS
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedActionButton(
                            label = "Load Safe Example",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier.weight(1f),
                            onClick = { loadSafeExample() }
                        )
                        OutlinedActionButton(
                            label = "Load Risky Example",
                            icon = Icons.Filled.ArrowBack,
                            modifier = Modifier.weight(1f),
                            onClick = { loadRiskyExample() }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // SUBMIT BUTTON (manual mode)
                    PrimaryGradientButton(
                        label = if (isLoading) "Analyzing..." else "Check Risk",
                        enabled = !isLoading,
                        onClick = { runManualCheck() }
                    )
                } else {
                    // QR mode: show a "Re-scan" option
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Transactions fetched from simulation DB for: $scannedUserId",
                        color = CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedActionButton(
                        label = "Clear & Use Manual Input",
                        icon = Icons.Filled.ArrowBack,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scannedUserId = null
                            upiId = ""
                            result = null
                            scanStatusMsg = null
                        }
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanAccent)
                    }
                }

                // ── RESULTS ────────────────────────────────────────────────────
                result?.let { r ->
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgCard, RoundedCornerShape(16.dp))
                            .border(1.dp, StrokeColor, RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            if (upiId.isNotBlank()) {
                                Text(
                                    text = "Checked: $upiId",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Risk Score",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(r.riskLevel.backgroundColor, RoundedCornerShape(100.dp))
                                        .border(1.dp, r.riskLevel.color.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = r.riskLevel.displayLabel,
                                        color = r.riskLevel.color,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${(r.score * 100).toInt()}",
                                color = TextPrimary,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Explainable AI text removed as per user request
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanAccent,
    unfocusedBorderColor = StrokeColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = CyanAccent,
    unfocusedLabelColor = TextHint,
    cursorColor = CyanAccent
)
