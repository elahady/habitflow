package com.roziqrizal.habitflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.PlaceLocation
import com.roziqrizal.habitflow.data.sync.HealthFocusPayload
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.data.calendar.CalendarSettings
import com.roziqrizal.habitflow.data.calendar.PhoneCalendarSource
import com.roziqrizal.habitflow.domain.meals.MealKind
import com.roziqrizal.habitflow.domain.schedule.WorkAction
import com.roziqrizal.habitflow.ui.theme.tokens
import java.time.LocalTime

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Hari ini", AppIcons.Home),
    WORK("Kerja", AppIcons.Briefcase),
    TEAM("Tugas Rumah", AppIcons.Users),
    CONTRIBUTION("Progres", AppIcons.BarChart),
    HABITS("Habit", AppIcons.CheckSquare),
    SETTINGS("Setelan", AppIcons.Settings),
}

/** Layar penuh di atas tab Kerja. */
private enum class WorkSession { SCRUM, EOD, AGENDA }

@Composable
fun HabitFlowApp(
    today: TodayViewModel,
    contribution: ContributionViewModel,
    manage: ManageHabitsViewModel,
    schedule: ScheduleViewModel,
    agenda: AgendaViewModel,
    work: WorkViewModel,
    syncViewModel: SyncViewModel,
    accountViewModel: AccountViewModel,
    team: TeamViewModel,
    /** Token undangan tim dari deep link (tahap 26 langkah 2). Null kalau tidak ada. */
    pendingInviteToken: String?,
    onInviteTokenHandled: () -> Unit,
    /** Permintaan membuka tab Tugas Rumah dari notifikasi tugas baru (tahap 26 langkah 4). */
    openTeamRequest: Boolean,
    onOpenTeamRequestHandled: () -> Unit,
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
    mealReminder: Boolean,
    onMealReminderChange: (Boolean) -> Unit,
    /** Fokus kesehatan yang dihitung layar Hari ini, diteruskan ke penyimpan untuk dikirim ke server (tahap 30). */
    onFocusComputed: (HealthFocusPayload) -> Unit,
    health: HealthViewModel,
    healthSettings: HealthSettings,
    phoneCalendar: PhoneCalendarSource,
    calendarSettings: CalendarSettings,
    onRequestStepsAccess: () -> Unit,
    onOpenHealthConnectStore: () -> Unit,
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
    // Layar Profil (31I): layar penuh di atas tab, dibuka dari lingkaran inisial di Hari ini.
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var sessionName by rememberSaveable { mutableStateOf<String?>(null) }
    var showCapture by rememberSaveable { mutableStateOf(false) }
    // Sheet catat berat atau tensi (tahap 21). Null = tertutup.
    var recordModeName by rememberSaveable { mutableStateOf<String?>(null) }
    // Sheet catat makan (tahap 23). Null = tertutup.
    var mealKindName by rememberSaveable { mutableStateOf<String?>(null) }
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

    // Notifikasi tugas tim baru membuka tab Tugas Rumah (tahap 26 langkah 4).
    LaunchedEffect(openTeamRequest) {
        if (openTeamRequest) {
            if (current != Tab.TEAM) {
                history.remove(Tab.TEAM)
                history.add(Tab.TEAM)
            }
            onOpenTeamRequestHandled()
        }
    }

    // Link undangan tim dari deep link (tahap 26 langkah 2): coba terima begitu akun sudah ada
    // (langsung kalau sudah login, atau begitu login selesai dari dalam dialog).
    val accountStateForInvite by accountViewModel.state.collectAsState()
    val joinState by team.joinState.collectAsState()
    LaunchedEffect(pendingInviteToken, accountStateForInvite.account) {
        if (pendingInviteToken != null) team.acceptInvite(pendingInviteToken)
    }

    BackHandler(enabled = showCapture) { showCapture = false }
    BackHandler(enabled = showProfile) { showProfile = false }
    BackHandler(enabled = showSchedule) { showSchedule = false }
    BackHandler(enabled = session != null) { sessionName = null }
    BackHandler(enabled = !showProfile && !showSchedule && session == null && history.size > 1) {
        history.removeAt(history.lastIndex)
    }

    val workState by work.state.collectAsState()
    val briefCard by work.brief.collectAsState()
    // Ringkasan dari Claude ditarik tiap tab Kerja dibuka. Gagal tanpa suara, kartu memakai salinan di HP.
    LaunchedEffect(current) { if (current == Tab.WORK) work.refreshBrief() }
    val healthState by health.state.collectAsState()
    val fullScreen = showProfile || showSchedule || session != null

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
                            // Layar penuh (Atur jadwal, Profil, Scrum/EOD) menutupi tab, jadi ditutup dulu supaya tab yang
                            // diketuk langsung terlihat, sama seperti tombol kembali.
                            showSchedule = false
                            showProfile = false
                            sessionName = null
                            if (tab != current) {
                                history.remove(tab)
                                history.add(tab)
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 2, fontSize = 11.sp, lineHeight = 12.sp, textAlign = TextAlign.Center) },
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
            if (showProfile) {
                val profileState by accountViewModel.state.collectAsState()
                ProfileScreen(
                    state = profileState,
                    actions = AccountActions(
                        onLoadMe = accountViewModel::loadMe,
                        onRequestSignOut = accountViewModel::requestSignOut,
                        onRequestSignOutAllDevices = accountViewModel::requestSignOutAllDevices,
                        onConfirmSignOut = accountViewModel::confirmSignOut,
                        onDismissSignOut = accountViewModel::dismissSignOut,
                        onRequestDelete = accountViewModel::requestDeleteAccount,
                        onDeleteWithPassword = accountViewModel::deleteWithPassword,
                        onDeleteWithGoogle = accountViewModel::deleteWithGoogle,
                        onDismissDelete = accountViewModel::dismissDelete,
                        onOpenChangePassword = accountViewModel::openChangePassword,
                        onChangePassword = accountViewModel::changePassword,
                        onDismissChangePassword = accountViewModel::dismissChangePassword,
                        onSyncHabitsNow = accountViewModel::syncHabitsNow,
                    ),
                    healthSettings = healthSettings,
                    onClose = {
                        accountViewModel.clearMessage()
                        showProfile = false
                    },
                )
                return@Box
            }
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
                WorkSession.AGENDA -> {
                    val agendaState by agenda.state.collectAsState()
                    AgendaScreen(
                        state = agendaState,
                        onShiftMonth = agenda::shiftMonth,
                        onSelectDay = agenda::selectDay,
                        onSaveEvent = agenda::saveEvent,
                        onDeleteEvent = agenda::deleteEvent,
                        onSkip = agenda::skip,
                        onChangeOccurrence = agenda::changeOccurrence,
                        onRestoreOccurrence = agenda::restoreOccurrence,
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
                    val nextAlarmToday by schedule.nextAlarm.collectAsState()
                    TodayScreen(
                        state = state,
                        schedule = scheduleState,
                        work = workState,
                        onOpenWork = {
                            history.remove(Tab.WORK)
                            history.add(Tab.WORK)
                        },
                        onSetDayOff = schedule::setDayOff,
                        onSetHolidayCancelled = schedule::setHolidayCancelled,
                        onOpenSchedule = { showSchedule = true },
                        onToggleHabit = today::toggleHabit,
                        onAddGlass = today::addGlass,
                        onRemoveGlass = today::removeGlass,
                        onAddCoffee = today::addCoffee,
                        onRemoveCoffee = today::removeCoffee,
                        onAddSweetDrink = today::addSweetDrink,
                        onRemoveSweetDrink = today::removeSweetDrink,
                        onOpenMeal = { mealKindName = it.name },
                        health = healthState,
                        onRequestStepsAccess = onRequestStepsAccess,
                        onOpenHealthConnectStore = onOpenHealthConnectStore,
                        onRecordBp = { recordModeName = HealthRecordMode.BLOOD_PRESSURE.name },
                        onRecordWeight = { recordModeName = HealthRecordMode.WEIGHT.name },
                        onAddTodo = today::addTodo,
                        onToggleTodo = today::toggleTodo,
                        onDeleteTodo = today::deleteTodo,
                        accountName = accountStateForInvite.account?.name,
                        onOpenProfile = { showProfile = true },
                        alarmPrompt = rememberExactAlarmPrompt(nextAlarmToday, alarmSettings),
                        onFocusComputed = onFocusComputed,
                    )
                }
                Tab.WORK -> WorkScreen(
                    state = workState,
                    brief = briefCard,
                    onOpenScrum = { sessionName = WorkSession.SCRUM.name },
                    onOpenEod = { sessionName = WorkSession.EOD.name },
                    onOpenAgenda = { sessionName = WorkSession.AGENDA.name },
                    onSelectPerson = work::selectPerson,
                    onDone = work::setDone,
                    onSave = work::save,
                    onDelete = work::delete,
                    onResolveConflict = work::resolveConflict,
                )
                Tab.TEAM -> {
                    val teamState by team.state.collectAsState()
                    TeamScreen(
                        state = teamState,
                        onCreateTeam = team::createTeam,
                        onCreateInvite = team::createInvite,
                        onInviteShared = team::clearInviteUrl,
                        onJoinWithToken = team::acceptInvite,
                        onAddTodo = team::addTodo,
                        onSetDone = team::setDone,
                        onAssign = team::assign,
                        onDelete = team::deleteTodo,
                    )
                }
                Tab.CONTRIBUTION -> {
                    val state by contribution.state.collectAsState()
                    ProgressScreen(contribution = state, health = healthState)
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
                Tab.SETTINGS -> {
                    val nextAlarm by schedule.nextAlarm.collectAsState()
                    val syncState by syncViewModel.state.collectAsState()
                    SettingsScreen(
                        versionName = versionName,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        location = location,
                        onLocationChange = onLocationChange,
                        persistentNotification = persistentNotification,
                        onPersistentNotificationChange = onPersistentNotificationChange,
                        waterReminders = waterReminders,
                        onWaterRemindersChange = onWaterRemindersChange,
                        breakReminders = breakReminders,
                        onBreakRemindersChange = onBreakRemindersChange,
                        mealReminder = mealReminder,
                        onMealReminderChange = onMealReminderChange,
                        healthSettings = healthSettings,
                        phoneCalendar = phoneCalendar,
                        calendarSettings = calendarSettings,
                        nextAlarm = nextAlarm,
                        alarmSettings = alarmSettings,
                        sync = syncState,
                        syncActions = SyncActions(
                            onSetEnabled = syncViewModel::setEnabled,
                            onSync = syncViewModel::syncNow,
                            onPrepareRestore = syncViewModel::prepareRestore,
                            onDismissRestore = syncViewModel::dismissRestore,
                            onConfirmRestore = syncViewModel::confirmRestore,
                        ),
                    )
                }
            }
        }
    }

    if (showCapture) {
        val ongoingEvent = schedule.state.collectAsState().value.ongoingWorkEvent
        QuickCaptureSheet(
            onAdd = { work.quickAdd(it, ongoingEvent) },
            linkedEventTitle = ongoingEvent?.title,
            onDismiss = { showCapture = false },
        )
    }

    if (pendingInviteToken != null) {
        JoinTeamDialog(
            joinState = joinState,
            onDismiss = {
                if (joinState is JoinInviteState.Success) {
                    history.remove(Tab.TEAM)
                    history.add(Tab.TEAM)
                }
                team.dismissJoin()
                onInviteTokenHandled()
            },
        )
    }

    mealKindName?.let { name ->
        val kind = MealKind.valueOf(name)
        val todayState by today.state.collectAsState()
        MealSheet(
            kind = kind,
            date = todayState.date,
            initial = todayState.meals[kind],
            defaultMinute = LocalTime.now().let { it.hour * 60 + it.minute },
            onSave = {
                today.saveMeal(it)
                mealKindName = null
            },
            onDelete = {
                today.deleteMeal(kind)
                mealKindName = null
            },
            onDismiss = { mealKindName = null },
        )
    }

    recordModeName?.let { name ->
        val saved by health.saved.collectAsState()
        HealthRecordSheet(
            mode = HealthRecordMode.valueOf(name),
            heightCm = healthState.heightCm,
            saved = saved,
            onSaveWeight = health::saveWeight,
            onSaveBloodPressure = health::saveBloodPressure,
            onDismiss = {
                health.clearSaved()
                recordModeName = null
            },
        )
    }
}
