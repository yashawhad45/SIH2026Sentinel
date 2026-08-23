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

class DeepfakeApiClient {
    // NOTE: Change this IP to your computer's local IP address when running the FastAPI server
    // e.g., "http://192.168.1.5:8000/analyze"
    private val serverUrl = "http://10.0.2.2:8000/analyze" 
    private val client = OkHttpClient()

    fun analyzeAudioFile(audioFile: File, onResult: (Boolean, Double) -> Unit, onError: (String) -> Unit) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/*".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed to connect to server: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    if (responseData != null) {
                        try {
                            val json = JSONObject(responseData)
                            val isDeepfake = json.getBoolean("is_deepfake")
                            // You can parse more info here, like chunk_breakdown if you want!
                            onResult(isDeepfake, if(isDeepfake) 100.0 else 0.0)
                        } catch (e: Exception) {
                            onError("Failed to parse response: ${e.message}")
                        }
                    } else {
                        onError("Empty response from server")
                    }
                } else {
                    onError("Server error: ${response.code}")
                }
            }
        })
    }
}
