package com.roziqrizal.habitflow.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Izin alarm presisi (31L). `setAlarmClock` ikut dibatasi izin ini sejak Android 12: tanpa izin, sistem melempar
 * `SecurityException` dan alarm Subuh jatuh ke alarm tidak presisi yang bisa terlambat sampai satu jam
 * (perilaku yang didokumentasikan Android; belum diuji di perangkat, lihat concept.md 31L).
 *
 * Jalur normal: `USE_EXACT_ALARM` diberikan otomatis pada Android 13 ke atas. Jalur cadangan kalau Google menolak
 * deklarasinya dan izin itu dicabut dari manifest: `SCHEDULE_EXACT_ALARM`, yang harus diberikan pengguna di pengaturan sistem.
 */
object ExactAlarmAccess {

    /** Benar kalau alarm presisi boleh dipasang (selalu benar di bawah Android 12). */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Layar sistem tempat pengguna memberikan izin, atau null kalau tidak ada yang perlu diberikan. */
    fun requestIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
        else Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
}
