package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.Habit
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.PlaceLocation
import com.roziqrizal.habitflow.data.ScheduleRepository
import com.roziqrizal.habitflow.domain.prayer.EphemerisPrayerCalculator
import com.roziqrizal.habitflow.domain.prayer.PrayerTimes
import com.roziqrizal.habitflow.domain.schedule.AlarmTime
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.domain.schedule.ScheduleBlock
import com.roziqrizal.habitflow.domain.schedule.nextAlarm as computeNextAlarm
import com.roziqrizal.habitflow.domain.schedule.nowAndNext
import com.roziqrizal.habitflow.domain.schedule.resolveBlocks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class BlockStatus { PAST, CURRENT, UPCOMING }

data class TimelineItem(val resolved: ResolvedBlock, val status: BlockStatus)

data class ScheduleUiState(
    val date: LocalDate,
    val isDayOff: Boolean = false,
    val timeline: List<TimelineItem> = emptyList(),
    val now: ResolvedBlock? = null,
    val next: ResolvedBlock? = null,
    val nowMinute: Int = 0,
)

/** Semua blok (tanpa memandang hari) untuk layar Atur jadwal, dengan waktu sholat hari ini untuk contoh. */
data class ScheduleEditorState(
    val blocks: List<ScheduleBlock> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val prayerTimes: PrayerTimes? = null,
    val location: PlaceLocation = LocationSettings.DEFAULT,
)

/** Emit menit sejak 00.00 sekarang, lalu setiap awal menit berikutnya. */
fun minuteTicker(): Flow<Int> = flow {
    while (true) {
        val now = LocalTime.now()
        emit(now.hour * 60 + now.minute)
        delay((60 - now.second) * 1000L - now.nano / 1_000_000L)
    }
}

class ScheduleViewModel(
    private val repo: ScheduleRepository,
    habitRepo: HabitRepository,
    private val locationSettings: LocationSettings,
    private val clock: DayClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val prayerTimes: Flow<PrayerTimes> = combine(clock.date, locationSettings.location) { date, place ->
        EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone())
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<ScheduleUiState> = combine(
        repo.observeBlocks(),
        repo.observeDaysOff(),
        prayerTimes,
        clock.date,
        minuteTicker(),
    ) { blocks, daysOff, prayers, date, nowMinute ->
        val resolved = resolveBlocks(blocks, prayers, date, isDayOff = date in daysOff)
        val current = nowAndNext(resolved, nowMinute)
        ScheduleUiState(
            date = date,
            isDayOff = date in daysOff,
            timeline = resolved.map { item ->
                // Blok yang sedang ditimpa blok lain (misalnya kerja saat sholat) tetap dianggap belum selesai.
                val status = when {
                    item == current.now -> BlockStatus.CURRENT
                    item.endMinute <= nowMinute || (item.isPoint && item.startMinute <= nowMinute) -> BlockStatus.PAST
                    else -> BlockStatus.UPCOMING
                }
                TimelineItem(item, status)
            },
            now = current.now,
            next = current.next,
            nowMinute = nowMinute,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(date = clock.date.value),
    )

    /** Alarm berikutnya tanpa memandang tanggal yang dimatikan, supaya tanggal itu bisa dinyalakan lagi dari Tentang. */
    val nextAlarm: StateFlow<AlarmTime?> = combine(
        repo.observeBlocks(),
        locationSettings.location,
        clock.date,
        minuteTicker(),
    ) { blocks, place, _, _ ->
        computeNextAlarm(blocks, LocalDateTime.now(), emptySet()) { date ->
            EphemerisPrayerCalculator.calculate(date, place.latitude, place.longitude, zone())
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val editor: StateFlow<ScheduleEditorState> = combine(
        repo.observeBlocks(),
        habitRepo.observeHabits(),
        prayerTimes,
        locationSettings.location,
    ) { blocks, habits, prayers, place ->
        ScheduleEditorState(blocks, habits, prayers, place)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleEditorState(),
    )

    fun setDayOff(off: Boolean) {
        viewModelScope.launch { repo.setDayOff(clock.date.value, off) }
    }

    fun saveBlock(block: ScheduleBlock) {
        viewModelScope.launch { repo.saveBlock(block) }
    }

    fun deleteBlock(blockId: Long) {
        viewModelScope.launch { repo.deleteBlock(blockId) }
    }
}
