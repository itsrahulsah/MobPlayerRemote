package com.mobplayer.remote.repository

import android.content.Context
import android.content.SharedPreferences
import com.mobplayer.remote.model.RemoteLogEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface LogStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesLogStorage(private val sharedPreferences: SharedPreferences) : LogStorage {
    override fun getString(key: String): String? = sharedPreferences.getString(key, null)
    override fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
}

class LogPreferencesRepository(
    private val storage: LogStorage,
    val retentionPeriodMs: Long = ONE_DAY_MS,
    val maxStoredLogs: Int = MAX_STORED_LOGS
) {
    private val json = Json { ignoreUnknownKeys = true }

    constructor(
        context: Context,
        retentionPeriodMs: Long = ONE_DAY_MS,
        maxStoredLogs: Int = MAX_STORED_LOGS
    ) : this(
        SharedPreferencesLogStorage(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
        retentionPeriodMs,
        maxStoredLogs
    )

    /**
     * Loads logs from SharedPreferences, automatically filtering out entries older than retentionPeriodMs (1 day).
     */
    fun loadLogs(currentTimeMs: Long = System.currentTimeMillis()): List<RemoteLogEntry> {
        val rawJson = storage.getString(KEY_LOGS) ?: return emptyList()
        return try {
            val decoded = json.decodeFromString<List<RemoteLogEntry>>(rawJson)
            val pruned = pruneOldLogs(decoded, currentTimeMs)
            if (pruned.size != decoded.size) {
                saveLogsInternal(pruned)
            }
            pruned
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Saves logs to SharedPreferences after pruning entries older than retentionPeriodMs (1 day)
     * and limiting to maxStoredLogs.
     */
    fun saveLogs(logs: List<RemoteLogEntry>, currentTimeMs: Long = System.currentTimeMillis()) {
        val pruned = pruneOldLogs(logs, currentTimeMs)
        saveLogsInternal(pruned)
    }

    private fun saveLogsInternal(logs: List<RemoteLogEntry>) {
        val limited = logs.take(maxStoredLogs)
        try {
            val encoded = json.encodeToString(limited)
            storage.putString(KEY_LOGS, encoded)
        } catch (_: Exception) {
        }
    }

    /**
     * Filters out any log entry whose age exceeds retentionPeriodMs (default 24 hours).
     */
    fun pruneOldLogs(logs: List<RemoteLogEntry>, currentTimeMs: Long = System.currentTimeMillis()): List<RemoteLogEntry> {
        val cutoff = currentTimeMs - retentionPeriodMs
        return logs.filter { entry -> entry.epochMs >= cutoff }
    }

    /**
     * Clears all persisted logs from SharedPreferences.
     */
    fun clearLogs() {
        storage.remove(KEY_LOGS)
    }

    companion object {
        const val PREFS_NAME = "mobplayer_tv_logs_prefs"
        const val KEY_LOGS = "saved_tv_logs"
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        const val MAX_STORED_LOGS = 500
    }
}
