package com.example.sentinel.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.module2.DeepfakeApiClient
import com.example.sentinel.module2.DeepfakeAnalysisResponse
import com.example.sentinel.ui.theme.BgCard
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.BgSurface
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.RiskClear
import com.example.sentinel.ui.theme.RiskForged
import com.example.sentinel.ui.theme.StrokeColor
import com.example.sentinel.ui.theme.TextHint
import com.example.sentinel.ui.theme.TextPrimary
import com.example.sentinel.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream

fun getAudioFileName(context: Context, uri: Uri): String {
    var result = "audio_sample.webm"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) {
                    result = name
                }
            }
        }
    } catch (_: Exception) {}
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<DeepfakeAnalysisResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val apiClient = remember { DeepfakeApiClient() }

    // OpenDocument accepts all audio & webm media containers
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzing = true
            analysisResult = null
            errorMessage = null

            val originalName = getAudioFileName(context, uri)
            selectedFileName = originalName

            val tempFile = File(context.cacheDir, "temp_$originalName")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Send to Sentinel FastAPI Backend
                apiClient.analyzeAudioFile(
                    audioFile = tempFile,
                    onResult = { response ->
                        isAnalyzing = false
                        analysisResult = response
                        tempFile.delete()
                    },
                    onError = { error ->
                        isAnalyzing = false
                        errorMessage = error
                        tempFile.delete()
                    }
                )
            } catch (e: Exception) {
                isAnalyzing = false
                errorMessage = "Failed to read audio file: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice Deepfake Detector",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgDeep
                )
            )
        },
        containerColor = BgDeep
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Module Header Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(16.dp))
                        .border(1.dp, StrokeColor, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF0D2B3A), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Module 2: Voice Forensics",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Supports WEBM, MP3, WAV, M4A, OGG with lossless temporal analysis",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Upload Button Card
                Button(
                    onClick = {
                        audioPicker.launch(
                            arrayOf(
                                "audio/*",
                                "video/webm",
                                "audio/webm",
                                "application/ogg",
                                "video/*",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isAnalyzing
                ) {
                    Icon(
                        imageVector = Icons.Filled.Audiotrack,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isAnalyzing) "Analyzing Audio..." else "Upload Audio / WEBM File",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (selectedFileName != null && !isAnalyzing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "File: $selectedFileName",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Loading State
                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CyanAccent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Running AI Audio Forensic Pipeline...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Extracting uncompressed PCM audio & evaluating 4s chunks",
                                color = TextHint,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Error Message
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A0D0D), RoundedCornerShape(12.dp))
                            .border(1.dp, RiskForged.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = RiskForged,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = RiskForged,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Results View
                analysisResult?.let { result ->
                    val isFake = result.isDeepfake
                    val bannerColor = if (isFake) RiskForged else RiskClear
                    val bannerBg = if (isFake) Color(0xFF2B0E14) else Color(0xFF0D2B1A)

                    // Final Verdict Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bannerBg, RoundedCornerShape(16.dp))
                            .border(1.dp, bannerColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isFake) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = bannerColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (isFake) "DEEPFAKE DETECTED" else "AUTHENTIC HUMAN VOICE",
                                    color = bannerColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Analyzed ${result.totalChunks} audio segment(s)",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Temporal Segment Analysis",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Chunk breakdown list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.chunks) { chunk ->
                            val chunkFake = chunk.isFake
                            val chunkColor = if (chunkFake) RiskForged else RiskClear
                            val chunkBg = BgCard

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(chunkBg)
                                    .border(1.dp, StrokeColor, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Segment ${chunk.index} (4 sec)",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Human: ${chunk.realConfidence}%  |  AI: ${chunk.fakeConfidence}%",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (chunkFake) Color(0xFF3B141C) else Color(0xFF0F3622),
                                                RoundedCornerShape(100.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (chunkFake) "🚨 FAKE" else "✅ REAL",
                                            color = chunkColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
