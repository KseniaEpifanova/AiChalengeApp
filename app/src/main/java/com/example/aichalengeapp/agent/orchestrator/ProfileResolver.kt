package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import javax.inject.Inject

class ProfileResolver @Inject constructor() {

    fun resolve(
        profiles: List<AssistantProfile>,
        activeProfileId: String?
    ): AssistantProfile {
        if (profiles.isEmpty()) return AssistantProfile.default()

        profiles.firstOrNull { it.id == activeProfileId }?.let { return it }
        profiles.firstOrNull { it.isDefault }?.let { return it }

        return profiles.first()
    }
}
