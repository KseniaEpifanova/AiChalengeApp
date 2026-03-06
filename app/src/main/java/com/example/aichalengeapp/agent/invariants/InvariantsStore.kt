package com.example.aichalengeapp.agent.invariants

interface InvariantsStore {
    suspend fun loadProfile(): InvariantsProfile
    suspend fun saveProfile(profile: InvariantsProfile)
    suspend fun clearProfile()

    suspend fun loadGuardEnabled(): Boolean
    suspend fun saveGuardEnabled(enabled: Boolean)
}
