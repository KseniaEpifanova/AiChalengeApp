package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsChatResponse
import com.example.aichalengeapp.data.DsEmbeddingRequest
import com.example.aichalengeapp.data.DsEmbeddingResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApi {

    @POST("chat/completions")
    suspend fun chat(@Body body: DsChatRequest): DsChatResponse

    @POST("embeddings")
    suspend fun embeddings(@Body body: DsEmbeddingRequest): DsEmbeddingResponse
}
