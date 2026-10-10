package com.mobplayer.remote

import com.mobplayer.remote.repository.LogStorage
import com.mobplayer.remote.repository.SearchPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchPreferencesRepositoryTest {

    private class InMemoryLogStorage : LogStorage {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private lateinit var storage: InMemoryLogStorage
    private lateinit var repository: SearchPreferencesRepository

    @Before
    fun setUp() {
        storage = InMemoryLogStorage()
        repository = SearchPreferencesRepository(storage = storage)
    }

    @Test
    fun testEmptyHistoryReturnsEmptyList() {
        assertTrue(repository.getSearchHistory().isEmpty())
    }

    @Test
    fun testSaveQueryPutsLatestOnTop() {
        repository.saveQuery("Matrix")
        repository.saveQuery("Inception")
        repository.saveQuery("Interstellar")

        val history = repository.getSearchHistory()
        assertEquals(3, history.size)
        assertEquals("Interstellar", history[0])
        assertEquals("Inception", history[1])
        assertEquals("Matrix", history[2])
    }

    @Test
    fun testDuplicateQueryMovesToTop() {
        repository.saveQuery("Matrix")
        repository.saveQuery("Inception")
        // Repeat Matrix -> must move to top without duplicate
        repository.saveQuery("Matrix")

        val history = repository.getSearchHistory()
        assertEquals(2, history.size)
        assertEquals("Matrix", history[0])
        assertEquals("Inception", history[1])
    }

    @Test
    fun testCaseInsensitiveDeduplication() {
        repository.saveQuery("youtube trailer")
        repository.saveQuery("YouTube Trailer")

        val history = repository.getSearchHistory()
        assertEquals(1, history.size)
        assertEquals("YouTube Trailer", history[0])
    }

    @Test
    fun testBlankQueriesAreIgnored() {
        repository.saveQuery("")
        repository.saveQuery("   ")

        assertTrue(repository.getSearchHistory().isEmpty())
    }

    @Test
    fun testMaxHistoryLimit() {
        val limitedRepo = SearchPreferencesRepository(storage = storage, maxHistorySize = 3)
        limitedRepo.saveQuery("A")
        limitedRepo.saveQuery("B")
        limitedRepo.saveQuery("C")
        limitedRepo.saveQuery("D")
        limitedRepo.saveQuery("E")

        val history = limitedRepo.getSearchHistory()
        assertEquals(3, history.size)
        assertEquals("E", history[0])
        assertEquals("D", history[1])
        assertEquals("C", history[2])
    }

    @Test
    fun testClearHistory() {
        repository.saveQuery("Avatar")
        repository.saveQuery("Titanic")
        assertEquals(2, repository.getSearchHistory().size)

        repository.clearHistory()
        assertTrue(repository.getSearchHistory().isEmpty())
    }

    @Test
    fun testRemoveIndividualQuery() {
        repository.saveQuery("Alpha")
        repository.saveQuery("Beta")
        repository.saveQuery("Gamma")

        repository.removeQuery("Beta")
        val history = repository.getSearchHistory()
        assertEquals(2, history.size)
        assertEquals("Gamma", history[0])
        assertEquals("Alpha", history[1])
    }

    @Test
    fun testCorruptJsonInStorageHandlesGracefully() {
        storage.putString(SearchPreferencesRepository.KEY_SEARCH_HISTORY, "invalid json")
        assertTrue(repository.getSearchHistory().isEmpty())
    }

    @Test
    fun testFilterAndRankExactMatchOnTop() {
        val history = listOf("Avatar: The Way of Water", "Avatar", "The Last Airbender: Avatar")
        val ranked = SearchPreferencesRepository.filterAndRankSuggestions("avatar", history)

        assertEquals("Avatar", ranked[0])
        assertEquals("Avatar: The Way of Water", ranked[1])
        assertEquals("The Last Airbender: Avatar", ranked[2])
    }

    @Test
    fun testFilterAndRankPrefixMatchAboveSubstringMatch() {
        val history = listOf("Best of YouTube", "YouTube Music", "About YouTube")
        val ranked = SearchPreferencesRepository.filterAndRankSuggestions("you", history)

        assertEquals("YouTube Music", ranked[0])
        assertTrue(ranked.contains("Best of YouTube"))
        assertTrue(ranked.contains("About YouTube"))
    }

    @Test
    fun testFilterAndRankWordPrefixMatch() {
        val history = listOf("Signatures of Earth", "Top 10 Nature Videos")
        val ranked = SearchPreferencesRepository.filterAndRankSuggestions("nat", history)

        // "Nature" is a word prefix, whereas "Signatures" contains "nat" inside the word
        assertEquals("Top 10 Nature Videos", ranked[0])
        assertEquals("Signatures of Earth", ranked[1])
    }

    @Test
    fun testFilterAndRankMultiKeywordTokens() {
        val history = listOf("4K Nature Relaxing", "4K HDR Movies", "Nature Sound")
        val ranked = SearchPreferencesRepository.filterAndRankSuggestions("nature 4k", history)

        assertEquals(1, ranked.size)
        assertEquals("4K Nature Relaxing", ranked[0])
    }

    @Test
    fun testFilterAndRankPreservesLatestOnTopForEqualScores() {
        // Both start with "mo" (same match quality score 80)
        val history = listOf("Modern Family", "Movie Trailers")
        val ranked = SearchPreferencesRepository.filterAndRankSuggestions("mo", history)

        // Preserves MRU: Modern Family was searched more recently so stays on top
        assertEquals("Modern Family", ranked[0])
        assertEquals("Movie Trailers", ranked[1])
    }

    @Test
    fun testFilterAndRankBlankQueryReturnsFullHistory() {
        val history = listOf("Alpha", "Beta", "Gamma")
        val rankedBlank = SearchPreferencesRepository.filterAndRankSuggestions("", history)
        val rankedSpaces = SearchPreferencesRepository.filterAndRankSuggestions("   ", history)

        assertEquals(history, rankedBlank)
        assertEquals(history, rankedSpaces)
    }
}
