package com.example.meditrackai.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

interface CollectionApi {
    @POST("analyze-symptom")
    suspend fun analyze(@Body request: SymptomRequest, @Header("X-Collection-Token") token: String): Response<SymptomResponse>

    @DELETE("collection/mine")
    suspend fun deleteMine(@Header("X-Collection-Token") token: String): Response<CollectionDeletion>
}

data class CollectionConsent(
    @com.google.gson.annotations.SerializedName("consent_version") val consentVersion: Int = 2,
    @com.google.gson.annotations.SerializedName("request_id") val requestId: String = java.util.UUID.randomUUID().toString()
)
data class CollectionDeletion(val deleted: Int)
