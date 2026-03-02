package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage

data class ContextPlan(
    val messagesForLlm: List<AgentMessage>,
    val debugLabel: String
)
