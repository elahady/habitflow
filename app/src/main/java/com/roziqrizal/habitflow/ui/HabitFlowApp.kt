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
import com.roziqrizal.habitflow.ui.theme.tokens

enum class Tab(val label: String) {
    TODAY("Hari ini"),
    CONTRIBUTION("Kontribusi"),
    HABITS("Habit"),
    ABOUT("Tentang"),
}

@Composable
fun HabitFlowApp(
    today: TodayViewModel,
    contribution: ContributionViewModel,
    manage: ManageHabitsViewModel,
    schedule: ScheduleViewModel,
    versionName: String,
    themeMode: ThemeMode,
    location: PlaceLocation,
    onLocationChange: (PlaceLocation) -> Unit,
    persistentNotification: Boolean,
    alarmSettings: AlarmSettings,
    onPersistentNotificationChange: (Boolean) -> Unit,
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

    BackHandler(enabled = showSchedule) { showSchedule = false }
    BackHandler(enabled = !showSchedule && history.size > 1) {
        history.removeAt(history.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
            when (current) {
                Tab.TODAY -> {
                    val state by today.state.collectAsState()
                    val scheduleState by schedule.state.collectAsState()
                    TodayScreen(
                        state = state,
                        schedule = scheduleState,
                        onSetDayOff = schedule::setDayOff,
                        onOpenSchedule = { showSchedule = true },
                        onToggleHabit = today::toggleHabit,
                        onAddTodo = today::addTodo,
                        onToggleTodo = today::toggleTodo,
                        onDeleteTodo = today::deleteTodo,
                    )
                }
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
                    AboutScreen(
                        versionName = versionName,
                        themeMode = themeMode,
                        location = location,
                        onLocationChange = onLocationChange,
                        persistentNotification = persistentNotification,
                        nextAlarm = nextAlarm,
                        alarmSettings = alarmSettings,
                        onPersistentNotificationChange = onPersistentNotificationChange,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
            }
        }
    }
}
