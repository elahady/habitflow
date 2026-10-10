package com.roziqrizal.habitflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.BloodPressureEntry
import com.roziqrizal.habitflow.data.DrinkKind
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.HealthRepository
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.data.MealRepository
import com.roziqrizal.habitflow.data.WeightEntry
import com.roziqrizal.habitflow.data.health.StepsReading
import com.roziqrizal.habitflow.data.health.StepsTracker
import com.roziqrizal.habitflow.domain.health.BmiCategory
import com.roziqrizal.habitflow.domain.health.BpCategory
import com.roziqrizal.habitflow.domain.health.WeightPoint
import com.roziqrizal.habitflow.domain.health.WeightTrend
import com.roziqrizal.habitflow.domain.health.bmi
import com.roziqrizal.habitflow.domain.health.bmiCategory
import com.roziqrizal.habitflow.domain.health.bpCategory
import com.roziqrizal.habitflow.domain.health.bpNeedsAdvice
import com.roziqrizal.habitflow.domain.health.defaultTargetKg
import com.roziqrizal.habitflow.domain.health.isValidBloodPressure
import com.roziqrizal.habitflow.domain.health.isValidHeight
import com.roziqrizal.habitflow.domain.health.isValidWeight
import com.roziqrizal.habitflow.domain.health.remainingToTarget
import com.roziqrizal.habitflow.domain.health.weightTrend
import com.roziqrizal.habitflow.domain.meals.WeeklyMealSummary
import com.roziqrizal.habitflow.domain.meals.weeklyMealSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Jendela data untuk grafik di tab Progres, dalam hari. */
const val HEALTH_CHART_DAYS = 90L

/** Keadaan kartu langkah di Hari ini. */
sealed interface StepsUiState {
    data object Loading : StepsUiState

    /**
     * [backgroundOk] salah berarti langkah baru dibaca saat app dibuka. [backgroundAvailable] salah berarti Health Connect
     * di HP ini belum mendukung izin latar belakang, jadi tidak ada yang bisa diminta.
     */
    data class Ready(val steps: Long, val backgroundOk: Boolean, val backgroundAvailable: Boolean) : StepsUiState
    data object NeedsPermission : StepsUiState
    data object NotInstalled : StepsUiState
    data object UpdateRequired : StepsUiState
}

data class WeightSummary(
    val kg: Double,
    val timeMillis: Long,
    val bmi: Double?,
    val category: BmiCategory?,
    val targetKg: Double?,
    /** Positif berarti masih di atas target. */
    val remainingKg: Double?,
    val trend: WeightTrend?,
)

data class BpSummary(val entry: BloodPressureEntry, val category: BpCategory, val needsAdvice: Boolean)

data class HealthUiState(
    val today: LocalDate,
    val steps: StepsUiState = StepsUiState.Loading,
    val weight: WeightSummary? = null,
    val bp: BpSummary? = null,
    val heightCm: Double? = null,
    /** Catatan 90 hari terakhir, urut dari yang paling lama, untuk grafik. */
    val weights: List<WeightEntry> = emptyList(),
    val bps: List<BloodPressureEntry> = emptyList(),
    /** Ringkasan makan tujuh hari terakhir (tahap 23). */
    val mealWeek: WeeklyMealSummary? = null,
)

/** Hasil yang ditampilkan di sheet setelah menyimpan. */
sealed interface HealthSaveResult {
    data class Weight(
        val kg: Double,
        val bmi: Double?,
        val category: BmiCategory?,
        val targetKg: Double?,
        val remainingKg: Double?,
    ) : HealthSaveResult

    data class BloodPressure(val systolic: Int, val diastolic: Int, val category: BpCategory, val needsAdvice: Boolean) :
        HealthSaveResult
}

