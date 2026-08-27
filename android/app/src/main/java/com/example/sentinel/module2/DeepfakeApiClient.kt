package com.example.sentinel.module2

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Callback
import okhttp3.Call
import okhttp3.Response
import java.io.File
import java.io.IOException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AudioChunkResult(
    val index: Int,
    val realConfidence: Double,
    val fakeConfidence: Double,
    val isFake: Boolean
)

data class DeepfakeAnalysisResponse(
    val verdict: String,
    val isDeepfake: Boolean,
    val totalChunks: Int,
    val chunks: List<AudioChunkResult>
)

class DeepfakeApiClient {
    private val primaryUrl = "http://10.225.12.244:8000/analyze"
    private val emulatorUrl = "http://10.0.2.2:8000/analyze"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun analyzeAudioFile(
        audioFile: File,
        onResult: (DeepfakeAnalysisResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(primaryUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Fallback to emulator loopback IP if WiFi connection fails
                val fallbackRequest = Request.Builder()
                    .url(emulatorUrl)
                    .post(requestBody)
                    .build()

                client.newCall(fallbackRequest).enqueue(object : Callback {
                    override fun onFailure(fallbackCall: Call, ex: IOException) {
                        onError("Connection failed: Make sure Python server is running on port 8000.")
                    }

                    override fun onResponse(fallbackCall: Call, response: Response) {
                        handleResponse(response, onResult, onError)
                    }
                })
            }

            override fun onResponse(call: Call, response: Response) {
                handleResponse(response, onResult, onError)
            }
        })
    }

    private fun handleResponse(
        response: Response,
        onResult: (DeepfakeAnalysisResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val responseData = response.body?.string()
        if (response.isSuccessful && responseData != null) {
            try {
                val json = JSONObject(responseData)
                val isDeepfake = json.optBoolean("is_deepfake", false)
                val verdict = json.optString("verdict", if (isDeepfake) "DEEPFAKE DETECTED" else "AUTHENTIC HUMAN")
                val totalChunks = json.optInt("total_chunks_analyzed", 0)

                val chunkList = mutableListOf<AudioChunkResult>()
                val chunkArray = json.optJSONArray("chunk_breakdown")
                if (chunkArray != null) {
                    for (i in 0 until chunkArray.length()) {
                        val c = chunkArray.getJSONObject(i)
                        val idx = c.optInt("chunk_index", i + 1)
                        val real = c.optDouble("real_confidence", 0.0)
                        val fake = c.optDouble("fake_confidence", 0.0)
                        chunkList.add(
                            AudioChunkResult(
                                index = idx,
                                realConfidence = real,
                                fakeConfidence = fake,
                                isFake = fake > real
                            )
                        )
                    }
                }

                onResult(
                    DeepfakeAnalysisResponse(
                        verdict = verdict,
                        isDeepfake = isDeepfake,
                        totalChunks = totalChunks,
                        chunks = chunkList
                    )
                )
            } catch (e: Exception) {
                onError("Failed to parse AI response: ${e.message}")
            }
        } else {
            // Extract detailed server error message if present
            var errorMsg = "Server error (${response.code})"
            if (responseData != null) {
                try {
                    val json = JSONObject(responseData)
                    val msg = json.optString("message", "")
                    if (msg.isNotBlank()) {
                        errorMsg = "Server: $msg"
                    }
                } catch (_: Exception) {}
            }
            onError(errorMsg)
        }
    }
}
