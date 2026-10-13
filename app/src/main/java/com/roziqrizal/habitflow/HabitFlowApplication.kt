package com.roziqrizal.habitflow

import android.app.Application
import android.os.Build
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.account.AccountClient
import com.roziqrizal.habitflow.data.account.AccountManager
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.GoogleSignIn
import com.roziqrizal.habitflow.data.account.SessionGuard
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.calendar.CalendarSettings
import com.roziqrizal.habitflow.data.calendar.PhoneCalendarSource
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.MealSettings
import com.roziqrizal.habitflow.data.NotificationSettings
import com.roziqrizal.habitflow.data.PlaceLocation
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.data.ThemeSettings
import com.roziqrizal.habitflow.data.health.HealthConnectStepsSource
import com.roziqrizal.habitflow.data.health.StepsTracker
import com.roziqrizal.habitflow.data.WorkReminderSettings
import com.roziqrizal.habitflow.data.sync.AppSettingsGateway
import com.roziqrizal.habitflow.data.sync.HabitSyncManager
import com.roziqrizal.habitflow.data.sync.HabitSyncScheduler
import com.roziqrizal.habitflow.data.sync.SnapshotRepository
import com.roziqrizal.habitflow.data.sync.SnapshotSettings
import com.roziqrizal.habitflow.data.sync.SyncManager
import com.roziqrizal.habitflow.data.sync.SyncScheduler
import com.roziqrizal.habitflow.data.sync.SyncSettings
import com.roziqrizal.habitflow.data.team.PendingInviteStore
import com.roziqrizal.habitflow.data.team.TeamPollScheduler
import com.roziqrizal.habitflow.data.team.TeamSettings
import com.roziqrizal.habitflow.domain.health.BpFrequency
import com.roziqrizal.habitflow.domain.prayer.PrayerName
import com.roziqrizal.habitflow.session.AndroidBackgroundWork
import com.roziqrizal.habitflow.session.SessionManager

class HabitFlowApplication : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Pantau perubahan data di proses mana pun untuk sinkron tertunda. Pengamat ini pasif: ia hanya menjadwalkan kalau
        // sesi masuk (lihat `isActive` di masing-masing penjadwal).
        graph.syncScheduler.observeChanges(graph.db, SyncScheduler.newScope())
        graph.habitSyncScheduler.observeChanges(graph.db, HabitSyncScheduler.newScope())
        // Satu-satunya yang menyalakan dan mematikan latar belakang (sinkron, polling tim, langkah, alarm, notifikasi): hanya
        // hidup selagi sesi masuk (tahap 31H).
        graph.sessionManager.observe(SyncScheduler.newScope())
    }
}

/**
 * Objek yang dipakai bersama oleh seluruh app. Pengaturan hanya dibuat sekali di sini supaya UI dan pemulihan dari
 * server memegang objek (dan StateFlow) yang sama.
 */
class AppGraph(app: Application) {
    val db: HabitDatabase by lazy { HabitDatabase.get(app) }
    val themeSettings = ThemeSettings(app)
    val locationSettings = LocationSettings(app)
    val notificationSettings = NotificationSettings(app)
    val alarmSettings = AlarmSettings(app)
    val workReminderSettings = WorkReminderSettings(app)
    val healthSettings = HealthSettings(app)
    val mealSettings = MealSettings(app)
    val calendarSettings = CalendarSettings(app)
    val phoneCalendar = PhoneCalendarSource(app)
    val syncSettings = SyncSettings(app)
    val accountSettings = AccountSettings(app)

    /** Satu-satunya penangan 401: semua klien melapor ke sini (tahap 31F). */
    val sessionGuard = SessionGuard(accountSettings)
    val syncScheduler = SyncScheduler(app, syncSettings, accountSettings)
    val googleSignIn = GoogleSignIn(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
    val accountManager = AccountManager(
        accountSettings,
        googleSignIn,
        AccountClient(unauthorized = sessionGuard),
        deviceName = { "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100) },
    )
    val habitSyncManager: HabitSyncManager by lazy { HabitSyncManager(db, accountSettings, sessionGuard) }
    val habitSyncScheduler = HabitSyncScheduler(app, accountSettings)
    val teamSettings = TeamSettings(app)

    /** Token undangan tim yang menunggu login (tahap 31G). */
    val pendingInviteStore = PendingInviteStore(app)
    val teamPollScheduler = TeamPollScheduler(app, accountSettings)

    /** Menyalakan dan mematikan latar belakang menurut sesi (tahap 31H). */
    val sessionManager = SessionManager(
        accountSettings,
        AndroidBackgroundWork(app, syncScheduler, habitSyncScheduler, teamPollScheduler, SyncScheduler.newScope()),
    )
    val stepsTracker: StepsTracker by lazy { StepsTracker(HealthConnectStepsSource(app), db, healthSettings) }

    private val versionName: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    val syncManager: SyncManager by lazy {
        SyncManager(
            snapshots = SnapshotRepository(db),
            settings = syncSettings,
            accountSettings = accountSettings,
            appSettings = SettingsGateway(),
            appVersion = versionName,
            unauthorized = sessionGuard,
            followUpsOnAccount = { accountSettings.account.value != null },
        )
    }

    /** Pengaturan yang ikut snapshot. Nada alarm dan tanggal alarm yang dimatikan sekali sengaja tidak ikut. */
    private inner class SettingsGateway : AppSettingsGateway {
        override fun read(): SnapshotSettings {
            val place = locationSettings.location.value
            return SnapshotSettings(
                locationName = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                persistentNotification = notificationSettings.persistent.value,
                adzan = PrayerName.entries.associate { it.name to alarmSettings.isAdzanEnabled(it) },
                themeMode = themeSettings.mode.value.name,
                waterReminders = workReminderSettings.water.value,
                breakReminders = workReminderSettings.breaks.value,
                heightCm = healthSettings.heightCm.value,
                targetKg = healthSettings.targetKg.value,
                weightReminder = healthSettings.weightReminder.value,
                bpFrequency = healthSettings.bpFrequency.value.name,
                mealReminder = mealSettings.reminder.value,
            )
        }

        override fun apply(settings: SnapshotSettings) {
            if (LocationSettings.isValid(settings.latitude, settings.longitude) && settings.locationName.isNotBlank()) {
                locationSettings.set(PlaceLocation(settings.locationName, settings.latitude, settings.longitude))
            }
            notificationSettings.setPersistent(settings.persistentNotification)
            PrayerName.entries.forEach { alarmSettings.setAdzanEnabled(it, settings.adzan[it.name] ?: true) }
            ThemeMode.entries.firstOrNull { it.name == settings.themeMode }?.let(themeSettings::setMode)
            workReminderSettings.setWater(settings.waterReminders)
            workReminderSettings.setBreaks(settings.breakReminders)
            healthSettings.setHeightCm(settings.heightCm)
            healthSettings.setTargetKg(settings.targetKg)
            healthSettings.setWeightReminder(settings.weightReminder)
            healthSettings.setBpFrequency(BpFrequency.entries.firstOrNull { it.name == settings.bpFrequency } ?: BpFrequency.WEEKLY)
            mealSettings.setReminder(settings.mealReminder)
        }
    }
}
