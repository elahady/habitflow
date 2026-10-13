package com.roziqrizal.habitflow.data.account

/**
 * Penghapusan data HP ini yang dipicu alur akun (tahap 31H). Implementasi app menulis penanda lalu mematikan proses; data
 * baru benar-benar terhapus di awal proses berikutnya (lihat `PendingWipe`). Dipisah dari [AccountManager] supaya tes
 * bisa mencatat urutannya tanpa Android.
 */
interface LocalDataReset {
    /** Menandai penghapusan semua data lokal. [thenSignIn] diisi saat ganti akun: sesi akun baru dipulihkan sesudahnya. */
    fun scheduleWipe(thenSignIn: Account? = null)

    /** Mematikan proses app dan membuka lagi layar utama. Tidak kembali. */
    fun restartProcess()
}
