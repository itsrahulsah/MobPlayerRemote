package com.mobplayer.remote

import com.mobplayer.remote.model.LogDirection
import com.mobplayer.remote.model.RemoteLogEntry
import com.mobplayer.remote.repository.LogPreferencesRepository
import com.mobplayer.remote.repository.LogStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogPreferencesRepositoryTest {

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
    private lateinit var repository: LogPreferencesRepository

    @Before
    fun setUp() {
        storage = InMemoryLogStorage()
        repository = LogPreferencesRepository(storage = storage)
    }

    @Test
    fun testEmptyStorageReturnsEmptyList() {
        val logs = repository.loadLogs()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun testSaveAndLoadLogsWithin24Hours() {
        val now = System.currentTimeMillis()
        val entry1 = RemoteLogEntry(
            id = 1L,
            timestamp = "10:00:00.000",
            direction = LogDirection.REQUEST,
            type = "COMMAND",
            payload = "{\"action\":\"PLAY\"}",
            epochMs = now - 1000L
        )
        val entry2 = RemoteLogEntry(
            id = 2L,
            timestamp = "10:01:00.000",
            direction = LogDirection.RESPONSE,
            type = "COMMAND_SUCCESS",
            payload = "PLAY",
            epochMs = now - 500L
        )

        repository.saveLogs(listOf(entry2, entry1), currentTimeMs = now)

        val loaded = repository.loadLogs(currentTimeMs = now)
        assertEquals(2, loaded.size)
        assertEquals(2L, loaded[0].id)
        assertEquals(1L, loaded[1].id)
        assertEquals(LogDirection.RESPONSE, loaded[0].direction)
        assertEquals(LogDirection.REQUEST, loaded[1].direction)
    }

    @Test
    fun testPruneLogsOlderThan24HoursOnSave() {
        val now = 1_000_000_000_000L
        val oneHourAgo = now - (1 * 60 * 60 * 1000L)
        val twentyThreeHoursAgo = now - (23 * 60 * 60 * 1000L)
        val twentyFiveHoursAgo = now - (25 * 60 * 60 * 1000L)
        val twoDaysAgo = now - (48 * 60 * 60 * 1000L)

        val recentEntry = RemoteLogEntry(
            id = 1L,
            timestamp = "12:00:00.000",
            direction = LogDirection.REQUEST,
            type = "PLAY",
            payload = "{}",
            epochMs = oneHourAgo
        )
        val boundaryEntry = RemoteLogEntry(
            id = 2L,
            timestamp = "01:00:00.000",
            direction = LogDirection.STATE,
            type = "STATE",
            payload = "{}",
            epochMs = twentyThreeHoursAgo
        )
        val expiredEntry1 = RemoteLogEntry(
            id = 3L,
            timestamp = "Yesterday",
            direction = LogDirection.REQUEST,
            type = "OLD",
            payload = "{}",
            epochMs = twentyFiveHoursAgo
        )
        val expiredEntry2 = RemoteLogEntry(
            id = 4L,
            timestamp = "TwoDaysAgo",
            direction = LogDirection.SYSTEM,
            type = "EXPIRED",
            payload = "{}",
            epochMs = twoDaysAgo
        )

        val allLogs = listOf(recentEntry, boundaryEntry, expiredEntry1, expiredEntry2)
        repository.saveLogs(allLogs, currentTimeMs = now)

        val loaded = repository.loadLogs(currentTimeMs = now)
        assertEquals(2, loaded.size)
        assertEquals(1L, loaded[0].id)
        assertEquals(2L, loaded[1].id)
    }

    @Test
    fun testPruneLogsOlderThan24HoursOnLoad() {
        val t0 = 1_000_000_000_000L
        val entry = RemoteLogEntry(
            id = 100L,
            timestamp = "12:00:00.000",
            direction = LogDirection.REQUEST,
            type = "PING",
            payload = "{}",
            epochMs = t0
        )

        // Save at t0
        repository.saveLogs(listOf(entry), currentTimeMs = t0)
        assertEquals(1, repository.loadLogs(currentTimeMs = t0).size)

        // Simulate reading 25 hours later -> must be pruned
        val tFuture = t0 + (25 * 60 * 60 * 1000L)
        val loadedFuture = repository.loadLogs(currentTimeMs = tFuture)
        assertTrue(loadedFuture.isEmpty())

        // Verify storage was cleaned up
        val rawAfterPrune = storage.getString(LogPreferencesRepository.KEY_LOGS)
        assertNotNull(rawAfterPrune)
        assertEquals("[]", rawAfterPrune)
    }

    @Test
    fun testPruneOldLogsExactCutoffBoundary() {
        val now = 1_000_000_000_000L
        val exactCutoff = now - LogPreferencesRepository.ONE_DAY_MS

        val validBoundaryEntry = RemoteLogEntry(
            id = 1L,
            timestamp = "Exact",
            direction = LogDirection.REQUEST,
            type = "EXACT",
            payload = "{}",
            epochMs = exactCutoff
        )
        val justExpiredEntry = RemoteLogEntry(
            id = 2L,
            timestamp = "ExpiredBy1Ms",
            direction = LogDirection.REQUEST,
            type = "EXPIRED",
            payload = "{}",
            epochMs = exactCutoff - 1L
        )

        val pruned = repository.pruneOldLogs(listOf(validBoundaryEntry, justExpiredEntry), currentTimeMs = now)
        assertEquals(1, pruned.size)
        assertEquals(1L, pruned[0].id)
    }

    @Test
    fun testClearLogs() {
        val entry = RemoteLogEntry(
            id = 1L,
            timestamp = "10:00:00.000",
            direction = LogDirection.REQUEST,
            type = "COMMAND",
            payload = "{}",
            epochMs = System.currentTimeMillis()
        )
        repository.saveLogs(listOf(entry))
        assertEquals(1, repository.loadLogs().size)

        repository.clearLogs()
        assertTrue(repository.loadLogs().isEmpty())
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun testMaxStoredLogsCapacity() {
        val smallCapacityRepo = LogPreferencesRepository(
            storage = storage,
            maxStoredLogs = 3
        )

        val now = System.currentTimeMillis()
        val entries = (1..10).map { i ->
            RemoteLogEntry(
                id = i.toLong(),
                timestamp = "$i",
                direction = LogDirection.REQUEST,
                type = "T$i",
                payload = "P$i",
                epochMs = now
            )
        }

        smallCapacityRepo.saveLogs(entries, currentTimeMs = now)
        val loaded = smallCapacityRepo.loadLogs(currentTimeMs = now)
        assertEquals(3, loaded.size)
        assertEquals(1L, loaded[0].id)
        assertEquals(2L, loaded[1].id)
        assertEquals(3L, loaded[2].id)
    }

    @Test
    fun testCorruptJsonInStorageHandlesGracefully() {
        storage.putString(LogPreferencesRepository.KEY_LOGS, "{ not valid json array }")
        val loaded = repository.loadLogs()
        assertTrue(loaded.isEmpty())
    }
}
