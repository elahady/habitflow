package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.sync.HabitSyncManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ManageHabitsViewModel(
    private val repo: HabitRepository,
    private val sync: HabitSyncManager,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val habits: StateFlow<List<Habit>> = repo.observeHabits().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        // Sinkron (push dulu, baru pull) saat layar ini dibuka (tahap 28 langkah 5). Push harus
        // jalan dulu supaya penghapusan/perubahan lokal yang belum terkirim tidak "dihidupkan
        // lagi" oleh pull - lihat catatan di MainActivity.kt. Gagal diam-diam - worker latar
        // belakang yang akan mencoba lagi, UI tidak perlu menunggu ini.
        viewModelScope.launch { sync.syncNow() }
    }

    fun addHabit(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addHabit(name, today()) }
    }

    fun renameHabit(habit: Habit, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.renameHabit(habit, name) }
    }

    fun setMandatory(habit: Habit, mandatory: Boolean) {
        viewModelScope.launch { repo.setMandatory(habit.id, mandatory) }
    }

    /** Habit wajib ditolak di repository, jadi tombol hapus untuk habit wajib tidak berpengaruh. */
    fun deleteHabit(habit: Habit) {
        viewModelScope.launch { repo.deleteHabit(habit.id) }
    }
}
