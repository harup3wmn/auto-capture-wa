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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // Hanya proses notifikasi dari WhatsApp
        if (packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Mengecek apakah mengandung teks kunci
        if (text.contains("LAPORAN KEGIATAN PEKERJAAN TEAM ROW", ignoreCase = true) ||
            text.contains("PEROLEHAN PENEBANGAN SEJUMLAH", ignoreCase = true)) {
            
            Log.d("NotificationService", "Pesan laporan terdeteksi: $text")
            processReport(text)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Tidak perlu implementasi khusus
    }

    private fun processReport(message: String) {
        serviceScope.launch {
            val parsedData = ReportParser.parseMessage(message)
            if (parsedData != null) {
                val dataStoreManager = DataStoreManager(applicationContext)
                
                // Tambahkan akumulasi
                dataStoreManager.addAccumulation(parsedData.tHariIni)

                // Ambil nilai terbaru
                val totalBulanIni = dataStoreManager.totalBulanIniFlow.first()
                val totalTahunIni = dataStoreManager.totalTahunIniFlow.first()

                // Buat format report final
                val finalReport = ReportParser.formatReport(parsedData, totalBulanIni, totalTahunIni)
                
                // Simpan ke local storage agar tampil di UI MainActivity
                dataStoreManager.saveLatestReport(finalReport)

                // Tampilkan notifikasi untuk kemudahan meng-copy
                showCopyNotification(finalReport)
            }
        }
    }

    private fun showCopyNotification(reportText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "auto_koteka_channel"

        // Buat channel (wajib untuk Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Koteka Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intent untuk men-copy teks saat notifikasi diklik
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
