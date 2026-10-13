package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.account.Account
import org.json.JSONObject
import java.io.File

/**
 * Jurnal "hapus semua data HP ini" (tahap 31H). Keluar, hapus akun, dan ganti akun tidak menghapus data saat itu juga: mereka
 * menulis penanda, mematikan proses app, dan **proses berikutnya menghapus semuanya di awal, sebelum database dibuka**
 * ([runIfPending], dipanggil pertama di `HabitFlowApplication.onCreate`). Alasannya:
 *
 *  - Tidak ada pengamat Room, ViewModel, atau pekerja yang masih memegang database saat berkas dihapus (yang di tengah jalan
 *    bisa melempar galat dan membuat app crash tepat saat Keluar).
 *  - Aman terhadap proses yang mati di tengah jalan: penanda baru dihapus setelah semuanya selesai, jadi penghapusan yang
 *    terpotong diulang pada start berikutnya (menghapus bersifat idempoten).
 *
 * Untuk ganti akun, penanda membawa akun baru ([thenSignIn]) yang dipulihkan sesudah penghapusan, supaya login yang sudah
 * dilakukan di server tidak perlu diulang. Isinya (termasuk token) hanya ada di folder privat app dan dihapus bersama penanda.
 */
class PendingWipe(
    private val marker: File,
    private val wipe: () -> Unit,
    private val restoreSession: (Account) -> Unit,
) {
    fun isPending(): Boolean = marker.exists()

    /** Menandai penghapusan. [thenSignIn] diisi untuk ganti akun, kosong untuk Keluar dan hapus akun. */
    fun schedule(thenSignIn: Account? = null) {
        marker.parentFile?.mkdirs()
        val json = JSONObject()
        if (thenSignIn != null) {
            json.put("id", thenSignIn.id).put("email", thenSignIn.email).put("name", thenSignIn.name)
                .put("token", thenSignIn.token)
            if (thenSignIn.avatar != null) json.put("avatar", thenSignIn.avatar)
        }
        // Tulis ke berkas sementara lalu ganti nama, supaya penanda tidak pernah setengah tertulis.
        val temp = File(marker.parentFile, marker.name + ".tmp")
        temp.writeText(json.toString())
        check(temp.renameTo(marker) || (marker.delete() && temp.renameTo(marker))) { "Gagal menulis penanda hapus data" }
    }

    /** Menjalankan penghapusan kalau ada penanda. Mengembalikan true kalau ada yang dijalankan. */
    fun runIfPending(): Boolean {
        if (!marker.exists()) return false
        val account = readAccount()
        wipe()
        account?.let(restoreSession)
        marker.delete()
        return true
    }

    /** Penanda rusak (mis. terpotong) tetap menghapus data, tapi tidak memulihkan sesi: pengguna masuk sendiri. */
    private fun readAccount(): Account? = runCatching {
        val json = JSONObject(marker.readText())
        if (!json.has("token")) return null
        Account(
            id = json.getLong("id"),
            email = json.getString("email"),
            name = json.getString("name"),
            avatar = if (json.has("avatar")) json.getString("avatar") else null,
            token = json.getString("token"),
        )
    }.getOrNull()
}
