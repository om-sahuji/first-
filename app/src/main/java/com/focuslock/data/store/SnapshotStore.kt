package com.focuslock.data.store

import com.focuslock.data.model.AppSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Storage contract for the single documented app state.
 *
 * The repository depends on this interface rather than DataStore so session and escape logic
 * can be tested with an in-memory implementation. The Android implementation is
 * [FocusStore].
 */
interface SnapshotStore {

    val data: Flow<AppSnapshot>

    /** Atomically applies [transform] to the stored state and returns the result. */
    suspend fun update(transform: (AppSnapshot) -> AppSnapshot): AppSnapshot

    /** Synchronous seed used at process start, before the flow has emitted. */
    fun readBlocking(): AppSnapshot
}
