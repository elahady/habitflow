package com.roziqrizal.habitflow.data.account

/** Cara mengakhiri akun dari app (tahap 31H). Diimplementasikan [AccountManager]; alur Keluar memakainya lewat antarmuka ini supaya bisa diuji. */
interface SessionEnding {
    /** Mencabut token di server lalu menutup sesi di HP. Sesi di HP selalu ditutup, juga saat server tidak terjangkau. */
    suspend fun revokeAndClear(): AccountResult<Unit>

    /** Menghapus akun dan semua datanya di server. Berhasil berarti sesi di HP ditutup. */
    suspend fun deleteAccount(password: String?, googleIdToken: String?): AccountResult<Unit>

    /** Status akun, antara lain apakah punya kata sandi (menentukan cara konfirmasi hapus akun). */
    suspend fun me(): AccountResult<MeInfo>
}
