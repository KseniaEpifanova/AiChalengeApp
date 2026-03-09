package com.example.aichalengeapp.agent.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private val Context.assistantProfilesDataStore by preferencesDataStore(name = "assistant_profiles")

class DataStoreAssistantProfilesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : AssistantProfilesStore {

    private val keyProfiles = stringPreferencesKey("profiles_json")
    private val keyActiveProfileId = stringPreferencesKey("active_profile_id")

    override suspend fun loadProfiles(): List<AssistantProfile> {
        val prefs = context.assistantProfilesDataStore.data.first()
        val raw = prefs[keyProfiles].orEmpty()
        if (raw.isBlank()) return listOf(AssistantProfile.default())

        val decoded = withContext(Dispatchers.Default) {
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val response = obj.optJSONObject("responseProfile") ?: JSONObject()
                        val planning = obj.optJSONObject("planningProfile") ?: JSONObject()
                        val sensitivityName = planning.optString(
                            "complexitySensitivity",
                            ComplexitySensitivity.MEDIUM.name
                        )
                        val sensitivity = runCatching {
                            ComplexitySensitivity.valueOf(sensitivityName)
                        }.getOrElse { ComplexitySensitivity.MEDIUM }

                        val id = obj.optString("id", "").trim()
                        val name = obj.optString("name", "").trim()
                        if (id.isEmpty() || name.isEmpty()) continue

                        add(
                            AssistantProfile(
                                id = id,
                                name = name,
                                responseProfile = ResponseProfile(
                                    style = response.optString("style", ""),
                                    format = response.optString("format", ""),
                                    constraints = response.optString("constraints", "")
                                ),
                                planningProfile = PlanningProfile(
                                    autoDetectComplexity = planning.optBoolean("autoDetectComplexity", true),
                                    complexitySensitivity = sensitivity,
                                    requirePlanApproval = planning.optBoolean("requirePlanApproval", true),
                                    allowAutoContinueExecution = planning.optBoolean("allowAutoContinueExecution", false),
                                    requireValidationBeforeDone = planning.optBoolean("requireValidationBeforeDone", true)
                                ),
                                isDefault = obj.optBoolean("isDefault", false)
                            )
                        )
                    }
                }
            }.getOrElse { emptyList() }
        }

        val normalized = decoded.ifEmpty { listOf(AssistantProfile.default()) }
        return ensureSingleDefault(normalized)
    }

    override suspend fun saveProfiles(profiles: List<AssistantProfile>) {
        val normalized = ensureSingleDefault(
            if (profiles.isEmpty()) listOf(AssistantProfile.default()) else profiles
        )

        val encoded = withContext(Dispatchers.Default) {
            JSONArray().apply {
                normalized.forEach { profile ->
                    put(
                        JSONObject().apply {
                            put("id", profile.id)
                            put("name", profile.name)
                            put("isDefault", profile.isDefault)
                            put(
                                "responseProfile",
                                JSONObject().apply {
                                    put("style", profile.responseProfile.style)
                                    put("format", profile.responseProfile.format)
                                    put("constraints", profile.responseProfile.constraints)
                                }
                            )
                            put(
                                "planningProfile",
                                JSONObject().apply {
                                    put("autoDetectComplexity", profile.planningProfile.autoDetectComplexity)
                                    put("complexitySensitivity", profile.planningProfile.complexitySensitivity.name)
                                    put("requirePlanApproval", profile.planningProfile.requirePlanApproval)
                                    put("allowAutoContinueExecution", profile.planningProfile.allowAutoContinueExecution)
                                    put("requireValidationBeforeDone", profile.planningProfile.requireValidationBeforeDone)
                                }
                            )
                        }
                    )
                }
            }.toString()
        }

        context.assistantProfilesDataStore.edit { prefs ->
            prefs[keyProfiles] = encoded
        }
    }

    override suspend fun loadActiveProfileId(): String? {
        val prefs = context.assistantProfilesDataStore.data.first()
        return prefs[keyActiveProfileId]?.trim().takeUnless { it.isNullOrEmpty() }
    }

    override suspend fun saveActiveProfileId(profileId: String) {
        context.assistantProfilesDataStore.edit { prefs ->
            prefs[keyActiveProfileId] = profileId
        }
    }

    override suspend fun clear() {
        context.assistantProfilesDataStore.edit { prefs ->
            prefs[keyProfiles] = ""
            prefs[keyActiveProfileId] = AssistantProfile.DEFAULT_ID
        }
    }

    private fun ensureSingleDefault(profiles: List<AssistantProfile>): List<AssistantProfile> {
        if (profiles.isEmpty()) return listOf(AssistantProfile.default())
        val firstDefault = profiles.indexOfFirst { it.isDefault }
        val defaultIndex = if (firstDefault >= 0) firstDefault else 0

        return profiles.mapIndexed { index, profile ->
            profile.copy(isDefault = index == defaultIndex)
        }
    }
}
