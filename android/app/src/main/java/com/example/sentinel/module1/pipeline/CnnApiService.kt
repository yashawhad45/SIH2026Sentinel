package com.example.sentinel.module1.pipeline

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class CnnResponse(
    val filename: String,
    val forgery_probability: Float,
    val is_forged: Boolean,
    val doctamper_pixel_ratio: Float,
    val srm_score: Float,
    val srm_details: String
)

interface CnnApi {
    @Multipart
    @POST("/detect-forgery")
    suspend fun detectForgery(
        @Part file: MultipartBody.Part
    ): Response<CnnResponse>
}

object CnnApiClient {
    // 10.0.2.2 is the special IP to access the host machine's localhost from the Android Emulator
    // If testing on a real device, change this to your laptop's local IP (e.g., "http://192.168.1.x:8000/")
    private const val BASE_URL = "http://192.168.0.213:8000/"

    val api: CnnApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CnnApi::class.java)
    }
}






