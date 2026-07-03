package com.baim.autocapture

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val rawText: String,
    val isAnomaly: Boolean = false,
    val anomalyReason: String = "",
    var status: String = "PENDING", // PENDING, SENT, REJECTED
    var reportType: String = "Unknown" // row, pemeliharaan, inspeksi
)

class DataStoreManager(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        private val LOG_HISTORY = stringPreferencesKey("log_history")
    }

    private val gson = Gson()

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: ""
    }

    val webhookUrlFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WEBHOOK_URL] ?: ""
    }

    val logHistoryFlow: Flow<List<LogEntry>> = context.dataStore.data.map { preferences ->
        val json = preferences[LOG_HISTORY] ?: "[]"
        val type = object : TypeToken<List<LogEntry>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun saveWebhookUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBHOOK_URL] = url
        }
    }

    suspend fun addLogEntry(entry: LogEntry) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOG_HISTORY] ?: "[]"
            val type = object : TypeToken<MutableList<LogEntry>>() {}.type
            val currentLogs: MutableList<LogEntry> = gson.fromJson(currentJson, type)

            // Simpan max 50 log terbaru
            currentLogs.add(0, entry)
            if (currentLogs.size > 50) {
                currentLogs.removeAt(currentLogs.size - 1)
            }

            preferences[LOG_HISTORY] = gson.toJson(currentLogs)
        }
    }

    suspend fun updateLogStatus(id: Long, newStatus: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOG_HISTORY] ?: "[]"
            val type = object : TypeToken<MutableList<LogEntry>>() {}.type
            val currentLogs: MutableList<LogEntry> = gson.fromJson(currentJson, type)

            val logIndex = currentLogs.indexOfFirst { it.id == id }
            if (logIndex != -1) {
                currentLogs[logIndex].status = newStatus
                preferences[LOG_HISTORY] = gson.toJson(currentLogs)
            }
        }
    }
    
    suspend fun updateLogText(id: Long, newText: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOG_HISTORY] ?: "[]"
            val type = object : TypeToken<MutableList<LogEntry>>() {}.type
            val currentLogs: MutableList<LogEntry> = gson.fromJson(currentJson, type)

            val logIndex = currentLogs.indexOfFirst { it.id == id }
            if (logIndex != -1) {
                // Buat copy baru dari LogEntry karena data class immutable jika property nya val
                currentLogs[logIndex] = currentLogs[logIndex].copy(rawText = newText)
                preferences[LOG_HISTORY] = gson.toJson(currentLogs)
            }
        }
    }
}
