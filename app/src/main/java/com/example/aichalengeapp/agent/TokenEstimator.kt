package com.example.aichalengeapp.agent

import com.example.aichalengeapp.data.AgentMessage
import jakarta.inject.Inject
import kotlin.math.ceil

interface TokenEstimator {
    fun estimateTokens(text: String): Int
    fun estimateTokens(messages: List<AgentMessage>): Int
}

class SimpleCharTokenEstimator @Inject constructor() : TokenEstimator {

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0

        var tokens = 0.0
        for (ch in text) {
            tokens += when {
                ch.code in 0x0000..0x007F -> 0.3
                else -> 0.6
            }
        }
        return ceil(tokens).toInt() + 4
    }

    override fun estimateTokens(messages: List<AgentMessage>): Int {
        var sum = 6
        for (m in messages) {
            sum += estimateTokens(m.content) + 2
        }
        return sum
    }
}
