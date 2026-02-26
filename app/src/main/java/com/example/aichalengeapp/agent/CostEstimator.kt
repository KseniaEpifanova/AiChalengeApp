package com.example.aichalengeapp.agent

object CostEstimator {
    // DeepSeek pricing (cache miss input, output) – можно обновить из доков при желании
    // https://api-docs.deepseek.com/quick_start/pricing
    private const val INPUT_PER_1M = 0.28
    private const val OUTPUT_PER_1M = 0.42

    fun estimateUsd(promptTokens: Int, completionTokens: Int): Double {
        val input = (promptTokens / 1_000_000.0) * INPUT_PER_1M
        val output = (completionTokens / 1_000_000.0) * OUTPUT_PER_1M
        return input + output
    }
}