package com.roziqrizal.habitflow.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.roziqrizal.habitflow.MainActivity
import com.roziqrizal.habitflow.R
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.NotificationSettings
import com.roziqrizal.habitflow.data.ScheduleRepository
import com.roziqrizal.habitflow.domain.prayer.EphemerisPrayerCalculator
import com.roziqrizal.habitflow.domain.schedule.AlarmTime
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.domain.schedule.adzanPrayer
import com.roziqrizal.habitflow.domain.schedule.blocksStartedBetween
import com.roziqrizal.habitflow.domain.schedule.boundaryMinutes
import com.roziqrizal.habitflow.domain.schedule.nextBoundaryMinute
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import com.roziqrizal.habitflow.domain.schedule.nextAlarm
import com.roziqrizal.habitflow.domain.schedule.notificationWindow
import com.roziqrizal.habitflow.domain.schedule.nowAndNext
import com.roziqrizal.habitflow.domain.schedule.resolveBlocks
import com.roziqrizal.habitflow.ui.formatMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Notifikasi jadwal harian: satu notifikasi per blok saat mulai (sesuai tingkatnya), dan satu
 * notifikasi tetap "Sekarang · Berikutnya" dari blok pertama sampai batas tidur.
 *
 * Alarm dijadwalkan di batas blok berikutnya dengan `setWindow` 5 menit (tidak presisi, bisa
 * terlambat sampai 5 menit; saat HP dalam Doze bisa tertunda sampai jendela pemeliharaan).
 * Setiap [refresh] menjadwalkan ulang alarm berikutnya.
 */
object ScheduleNotifier {

    const val ACTION_ALARM = "com.roziqrizal.habitflow.SCHEDULE_ALARM"
    const val ACTION_DONE = "com.roziqrizal.habitflow.BLOCK_DONE"
    const val ACTION_RING = "com.roziqrizal.habitflow.ALARM_RING"
    const val EXTRA_BLOCK_ID = "blockId"
    const val EXTRA_DATE = "date"

    private const val CHANNEL_INFO = "schedule_info"
    private const val CHANNEL_REMINDER = "schedule_reminder"
    private const val CHANNEL_ONGOING = "schedule_ongoing"
    private const val ONGOING_ID = 1
    private const val REQUEST_ALARM_CLOCK = 1
    private const val BLOCK_ID_BASE = 1000

    /** Jendela mundur untuk blok yang baru mulai, kalau alarm terlambat atau HP baru menyala. */
    private const val CATCH_UP_MINUTES = 10

    /** Jendela alarm tidak presisi: bunyi paling lambat 5 menit setelah batas blok. */
    private const val ALARM_WINDOW_MILLIS = 5 * 60 * 1000L

    private const val STATE_PREFS = "schedule_notifier_state"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_LAST_MINUTE = "last_minute"

    /**
     * Hitung ulang notifikasi tetap dan jadwalkan alarm berikutnya. Dengan [announce], blok yang
     * baru mulai ikut dinotifikasikan (dipakai saat alarm berbunyi). Tidak bisa dibatalkan: keluar dari
     * app di tengah proses tidak boleh membuat alarm batal terjadwal.
     */
    suspend fun refresh(context: Context, announce: Boolean) = withContext(NonCancellable + Dispatchers.Default) {
        val app = context.applicationContext
        ensureChannels(app)

        val repo = ScheduleRepository(HabitDatabase.get(app))
        val place = LocationSettings(app).location.value
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val blocks = repo.getBlocks()

        fun resolveFor(date: LocalDate, off: Boolean) = resolveBlocks(
            blocks, EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone), date, off,
        )

        val resolved = resolveFor(today, repo.isDayOff(today))

        if (announce) announceStarted(app, resolved, today, nowMinute)
        updateOngoing(app, resolved, nowMinute)

        val nextToday = nextBoundaryMinute(resolved, nowMinute)
        val trigger = if (nextToday != null) {
            today.atStartOfDay(zone).plusMinutes(nextToday.toLong())
        } else {
            val tomorrow = today.plusDays(1)
            val first = boundaryMinutes(resolveFor(tomorrow, repo.isDayOff(tomorrow))).firstOrNull() ?: 5
            tomorrow.atStartOfDay(zone).plusMinutes(first.toLong())
        }
        scheduleAlarm(app, trigger.toInstant().toEpochMilli())

