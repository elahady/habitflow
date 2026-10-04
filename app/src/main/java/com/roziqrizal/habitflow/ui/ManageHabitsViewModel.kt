package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ManageHabitsViewModel(
    private val repo: HabitRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val habits: StateFlow<List<Habit>> = repo.observeHabits().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun addHabit(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addHabit(name, today()) }
    }

    fun renameHabit(habit: Habit, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameHabit(habit, name) }
    }

    /** Habit wajib ditolak di repository, jadi tombol hapus untuk habit wajib tidak berpengaruh. */
    fun deleteHabit(habit: Habit) {
        viewModelScope.launch { repo.deleteHabit(habit.id) }
    }
}
