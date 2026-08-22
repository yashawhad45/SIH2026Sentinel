package com.example.sentinel.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.ModuleInput
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module3.UpiForensicModule
import com.example.sentinel.ui.theme.BgCard
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.StrokeColor
import com.example.sentinel.ui.theme.TextHint
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiCheckScreen(
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val upiModule = remember { UpiForensicModule() }

    // 5 editable fields
    var amount by remember { mutableStateOf("450") }
    var receiverAccountAge by remember { mutableStateOf("420") }
    var transactionTimeOfDay by remember { mutableFloatStateOf(15f) }
    var sessionSource by remember { mutableStateOf("app") } // "app" | "link"
    var handleVerificationStatus by remember { mutableStateOf("verified") } // "verified" | "unverified"

    var result by remember { mutableStateOf<LayerResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
        amount = "450"
        receiverAccountAge = "420"
        transactionTimeOfDay = 15f
        sessionSource = "app"
        handleVerificationStatus = "verified"
    }

    fun loadRiskyExample() {
        amount = "75000"
        receiverAccountAge = "0"
        transactionTimeOfDay = 3f
        sessionSource = "link"
        handleVerificationStatus = "unverified"
    }

    fun runCheck() {
        val amountVal = amount.toFloatOrNull() ?: 450f
        val ageVal = receiverAccountAge.toIntOrNull() ?: 420
        val timeVal = transactionTimeOfDay.toInt()

        val fields = defaultTransaction.toMutableMap()
        fields["amount"] = amountVal
        fields["receiver_account_age"] = ageVal
        fields["transaction_time_of_day"] = timeVal
        fields["session_source"] = sessionSource
        fields["handle_verification_status"] = handleVerificationStatus

        // Overwrite some correlated fields if risky to actually trigger the model properly
        // since the model relies heavily on network indicators.
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
                        icon = Icons.Filled.ArrowBack, // Not used but required
                        modifier = Modifier
                            .weight(1f)
                            .border(if (sessionSource == "app") 2.dp else 1.dp, if (sessionSource == "app") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                        onClick = { sessionSource = "app" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedActionButton(
                        label = "Link",
                        icon = Icons.Filled.ArrowBack, // Not used
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
                        icon = Icons.Filled.ArrowBack, // Not used
                        modifier = Modifier
                            .weight(1f)
                            .border(if (handleVerificationStatus == "verified") 2.dp else 1.dp, if (handleVerificationStatus == "verified") CyanAccent else StrokeColor, RoundedCornerShape(12.dp)),
                        onClick = { handleVerificationStatus = "verified" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedActionButton(
                        label = "Unverified",
                        icon = Icons.Filled.ArrowBack, // Not used
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

                // SUBMIT BUTTON
                PrimaryGradientButton(
                    label = if (isLoading) "Analyzing..." else "Check Risk",
                    enabled = !isLoading,
                    onClick = { runCheck() }
                )

                if (isLoading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanAccent)
                    }
                }

                // RESULTS
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
                            r.details.forEach { detail ->
                                Text(
                                    text = detail,
                                    color = if (detail.startsWith(" • ")) r.riskLevel.color else TextSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
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
