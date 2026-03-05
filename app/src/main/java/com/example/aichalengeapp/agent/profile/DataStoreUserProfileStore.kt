package com.example.aichalengeapp.agent.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profile")

class DataStoreUserProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UserProfileStore {

    private val KEY_PROFILE = stringPreferencesKey("profile_json")

    override suspend fun load(): UserProfile {
        val prefs = context.userProfileDataStore.data.first()
        val raw = prefs[KEY_PROFILE].orEmpty()
        if (raw.isBlank()) return UserProfile()

        return withContext(Dispatchers.Default) {
            runCatching {
                val o = JSONObject(raw)
                UserProfile(
                    style = o.optString("style", ""),
                    format = o.optString("format", ""),
                    constraints = o.optString("constraints", "")
                )
            }.getOrElse { UserProfile() }
        }
    }

    override suspend fun save(profile: UserProfile) {
        val encoded = withContext(Dispatchers.Default) {
            JSONObject().apply {
                put("style", profile.style)
                put("format", profile.format)
                put("constraints", profile.constraints)
            }.toString()
        }
        context.userProfileDataStore.edit { it[KEY_PROFILE] = encoded }
    }

    override suspend fun clear() {
        context.userProfileDataStore.edit { it[KEY_PROFILE] = "" }
    }
}
