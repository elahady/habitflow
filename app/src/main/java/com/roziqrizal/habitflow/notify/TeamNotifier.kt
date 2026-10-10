package com.roziqrizal.habitflow.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.roziqrizal.habitflow.MainActivity
import com.roziqrizal.habitflow.R
import com.roziqrizal.habitflow.domain.team.TeamTodoAssignment

/**
 * Notifikasi senyap (channel Info, `IMPORTANCE_LOW`) saat ada tugas tim baru yang ditugaskan ke
 * pengguna (tahap 26 langkah 4) - pola sama dengan notifikasi follow-up kerja di [ScheduleNotifier].
 * Dipanggil dari [com.roziqrizal.habitflow.data.team.TeamPollWorker].
 */
object TeamNotifier {
    private const val CHANNEL_ID = "team_todo"
    private const val ID_BASE = 7_000_000

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tugas tim", NotificationManager.IMPORTANCE_LOW),
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyAssigned(context: Context, todo: TeamTodoAssignment) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Tugas baru: ${todo.title}")
            .setContentText("Ditugaskan ke Anda di Tugas Rumah.")
            .setContentIntent(openTeamIntent(context, todo.id))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BASE + (todo.id % 1_000_000).toInt(), notification)
    }

    private fun openTeamIntent(context: Context, todoId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TEAM, true)
        return PendingIntent.getActivity(
            context, (ID_BASE + (todoId % 1_000_000)).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
