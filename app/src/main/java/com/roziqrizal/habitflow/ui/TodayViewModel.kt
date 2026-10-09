package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.domain.canAddTodo
import com.roziqrizal.habitflow.domain.completeDays
import com.roziqrizal.habitflow.domain.currentStreak
import com.roziqrizal.habitflow.domain.scoreDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitItem(
    val habit: Habit,
    val doneToday: Boolean,
)

data class TodayUiState(
    val date: LocalDate,
    val habits: List<HabitItem> = emptyList(),
    val todos: List<Todo> = emptyList(),
    val totalHabits: Int = 0,
    val doneHabits: Int = 0,
    val doneTodos: Int = 0,
    val level: Int = 0,
    val streak: Int = 0,
    val canAddTodo: Boolean = true,
    /** Gelas air hari ini (tahap 20). */
    val glasses: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: HabitRepository,
    private val drinks: DrinkRepository,
    private val clock: DayClock,
) : ViewModel() {

    val state: StateFlow<TodayUiState> = combine(
        repo.observeHabits(),
        repo.observeAllEntries(),
        repo.observeAllTodos(),
        clock.date,
        clock.date.flatMapLatest { drinks.observeGlasses(it) },
    ) { habits, entries, todos, now, glasses ->
        val nowKey = now.toString()
        val createdOn = habits.associate { it.id to LocalDate.parse(it.createdAt) }

        val doneByDate = entries.groupBy({ LocalDate.parse(it.date) }, { it.habitId }).mapValues { it.value.toSet() }
        val doneTodosByDate = todos.filter { it.done }.groupingBy { LocalDate.parse(it.date) }.eachCount()

        // Streak hanya bisa berasal dari tanggal yang punya centang habit, ditambah hari ini.
        val complete = completeDays(createdOn, doneByDate, doneTodosByDate, doneByDate.keys + now)

        val doneIdsToday = doneByDate[now].orEmpty()
        val score = scoreDay(now, createdOn, doneIdsToday, doneTodosByDate[now] ?: 0)
        val todosToday = todos.filter { it.date == nowKey }

        TodayUiState(
            date = now,
            habits = habits.map { HabitItem(it, it.id in doneIdsToday) },
            todos = todosToday.sortedWith(compareBy({ it.done }, { it.id })),
            totalHabits = score.totalHabits,
            doneHabits = score.doneHabits,
            doneTodos = score.doneTodos,
            level = score.level,
            streak = currentStreak(complete, now),
            canAddTodo = canAddTodo(todosToday.size),
            glasses = glasses,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(date = clock.date.value),
    )

    init {
        // Pindahkan to-do yang belum selesai dari hari sebelumnya saat layar dibuka, dan ulangi
        // setiap kali hari berganti selama app masih hidup.
        viewModelScope.launch { clock.date.collect { repo.carryOver(it) } }
    }

    fun toggleHabit(habitId: Long) {
        viewModelScope.launch { repo.toggleHabit(habitId, clock.date.value) }
    }

    fun addGlass() {
        viewModelScope.launch { drinks.addGlass(clock.date.value) }
    }

    fun removeGlass() {
        viewModelScope.launch { drinks.removeGlass(clock.date.value) }
    }

    fun addTodo(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.addTodo(title, clock.date.value) }
    }

    fun toggleTodo(todo: Todo) {
        viewModelScope.launch { repo.toggleTodo(todo) }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch { repo.deleteTodo(todo.id) }
    }
}
