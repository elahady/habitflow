package com.roziqrizal.habitflow.data.account

/** Dipanggil klien HTTP saat server menjawab 401 untuk [token] yang dikirimnya. */
fun interface UnauthorizedListener {
    fun onUnauthorized(token: String)

    companion object {
        val None = UnauthorizedListener {}
    }
}

/**
 * Satu-satunya tempat yang memutuskan apa yang terjadi saat server menjawab 401 (tahap 31F). Semua klien
 * (akun, habit/to-do, follow-up, tim, cadangan) melapor ke sini lewat [UnauthorizedListener], tidak ada yang
 * menangani 401 sendiri. Sesi ditandai [SessionState.Expired] tanpa menghapus data lokal.
 *
 * Yang dilaporkan adalah **token yang ditolak**, bukan sekadar "ada 401": permintaan yang masih terbang dengan
 * token lama tidak boleh mematikan sesi baru yang sudah dibuat sesudah masuk lagi.
 */
class SessionGuard(private val settings: AccountSettings) : UnauthorizedListener {
    override fun onUnauthorized(token: String) {
        settings.expire(token)
    }
}
