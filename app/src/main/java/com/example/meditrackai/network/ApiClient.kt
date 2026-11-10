package com.example.meditrackai.network

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import com.example.meditrackai.BuildConfig

interface MediTrackApi {
    @POST("analyze-symptom")
    suspend fun analyzeSymptom(@Body request: SymptomRequest): Response<SymptomResponse>
}

data class HealthResponse(val status: String)
interface DiagnosticsApi {
    @GET("health") suspend fun health(): Response<HealthResponse>
}

object ApiClient {
    val diagnosticsApi: DiagnosticsApi by lazy { retrofit.create(DiagnosticsApi::class.java) }
    val collectionApi: CollectionApi by lazy { retrofit.create(CollectionApi::class.java) }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val started = System.nanoTime()
            try {
                chain.proceed(request).also { response ->
                    if (request.url.encodedPath == "/analyze-symptom") {
                        com.example.meditrackai.diagnostics.ApiDiagnostics.record(response.code,
                            response.header("X-Request-ID"), (System.nanoTime() - started) / 1_000_000)
                    }
                }
            } catch (e: java.io.IOException) {
                if (request.url.encodedPath == "/analyze-symptom") {
                    com.example.meditrackai.diagnostics.ApiDiagnostics.record(null, null, (System.nanoTime() - started) / 1_000_000)
                }
                throw e
            }
        }
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
