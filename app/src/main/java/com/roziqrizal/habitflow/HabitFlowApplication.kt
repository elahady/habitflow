package com.roziqrizal.habitflow

import android.app.Application
import com.roziqrizal.habitflow.data.AlarmSettings
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
import com.roziqrizal.habitflow.data.health.StepsWorker
import com.roziqrizal.habitflow.data.WorkReminderSettings
import com.roziqrizal.habitflow.data.sync.AppSettingsGateway
import com.roziqrizal.habitflow.data.sync.SnapshotRepository
import com.roziqrizal.habitflow.data.sync.SnapshotSettings
import com.roziqrizal.habitflow.data.sync.SyncManager
import com.roziqrizal.habitflow.data.sync.SyncScheduler
import com.roziqrizal.habitflow.data.sync.SyncSettings
import com.roziqrizal.habitflow.domain.health.BpFrequency
import com.roziqrizal.habitflow.domain.prayer.PrayerName

class HabitFlowApplication : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Sinkron otomatis: pasang jadwal harian kalau aktif, dan pantau perubahan data di proses mana pun.
        graph.syncScheduler.onConfigChanged()
        graph.syncScheduler.observeChanges(graph.db, SyncScheduler.newScope())
        // Cek langkah sekitar tiap jam untuk mencentang habit langkah walau app tertutup (tahap 21).
        StepsWorker.ensureScheduled(this)
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
    val syncScheduler = SyncScheduler(app, syncSettings)
    val stepsTracker: StepsTracker by lazy { StepsTracker(HealthConnectStepsSource(app), db, healthSettings) }

    private val versionName: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty() }.getOrDefault("")

    val syncManager: SyncManager by lazy {
        SyncManager(
            snapshots = SnapshotRepository(db),
            settings = syncSettings,
            appSettings = SettingsGateway(),
            appVersion = versionName,
            allowLocalCleartext = BuildConfig.DEBUG,
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
