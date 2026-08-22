package com.example.sentinel.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.ui.theme.BgCard
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.DividerColor
import com.example.sentinel.ui.theme.ModuleLocked
import com.example.sentinel.ui.theme.RiskClear
import com.example.sentinel.ui.theme.StrokeColor
import com.example.sentinel.ui.theme.TextHint
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary

@Composable
fun HomeScreen(onModule1Click: () -> Unit, onModule3Click: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1525), BgDeep, BgDeep)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x4400D4FF), Color(0x0000D4FF))
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SENTINEL",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Forensic Intelligence, On-Device",
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Forensic Modules",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a module to begin analysis",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            ModuleCard(
                icon = Icons.Filled.Security,
                title = "Document Forensics",
                description = "Detect forged identity documents using multi-layer on-device AI analysis — OCR, ELA, DocTamper, QR cross-verification.",
                statusLabel = "ACTIVE",
                statusColor = RiskClear,
                statusBgColor = Color(0xFF0D2B1A),
                iconBgBrush = Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF0099CC))),
                borderColor = CyanAccent,
                isEnabled = true,
                onClick = onModule1Click
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModuleCard(
                icon = Icons.Filled.MicNone,
                title = "Voice Deepfake Detector",
                description = "Detect AI-generated or cloned voice recordings using on-device audio forensic analysis.",
                statusLabel = "COMING SOON",
                statusColor = TextHint,
                statusBgColor = ModuleLocked,
                iconBgBrush = Brush.linearGradient(listOf(ModuleLocked, ModuleLocked)),
                borderColor = StrokeColor,
                isEnabled = false,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModuleCard(
                icon = Icons.Filled.Stars,
                title = "UPI Fraud Analysis",
                description = "Real-time, on-device analysis of UPI transactions to detect phishing, social engineering, and behavioral anomalies.",
                statusLabel = "ACTIVE",
                statusColor = RiskClear,
                statusBgColor = Color(0xFF0D2B1A),
                iconBgBrush = Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF0099CC))),
                borderColor = CyanAccent,
                isEnabled = true,
                onClick = onModule3Click
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "🔒  Fully Offline",
                        color = CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All analysis runs entirely on-device. No images, IDs, or results are ever transmitted to external servers.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String,
    statusColor: Color,
    statusBgColor: Color,
    iconBgBrush: Brush,
    borderColor: Color,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled) 0.97f else 1f,
        animationSpec = tween(120),
        label = "card_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(brush = iconBgBrush, shape = RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = if (isEnabled) icon else Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (isEnabled) Color(0xFF090D1A) else TextHint,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (isEnabled) TextPrimary else TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(statusBgColor, RoundedCornerShape(100.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = description,
                color = if (isEnabled) TextSecondary else TextHint,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}
