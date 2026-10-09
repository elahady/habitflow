package com.roziqrizal.habitflow.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.roziqrizal.habitflow.MainActivity
import com.roziqrizal.habitflow.R
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HealthRepository
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.NotificationSettings
import com.roziqrizal.habitflow.data.ScheduleRepository
import com.roziqrizal.habitflow.data.WorkReminderSettings
import com.roziqrizal.habitflow.data.WorkRepository
import com.roziqrizal.habitflow.domain.health.BpFrequency
import com.roziqrizal.habitflow.domain.health.HealthReminderKind
import com.roziqrizal.habitflow.domain.health.healthReminderKind
import com.roziqrizal.habitflow.domain.health.healthReminderMinute
import com.roziqrizal.habitflow.domain.health.nextHealthReminderDate
import com.roziqrizal.habitflow.domain.prayer.EphemerisPrayerCalculator
import com.roziqrizal.habitflow.domain.schedule.AlarmTime
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.nextReminder
import com.roziqrizal.habitflow.domain.work.remindersBetween
import com.roziqrizal.habitflow.domain.schedule.GLASSES_TARGET
import com.roziqrizal.habitflow.domain.schedule.NotificationLevel
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.domain.schedule.adzanPrayer
import com.roziqrizal.habitflow.domain.schedule.blocksStartedBetween
import com.roziqrizal.habitflow.domain.schedule.boundaryMinutes
import com.roziqrizal.habitflow.domain.schedule.nextBoundaryMinute
import com.roziqrizal.habitflow.domain.schedule.WaterReminderState
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import com.roziqrizal.habitflow.domain.schedule.WorkReminder
import com.roziqrizal.habitflow.domain.schedule.WorkReminderKind
import com.roziqrizal.habitflow.domain.schedule.decideWaterReminder
import com.roziqrizal.habitflow.domain.schedule.nextWorkReminderMinute
import com.roziqrizal.habitflow.domain.schedule.workReminders
import com.roziqrizal.habitflow.domain.schedule.workRemindersBetween
import com.roziqrizal.habitflow.domain.schedule.nextAlarm
import com.roziqrizal.habitflow.domain.schedule.notificationWindow
import com.roziqrizal.habitflow.domain.schedule.nowAndNext
import com.roziqrizal.habitflow.domain.schedule.resolveBlocks
import com.roziqrizal.habitflow.ui.formatMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Notifikasi jadwal harian: satu notifikasi per blok saat mulai (sesuai tingkatnya), dan satu
 * notifikasi tetap "Sekarang · Berikutnya" dari blok pertama sampai batas tidur.
 *
 * Alarm dijadwalkan di batas blok atau pengingat berikutnya dengan `setAndAllowWhileIdle` (tidak presisi, bisa
 * terlambat beberapa menit, tapi tetap berbunyi saat HP dalam Doze).
 * Setiap [refresh] menjadwalkan ulang alarm berikutnya.
 */
object ScheduleNotifier {

    const val ACTION_ALARM = "com.roziqrizal.habitflow.SCHEDULE_ALARM"
    const val ACTION_DONE = "com.roziqrizal.habitflow.BLOCK_DONE"
    const val ACTION_RING = "com.roziqrizal.habitflow.ALARM_RING"
    const val ACTION_WATER = "com.roziqrizal.habitflow.WATER_DRUNK"
    const val EXTRA_BLOCK_ID = "blockId"
    const val EXTRA_DATE = "date"

    private const val CHANNEL_INFO = "schedule_info"
    private const val CHANNEL_REMINDER = "schedule_reminder"
    private const val CHANNEL_ONGOING = "schedule_ongoing"
    private const val ONGOING_ID = 1
    private const val WORK_REMINDER_ID = 2
    private const val HEALTH_REMINDER_ID = 3
    private const val REQUEST_ALARM_CLOCK = 1
    private const val REQUEST_WATER = 2000
    private const val BLOCK_ID_BASE = 1000
    private const val FOLLOW_UP_ID_BASE = 100_000

    /**
     * Jendela mundur untuk blok dan pengingat yang baru jatuh tempo, kalau alarm terlambat atau HP baru menyala.
     * Alarm tidak presisi bisa terlambat beberapa menit (di Doze, pembatasan sistem sekitar sekali per 9 menit),
     * dan awal rentang bersifat eksklusif, jadi jendela ini sengaja lebih lebar dari 10 menit supaya pengingat tepat
     * di batasnya tidak terlewat.
     */
    private const val CATCH_UP_MINUTES = 15

    private const val STATE_PREFS = "schedule_notifier_state"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_LAST_MINUTE = "last_minute"
    private const val KEY_WATER_SEGMENT = "water_segment"
    private const val KEY_WATER_IGNORED = "water_ignored"
    private const val KEY_WATER_GLASSES = "water_glasses"

