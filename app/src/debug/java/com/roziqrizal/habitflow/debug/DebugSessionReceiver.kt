package com.roziqrizal.habitflow.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.roziqrizal.habitflow.HabitFlowApplication
import com.roziqrizal.habitflow.data.account.Account

/**
 * Khusus build debug: memasang, mengakhiri, atau menghapus sesi akun lewat adb, supaya alur uji tidak perlu mengetik
 * kata sandi dan menunggu email. **Tidak boleh ada di build rilis** (hanya hidup di `src/debug`; dijaga skrip cek rilis di 31J).
 *
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugSessionReceiver --es cmd set --el id 1 \
 *         --es email ani@contoh.com --es name Ani --es token TOKEN-DARI-SERVER
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugSessionReceiver --es cmd expire
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugSessionReceiver --es cmd clear
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugSessionReceiver --es cmd owner --el id 1
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugSessionReceiver --es cmd owner-clear
 *
 * `set` memasang sesi `SignedIn` (token boleh palsu untuk menguji layar tanpa server). `expire` menandai sesi sekarang
 * berakhir seperti kalau server menjawab 401. Hasil tiap perintah ditulis ke logcat dengan tag `DebugSession`.
 */
class DebugSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = (context.applicationContext as HabitFlowApplication).graph.accountSettings
        when (val cmd = intent.getStringExtra("cmd")) {
            "set" -> {
                val id = intent.getLongExtra("id", -1L)
                val email = intent.getStringExtra("email")
                val name = intent.getStringExtra("name")
                val token = intent.getStringExtra("token")
                if (id < 0 || email == null || name == null || token == null) {
                    Log.w(TAG, "set butuh --el id, --es email, --es name, --es token")
                    return
                }
                settings.save(Account(id, email, name, intent.getStringExtra("avatar"), token))
                Log.i(TAG, "Sesi dipasang: $email (id $id)")
            }
            "expire" -> {
                val token = settings.account.value?.token
                if (token == null) {
                    Log.w(TAG, "Tidak ada sesi masuk yang bisa diakhiri")
                    return
                }
                settings.expire(token)
                Log.i(TAG, "Sesi ditandai berakhir: ${settings.session.value}")
            }
            "clear" -> {
                settings.clear()
                Log.i(TAG, "Sesi dihapus")
            }
            "owner" -> {
                val id = intent.getLongExtra("id", -1L)
                if (id < 0) {
                    Log.w(TAG, "owner butuh --el id")
                    return
                }
                settings.setOwnerAccountId(id)
                Log.i(TAG, "ownerAccountId = $id")
            }
            "owner-clear" -> {
                settings.clearOwnerAccountId()
                Log.i(TAG, "ownerAccountId dikosongkan")
            }
            else -> Log.w(TAG, "Perintah tidak dikenal: $cmd (set, expire, clear, owner, owner-clear)")
        }
    }

    private companion object {
        const val TAG = "DebugSession"
    }
}
