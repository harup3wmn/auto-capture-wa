package com.baim.autocapture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoCaptureApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val componentName = android.content.ComponentName(this, NotificationService::class.java)
            android.service.notification.NotificationListenerService.requestRebind(componentName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AutoCaptureApp() {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val logHistory by dataStoreManager.logHistoryFlow.collectAsState(initial = emptyList())
    val apiKey by dataStoreManager.apiKeyFlow.collectAsState(initial = "")
    val webhookUrl by dataStoreManager.webhookUrlFlow.collectAsState(initial = "")

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("ALL") }

    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    var isBatteryOptimized by remember { mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto-Capture WA",
                style = MaterialTheme.typography.headlineMedium,
            )
            Row {
                Button(onClick = { showManualDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("➕")
                }
                Button(onClick = { showSettingsDialog = true }) {
                    Text("⚙️")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCheckSection(context)

        if (isBatteryOptimized) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("⚠️ Matikan Penghemat Baterai (Penting)")
            }
            Text("Agar aplikasi tidak mati sendiri di background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Antrean Laporan",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("ALL" to "Semua", "PENDING" to "Tertunda", "SENT" to "Terkirim", "REJECTED" to "Ditolak")
            filters.forEach { (status, label) ->
                val isSelected = filterStatus == status
                Button(
                    onClick = { filterStatus = status },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val filteredLogs = if (filterStatus == "ALL") logHistory else logHistory.filter { it.status == filterStatus }

        if (filteredLogs.isEmpty()) {
            Text("Belum ada laporan yang ditangkap.", style = MaterialTheme.typography.bodyMedium)
        } else {
            filteredLogs.forEach { entry ->
                LogEntryCard(
                    entry = entry,
                    dataStoreManager = dataStoreManager,
                    context = context,
                    coroutineScope = coroutineScope,
                    webhookUrl = webhookUrl
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showManualDialog) {
        var manualText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Input Laporan Manual") },
            text = {
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text("Paste / Ketik Laporan Disini") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (manualText.isNotBlank()) {
                        coroutineScope.launch {
                            val entry = LogEntry(
                                id = System.currentTimeMillis(),
                                timestamp = System.currentTimeMillis(),
                                rawText = manualText.trim(),
                                isAnomaly = false,
                                anomalyReason = "",
                                status = "PENDING",
                                reportType = "auto"
                            )
                            dataStoreManager.addLogEntry(entry)
                            showManualDialog = false
                        }
                    } else {
                        Toast.makeText(context, "Pesan tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showSettingsDialog) {
        var editApiKey by remember { mutableStateOf(apiKey) }
        var editWebhookUrl by remember { mutableStateOf(webhookUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Pengaturan API & Webhook") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = editApiKey,
                        onValueChange = { editApiKey = it },
                        label = { Text("Groq API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editWebhookUrl,
                        onValueChange = { editWebhookUrl = it },
                        label = { Text("URL Webhook Apps Script") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            dataStoreManager.saveApiKey(editApiKey)
                            dataStoreManager.saveWebhookUrl(editWebhookUrl)
                            showSettingsDialog = false
                            Toast.makeText(context, "Pengaturan Disimpan!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun PermissionCheckSection(context: Context) {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
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
                Text("Izin Notifikasi Belum Aktif", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) {
                    Text("Buka Pengaturan")
                }
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Izin Notifikasi Aktif ✓", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry,
    dataStoreManager: DataStoreManager,
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    webhookUrl: String
) {
    val dateString = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(entry.timestamp))

    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(entry.rawText) }
    var isSending by remember { mutableStateOf(false) }

    val (containerColor, contentColor) = when (entry.status) {
        "PENDING" -> Pair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        "SENT" -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
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

            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { isEditing = false }) { Text("Batal") }
                    Button(onClick = {
                        coroutineScope.launch {
                            dataStoreManager.updateLogText(entry.id, editedText)
                            isEditing = false
                        }
                    }) { Text("Simpan") }
                }
            } else {
                Text(entry.rawText, style = MaterialTheme.typography.bodySmall, color = contentColor)
            }

            if (entry.status == "PENDING" && !isEditing) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { isEditing = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor)
                    ) {
                        Text("✏️ Edit")
                    }

                    Row {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    dataStoreManager.updateLogStatus(entry.id, "REJECTED")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Tolak")
                        }
                        
                        Button(
                            onClick = {
                                if (webhookUrl.isEmpty()) {
                                    Toast.makeText(context, "URL Webhook kosong! Isi di Pengaturan.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                
                                isSending = true
                                coroutineScope.launch {
                                    try {
                                        val payload = WebhookPayload(pesan = entry.rawText)
                                        val response = NetworkClient.webhookApi.sendReport(webhookUrl, payload)
                                        if (response.isSuccessful) {
                                            val body = response.body()
                                            if (body?.status == "success") {
                                                dataStoreManager.updateLogStatus(entry.id, "SENT")
                                                Toast.makeText(context, "Berhasil Dikirim!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val errorMsg = body?.message ?: "Terjadi kesalahan di Chatbot"
                                                Toast.makeText(context, "Chatbot Error: $errorMsg", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Gagal: ${response.code()}", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error Koneksi", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isSending = false
                                    }
                                }
                            },
                            enabled = !isSending
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading...")
                            } else {
                                Text("Kirim 🚀")
                            }
                        }
                    }
                }
            }
        }
    }
}
