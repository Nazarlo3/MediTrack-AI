package com.example.meditrackai.network

data class SymptomRequest(
    val description: String
)

data class SymptomResponse(
    val category: String,
    val explanation: String,
    val disclaimer: String
)
