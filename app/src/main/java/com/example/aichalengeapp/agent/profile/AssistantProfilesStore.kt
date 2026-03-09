package com.example.aichalengeapp.agent.profile

interface AssistantProfilesStore {
    suspend fun loadProfiles(): List<AssistantProfile>
    suspend fun saveProfiles(profiles: List<AssistantProfile>)
    suspend fun loadActiveProfileId(): String?
    suspend fun saveActiveProfileId(profileId: String)
    suspend fun clear()
}
