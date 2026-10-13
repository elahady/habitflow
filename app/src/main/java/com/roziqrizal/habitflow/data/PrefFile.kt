package com.roziqrizal.habitflow.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Daftar **semua** berkas `SharedPreferences` app (tahap 31H). Setiap berkas dibuka lewat [open]; memanggil
 * `getSharedPreferences` langsung di tempat lain dilarang dan digagalkan tes (`PrefFileRegistryTest`). Tujuannya satu:
 * Keluar menghapus data akun, dan berkas baru yang lupa didaftarkan tidak mungkin lolos dari penghapusan itu
 * ([PrefWiper] menghapus setiap entri di sini).
 *
 * [preservedKeys] adalah kunci yang sengaja dibiarkan saat Keluar karena preferensi perangkat, bukan data akun.
 */
enum class PrefFile(val fileName: String, val preservedKeys: Set<String> = emptySet()) {
    /** Pengaturan umum (lokasi, pengingat, alarm, kesehatan, makan, tema). Tema dikecualikan dari penghapusan. */
    SETTINGS("settings", preservedKeys = setOf(ThemeSettings.KEY_MODE)),
    CALENDAR("calendar"),
    SYNC("sync"),
    TEAM("team"),
    ACCOUNT("account"),
    PENDING_INVITE("pending_invite"),
    NOTIFIER_STATE("schedule_notifier_state"),
    ;

    fun open(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
}

/** Menghapus isi semua berkas di [PrefFile] kecuali kunci yang dikecualikan. */
object PrefWiper {
    /** [open] disuntikkan supaya bisa diuji dengan prefs palsu. Memakai `commit` karena proses app dimatikan sesudahnya. */
    fun wipe(open: (PrefFile) -> SharedPreferences) {
        PrefFile.entries.forEach { file ->
            val prefs = open(file)
            val editor = prefs.edit()
            prefs.all.keys.filter { it !in file.preservedKeys }.forEach { editor.remove(it) }
            editor.commit()
        }
    }
}
