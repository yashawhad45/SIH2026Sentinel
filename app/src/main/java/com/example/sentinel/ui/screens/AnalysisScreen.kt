package com.example.sentinel.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import com.example.sentinel.module1.DocumentViewModel
import com.example.sentinel.ui.theme.BgCard
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.DividerColor
import com.example.sentinel.ui.theme.RiskClear
import com.example.sentinel.ui.theme.RiskForged
import com.example.sentinel.ui.theme.RiskSuspicious
import com.example.sentinel.ui.theme.StrokeColor
import com.example.sentinel.ui.theme.TextHint
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary

private val allLayerNames = listOf(
    "OCR Text Extraction",
    "Rule-Based Validation",
    "Error Level Analysis"
)

@Composable
fun AnalysisScreen(
    viewModel: DocumentViewModel,
    onAnalysisComplete: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.report) {
        if (uiState.report != null) onAnalysisComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val progressFraction by animateFloatAsState(
        targetValue = if (allLayerNames.isEmpty()) 0f
        else uiState.completedLayers.size.toFloat() / allLayerNames.size.toFloat(),
        animationSpec = tween(500),
        label = "overall_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0D1525), BgDeep))
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(pulseAlpha)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Running Analysis",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Multi-layer forensic pipeline in progress…",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(allLayerNames) { index, layerName ->
                    val completedResult = uiState.completedLayers.getOrNull(index)
                    val isRunning = uiState.isAnalysing &&
                        completedResult == null &&
                        uiState.completedLayers.size == index

                    AnalysisLayerRow(
                        layerName = layerName,
                        index = index + 1,
                        isRunning = isRunning,
                        result = completedResult
                    )

                    if (index < allLayerNames.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 72.dp)
                                .height(1.dp)
                                .background(DividerColor)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDeep)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = CyanAccent,
                    trackColor = BgSurface,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        uiState.report != null -> "Analysis complete — preparing report…"
                        uiState.isAnalysing && uiState.completedLayers.isNotEmpty() ->
                            "Running: ${allLayerNames.getOrElse(uiState.completedLayers.size) { "Finalising…" }}"
                        uiState.isAnalysing -> "Initialising pipeline…"
                        else -> "Waiting…"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun AnalysisLayerRow(
    layerName: String,
    index: Int,
    isRunning: Boolean,
    result: LayerResult?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = when {
                        result != null -> result.riskLevel.backgroundColor
                        isRunning -> Color(0xFF1A2236)
                        else -> BgSurface
                    },
                    shape = CircleShape
                )
        ) {
            when {
                isRunning -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = CyanAccent,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent
                )
                result != null -> Icon(
                    imageVector = when (result.riskLevel) {
                        RiskLevel.CLEAR -> Icons.Filled.Check
                        RiskLevel.SUSPICIOUS -> Icons.Filled.Warning
                        RiskLevel.FORGED -> Icons.Filled.Warning
                    },
                    contentDescription = null,
                    tint = result.riskLevel.color,
                    modifier = Modifier.size(20.dp)
                )
                else -> Text(
                    text = "$index",
                    color = TextHint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = layerName,
                color = if (result != null || isRunning) TextPrimary else TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (isRunning) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = when {
                    isRunning -> "Running…"
                    result != null -> result.riskLevel.displayLabel
                    else -> "Pending"
                },
                color = when {
                    isRunning -> CyanAccent
                    result != null -> result.riskLevel.color
                    else -> TextHint
                },
                fontSize = 12.sp
            )
        }

        if (result != null) {
            Text(
                text = "${"%.0f".format(result.score * 100)}%",
                color = result.riskLevel.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
