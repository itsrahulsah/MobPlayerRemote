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

    /**
     * Filters and ranks search suggestions using keyword match quality:
     * - Exact matches (highest priority)
     * - Suggestions starting with query
     * - Suggestions where a word starts with query
     * - Multi-keyword token prefix matches
     * - Substring matches
     * Ties preserve MRU order (latest searched on top).
     */
    fun filterAndRank(query: String, history: List<String> = getSearchHistory()): List<String> {
        return filterAndRankSuggestions(query, history)
    }

    companion object {
        const val PREFS_NAME = "mobplayer_search_prefs"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val DEFAULT_MAX_HISTORY = 30

        /**
         * Key-based filtering and ranking for optimal search suggestions.
         * Ranks the best matching suggestions to the top while preserving
         * latest-on-top ordering for equivalent match scores.
         */
        fun filterAndRankSuggestions(query: String, history: List<String>): List<String> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return history

            val lowerQuery = trimmed.lowercase()
            val queryTokens = lowerQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }

            return history.mapNotNull { candidate ->
                val lowerCandidate = candidate.lowercase()
                val matchesAllTokens = queryTokens.all { token -> lowerCandidate.contains(token) }
                if (!matchesAllTokens) return@mapNotNull null

                val candidateWords = lowerCandidate.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }

                val score = when {
                    lowerCandidate == lowerQuery -> 100
                    lowerCandidate.startsWith(lowerQuery) -> 80
                    candidateWords.any { it.startsWith(lowerQuery) } -> 70
                    queryTokens.isNotEmpty() && lowerCandidate.startsWith(queryTokens[0]) -> 60
                    queryTokens.all { token -> candidateWords.any { word -> word.startsWith(token) } } -> 50
                    lowerCandidate.contains(lowerQuery) -> 40
                    else -> 20
                }
                candidate to score
            }
            .sortedWith(compareByDescending { it.second })
            .map { it.first }
        }
    }
}
