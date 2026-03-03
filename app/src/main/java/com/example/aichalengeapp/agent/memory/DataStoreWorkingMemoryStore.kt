package com.example.aichalengeapp.agent.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val Context.workingMemoryDataStore by preferencesDataStore(name = "working_memory")

class DataStoreWorkingMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkingMemoryStore {

    private val KEY = stringPreferencesKey("working_json")

    override suspend fun loadJson(): String {
        val prefs = context.workingMemoryDataStore.data.first()
        return prefs[KEY].orEmpty()
    }

    override suspend fun saveJson(json: String) {
        context.workingMemoryDataStore.edit { prefs ->
            prefs[KEY] = json
        }
    }

    override suspend fun clear() {
        context.workingMemoryDataStore.edit { prefs ->
            prefs[KEY] = ""
        }
    }
}
