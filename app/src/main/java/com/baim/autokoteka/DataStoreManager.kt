package com.baim.autokoteka

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "koteka_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val WAMENA_BULAN_INI = intPreferencesKey("wamena_bulan_ini")
        val WAMENA_TAHUN_INI = intPreferencesKey("wamena_tahun_ini")
        val YALIMO_BULAN_INI = intPreferencesKey("yalimo_bulan_ini")
        val YALIMO_TAHUN_INI = intPreferencesKey("yalimo_tahun_ini")
        val LATEST_REPORT = stringPreferencesKey("latest_report")
        
        // Variabel untuk Karantina & Duplikat
        val LATEST_RAW_TEXT = stringPreferencesKey("latest_raw_text")
        val PENDING_RAW_TEXT = stringPreferencesKey("pending_raw_text")
        val PENDING_REASON = stringPreferencesKey("pending_reason")
    }

    val wamenaBulanIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAMENA_BULAN_INI] ?: 21
    }
    val wamenaTahunIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAMENA_TAHUN_INI] ?: 294
    }

    val yalimoBulanIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[YALIMO_BULAN_INI] ?: 0
    }
    val yalimoTahunIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[YALIMO_TAHUN_INI] ?: 0
    }

    val latestReportFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LATEST_REPORT] ?: ""
    }

    val latestRawTextFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LATEST_RAW_TEXT] ?: ""
    }

    val pendingRawTextFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PENDING_RAW_TEXT] ?: ""
    }

    val pendingReasonFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PENDING_REASON] ?: ""
    }

    suspend fun savePendingReport(rawText: String, reason: String) {
        context.dataStore.edit { preferences ->
            preferences[PENDING_RAW_TEXT] = rawText
            preferences[PENDING_REASON] = reason
        }
    }

    suspend fun clearPendingReport() {
        context.dataStore.edit { preferences ->
            preferences[PENDING_RAW_TEXT] = ""
            preferences[PENDING_REASON] = ""
        }
    }

    suspend fun setLatestRawText(rawText: String) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_RAW_TEXT] = rawText
        }
    }

    suspend fun updateDataWamena(bulan: Int, tahun: Int) {
        context.dataStore.edit { preferences ->
            preferences[WAMENA_BULAN_INI] = bulan
            preferences[WAMENA_TAHUN_INI] = tahun
        }
    }

    suspend fun updateDataYalimo(bulan: Int, tahun: Int) {
        context.dataStore.edit { preferences ->
            preferences[YALIMO_BULAN_INI] = bulan
            preferences[YALIMO_TAHUN_INI] = tahun
        }
    }

    suspend fun addAccumulation(amount: Int, isYalimo: Boolean) {
        context.dataStore.edit { preferences ->
            if (isYalimo) {
                val currentBulan = preferences[YALIMO_BULAN_INI] ?: 0
                val currentTahun = preferences[YALIMO_TAHUN_INI] ?: 0
                preferences[YALIMO_BULAN_INI] = currentBulan + amount
                preferences[YALIMO_TAHUN_INI] = currentTahun + amount
            } else {
                val currentBulan = preferences[WAMENA_BULAN_INI] ?: 21
                val currentTahun = preferences[WAMENA_TAHUN_INI] ?: 294
                preferences[WAMENA_BULAN_INI] = currentBulan + amount
                preferences[WAMENA_TAHUN_INI] = currentTahun + amount
            }
        }
    }

    suspend fun saveLatestReport(report: String) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_REPORT] = report
        }
    }
}