        val alarmSettings = AlarmSettings(app)
        scheduleAlarmClock(
            app,
            nextAlarm(blocks, now.toLocalDateTime(), alarmSettings.skipped.value) { date ->
                EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone)
            },
            zone,
        )
    }

    private fun announceStarted(context: Context, resolved: List<ResolvedBlock>, today: LocalDate, nowMinute: Int) {
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val since = if (state.getString(KEY_LAST_DATE, null) == today.toString()) {
            state.getInt(KEY_LAST_MINUTE, -1)
        } else {
            -1
        }.coerceAtLeast(nowMinute - CATCH_UP_MINUTES)
        state.edit().putString(KEY_LAST_DATE, today.toString()).putInt(KEY_LAST_MINUTE, nowMinute).apply()

        val alarmSettings = AlarmSettings(context)
        blocksStartedBetween(resolved, since, nowMinute)
            // Alarm dibunyikan AlarmService, bukan notifikasi biasa. Adzan yang dimatikan tidak dinotifikasikan.
            .filter { it.block.level != NotificationLevel.ALARM }
            .filter { item -> item.block.adzanPrayer()?.let(alarmSettings::isAdzanEnabled) ?: true }
            .forEach { notifyBlock(context, it, today) }
    }

    @SuppressLint("MissingPermission")
    private fun notifyBlock(context: Context, item: ResolvedBlock, date: LocalDate) {
        if (!canNotify(context)) return
        val block = item.block
        val channel = if (block.level == NotificationLevel.REMINDER) CHANNEL_REMINDER else CHANNEL_INFO
        val detail = if (item.isPoint) "" else " · sampai ${formatMinute(item.endMinute)}"
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(block.name)
            .setContentText("Mulai ${formatMinute(item.startMinute)}$detail")
            .setContentIntent(openAppIntent(context, block.workAction))
            .setAutoCancel(true)
        if (block.habitIds.isNotEmpty()) {
            val done = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_BLOCK_ID, block.id)
                putExtra(EXTRA_DATE, date.toString())
            }
            val pending = PendingIntent.getBroadcast(
                context, block.id.toInt(), done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Sudah", pending)
        }
        NotificationManagerCompat.from(context).notify(blockNotificationId(block.id), builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun updateOngoing(context: Context, resolved: List<ResolvedBlock>, nowMinute: Int) {
        val manager = NotificationManagerCompat.from(context)
        val window = notificationWindow(resolved)
        val enabled = NotificationSettings(context).persistent.value
        if (!enabled || window == null || nowMinute !in window || !canNotify(context)) {
            manager.cancel(ONGOING_ID)
            return
        }
        val current = nowAndNext(resolved, nowMinute)
        val title = "Sekarang · ${current.now?.block?.name ?: "tidak ada blok"}"
        val text = current.next?.let { "Berikutnya · ${it.block.name} ${formatMinute(it.startMinute)}" }
            ?: "Selesai untuk hari ini"
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        manager.notify(ONGOING_ID, notification)
    }

    fun cancelBlockNotification(context: Context, blockId: Long) {
        NotificationManagerCompat.from(context).cancel(blockNotificationId(blockId))
    }

    private fun blockNotificationId(blockId: Long) = BLOCK_ID_BASE + blockId.toInt()

    /** Membuka app. Dengan [workAction], app langsung membuka daily scrum atau EOD di tab Kerja. */
    fun openAppIntent(context: Context, workAction: WorkAction? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        workAction?.let { intent.putExtra(MainActivity.EXTRA_WORK_ACTION, it.name) }
        return PendingIntent.getActivity(
            context, 100 + (workAction?.ordinal?.plus(1) ?: 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAlarm(context: Context, triggerAtMillis: Long) {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_ALARM)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, ALARM_WINDOW_MILLIS, pending)
    }

    /** Alarm jam (`setAlarmClock`): presisi, menembus Doze, dan ikon alarm tampil di status bar. Null membatalkan. */
    private fun scheduleAlarmClock(context: Context, alarm: AlarmTime?, zone: ZoneId) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_RING)
        if (alarm == null) {
            PendingIntent.getBroadcast(context, REQUEST_ALARM_CLOCK, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { manager.cancel(it) }
            return
        }
        intent.putExtra(AlarmService.EXTRA_NAME, alarm.block.name)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_ALARM_CLOCK, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = alarm.dateTime.atZone(zone).toInstant().toEpochMilli()
        setClockAlarm(context, triggerAt, pending)
    }

    /**
     * Alarm jam presisi yang menembus Doze. Kalau izin alarm presisi dicabut pengguna (Android 12),
     * jangan jatuhkan app: pakai alarm tidak presisi sebagai gantinya.
     */
    fun setClockAlarm(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, openAppIntent(context)), pending)
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Pengingat jadwal", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Info jadwal", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Sekarang dan berikutnya", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
