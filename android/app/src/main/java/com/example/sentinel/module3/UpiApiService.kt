package com.example.sentinel.module3

import retrofit2.http.Body
import retrofit2.http.POST

interface UpiApiService {
    @POST("check-transaction")
    suspend fun checkTransaction(@Body transaction: Map<String, @JvmSuppressWildcards Any>): TransactionResponse
}
