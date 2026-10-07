package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.data.WorkRepository
import com.roziqrizal.habitflow.domain.work.FollowUp
import com.roziqrizal.habitflow.domain.work.WorkDay
import com.roziqrizal.habitflow.domain.work.doneDate
import com.roziqrizal.habitflow.domain.HEATMAP_MAX_WEEKS
import com.roziqrizal.habitflow.domain.completeDays
import com.roziqrizal.habitflow.domain.currentStreak
import com.roziqrizal.habitflow.domain.habitLevelOn
import com.roziqrizal.habitflow.domain.heatmapStart
import com.roziqrizal.habitflow.domain.longestStreak
import com.roziqrizal.habitflow.domain.scoreDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Level sel heatmap satu habit untuk setiap hari dalam jendela heatmap terlebar. */
data class HabitHeat(
    val habit: Habit,
    val levels: Map<LocalDate, Int>,
)

data class ContributionUiState(
    val today: LocalDate,
    val combined: Map<LocalDate, Int> = emptyMap(),
    val perHabit: List<HabitHeat> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val createdOn: Map<Long, LocalDate> = emptyMap(),
    val doneIdsByDate: Map<LocalDate, Set<Long>> = emptyMap(),
    val todosByDate: Map<LocalDate, List<Todo>> = emptyMap(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** Follow-up yang selesai per tanggal dan catatan harian kerja (EOD), untuk detail hari. */
    val followUpsDoneByDate: Map<LocalDate, List<FollowUp>> = emptyMap(),
    val workDays: Map<LocalDate, WorkDay> = emptyMap(),
)

class ContributionViewModel(
    repo: HabitRepository,
    work: WorkRepository,
    private val clock: DayClock,
) : ViewModel() {

    val state: StateFlow<ContributionUiState> = combine(
        repo.observeHabits(),
        repo.observeAllEntries(),
        repo.observeAllTodos(),
        combine(work.observeFollowUps(), work.observeWorkDays()) { followUps, days -> followUps to days },
        clock.date,
    ) { habits, entries, todos, workData, now ->
        val (followUps, workDays) = workData
        val createdOn = habits.associate { it.id to LocalDate.parse(it.createdAt) }
        val doneIdsByDate = entries.groupBy({ LocalDate.parse(it.date) }, { it.habitId }).mapValues { it.value.toSet() }
        val todosByDate = todos.groupBy { LocalDate.parse(it.date) }
        val doneTodosByDate = todosByDate.mapValues { entry -> entry.value.count { it.done } }

        // Hari dalam jendela heatmap terlebar, dari awal sampai hari ini. Grid sendiri yang
        // memilih berapa minggu yang tampil sesuai lebar layar.
        val windowDays = generateSequence(heatmapStart(now, HEATMAP_MAX_WEEKS)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(now) }
            .toList()

        val combined = windowDays.associateWith { day ->
            scoreDay(day, createdOn, doneIdsByDate[day].orEmpty(), doneTodosByDate[day] ?: 0).level
        }
        val perHabit = habits.map { habit ->
            val created = createdOn.getValue(habit.id)
            HabitHeat(
                habit = habit,
                levels = windowDays.associateWith { day ->
                    habitLevelOn(created, doneIdsByDate[day]?.contains(habit.id) == true, day)
                },
            )
        }

        // Streak memakai seluruh riwayat, bukan hanya jendela heatmap.
        val complete = completeDays(createdOn, doneIdsByDate, doneTodosByDate, doneIdsByDate.keys + now)

        ContributionUiState(
            today = now,
            combined = combined,
            perHabit = perHabit,
            habits = habits,
            createdOn = createdOn,
            doneIdsByDate = doneIdsByDate,
            todosByDate = todosByDate,
            currentStreak = currentStreak(complete, now),
            longestStreak = longestStreak(complete),
            followUpsDoneByDate = followUps.mapNotNull { f -> f.doneDate(ZoneId.systemDefault())?.let { it to f } }
                .groupBy({ it.first }, { it.second }),
            workDays = workDays,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ContributionUiState(today = clock.date.value),
    )
}
