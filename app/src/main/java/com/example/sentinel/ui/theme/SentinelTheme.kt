package com.example.sentinel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanAccent = Color(0xFF00D4FF)
val CyanDim = Color(0xFF1A4A5A)
val PurpleAccent = Color(0xFF7C3AED)

val BgDeep = Color(0xFF090D1A)
val BgCard = Color(0xFF111827)
val BgSurface = Color(0xFF1A2236)

val RiskClear = Color(0xFF00E676)
val RiskClearBg = Color(0xFF0D2B1A)
val RiskSuspicious = Color(0xFFFF9800)
val RiskSuspiciousBg = Color(0xFF2B1A00)
val RiskForged = Color(0xFFFF3B3B)
val RiskForgedBg = Color(0xFF2B0D0D)

val TextPrimary = Color(0xFFF0F4FF)
val TextSecondary = Color(0xFF8892A4)
val TextHint = Color(0xFF4A5568)

val StrokeColor = Color(0xFF243044)
val DividerColor = Color(0xFF1E2A3A)
val ModuleLocked = Color(0xFF2A3040)

private val SentinelColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = BgDeep,
    secondary = PurpleAccent,
    onSecondary = TextPrimary,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgSurface,
    onSurfaceVariant = TextSecondary,
    outline = StrokeColor,
    error = RiskForged,
    onError = TextPrimary
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SentinelColorScheme,
        content = content
    )
}
