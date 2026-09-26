package com.example.meditrackai.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

private const val BASE_URL = "http://192.168.0.101:8000/"

interface MediTrackApi {
    @POST("analyze-symptom")
    suspend fun analyzeSymptom(@Body request: SymptomRequest): Response<SymptomResponse>
}

object ApiClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: MediTrackApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MediTrackApi::class.java)
    }
}
