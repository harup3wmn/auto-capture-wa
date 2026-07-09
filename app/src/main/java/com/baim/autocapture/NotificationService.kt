package com.baim.autocapture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private var lastProcessedText: String = ""
        private var lastPostTime: Long = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        // Cek nama pengirim (title)
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        
        // Logika Pre-Filter Lokal (Diperketat untuk membuang obrolan biasa)
        // 1. Teks laporan lapangan pasti panjang (biasanya > 70 karakter)
        if (text.length < 70) return
        
        val lowerText = text.lowercase()
        
        // 2. Harus mengandung kata kunci formal
        val hasFormalKeyword = lowerText.contains("laporan") || lowerText.contains("realisasi") || lowerText.contains("pekerjaan")
        if (!hasFormalKeyword) return

        // 3. Cek kategori pekerjaan
        val isRow = lowerText.contains("row") || lowerText.contains("perabasan") || lowerText.contains("penebangan") || lowerText.contains("rabas") || lowerText.contains("tebang")
        val isPemeliharaan = lowerText.contains("pemeliharaan") || lowerText.contains("har") || lowerText.contains("sutm")
        val isInspeksi = lowerText.contains("inspeksi")
        
        if (isRow || isPemeliharaan || isInspeksi) {
            val postTime = sbn.postTime
            if (text == lastProcessedText && postTime == lastPostTime) return
            lastProcessedText = text
            lastPostTime = postTime

            Log.d("NotificationService", "Pre-Filter Passed: $text")
            
            val type = when {
                isRow -> "row"
                isPemeliharaan -> "pemeliharaan"
                isInspeksi -> "inspeksi"
                else -> "unknown"
            }
            
            processReport(text, type, title)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun processReport(message: String, reportType: String, senderName: String) {
        serviceScope.launch {
            val dataStore = DataStoreManager(applicationContext)
            val apiKey = dataStore.apiKeyFlow.first()
            val currentTime = System.currentTimeMillis()

            if (apiKey.isEmpty()) {
                // Lewati AI Filter jika API Key kosong, langsung masukkan antrean
                val entry = LogEntry(currentTime, currentTime, message, false, "", "PENDING", reportType)
                dataStore.addLogEntry(entry)
                showNotification("Laporan Baru Masuk (Tanpa Filter AI)", "Buka aplikasi untuk cek")
                return@launch
            }

            // AI Filter dengan Groq
            try {
                val prompt = "Apakah teks berikut adalah sebuah laporan kegiatan lapangan petugas PLN (seperti perabasan, inspeksi, pemeliharaan)? Jawab HANYA dengan 'YA' atau 'TIDAK'. Teks: \"$message\""
                
                val request = GroqRequest(
                    messages = listOf(GroqMessage(role = "user", content = prompt))
                )
                
                val response = NetworkClient.groqApi.checkMessage("Bearer $apiKey", request)
                if (response.isSuccessful) {
                    val aiAnswer = response.body()?.choices?.firstOrNull()?.message?.content?.trim()?.uppercase() ?: ""
                    
                    if (aiAnswer.contains("YA")) {
                        val entry = LogEntry(currentTime, currentTime, message, false, "", "PENDING", reportType)
                        dataStore.addLogEntry(entry)
                        showNotification("Laporan Lapangan Ditangkap", "Buka aplikasi untuk mengirim")
                    } else {
                        Log.d("NotificationService", "AI Filter menolak pesan: $aiAnswer")
                    }
                } else {
                    Log.e("NotificationService", "Groq API Error: ${response.errorBody()?.string()}")
                    // Fallback to PENDING
                    val entry = LogEntry(currentTime, currentTime, message, true, "Gagal filter AI", "PENDING", reportType)
                    dataStore.addLogEntry(entry)
                    showNotification("Laporan Masuk (Error AI)", "Buka aplikasi untuk cek")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to PENDING
                val entry = LogEntry(currentTime, currentTime, message, true, "Exception API", "PENDING", reportType)
                dataStore.addLogEntry(entry)
                showNotification("Laporan Masuk (Error Koneksi)", "Buka aplikasi untuk cek")
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_capture_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Auto Capture Notifications", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