    /**
     * Hitung ulang notifikasi tetap dan jadwalkan alarm berikutnya. Dengan [announce], blok yang
     * baru mulai ikut dinotifikasikan (dipakai saat alarm berbunyi). Tidak bisa dibatalkan: keluar dari
     * app di tengah proses tidak boleh membuat alarm batal terjadwal.
     */
    suspend fun refresh(
        context: Context,
        announce: Boolean,
        clock: Clock = Clock.systemDefaultZone(),
    ) = withContext(NonCancellable + Dispatchers.Default) {
        val app = context.applicationContext
        ensureChannels(app)

        val repo = ScheduleRepository(HabitDatabase.get(app))
        val place = LocationSettings(app).location.value
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(clock.withZone(zone))
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val blocks = repo.getBlocks()
        val followUps = WorkRepository(HabitDatabase.get(app)).getFollowUps()

        fun resolveFor(date: LocalDate, off: Boolean) = resolveBlocks(
            blocks, EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone), date, off,
        )

        val resolved = resolveFor(today, repo.isDayOff(today))
        val reminderSettings = WorkReminderSettings(app)
        val reminders = workReminders(resolved, reminderSettings.water.value, reminderSettings.breaks.value)

        val health = HealthSettings(app)
        fun healthMinuteFor(date: LocalDate): Int? =
            EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone).subuh
                ?.let { healthReminderMinute(it.hour * 60 + it.minute) }

        if (announce) {
            announceStarted(app, resolved, reminders, followUps, today, nowMinute, healthMinuteFor(today), health, zone)
        }
        updateOngoing(app, resolved, nowMinute)

        val nextToday = nextBoundaryMinute(resolved, nowMinute)
        val trigger = if (nextToday != null) {
            today.atStartOfDay(zone).plusMinutes(nextToday.toLong())
        } else {
            val tomorrow = today.plusDays(1)
            val first = boundaryMinutes(resolveFor(tomorrow, repo.isDayOff(tomorrow))).firstOrNull() ?: 5
            tomorrow.atStartOfDay(zone).plusMinutes(first.toLong())
        }
        // Pengingat follow-up berjam khusus dan pengingat air dan break ikut menentukan alarm berikutnya.
        val reminder = nextReminder(followUps, now.toLocalDateTime())?.atZone(zone)
        val workReminder = nextWorkReminderMinute(reminders, nowMinute)
            ?.let { today.atStartOfDay(zone).plusMinutes(it.toLong()) }
        var windowTrigger = trigger
        if (reminder != null && reminder.isBefore(windowTrigger)) windowTrigger = reminder
        if (workReminder != null && workReminder.isBefore(windowTrigger)) windowTrigger = workReminder
        val healthReminder = nextHealthReminder(
            today, nowMinute, zone, health.weightReminder.value, health.bpFrequency.value, ::healthMinuteFor,
        )
        if (healthReminder != null && healthReminder.isBefore(windowTrigger)) windowTrigger = healthReminder
        scheduleAlarm(app, windowTrigger.toInstant().toEpochMilli())

        val alarmSettings = AlarmSettings(app)
        scheduleAlarmClock(
            app,
            nextAlarm(blocks, now.toLocalDateTime(), alarmSettings.skipped.value) { date ->
                EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone)
            },
            zone,
        )
    }

    private suspend fun announceStarted(
        context: Context,
        resolved: List<ResolvedBlock>,
        reminders: List<WorkReminder>,
        followUps: List<FollowUp>,
        today: LocalDate,
        nowMinute: Int,
        healthMinute: Int?,
        health: HealthSettings,
        zone: ZoneId,
    ) {
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
        remindersBetween(followUps, today, since, nowMinute).forEach { notifyFollowUp(context, it) }
        // Notifikasi Info bersifat menggantikan: kalau lebih dari satu terlewat, hanya yang terakhir yang tampil.
        workRemindersBetween(reminders, since, nowMinute).lastOrNull()?.let { notifyWorkReminder(context, it, today) }
        if (healthMinute != null && healthMinute > since && healthMinute <= nowMinute) {
            notifyHealthReminder(context, today, health, zone)
        }
    }

    /**
     * Pengingat timbang dan ukur tensi (tingkat Info): satu notifikasi senyap dengan id tetap. Jenis yang hari itu
     * sudah dicatat tidak diingatkan, dan kalau keduanya sudah dicatat tidak ada notifikasi sama sekali.
     */
    @SuppressLint("MissingPermission")
    private suspend fun notifyHealthReminder(context: Context, today: LocalDate, health: HealthSettings, zone: ZoneId) {
        if (!canNotify(context)) return
        val repo = HealthRepository(HabitDatabase.get(context))
        val kind = healthReminderKind(
            date = today,
            weightEnabled = health.weightReminder.value,
            bpFrequency = health.bpFrequency.value,
            weightLoggedToday = repo.hasWeightOn(today, zone),
            bpLoggedToday = repo.hasBloodPressureOn(today, zone),
        ) ?: return
        val title = when (kind) {
            HealthReminderKind.WEIGHT -> "Waktunya timbang"
            HealthReminderKind.BLOOD_PRESSURE -> "Waktunya ukur tensi"
            HealthReminderKind.BOTH -> "Timbang dan ukur tensi"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText("Sebelum aktivitas pagi.")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(HEALTH_REMINDER_ID, notification)
    }

    /** Waktu pengingat kesehatan berikutnya setelah sekarang, atau null kalau keduanya mati atau Subuh tidak ada. */
    private fun nextHealthReminder(
        today: LocalDate,
        nowMinute: Int,
        zone: ZoneId,
        weightEnabled: Boolean,
        bpFrequency: BpFrequency,
        minuteFor: (LocalDate) -> Int?,
    ): ZonedDateTime? {
        var from = today
        repeat(8) {
            val date = nextHealthReminderDate(from, weightEnabled, bpFrequency) ?: return null
            val minute = minuteFor(date)
            if (minute != null && (date != today || minute > nowMinute)) {
                return date.atStartOfDay(zone).plusMinutes(minute.toLong())
            }
            from = date.plusDays(1)
        }
        return null
    }

    /**
     * Pengingat air dan break (tingkat Info): satu notifikasi senyap dengan id tetap yang menggantikan sebelumnya.
     * Pengingat air yang tak dijawab tiga kali berturut-turut di satu blok berhenti; break tetap jalan.
     */
    @SuppressLint("MissingPermission")
    private suspend fun notifyWorkReminder(context: Context, reminder: WorkReminder, today: LocalDate) {
        if (!canNotify(context)) return
        val glasses = DrinkRepository(HabitDatabase.get(context)).glasses(today)

        var kind = reminder.kind
        if (kind.includesWater) {
            val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val decision = decideWaterReminder(readWaterState(state), "$today:${reminder.blockId}", glasses)
            writeWaterState(state, decision.state)
            if (!decision.send) {
                if (kind == WorkReminderKind.WATER) return
                kind = WorkReminderKind.BREAK
            }
        }

        val (title, text) = when (kind) {
            WorkReminderKind.WATER -> "Waktunya minum" to "$glasses dari $GLASSES_TARGET gelas hari ini"
            WorkReminderKind.BREAK -> "Break sebentar" to "Berdiri dan regangkan badan."
            WorkReminderKind.BREAK_AND_WATER -> "Break + minum" to "$glasses dari $GLASSES_TARGET gelas hari ini"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (kind.includesWater) {
            val drunk = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_WATER)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_WATER, drunk, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Sudah minum", pending)
        }
        NotificationManagerCompat.from(context).notify(WORK_REMINDER_ID, builder.build())
    }

    private fun readWaterState(prefs: SharedPreferences) = WaterReminderState(
        segment = prefs.getString(KEY_WATER_SEGMENT, null),
        ignored = prefs.getInt(KEY_WATER_IGNORED, 0),
        glassesAtLast = prefs.getInt(KEY_WATER_GLASSES, -1).takeIf { it >= 0 },
    )

    private fun writeWaterState(prefs: SharedPreferences, state: WaterReminderState) {
        prefs.edit()
            .putString(KEY_WATER_SEGMENT, state.segment)
            .putInt(KEY_WATER_IGNORED, state.ignored)
            .putInt(KEY_WATER_GLASSES, state.glassesAtLast ?: -1)
            .apply()
    }

    fun cancelWorkReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(WORK_REMINDER_ID)
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

    /** Pengingat follow-up berjam khusus: notifikasi tingkat Pengingat yang membuka tab Kerja. */
    @SuppressLint("MissingPermission")
    private fun notifyFollowUp(context: Context, item: FollowUp) {
        if (!canNotify(context)) return
        val time = item.time ?: return
        val detail = listOfNotNull(item.person).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(item.title)
            .setContentText("Follow-up " + formatMinute(time.hour * 60 + time.minute) + if (detail.isEmpty()) "" else " · $detail")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(FOLLOW_UP_ID_BASE + item.id.toInt(), notification)
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
        // Tetap berbunyi saat HP dalam Doze (dibatasi sistem sekitar sekali per 9 menit), supaya pengingat air dan
        // blok tidak tertahan di HP yang diam di meja.
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
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
