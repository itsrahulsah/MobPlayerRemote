package com.mobplayer.remote.repository

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchPreferencesRepository(
    private val storage: LogStorage,
    val maxHistorySize: Int = DEFAULT_MAX_HISTORY
) {
    private val json = Json { ignoreUnknownKeys = true }

    constructor(
        context: Context,
        maxHistorySize: Int = DEFAULT_MAX_HISTORY
    ) : this(
        SharedPreferencesLogStorage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
        maxHistorySize
    )

    /**
     * Loads saved search history in MRU order (latest searched on top).
     */
    fun getSearchHistory(): List<String> {
        val rawJson = storage.getString(KEY_SEARCH_HISTORY) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(rawJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Adds a searched query to the top of the history list (latest searched on top).
     * If the query already exists, the older instance is removed and it is placed at index 0.
     */
    fun saveQuery(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getSearchHistory()

        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)

        val limited = current.take(maxHistorySize)
        try {
            storage.putString(KEY_SEARCH_HISTORY, json.encodeToString(limited))
        } catch (_: Exception) {
        }
        return limited
    }

    /**
     * Clears all saved search queries from storage.
     */
    fun clearHistory() {
        storage.remove(KEY_SEARCH_HISTORY)
    }

    /**
     * Removes an individual search query from history.
     */
    fun removeQuery(query: String): List<String> {
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        try {
            storage.putString(KEY_SEARCH_HISTORY, json.encodeToString(current))
        } catch (_: Exception) {
        }
        return current
    }

    companion object {
        const val PREFS_NAME = "mobplayer_search_prefs"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val DEFAULT_MAX_HISTORY = 30
    }
}
