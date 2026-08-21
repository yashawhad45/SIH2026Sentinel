package com.example.sentinel.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.sentinel.R
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }
    val progressAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(800, easing = EaseOutCubic))
        scale.animateTo(1f, animationSpec = tween(700, easing = EaseOutCubic))
        delay(300)
        progressAlpha.animateTo(1f, animationSpec = tween(400))
        delay(1400)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1830), BgDeep),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scale.value)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sentinel_wbg),
                    contentDescription = "Sentinel Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SENTINEL",
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Forensic Intelligence, On-Device",
                color = CyanAccent,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .size(width = 120.dp, height = 2.dp)
                    .alpha(progressAlpha.value),
                color = CyanAccent,
                trackColor = BgSurface,
                strokeCap = StrokeCap.Round
            )
        }

        Text(
            text = "v1.0  ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¢  Module 1 Active",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(alpha.value)
        )
    }
}
