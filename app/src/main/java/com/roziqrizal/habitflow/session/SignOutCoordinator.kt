package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.data.account.LocalDataReset
import com.roziqrizal.habitflow.data.account.SessionEnding

/** Hasil usaha mengirim semua perubahan sebelum Keluar. */
sealed interface SyncOutcome {
    /** Semua yang punya antrean kirim sudah terkirim. */
    data object Done : SyncOutcome

    /** Tidak ada internet: perubahan tetap di HP. */
    data object Offline : SyncOutcome

    /** Server menolak atau sesi berakhir: tidak bisa mengirim sekarang. */
    data class Failed(val reason: String) : SyncOutcome
}

/** Bahan dialog Keluar: apa yang masih akan hilang setelah usaha terakhir mengirim. */
data class SignOutPlan(val pending: PendingChanges, val sync: SyncOutcome)

/**
 * Alur Keluar dan Hapus akun dari app (tahap 31H). Urutan Keluar sengaja begini:
 *
 *  1. Tandai "hapus semua data" **lebih dulu** ([LocalDataReset.scheduleWipe]): sesudah titik ini penghapusan pasti terjadi,
 *     walau proses mati di langkah berikutnya.
 *  2. Cabut token di server dan tutup sesi di HP. Sesi di HP selalu ditutup, juga saat server tidak terjangkau.
 *     Menutup sesi mematikan semua latar belakang ([SessionManager]).
 *  3. Mulai ulang proses; data dihapus di awal proses berikutnya, sebelum database dibuka.
 */
class SignOutCoordinator(
    private val changes: PendingChangeSource,
    private val syncEverything: suspend () -> SyncOutcome,
    private val session: SessionEnding,
    private val reset: LocalDataReset,
) {
    /**
     * Bahan dialog Keluar: coba kirim semua dulu (dengan internet), lalu hitung yang tersisa. Tanpa internet, langsung dihitung
     * dan dialog menampilkan peringatan keras.
     */
    suspend fun prepare(): SignOutPlan {
        val outcome = syncEverything()
        return SignOutPlan(PendingChanges.of(changes.counts(), changes.backupState()), outcome)
    }

    /** Keluar setelah pengguna mengonfirmasi dialog. Tidak kembali kalau proses berhasil dimulai ulang. */
    suspend fun signOut() {
        reset.scheduleWipe()
        session.revokeAndClear()
        reset.restartProcess()
    }

    /**
     * Hapus akun: server diminta lebih dulu dan **hanya kalau berhasil** data HP dihapus. Kata sandi salah, tanpa internet, dan
     * sebagainya tidak menghapus apa pun di HP.
     */
    suspend fun deleteAccount(password: String?, googleIdToken: String?): AccountResult<Unit> {
        val result = session.deleteAccount(password, googleIdToken)
        if (result is AccountResult.Success) {
            reset.scheduleWipe()
            reset.restartProcess()
        }
        return result
    }
}
