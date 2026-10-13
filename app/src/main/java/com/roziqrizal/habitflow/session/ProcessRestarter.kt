package com.roziqrizal.habitflow.session

import android.content.Context
import android.content.Intent

/**
 * Memulai ulang proses app (tahap 31H): membuka lagi layar utama di tugas baru lalu mematikan proses ini, supaya tidak ada
 * keadaan di memori (StateFlow pengaturan, ViewModel, cache Room) yang tersisa dari akun sebelumnya.
 */
object ProcessRestarter {
    fun restart(context: Context) {
        val app = context.applicationContext
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch != null) {
            app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }
}
