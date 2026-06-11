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
                Toast.makeText(this, "Teks Laporan Berhasil Di-copy!", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun AutoKotekaApp() {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val wamenaBulan by dataStoreManager.wamenaBulanIniFlow.collectAsState(initial = 21)
    val wamenaTahun by dataStoreManager.wamenaTahunIniFlow.collectAsState(initial = 294)
    val yalimoBulan by dataStoreManager.yalimoBulanIniFlow.collectAsState(initial = 0)
    val yalimoTahun by dataStoreManager.yalimoTahunIniFlow.collectAsState(initial = 0)
    
    val latestReport by dataStoreManager.latestReportFlow.collectAsState(initial = "")
    val pendingRawText by dataStoreManager.pendingRawTextFlow.collectAsState(initial = "")
    val pendingReason by dataStoreManager.pendingReasonFlow.collectAsState(initial = "")

    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Auto Koteka",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        PermissionCheckSection(context)

        Spacer(modifier = Modifier.height(16.dp))

        if (pendingRawText.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Tinjauan Laporan Diperlukan!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pendingReason,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Isi Laporan Mentah:\n$pendingRawText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    dataStoreManager.clearPendingReport()
                                    Toast.makeText(context, "Laporan Dibuang", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Abaikan Laporan")
                        }
                        
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val parsedData = ReportParser.parseMessage(pendingRawText)
                                    if (parsedData != null) {
                                        dataStoreManager.setLatestRawText(pendingRawText)
                                        dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)
                                        
                                        // Update laporan format akhir untuk di-copy
                                        val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                                        val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                                        val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                                        val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()
                                        
                                        val finalReport = ReportParser.formatReport(
                                            parsedData, wamenaBulan, wamenaTahun, yalimoBulan, yalimoTahun
                                        )
                                        dataStoreManager.saveLatestReport(finalReport)
                                    }
                                    dataStoreManager.clearPendingReport()
                                    Toast.makeText(context, "Laporan Ditambahkan!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Tetap Setujui")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Laporan Terakhir",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (latestReport.isEmpty()) {
                    Text("Belum ada laporan yang diekstrak.")
                } else {
                    Text(
                        text = latestReport,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Koteka Report", latestReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Berhasil di-copy!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy to Clipboard")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { showEditDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Edit Data Akumulasi")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Tampilan Data Akumulasi
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Wamena Kota", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: $wamenaBulan | Tahun Ini: $wamenaTahun")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Yalimo", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: $yalimoBulan | Tahun Ini: $yalimoTahun")
                Spacer(modifier = Modifier.height(8.dp))
                Text("UP3 Wamena (Total)", fontWeight = FontWeight.Bold)
                Text("Bulan Ini: ${wamenaBulan + yalimoBulan} | Tahun Ini: ${wamenaTahun + yalimoTahun}")
            }
        }
    }

    if (showEditDialog) {
        var editWamenaBulan by remember { mutableStateOf(wamenaBulan.toString()) }
        var editWamenaTahun by remember { mutableStateOf(wamenaTahun.toString()) }
        var editYalimoBulan by remember { mutableStateOf(yalimoBulan.toString()) }
        var editYalimoTahun by remember { mutableStateOf(yalimoTahun.toString()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Akumulasi") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Wamena Kota", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editWamenaBulan,
                        onValueChange = { editWamenaBulan = it },
                        label = { Text("Bulan Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editWamenaTahun,
                        onValueChange = { editWamenaTahun = it },
                        label = { Text("Tahun Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Yalimo", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = editYalimoBulan,
                        onValueChange = { editYalimoBulan = it },
                        label = { Text("Bulan Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = editYalimoTahun,
                        onValueChange = { editYalimoTahun = it },
                        label = { Text("Tahun Ini") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val wb = editWamenaBulan.toIntOrNull() ?: wamenaBulan
                            val wt = editWamenaTahun.toIntOrNull() ?: wamenaTahun
                            val yb = editYalimoBulan.toIntOrNull() ?: yalimoBulan
                            val yt = editYalimoTahun.toIntOrNull() ?: yalimoTahun
                            
                            dataStoreManager.updateDataWamena(wb, wt)
                            dataStoreManager.updateDataYalimo(yb, yt)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
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
