package com.baim.autokoteka

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
        private var lastProcessedTime: Long = 0
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        if (packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (text.contains("LAPORAN KEGIATAN PEKERJAAN TEAM ROW", ignoreCase = true) ||
            text.contains("PEROLEHAN PENEBANGAN SEJUMLAH", ignoreCase = true)) {
            
            val currentTime = System.currentTimeMillis()
            // Cegah double-counting jika pesan yang sama persis diproses dalam waktu berdekatan (kurang dari 10 detik)
            if (text == lastProcessedText && (currentTime - lastProcessedTime) < 10000) {
                return
            }
            lastProcessedText = text
            lastProcessedTime = currentTime

            Log.d("NotificationService", "Pesan laporan terdeteksi: $text")
            processReport(text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
    }

    private fun processReport(message: String) {
        serviceScope.launch {
            val parsedData = ReportParser.parseMessage(message)
            if (parsedData != null) {
                val dataStoreManager = DataStoreManager(applicationContext)
                
                val latestRawText = dataStoreManager.latestRawTextFlow.first()
                val validation = ReportParser.validateReport(parsedData, message, latestRawText)

                if (!validation.isValid) {
                    // Masukkan ke karantina
                    dataStoreManager.savePendingReport(message, validation.reason)
                    showWarningNotification()
                    return@launch
                }
                
                // Lulus Validasi -> Eksekusi normal
                dataStoreManager.setLatestRawText(message)
                dataStoreManager.addAccumulation(parsedData.tHariIni, parsedData.isYalimo)

                // Ambil nilai terbaru untuk ke-4 keranjang
                val wamenaBulan = dataStoreManager.wamenaBulanIniFlow.first()
                val wamenaTahun = dataStoreManager.wamenaTahunIniFlow.first()
                val yalimoBulan = dataStoreManager.yalimoBulanIniFlow.first()
                val yalimoTahun = dataStoreManager.yalimoTahunIniFlow.first()

                // Buat format report final
                val finalReport = ReportParser.formatReport(
                    parsedData, 
                    wamenaBulan, wamenaTahun, 
                    yalimoBulan, yalimoTahun
                )
                
                dataStoreManager.saveLatestReport(finalReport)
                showCopyNotification(finalReport)
            }
        }
    }

    private fun showWarningNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_koteka_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Koteka Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Laporan Tertahan!")
            .setContentText("Ditemukan keanehan pada laporan. Klik untuk meninjau.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(888, notification) // ID unik untuk warning
    }

    private fun showCopyNotification(reportText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_koteka_channel"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Koteka Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val copyIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_COPY_TEXT"
            putExtra("EXTRA_TEXT", reportText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Laporan Baru Siap!")
            .setContentText("Klik di sini untuk meng-copy teks ke Clipboard.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
