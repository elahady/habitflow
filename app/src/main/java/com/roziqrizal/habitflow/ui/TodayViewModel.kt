package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.DrinkKind
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.MealRepository
import com.roziqrizal.habitflow.data.Todo
import com.roziqrizal.habitflow.domain.canAddTodo
import com.roziqrizal.habitflow.domain.completeDays
import com.roziqrizal.habitflow.domain.currentStreak
import com.roziqrizal.habitflow.domain.meals.Meal
import com.roziqrizal.habitflow.domain.meals.MealKind
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
    /** Catatan makan hari ini menurut jenisnya, dan gelas kopi dan minuman manis (tahap 23). */
    val meals: Map<MealKind, Meal> = emptyMap(),
    val coffee: Int = 0,
    val sweet: Int = 0,
)

/** Asupan hari ini yang dibaca bersama: air, makan, kopi, dan minuman manis. */
private data class Intake(val glasses: Int, val meals: List<Meal>, val coffee: Int, val sweet: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repo: HabitRepository,
    private val drinks: DrinkRepository,
    private val meals: MealRepository,
    private val clock: DayClock,
) : ViewModel() {

    val state: StateFlow<TodayUiState> = combine(
        repo.observeHabits(),
        repo.observeAllEntries(),
        repo.observeAllTodos(),
        clock.date,
        clock.date.flatMapLatest { date ->
            combine(
                drinks.observeGlasses(date),
                meals.observeMeals(date),
                drinks.observeCount(date, DrinkKind.COFFEE),
                drinks.observeCount(date, DrinkKind.SWEET),
                ::Intake,
            )
        },
    ) { habits, entries, todos, now, intake ->
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
            glasses = intake.glasses,
            meals = intake.meals.associateBy { it.kind },
            coffee = intake.coffee,
            sweet = intake.sweet,
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

    fun addCoffee() {
        viewModelScope.launch { drinks.add(clock.date.value, DrinkKind.COFFEE) }
    }

    fun removeCoffee() {
        viewModelScope.launch { drinks.remove(clock.date.value, DrinkKind.COFFEE) }
    }

    fun addSweetDrink() {
        viewModelScope.launch { drinks.add(clock.date.value, DrinkKind.SWEET) }
    }

    fun removeSweetDrink() {
        viewModelScope.launch { drinks.remove(clock.date.value, DrinkKind.SWEET) }
    }

    /** Simpan catatan makan (tahap 23). Satu catatan per tanggal dan jenis: yang lama diganti. */
    fun saveMeal(meal: Meal) {
        viewModelScope.launch { meals.save(meal) }
    }

    fun deleteMeal(kind: MealKind) {
        viewModelScope.launch { meals.delete(clock.date.value, kind) }
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
