package com.baim.autokoteka

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoKotekaApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_COPY_TEXT") {
            val textToCopy = intent.getStringExtra("EXTRA_TEXT")
            if (!textToCopy.isNullOrEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Koteka Report", textToCopy)
                clipboard.setPrimaryClip(clip)
    val isGranted = enabledListeners != null && enabledListeners.contains(packageName)

    if (!isGranted) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Izin Notifikasi Belum Aktif",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aplikasi butuh akses untuk membaca notifikasi WhatsApp.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                ) {
                    Text("Buka Pengaturan")
                }
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Izin Notifikasi Aktif ✓",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry, 
    dataStoreManager: DataStoreManager, 
    context: Context, 
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val dateString = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(entry.timestamp))

    val (containerColor, contentColor) = when {
        entry.status == "PENDING" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        entry.status == "PROCESSED" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateString, style = MaterialTheme.typography.labelSmall, color = contentColor)
                Text(entry.status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = contentColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (entry.isAnomaly) {
                Text(
                    text = "⚠️ Anomali: ${entry.anomalyReason}", 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = contentColor)

            if (entry.status == "PENDING") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                dataStoreManager.updateLogStatus(entry.id, "REJECTED")
                                Toast.makeText(context, "Laporan Ditolak", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Tolak")
                    }
                    
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val parsedData = ReportParser.parseMessage(entry.rawText)
                                if (parsedData != null) {
                                    dataStoreManager.setLatestRawText(entry.rawText)
                                    dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                    
                                    val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                                    val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                                    val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                                    val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                                    
                                    val finalReport = ReportParser.formatReport(
                                        parsedData, wamenaBulan, wamenaTahun, yalimoBulan, yalimoTahun
                                    )
                                    dataStoreManager.saveLatestReport(finalReport)
                                }
                                dataStoreManager.updateLogStatus(entry.id, "APPROVED")
                                Toast.makeText(context, "Laporan Disetujui!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Setujui")
                    }
                }
            }
        }
    }
}
