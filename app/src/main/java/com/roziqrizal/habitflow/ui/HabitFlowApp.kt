package com.roziqrizal.habitflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.PlaceLocation
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import com.roziqrizal.habitflow.ui.theme.tokens

enum class Tab(val label: String) {
    TODAY("Hari ini"),
    WORK("Kerja"),
    CONTRIBUTION("Kontribusi"),
    HABITS("Habit"),
    ABOUT("Tentang"),
}

/** Layar penuh di atas tab Kerja. */
private enum class WorkSession { SCRUM, EOD }

@Composable
fun HabitFlowApp(
    today: TodayViewModel,
    contribution: ContributionViewModel,
    manage: ManageHabitsViewModel,
    schedule: ScheduleViewModel,
    work: WorkViewModel,
    syncViewModel: SyncViewModel,
    /** Permintaan membuka daily scrum atau EOD dari notifikasi. Null kalau tidak ada. */
    workRequest: WorkAction?,
    onWorkRequestHandled: () -> Unit,
    versionName: String,
    themeMode: ThemeMode,
    location: PlaceLocation,
    onLocationChange: (PlaceLocation) -> Unit,
    persistentNotification: Boolean,
    alarmSettings: AlarmSettings,
    onPersistentNotificationChange: (Boolean) -> Unit,
    waterReminders: Boolean,
    onWaterRemindersChange: (Boolean) -> Unit,
    breakReminders: Boolean,
    onBreakRemindersChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    // Riwayat tab, dari yang paling lama sampai tab aktif. Tombol kembali membuka tab sebelumnya.
    val history = rememberSaveable(
        saver = listSaver<SnapshotStateList<Tab>, String>(
            save = { tabs -> tabs.map { it.name } },
            restore = { names -> names.map { Tab.valueOf(it) }.toMutableStateList() },
        ),
    ) { mutableStateListOf(Tab.TODAY) }
    val current = history.last()
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var sessionName by rememberSaveable { mutableStateOf<String?>(null) }
    var showCapture by rememberSaveable { mutableStateOf(false) }
    val session = sessionName?.let { WorkSession.valueOf(it) }

    // Notifikasi daily scrum atau EOD membuka tab Kerja langsung di layar yang sesuai.
    LaunchedEffect(workRequest) {
        if (workRequest != null) {
            if (current != Tab.WORK) {
                history.remove(Tab.WORK)
                history.add(Tab.WORK)
            }
            showSchedule = false
            sessionName = (if (workRequest == WorkAction.SCRUM) WorkSession.SCRUM else WorkSession.EOD).name
            onWorkRequestHandled()
        }
    }

    BackHandler(enabled = showCapture) { showCapture = false }
    BackHandler(enabled = showSchedule) { showSchedule = false }
    BackHandler(enabled = session != null) { sessionName = null }
    BackHandler(enabled = !showSchedule && session == null && history.size > 1) {
        history.removeAt(history.lastIndex)
    }

    val workState by work.state.collectAsState()
    val fullScreen = showSchedule || session != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        floatingActionButton = { if (!fullScreen) QuickCaptureButton { showCapture = true } },
        bottomBar = {
            // Indikator pill hijau muda dan label terpilih gelap, seperti navigasi di prototipe Rizqflow.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = {
                            if (tab != current) {
                                history.remove(tab)
                                history.add(tab)
                            }
                        },
                        icon = {},
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.tokens.primaryFixed,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showSchedule) {
                val editor by schedule.editor.collectAsState()
                ScheduleScreen(
                    state = editor,
                    onSave = schedule::saveBlock,
                    onDelete = schedule::deleteBlock,
                    onClose = { showSchedule = false },
                )
                return@Box
            }
            when (session) {
                WorkSession.SCRUM -> {
                    ScrumScreen(
                        state = workState,
                        onPick = work::pickForToday,
                        onFinish = {
                            work.completeScrum()
                            sessionName = null
                        },
                        onClose = { sessionName = null },
                    )
                    return@Box
                }
                WorkSession.EOD -> {
                    EodScreen(
                        state = workState,
                        onSave = work::completeEod,
                        onClose = { sessionName = null },
                    )
                    return@Box
                }
                null -> Unit
            }
            when (current) {
                Tab.TODAY -> {
                    val state by today.state.collectAsState()
                    val scheduleState by schedule.state.collectAsState()
                    TodayScreen(
                        state = state,
                        schedule = scheduleState,
                        work = workState,
                        onOpenWork = {
                            history.remove(Tab.WORK)
                            history.add(Tab.WORK)
                        },
                        onSetDayOff = schedule::setDayOff,
                        onOpenSchedule = { showSchedule = true },
                        onToggleHabit = today::toggleHabit,
                        onAddGlass = today::addGlass,
                        onRemoveGlass = today::removeGlass,
                        onAddTodo = today::addTodo,
                        onToggleTodo = today::toggleTodo,
                        onDeleteTodo = today::deleteTodo,
                    )
                }
                Tab.WORK -> WorkScreen(
                    state = workState,
                    onOpenScrum = { sessionName = WorkSession.SCRUM.name },
                    onOpenEod = { sessionName = WorkSession.EOD.name },
                    onSelectPerson = work::selectPerson,
                    onDone = work::setDone,
                    onSave = work::save,
                    onDelete = work::delete,
                )
                Tab.CONTRIBUTION -> {
                    val state by contribution.state.collectAsState()
                    ContributionScreen(state = state)
                }
                Tab.HABITS -> {
                    val habits by manage.habits.collectAsState()
                    ManageHabitsScreen(
                        habits = habits,
                        onAdd = manage::addHabit,
                        onRename = manage::renameHabit,
                        onSetMandatory = manage::setMandatory,
                        onDelete = manage::deleteHabit,
                    )
                }
                Tab.ABOUT -> {
                    val nextAlarm by schedule.nextAlarm.collectAsState()
                    val syncState by syncViewModel.state.collectAsState()
                    AboutScreen(
                        versionName = versionName,
                        themeMode = themeMode,
                        location = location,
                        onLocationChange = onLocationChange,
                        persistentNotification = persistentNotification,
                        nextAlarm = nextAlarm,
                        alarmSettings = alarmSettings,
                        sync = syncState,
                        syncActions = SyncActions(
                            onSaveServer = syncViewModel::saveServer,
                            onSetEnabled = syncViewModel::setEnabled,
                            onTest = syncViewModel::testConnection,
                            onSync = syncViewModel::syncNow,
                            onPrepareRestore = syncViewModel::prepareRestore,
                            onDismissRestore = syncViewModel::dismissRestore,
                            onConfirmRestore = syncViewModel::confirmRestore,
                        ),
                        onPersistentNotificationChange = onPersistentNotificationChange,
                        waterReminders = waterReminders,
                        onWaterRemindersChange = onWaterRemindersChange,
                        breakReminders = breakReminders,
                        onBreakRemindersChange = onBreakRemindersChange,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
            }
        }
    }

    if (showCapture) {
        QuickCaptureSheet(onAdd = work::quickAdd, onDismiss = { showCapture = false })
    }
}
