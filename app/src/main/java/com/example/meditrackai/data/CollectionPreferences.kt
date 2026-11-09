package com.example.meditrackai.data

interface CollectionPreferences {
    suspend fun tokenForCollection(): String?
    fun report(status: String?)
}
