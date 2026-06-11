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
        val TOTAL_BULAN_INI = intPreferencesKey("total_bulan_ini")
        val TOTAL_TAHUN_INI = intPreferencesKey("total_tahun_ini")
        val LATEST_REPORT = stringPreferencesKey("latest_report")
    }

    // Default values based on requirements
    val totalBulanIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_BULAN_INI] ?: 16
    }

    val totalTahunIniFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TOTAL_TAHUN_INI] ?: 289
    }

    val latestReportFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LATEST_REPORT] ?: ""
    }

    suspend fun updateTotalBulanIni(newValue: Int) {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_BULAN_INI] = newValue
        }
    }

    suspend fun updateTotalTahunIni(newValue: Int) {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_TAHUN_INI] = newValue
        }
    }

    suspend fun addAccumulation(amount: Int) {
        context.dataStore.edit { preferences ->
            val currentBulan = preferences[TOTAL_BULAN_INI] ?: 16
            val currentTahun = preferences[TOTAL_TAHUN_INI] ?: 289
            preferences[TOTAL_BULAN_INI] = currentBulan + amount
            preferences[TOTAL_TAHUN_INI] = currentTahun + amount
        }
    }

    suspend fun saveLatestReport(report: String) {
        context.dataStore.edit { preferences ->
            preferences[LATEST_REPORT] = report
        }
    }
}
