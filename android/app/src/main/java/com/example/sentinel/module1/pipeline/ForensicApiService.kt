package com.example.sentinel.module1.pipeline

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class SuspiciousRegion(
    val type: String,
    val bbox: List<Int>,
    val score: Float,
    val reason: String
)

data class ForensicAnalysisResponse(
    val success: Boolean,
    val error: String?,
    val forensic_score: Float,
    val risk_level: String,
    val text_size_anomaly: Float,
    val text_pixel_anomaly: Float,
    val text_style_anomaly: Float,
    val photo_boundary_anomaly: Float,
    val alignment_anomaly: Float,
    val local_noise_anomaly: Float,
    val suspicious_regions: List<SuspiciousRegion>,
    val explanations: List<String>,
    val card_detected: Boolean
)

interface ForensicApi {
    @Multipart
    @POST("/forensic-analysis")
    suspend fun analyzeForensics(
        @Part file: MultipartBody.Part,
        @Part("ocr_blocks") ocrBlocks: RequestBody
    ): Response<ForensicAnalysisResponse>
}

object ForensicApiClient {
    private const val BASE_URL = "http://10.48.111.172:8000/"

    val api: ForensicApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForensicApi::class.java)
    }
}

