package com.example.aichalengeapp.agent.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private val Context.longTermMemoryDataStore by preferencesDataStore(name = "long_term_memory")

class DataStoreLongTermMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : LongTermMemoryStore {

    private val KEY = stringPreferencesKey("long_term_json")

    override suspend fun loadJson(): String {
        val prefs = context.longTermMemoryDataStore.data.first()
        return prefs[KEY].orEmpty()
    }

    override suspend fun saveJson(json: String) {
        context.longTermMemoryDataStore.edit { prefs ->
            prefs[KEY] = json
        }
    }

    override suspend fun clear() {
        context.longTermMemoryDataStore.edit { prefs ->
            prefs[KEY] = ""
        }
    }
}
