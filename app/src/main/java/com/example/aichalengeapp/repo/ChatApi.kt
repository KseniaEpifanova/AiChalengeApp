package com.example.aichalengeapp.repo

import com.example.aichalengeapp.data.ChatRequest
import com.example.aichalengeapp.data.ChatResponse
import com.example.aichalengeapp.data.DsChatRequest
import com.example.aichalengeapp.data.DsChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatApi {

    @POST("chat/completions")
    suspend fun chat(@Body body: DsChatRequest): DsChatResponse
}
