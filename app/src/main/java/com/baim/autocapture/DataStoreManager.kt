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

    private fun cleanupOldLogs(logs: MutableList<LogEntry>) {
        val currentTime = System.currentTimeMillis()
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

        logs.removeAll { entry ->
            val age = currentTime - entry.timestamp
            val isResponded = entry.status != "PENDING"
            
            if (isResponded && age > threeDaysMs) {
                true
            } else if (!isResponded && age > sevenDaysMs) {
                true
            } else {
                false
            }
        }
    }

    suspend fun addLogEntry(entry: LogEntry) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LOG_HISTORY] ?: "[]"
            val type = object : TypeToken<MutableList<LogEntry>>() {}.type
            val currentLogs: MutableList<LogEntry> = gson.fromJson(currentJson, type)

            // Cek batasan duplikasi (maksimal 2 pesan yang 100% identik)
            val identicalCount = currentLogs.count { it.rawText.trim() == entry.rawText.trim() }
            if (identicalCount >= 2) {
                return@edit // Abaikan (auto cut) jika sudah ada 2 pesan yang sama persis
            }

            // Simpan max 200 log terbaru
            currentLogs.add(0, entry)
            
            cleanupOldLogs(currentLogs)
            
            if (currentLogs.size > 200) {
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
                cleanupOldLogs(currentLogs)
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
                cleanupOldLogs(currentLogs)
                preferences[LOG_HISTORY] = gson.toJson(currentLogs)
            }
        }
    }
}
