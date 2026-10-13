package com.roziqrizal.habitflow.data.account

/** Cara mengakhiri akun dari app (tahap 31H). Diimplementasikan [AccountManager]; alur Keluar memakainya lewat antarmuka ini supaya bisa diuji. */
interface SessionEnding {
    /** Mencabut token di server lalu menutup sesi di HP. Sesi di HP selalu ditutup, juga saat server tidak terjangkau. */
    suspend fun revokeAndClear(): AccountResult<Unit>

    /**
     * Meminta server mencabut semua sesi akun, termasuk yang ini. **Tidak menyentuh sesi di HP**: pemanggil menutupnya sendiri
     * setelah berhasil dengan [closeSession].
     */
    suspend fun revokeAllSessions(): AccountResult<Unit>

    /** Menutup sesi di HP saja, tanpa menghubungi server. Dipakai sesudah [revokeAllSessions], karena tokennya sudah mati di server. */
    suspend fun closeSession()

    /** Menghapus akun dan semua datanya di server. Berhasil berarti sesi di HP ditutup. */
    suspend fun deleteAccount(password: String?, googleIdToken: String?): AccountResult<Unit>

    /** Status akun, antara lain apakah punya kata sandi (menentukan cara konfirmasi hapus akun). */
    suspend fun me(): AccountResult<MeInfo>
}
