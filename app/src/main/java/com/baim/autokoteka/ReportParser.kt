package com.baim.autokoteka

import android.util.Log

object ReportParser {

    private val regexTanggal = Regex("𝙃𝘼𝙍𝙄 / 𝙏𝘼𝙉𝙂𝙂𝘼𝙇 : (.*)")
    private val regexTHariIni = Regex("PEROLEHAN PENEBANGAN SEJUMLAH = (\\d+)")
    private val regexPK = Regex("1\\. Penebangan pohon 5 - 20 Cm = (\\d+)")
    private val regexPS = Regex("2\\. Penebangan pohon 20 - 30 Cm = (\\d+)")
    private val regexPB1 = Regex("3\\. Penebangan pohon 30 - 50 Cm = (\\d+)")
    private val regexPB2 = Regex("4\\. Penebangan pohon 50 > Cm = (\\d+)")

    data class ParsedData(
        val tanggal: String,
        val tHariIni: Int,
        val pk: Int,
        val ps: Int,
        val pb: Int
    )

    fun parseMessage(message: String): ParsedData? {
        Log.d("ReportParser", "Parsing message: $message")
        
        val matchTanggal = regexTanggal.find(message)
        val matchTHariIni = regexTHariIni.find(message)
        
        // We only require Tanggal and T_Hari_Ini to be valid to consider it a report.
        if (matchTanggal == null || matchTHariIni == null) {
            Log.d("ReportParser", "Failed to parse required fields (Tanggal / T_Hari_Ini)")
            return null
        }

        val tanggal = matchTanggal.groupValues[1].trim()
        val tHariIni = matchTHariIni.groupValues[1].toIntOrNull() ?: 0

        val pk = regexPK.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ps = regexPS.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb1 = regexPB1.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pb2 = regexPB2.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val pb = pb1 + pb2

        return ParsedData(tanggal, tHariIni, pk, ps, pb)
    }

    fun formatReport(data: ParsedData, totalBulanIni: Int, totalTahunIni: Int): String {
        return """
*Laporan Realisasi Tebang Program KOTEKA 2026 UP3 Wamena*
Hari / ${data.tanggal}

UP3 Wamena : PK ${data.pk} btg / PS ${data.ps} btg / PB ${data.pb} btg / T. Hari ini ${data.tHariIni} btg / T. Bulan Ini $totalBulanIni btg / T. s.d. hari ini $totalTahunIni btg

Perhitungan Realisasi mulai dihitung sejak 1 Januari 2026, semua foto penebangan difoto (1 foto untuk 1 titik)

1. ULP Wamena Kota : PK ${data.pk} btg / PS ${data.ps} btg / PB ${data.pb} btg / T. Hari ini ${data.tHariIni} btg / T. Bulan Ini $totalBulanIni btg / T. s.d. hari ini $totalTahunIni btg
- PT Nusa Daya : PK ${data.pk} btg / PS ${data.ps} btg / PB ${data.pb} btg / T. Hari ini ${data.tHariIni} btg / T. Bulan Ini $totalBulanIni btg / T. s.d. hari ini $totalTahunIni btg

2. ULP Yalimo : PK 0 btg / PS 0 btg / PB 0 btg / T. Hari ini 0 btg / T. Bulan Ini 0 btg / T. s.d. hari ini 0 btg
* PT  : PK .... btg / PS ..... btg / PB ..... btg / T. Hari ini .... btg / T. Bulan Ini ..... btg / T. s.d. hari ini ..... btg

Ket : 
PK : Pohon Kecil
PS : Pohon Sedang
PB : Pohon Besar
T   : Total
""".trimIndent()
    }
}
