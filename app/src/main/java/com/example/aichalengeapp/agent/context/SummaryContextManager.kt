package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.agent.summary.Summarizer
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.data.MemorySnapshot
import javax.inject.Inject

class SummaryContextManager @Inject constructor(
    private val summarizer: Summarizer
) : ContextManager {

    private val keepLastN = 12
    private val summarizeBatchSize = 10

    override suspend fun prepare(snapshot: MemorySnapshot): OldContextPlan {
        val all = snapshot.messages.filter { it.role != AgentRole.SYSTEM }
        val summarizedCount = snapshot.summarizedCount

        val tail = all.takeLast(keepLastN)
        val compressible = all.dropLast(keepLastN)
        val alreadySummarized = compressible.take(summarizedCount.coerceAtMost(compressible.size))
        val remaining = compressible.drop(alreadySummarized.size)
        if (remaining.size >= summarizeBatchSize) {
            val chunk = remaining.take(summarizeBatchSize)

            val newSummary = summarizer.summarizeChunk(
                existingSummary = snapshot.summary,
                chunk = chunk
            )

            val newSummarizedCount = summarizedCount + chunk.size

            val updated = snapshot.copy(
                summary = newSummary,
                summarizedCount = newSummarizedCount
            )

            return OldContextPlan(
                summary = newSummary,
                tailMessages = tail,
                updatedSnapshot = updated
            )
        }

        return OldContextPlan(
            summary = snapshot.summary,
            tailMessages = tail,
            updatedSnapshot = null
        )
    }
}
