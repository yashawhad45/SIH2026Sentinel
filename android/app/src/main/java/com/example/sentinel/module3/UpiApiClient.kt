package com.example.sentinel.module3

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object UpiApiClient {
    // Note: 10.0.2.2 is the Android emulator's alias for the host machine's localhost (e.g., laptop).
    // It routes to the FastAPI server running on the same machine.
    // If you run this on a physical device for the live demo, this MUST be changed to the laptop's
    // real local IP address (e.g., 192.168.x.x) or a public ngrok URL!
    private const val BASE_URL = "http://192.168.0.74:8000/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: UpiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpiApiService::class.java)
    }
}
