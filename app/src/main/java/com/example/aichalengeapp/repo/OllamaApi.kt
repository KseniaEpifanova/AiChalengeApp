package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.OllamaRequest
import com.example.aichalengeapp.data.OllamaResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApi {

    @POST("api/generate")
    suspend fun generate(@Body body: OllamaRequest): OllamaResponse
}
