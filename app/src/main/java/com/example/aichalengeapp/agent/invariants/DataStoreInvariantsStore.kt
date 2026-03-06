package com.example.aichalengeapp.agent.invariants

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

private val Context.invariantsDataStore by preferencesDataStore(name = "invariant_guard")

class DataStoreInvariantsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : InvariantsStore {

    private val KEY_PROFILE = stringPreferencesKey("invariant_guard_profile_json")
    private val KEY_GUARD_ENABLED = booleanPreferencesKey("invariant_guard_enabled")

    override suspend fun loadProfile(): InvariantsProfile {
        val prefs = context.invariantsDataStore.data.first()
        val raw = prefs[KEY_PROFILE].orEmpty()
        if (raw.isBlank()) return InvariantsProfile()

        return withContext(Dispatchers.Default) {
            runCatching {
                val json = JSONObject(raw)
                InvariantsProfile(
                    techDecisions = json.optString("techDecisions", ""),
                    businessRules = json.optString("businessRules", "")
                )
            }.getOrElse { InvariantsProfile() }
        }
    }

    override suspend fun saveProfile(profile: InvariantsProfile) {
        val encoded = withContext(Dispatchers.Default) {
            JSONObject().apply {
                put("techDecisions", profile.techDecisions)
                put("businessRules", profile.businessRules)
            }.toString()
        }
        context.invariantsDataStore.edit { prefs ->
            prefs[KEY_PROFILE] = encoded
        }
    }

    override suspend fun clearProfile() {
        context.invariantsDataStore.edit { prefs ->
            prefs[KEY_PROFILE] = ""
        }
    }

    override suspend fun loadGuardEnabled(): Boolean {
        val prefs = context.invariantsDataStore.data.first()
        return prefs[KEY_GUARD_ENABLED] ?: false
    }

    override suspend fun saveGuardEnabled(enabled: Boolean) {
        context.invariantsDataStore.edit { prefs ->
            prefs[KEY_GUARD_ENABLED] = enabled
        }
    }
}
