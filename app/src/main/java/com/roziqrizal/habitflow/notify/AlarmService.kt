package com.roziqrizal.habitflow.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.roziqrizal.habitflow.R
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.ui.formatMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Keadaan alarm yang sedang berbunyi, diamati [AlarmActivity] supaya layarnya menutup saat alarm selesai. */
data class RingingAlarm(val name: String, val snoozeCount: Int, val water: Boolean = false)

/**
 * Membunyikan alarm: suara dengan `USAGE_ALARM` (ikut volume alarm dan menembus mode senyap) yang
 * volumenya naik dalam [RAMP_MILLIS], getar, dan layar alarm penuh di atas lock screen. Berhenti
 * sendiri setelah [AUTO_STOP_MILLIS] tanpa disentuh. Tunda paling banyak [MAX_SNOOZES] kali.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var startedAt = 0L
    private var rampMillis = RAMP_MILLIS

    private val ramp = object : Runnable {
        override fun run() {
            val fraction = ((System.currentTimeMillis() - startedAt).toFloat() / rampMillis).coerceIn(0f, 1f)
            val volume = START_VOLUME + (1f - START_VOLUME) * fraction
            player?.setVolume(volume, volume)
            if (fraction < 1f) handler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> snooze()
            ACTION_STOP -> finish()
            ACTION_DRANK -> drank()
            else -> {
                val name = intent?.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Alarm" }
                ring(name, intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0, intent?.getBooleanExtra(EXTRA_WATER, false) ?: false)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun ring(name: String, snoozeCount: Int, water: Boolean) {
        // Bunyi kedua saat sudah ada alarm yang berbunyi menggantikan yang lama, bukan menumpuk.
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        current.value = RingingAlarm(name, snoozeCount, water)
        rampMillis = if (water) WATER_RAMP_MILLIS else RAMP_MILLIS

        createChannel()
        val notification = buildNotification(name, snoozeCount, water)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startedAt = System.currentTimeMillis()
        player = createPlayer()
        handler.post(ramp)
        vibrate()
        handler.postDelayed({ finish() }, if (water) WATER_AUTO_STOP_MILLIS else AUTO_STOP_MILLIS)
    }

    /** Tombol "Sudah minum" di alarm minum: tambah satu gelas hari ini, tutup notifikasi pengingat, lalu matikan alarm. */
    private fun drank() {
        val app = applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            DrinkRepository(HabitDatabase.get(app)).addGlass(LocalDate.now())
            ScheduleNotifier.cancelWorkReminder(app)
        }
        finish()
    }

    private fun createPlayer(): MediaPlayer? {
        val settings = AlarmSettings(this)
        val candidates = listOf(settings.ringtoneUri(), android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
        for (uri in candidates) {
            val created = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    setVolume(START_VOLUME, START_VOLUME)
                    prepare()
                    start()
                }
            }.getOrNull()
            if (created != null) return created
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
        }
    }

    @Suppress("DEPRECATION")
    private fun releasePlayer() {
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    /** Tunda: bunyikan lagi [SNOOZE_MINUTES] menit lagi lewat alarm jam, kecuali sudah [MAX_SNOOZES] kali. */
    private fun snooze() {
        val ringing = current.value ?: return finish()
        if (ringing.snoozeCount < MAX_SNOOZES) {
            val trigger = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
            val intent = Intent(this, ScheduleReceiver::class.java).setAction(ScheduleNotifier.ACTION_RING)
                .putExtra(EXTRA_NAME, ringing.name)
                .putExtra(EXTRA_SNOOZE_COUNT, ringing.snoozeCount + 1)
            val pending = PendingIntent.getBroadcast(
                this, REQUEST_SNOOZE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ScheduleNotifier.setClockAlarm(this, trigger, pending)
        }
        finish()
    }

    private fun finish() {
        current.value = null
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        // Alarm berikutnya dijadwalkan ulang setelah yang ini selesai.
        CoroutineScope(Dispatchers.Default).launch { ScheduleNotifier.refresh(applicationContext, announce = false) }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ALARM, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(name: String, snoozeCount: Int, water: Boolean): android.app.Notification {
        val now = LocalTime.now()
        val fullScreen = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(name)
            .setContentText((if (water) "Pengingat minum " else "Alarm ") + formatMinute(now.hour * 60 + now.minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            // Isi alarm (nama dan jam) tidak sensitif, dan tanpa layar penuh notifikasi inilah satu-satunya yang terlihat di layar kunci.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
        // Alarm minum: "Sudah minum" lebih dulu, tanpa tunda (pengingat berikutnya datang sendiri satu jam lagi).
        if (water) builder.addAction(0, "Sudah minum", servicePending(ACTION_DRANK, 3))
        builder.addAction(0, "Matikan", servicePending(ACTION_STOP, 1))
        // Layar alarm penuh hanya kalau diizinkan (lihat FullScreenAlarmAccess). Tanpa izin, alarm tetap berbunyi dan
        // muncul sebagai notifikasi heads-up dengan tombol Matikan dan Tunda; mengetuknya membuka layar alarm.
        if (FullScreenAlarmAccess.isGranted(this)) builder.setFullScreenIntent(fullScreen, true)
        if (!water && snoozeCount < MAX_SNOOZES) builder.addAction(0, "Tunda $SNOOZE_MINUTES menit", servicePending(ACTION_SNOOZE, 2))
        return builder.build()
    }

    private fun servicePending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_STOP = "com.roziqrizal.habitflow.ALARM_STOP"
        const val ACTION_SNOOZE = "com.roziqrizal.habitflow.ALARM_SNOOZE"
        const val ACTION_DRANK = "com.roziqrizal.habitflow.ALARM_DRANK"
        const val EXTRA_NAME = "alarmName"
        const val EXTRA_SNOOZE_COUNT = "snoozeCount"
        const val EXTRA_WATER = "alarmWater"

        const val MAX_SNOOZES = 2
        const val SNOOZE_MINUTES = 5
        private const val AUTO_STOP_MILLIS = 10 * 60_000L
        private const val RAMP_MILLIS = 30_000L
        // Alarm minum datang tiap jam: lebih singkat dan volumenya naik lebih cepat daripada alarm bangun.
        private const val WATER_AUTO_STOP_MILLIS = 2 * 60_000L
        private const val WATER_RAMP_MILLIS = 10_000L
        private const val START_VOLUME = 0.05f

        private const val CHANNEL_ALARM = "alarm"
        private const val NOTIFICATION_ID = 2
        internal const val REQUEST_SNOOZE = 2

        private val current = MutableStateFlow<RingingAlarm?>(null)
        val ringing: StateFlow<RingingAlarm?> = current.asStateFlow()

        /** Menghentikan alarm yang sedang berbunyi dan menutup layar alarmnya (dipakai saat sesi berakhir atau Keluar). */
        fun stopRinging(context: Context) {
            current.value = null
            context.stopService(Intent(context, AlarmService::class.java))
        }

        fun intentFor(context: Context, action: String): Intent =
            Intent(context, AlarmService::class.java).setAction(action)
    }
}
