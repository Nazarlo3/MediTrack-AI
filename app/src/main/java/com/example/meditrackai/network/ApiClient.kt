package com.example.meditrackai.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import com.example.meditrackai.BuildConfig

interface MediTrackApi {
    @POST("analyze-symptom")
    suspend fun analyzeSymptom(@Body request: SymptomRequest): Response<SymptomResponse>
}

object ApiClient {
    val collectionApi: CollectionApi by lazy { retrofit.create(CollectionApi::class.java) }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val api: MediTrackApi by lazy { retrofit.create(MediTrackApi::class.java) }
}
