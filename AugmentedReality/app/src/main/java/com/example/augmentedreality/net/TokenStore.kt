package com.example.augmentedreality.net

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("auth")
private val KEY_TOKEN = stringPreferencesKey("jwt")
private val KEY_USERNAME = stringPreferencesKey("username")

data class StoredSession(
    val token: String,
    val username: String?
)

class TokenStore(private val context: Context) {

    val sessionFlow: Flow<StoredSession?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]?.let { StoredSession(it, prefs[KEY_USERNAME]) }
    }

    suspend fun save(token: String, username: String? = null) = context.dataStore.edit {
        it[KEY_TOKEN] = token
        if (username != null) {
            it[KEY_USERNAME] = username
        }
    }

    suspend fun currentSession(): StoredSession? = sessionFlow.firstOrNull()

    suspend fun clear() = context.dataStore.edit {
        it.remove(KEY_TOKEN)
        it.remove(KEY_USERNAME)
    }
}