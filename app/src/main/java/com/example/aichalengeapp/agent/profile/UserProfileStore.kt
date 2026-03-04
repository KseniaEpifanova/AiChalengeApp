package com.example.aichalengeapp.agent.profile

interface UserProfileStore {
    suspend fun load(): UserProfile
    suspend fun save(profile: UserProfile)
    suspend fun clear()
}
