package com.focuslock.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focuslock.core.FocusLog
import com.focuslock.data.model.AppSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.focusLockDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_lock")

/**
 * Persistence for the whole app state.
 *
 * Everything lives in a single JSON document so a mutation (for example consuming Cat's LIFE
 * and clearing the session) is atomic: there is no way to persist a used escape without also
 * ending the session, even if the process dies mid-write.
 */
class FocusStore(private val context: Context) : SnapshotStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val snapshotKey = stringPreferencesKey("snapshot_v1")

    override val data: Flow<AppSnapshot> = context.focusLockDataStore.data
        .catch { error ->
            if (error is IOException) {
                FocusLog.e("DataStore read failed, using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> decode(preferences[snapshotKey]) }

    override suspend fun update(transform: (AppSnapshot) -> AppSnapshot): AppSnapshot {
        var next: AppSnapshot? = null
        context.focusLockDataStore.edit { preferences ->
            val current = decode(preferences[snapshotKey])
            val updated = transform(current)
            next = updated
            preferences[snapshotKey] = json.encodeToString(AppSnapshot.serializer(), updated)
        }
        return checkNotNull(next) { "DataStore update did not complete" }
    }

    /**
     * One-off read used to seed the in-memory state at process start. The accessibility
     * service must know the active session before its first event, so a small synchronous
     * read at startup (a single preferences file) is preferable to missing a block.
     */
    override fun readBlocking(): AppSnapshot = runBlocking { data.first() }

    private fun decode(raw: String?): AppSnapshot {
        if (raw.isNullOrBlank()) return AppSnapshot()
        return try {
            json.decodeFromString(AppSnapshot.serializer(), raw)
        } catch (error: Exception) {
            FocusLog.e("Stored state could not be decoded, starting from defaults", error)
            AppSnapshot()
        }
    }
}
