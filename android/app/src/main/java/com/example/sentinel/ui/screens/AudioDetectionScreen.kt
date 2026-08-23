package com.example.sentinel.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sentinel.module2.DeepfakeApiClient
import com.example.sentinel.ui.theme.BgDeep
import com.example.sentinel.ui.theme.CyanAccent
import com.example.sentinel.ui.theme.RiskClear
import com.example.sentinel.ui.theme.RiskForged
import java.io.File
import java.io.FileOutputStream

@Composable
fun AudioDetectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isAnalyzing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isDeepfake by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val apiClient = remember { DeepfakeApiClient() }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzing = true
            resultMessage = null
            isDeepfake = null
            errorMessage = null

            // Copy the selected file to a temporary file
            val tempFile = File(context.cacheDir, "temp_audio_file")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Send to Python Backend
                apiClient.analyzeAudioFile(
                    audioFile = tempFile,
                    onResult = { fake, confidence ->
                        isAnalyzing = false
                        isDeepfake = fake
                        resultMessage = if (fake) "DEEPFAKE DETECTED" else "AUTHENTIC HUMAN"
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
                errorMessage = "Failed to read file: ${e.message}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Audiotrack,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Voice Deepfake Detector",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Upload an audio file to analyze it for AI generation or voice cloning.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isAnalyzing) {
                CircularProgressIndicator(color = CyanAccent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing audio on Sentinel Server...", color = Color.LightGray)
            } else if (resultMessage != null) {
                Icon(
                    imageVector = if (isDeepfake == true) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (isDeepfake == true) RiskForged else RiskClear,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = resultMessage!!,
                    color = if (isDeepfake == true) RiskForged else RiskClear,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (errorMessage != null) {
                Text(text = errorMessage!!, color = RiskForged)
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { audioPicker.launch("audio/*") },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
            ) {
                Text("Select Audio File", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = onBack) {
                Text("Go Back", color = Color.Gray)
            }
        }
    }
}