class HealthViewModel(
    private val repo: HealthRepository,
    private val settings: HealthSettings,
    private val steps: StepsTracker,
    private val meals: MealRepository,
    private val drinks: DrinkRepository,
    private val clock: DayClock,
) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Ringkasan makan tujuh hari terakhir termasuk hari ini (tahap 23). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mealWeek = clock.date.flatMapLatest { today ->
        val days = (0..6).map { today.minusDays(it.toLong()) }
        val from = days.last()
        combine(meals.observeMealsBetween(from, today), drinks.observeCounts(DrinkKind.COFFEE, from, today)) { logged, coffee ->
            weeklyMealSummary(days, logged, coffee)
        }
    }

    private val stepsState = MutableStateFlow<StepsUiState>(StepsUiState.Loading)

    private val _saved = MutableStateFlow<HealthSaveResult?>(null)
    val saved: StateFlow<HealthSaveResult?> = _saved.asStateFlow()

    val state: StateFlow<HealthUiState> = combine(
        repo.observeWeights(),
        repo.observeBloodPressures(),
        settings.heightCm,
        settings.targetKg,
        combine(stepsState, clock.date, mealWeek) { s, d, m -> Triple(s, d, m) },
    ) { weights, bps, height, target, (stepsUi, today, mealSummary) ->
        val from = today.minusDays(HEALTH_CHART_DAYS)
        val points = weights.map { WeightPoint(dateOf(it.timeMillis), it.timeMillis, it.kg) }
        val latestWeight = weights.lastOrNull()
        HealthUiState(
            today = today,
            steps = stepsUi,
            weight = latestWeight?.let { summarizeWeight(it, height, target, points, today) },
            bp = bps.lastOrNull()?.let { BpSummary(it, bpCategory(it.systolic, it.diastolic), bpNeedsAdvice(it.systolic, it.diastolic)) },
            heightCm = height,
            weights = weights.filter { !dateOf(it.timeMillis).isBefore(from) },
            bps = bps.filter { !dateOf(it.timeMillis).isBefore(from) },
            mealWeek = mealSummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HealthUiState(today = clock.date.value),
    )

    private fun dateOf(timeMillis: Long): LocalDate = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()

    private fun summarizeWeight(
        entry: WeightEntry,
        heightCm: Double?,
        targetKg: Double?,
        points: List<WeightPoint>,
        today: LocalDate,
    ): WeightSummary {
        val value = heightCm?.let { bmi(entry.kg, it) }
        val target = targetKg ?: heightCm?.let(::defaultTargetKg)
        return WeightSummary(
            kg = entry.kg,
            timeMillis = entry.timeMillis,
            bmi = value,
            category = value?.let(::bmiCategory),
            targetKg = target,
            remainingKg = target?.let { remainingToTarget(entry.kg, it) },
            trend = weightTrend(points, today),
        )
    }

    /** Membaca langkah dan mencentang habit kalau target tercapai. Dipanggil saat app dibuka dan berkala saat tampil. */
    fun refreshSteps() {
        viewModelScope.launch {
            stepsState.value = runCatching { steps.refresh(clock.date.value, zone, inBackground = false) }
                .map { reading ->
                    when (reading) {
                        is StepsReading.Available -> StepsUiState.Ready(reading.steps, reading.background, reading.backgroundAvailable)
                        StepsReading.NeedsPermission -> StepsUiState.NeedsPermission
                        StepsReading.NotInstalled -> StepsUiState.NotInstalled
                        StepsReading.UpdateRequired -> StepsUiState.UpdateRequired
                    }
                }
                // Gagal membaca (misalnya Health Connect sedang tidak siap) tidak boleh merusak dashboard.
                .getOrDefault(stepsState.value)
        }
    }

    suspend fun stepsPermissions(): Set<String> = steps.permissionsToRequest()

    /**
     * Menyimpan berat. [heightCm] hanya dipakai kalau tinggi belum ada. Mengembalikan false kalau angkanya tidak sah,
     * dan tidak menyimpan apa-apa.
     */
    fun saveWeight(kg: Double, heightCm: Double?): Boolean {
        if (!isValidWeight(kg)) return false
        val height = settings.heightCm.value ?: heightCm?.also { if (!isValidHeight(it)) return false }
        viewModelScope.launch {
            height?.let { if (settings.heightCm.value == null) settings.setHeightCm(it) }
            repo.addWeight(kg, System.currentTimeMillis())
            val value = height?.let { bmi(kg, it) }
            val target = settings.targetKg.value ?: height?.let(::defaultTargetKg)
            _saved.value = HealthSaveResult.Weight(
                kg = kg,
                bmi = value,
                category = value?.let(::bmiCategory),
                targetKg = target,
                remainingKg = target?.let { remainingToTarget(kg, it) },
            )
        }
        return true
    }

    fun saveBloodPressure(systolic: Int, diastolic: Int, pulse: Int?, note: String?): Boolean {
        if (!isValidBloodPressure(systolic, diastolic, pulse)) return false
        viewModelScope.launch {
            repo.addBloodPressure(systolic, diastolic, pulse, note, System.currentTimeMillis())
            _saved.value = HealthSaveResult.BloodPressure(
                systolic, diastolic, bpCategory(systolic, diastolic), bpNeedsAdvice(systolic, diastolic),
            )
        }
        return true
    }

    fun clearSaved() {
        _saved.value = null
    }
}
