package com.roziqrizal.habitflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.EventRepository
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.HealthRepository
import com.roziqrizal.habitflow.data.HolidayAssets
import com.roziqrizal.habitflow.data.calendar.CalendarSettings
import com.roziqrizal.habitflow.data.health.HealthConnectStepsSource
import com.roziqrizal.habitflow.data.health.StepsWorker
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.MealRepository
import com.roziqrizal.habitflow.data.NotificationSettings
import com.roziqrizal.habitflow.data.ScheduleRepository
import com.roziqrizal.habitflow.data.WorkReminderSettings
import com.roziqrizal.habitflow.data.WorkRepository
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.notify.ScheduleNotifier
import com.roziqrizal.habitflow.ui.AgendaViewModel
import com.roziqrizal.habitflow.ui.ContributionViewModel
import com.roziqrizal.habitflow.ui.DayClock
import com.roziqrizal.habitflow.ui.HabitFlowApp
import com.roziqrizal.habitflow.ui.HealthViewModel
import com.roziqrizal.habitflow.ui.ManageHabitsViewModel
import com.roziqrizal.habitflow.ui.ScheduleViewModel
import com.roziqrizal.habitflow.ui.SyncViewModel
import com.roziqrizal.habitflow.ui.TodayViewModel
import com.roziqrizal.habitflow.ui.WorkViewModel
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Pergantian hari, jam, atau zona waktu saat app terbuka langsung memperbarui tanggal.
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = clock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readWorkRequest(intent)
        val graph = (application as HabitFlowApplication).graph
        val themeSettings = graph.themeSettings
        val locationSettings = graph.locationSettings
        val notificationSettings = graph.notificationSettings
        val alarmSettings = graph.alarmSettings
        val workReminderSettings = graph.workReminderSettings
        requestNotificationPermission()
        keepNotificationsInSync(
            locationSettings, notificationSettings, alarmSettings, workReminderSettings, graph.healthSettings, graph.calendarSettings,
        )
        themeSettings.syncWithSystem()
        setContent {
            val themeMode by themeSettings.mode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Edge-to-edge: app digambar di belakang status bar dan bar gesture, jadi warnanya
            // sama dengan latar layar. Warna ikon system bar mengikuti tema app, bukan tema HP.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            HabitFlowTheme(darkTheme = darkTheme) {
                val repo = remember { HabitRepository(HabitDatabase.get(applicationContext)) }
                val drinkRepo = remember { DrinkRepository(HabitDatabase.get(applicationContext)) }
                val today: TodayViewModel = viewModel(
                    factory = viewModelFactory { initializer { TodayViewModel(repo, drinkRepo, MealRepository(HabitDatabase.get(applicationContext)), clock) } },
                )
                val contribution: ContributionViewModel = viewModel(
                    factory = viewModelFactory { initializer { ContributionViewModel(repo, WorkRepository(HabitDatabase.get(applicationContext)), clock) } },
                )
                val scheduleRepo = remember { ScheduleRepository(HabitDatabase.get(applicationContext), HolidayAssets.get(applicationContext)) }
                val eventRepo = remember { EventRepository(HabitDatabase.get(applicationContext)) }
                val schedule: ScheduleViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ScheduleViewModel(
                                scheduleRepo, repo, locationSettings, eventRepo, graph.phoneCalendar, graph.calendarSettings, clock,
                            )
                        }
                    },
                )
                val agenda: AgendaViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            AgendaViewModel(eventRepo, scheduleRepo, graph.phoneCalendar, graph.calendarSettings, clock)
                        }
                    },
                )
                val workRepo = remember { WorkRepository(HabitDatabase.get(applicationContext)) }
                val work: WorkViewModel = viewModel(
                    factory = viewModelFactory { initializer { WorkViewModel(workRepo, clock) } },
                )
                val healthRepo = remember { HealthRepository(HabitDatabase.get(applicationContext)) }
                val health: HealthViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HealthViewModel(healthRepo, graph.healthSettings, graph.stepsTracker, clock) }
                    },
                )
                healthViewModel = health
                // Langkah dibaca saat app dibuka dan tiap 5 menit selama app tampil (dan mencentang habit kalau tercapai).
                LaunchedEffect(health) {
                    this@MainActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            health.refreshSteps()
                            delay(STEPS_REFRESH_MILLIS)
                        }
                    }
                }
                val workRequest by pendingWork.collectAsState()
                val syncViewModel: SyncViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { SyncViewModel(graph.syncManager, graph.syncSettings, graph.syncScheduler) }
                    },
                )
                val manage: ManageHabitsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageHabitsViewModel(repo) } },
                )
                val versionName = remember {
                    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                }

                HabitFlowApp(
                    today = today,
                    contribution = contribution,
                    manage = manage,
                    schedule = schedule,
                    agenda = agenda,
                    work = work,
                    syncViewModel = syncViewModel,
                    workRequest = workRequest,
                    onWorkRequestHandled = { pendingWork.value = null },
                    versionName = versionName,
                    themeMode = themeMode,
                    location = locationSettings.location.collectAsState().value,
                    onLocationChange = locationSettings::set,
                    persistentNotification = notificationSettings.persistent.collectAsState().value,
                    alarmSettings = alarmSettings,
                    onPersistentNotificationChange = notificationSettings::setPersistent,
                    waterReminders = workReminderSettings.water.collectAsState().value,
                    onWaterRemindersChange = workReminderSettings::setWater,
                    breakReminders = workReminderSettings.breaks.collectAsState().value,
                    onBreakRemindersChange = workReminderSettings::setBreaks,
                    health = health,
                    healthSettings = graph.healthSettings,
                    phoneCalendar = graph.phoneCalendar,
                    calendarSettings = graph.calendarSettings,
                    onRequestStepsAccess = {
                        lifecycleScope.launch { stepsPermission.launch(health.stepsPermissions()) }
                    },
                    onOpenHealthConnectStore = ::openHealthConnectStore,
                    onThemeModeChange = themeSettings::setMode,
                )
            }
        }
    }

    private var healthViewModel: HealthViewModel? = null

    /** Layar izin Health Connect untuk langkah. Setelah dijawab, langkah dibaca ulang dan cek berkala dipasang. */
    private val stepsPermission =
        registerForActivityResult(HealthConnectStepsSource.requestContract()) {
            StepsWorker.ensureScheduled(applicationContext)
            healthViewModel?.refreshSteps()
        }

    /** Membuka Play Store ke Health Connect (pasang atau perbarui), dengan cadangan ke browser. */
    private fun openHealthConnectStore() {
        val store = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
        ).setPackage("com.android.vending").putExtra("overlay", true).putExtra("callerId", packageName)
        runCatching { startActivity(store) }.onFailure {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")),
                )
            }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) lifecycleScope.launch { ScheduleNotifier.refresh(applicationContext, announce = false) }
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Notifikasi dan alarm dihitung ulang saat jadwal, hari libur, lokasi, atau pengaturan notifikasi
     * berubah, dan setiap app dibuka (lewat tanggal) selama layar aktif.
     */
    @OptIn(FlowPreview::class)
    private fun keepNotificationsInSync(
        locationSettings: LocationSettings,
        notificationSettings: NotificationSettings,
        alarmSettings: AlarmSettings,
        workReminderSettings: WorkReminderSettings,
        healthSettings: HealthSettings,
        calendarSettings: CalendarSettings,
    ) {
        val repo = ScheduleRepository(HabitDatabase.get(applicationContext), HolidayAssets.get(applicationContext))
        val eventRepo = EventRepository(HabitDatabase.get(applicationContext))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.observeBlocks(),
                    repo.observeDaysOff(),
                    eventRepo.observeEvents(),
                    eventRepo.observeExceptions(),
                    calendarSettings.selection,
                    locationSettings.location,
                    notificationSettings.persistent,
                    alarmSettings.skipped,
                    alarmSettings.adzan,
                    workReminderSettings.water,
                    workReminderSettings.breaks,
                    healthSettings.weightReminder,
                    healthSettings.bpFrequency,
                    clock.date,
                ) { _ -> }
                    .debounce(500)
                    .collect { ScheduleNotifier.refresh(applicationContext, announce = false) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readWorkRequest(intent)
    }

    private fun readWorkRequest(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_WORK_ACTION) ?: return
        pendingWork.value = WorkAction.entries.firstOrNull { it.name == name }
        intent.removeExtra(EXTRA_WORK_ACTION)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Broadcast tidak diterima saat app di latar belakang, jadi cek ulang tanggal setiap kembali.
        clock.refresh()
    }

    override fun onStop() {
        unregisterReceiver(timeChangeReceiver)
        super.onStop()
    }

    companion object {
        // Disimpan di luar activity supaya ViewModel yang selamat dari rotasi tetap memakai jam yang sama.
        private val clock = DayClock()

        /** Permintaan membuka daily scrum atau EOD dari notifikasi, dibaca layar lalu dikosongkan. */
        private val pendingWork = MutableStateFlow<WorkAction?>(null)

        const val EXTRA_WORK_ACTION = "workAction"

        private const val STEPS_REFRESH_MILLIS = 5 * 60 * 1000L
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
