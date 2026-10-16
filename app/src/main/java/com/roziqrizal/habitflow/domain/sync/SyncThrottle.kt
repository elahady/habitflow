package com.roziqrizal.habitflow.domain.sync

/**
 * Pembatas sinkron otomatis saat app kembali tampil (tahap 33 langkah 3c): perubahan dari web harus terlihat tanpa
 * menutup paksa app, tapi berpindah-pindah app berkali-kali dalam semenit tidak boleh memukul server tiap kali.
 * [tryAcquire] mengizinkan satu sinkron, lalu menolak sampai [minIntervalMillis] lewat. Percobaan yang gagal tetap
 * dihitung, jadi HP yang sedang offline tidak mencoba ulang setiap kali app dibuka.
 */
class SyncThrottle(
    private val minIntervalMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastAt: Long? = null

    fun tryAcquire(): Boolean {
        val t = now()
        val last = lastAt
        if (last != null && t - last in 0 until minIntervalMillis) return false
        lastAt = t
        return true
    }
}
