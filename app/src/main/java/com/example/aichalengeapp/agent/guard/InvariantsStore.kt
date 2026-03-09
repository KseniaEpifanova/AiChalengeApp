package com.example.aichalengeapp.agent.guard

interface InvariantsStore {
    suspend fun loadProfile(): InvariantsProfile
    suspend fun saveProfile(profile: InvariantsProfile)
    suspend fun clearProfile()
}
