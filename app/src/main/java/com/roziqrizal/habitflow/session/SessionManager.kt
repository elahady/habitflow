package com.roziqrizal.habitflow.session

import android.content.Context
import com.roziqrizal.habitflow.data.PrefFile
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Semua yang jalan di latar belakang atas nama akun yang sedang masuk: pekerja terjadwal, alarm, dan notifikasi. */
interface BackgroundWork {
    /** Memasang semuanya. Aman dipanggil berulang. */
    fun start()

    /** Membatalkan semuanya: pekerja, alarm jam, alarm jadwal, notifikasi, dan alarm yang sedang berbunyi. Aman dipanggil berulang. */
    fun stop()
}

/**
 * Satu-satunya yang menyalakan dan mematikan latar belakang app (tahap 31H). Latar belakang hanya hidup selagi sesi
 * [SessionState.SignedIn]: sesi berakhir, keluar, dan belum pernah masuk semuanya mematikannya. `HabitFlowApplication`
 * tidak lagi memulai apa pun sendiri.
 *
 * Komponen yang bisa terpicu sendiri oleh sistem (alarm yang sudah terjadwal, boot, pekerja) tambahan memeriksa
 * [SessionGate] sebelum bekerja, karena pembatalan bisa kalah cepat dari alarm yang sudah di tengah jalan.
 */
class SessionManager(
    private val settings: AccountSettings,
    private val work: BackgroundWork,
) {
    /** Keadaan terakhir yang diterapkan: null berarti belum pernah. Penerapan berulang dengan keadaan sama diabaikan. */
    private var running: Boolean? = null

    fun observe(scope: CoroutineScope) {
        scope.launch { settings.session.collect { apply(it) } }
    }

    @Synchronized
    fun apply(session: SessionState) {
        // Pemilik data lokal selalu diketahui begitu ada akun yang tercatat, juga yang sesinya sudah berakhir: tanpa ini,
        // akun lain yang masuk setelahnya diam-diam mengambil alih data akun yang sesinya berakhir.
        if (session !is SessionState.SignedOut) settings.claimOwnerFromKnownAccount()

        val shouldRun = session is SessionState.SignedIn
        if (running == shouldRun) return
        running = shouldRun
        if (shouldRun) work.start() else work.stop()
    }
}

/**
 * Pintu cepat untuk komponen yang dipicu sistem dan tidak punya akses ke `AppGraph` (receiver, pekerja). Membaca berkas
 * sesi langsung, jadi benar walau proses baru saja dibuat.
 */
object SessionGate {
    fun isActive(context: Context): Boolean = AccountSettings.read(PrefFile.ACCOUNT.open(context)) is SessionState.SignedIn
}
