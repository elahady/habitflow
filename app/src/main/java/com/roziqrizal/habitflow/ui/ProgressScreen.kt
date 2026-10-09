package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.BloodPressureEntry
import com.roziqrizal.habitflow.data.WeightEntry
import com.roziqrizal.habitflow.domain.health.bpCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val SEGMENTS = listOf("Habit", "Kesehatan")

/**
 * Tab Progres (dulu Kontribusi): dua segmen. Habit memakai isi lama (streak dan heatmap), Kesehatan berisi ringkasan dan
 * grafik berat dan tensi 90 hari terakhir (tahap 21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(contribution: ContributionUiState, health: HealthUiState) {
    var segment by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Progres", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SEGMENTS.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = segment == index,
                        onClick = { segment = index },
                        shape = SegmentedButtonDefaults.itemShape(index, SEGMENTS.size),
                        colors = appSegmentedColors(),
                        label = { Text(label) },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (segment == 0) {
                ContributionScreen(state = contribution, showTitle = false)
            } else {
                HealthProgress(health)
            }
        }
    }
}

@Composable
private fun HealthProgress(state: HealthUiState) {
    val zone = ZoneId.systemDefault()
    val from = state.today.minusDays(HEALTH_CHART_DAYS)
    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SectionTitle("Berat") }
        item { WeightSummaryCard(state) }
        item {
            ChartCard(
                emptyText = "Belum ada catatan berat. Catat dari Hari ini.",
                hasData = state.weights.isNotEmpty(),
            ) {
                LineChart(
                    series = listOf(
                        ChartSeries(
                            points = state.weights.map { ChartPoint(dateOf(it.timeMillis), it.kg) },
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ),
                    from = from,
                    to = state.today,
                    targetLine = state.weight?.targetKg,
                    description = weightChartDescription(state.weights, state.weight?.targetKg),
                )
            }
        }

        item { SectionTitle("Tensi") }
        item { BloodPressureSummaryCard(state) }
        item {
            ChartCard(
                emptyText = "Belum ada catatan tensi. Catat dari Hari ini.",
                hasData = state.bps.isNotEmpty(),
            ) {
                LineChart(
                    series = listOf(
                        ChartSeries(
                            points = state.bps.map { ChartPoint(dateOf(it.timeMillis), it.systolic.toDouble()) },
                            color = MaterialTheme.colorScheme.primary,
                            strokeDp = 3f,
                        ),
                        ChartSeries(
                            points = state.bps.map { ChartPoint(dateOf(it.timeMillis), it.diastolic.toDouble()) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeDp = 1.5f,
                        ),
                    ),
                    from = from,
                    to = state.today,
                    formatValue = { it.toInt().toString() },
                    description = bpChartDescription(state.bps),
                )
                Text(
                    "Garis atas sistolik (tebal), garis bawah diastolik (tipis).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartCard(emptyText: String, hasData: Boolean, content: @Composable () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (hasData) {
            content()
        } else {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeightSummaryCard(state: HealthUiState) {
    val weight = state.weight
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (weight == null) {
            Text(
                "Belum ada catatan berat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        Text("Terakhir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatDecimal1(weight.kg)} kg", style = MaterialTheme.typography.displayMedium)
        if (weight.bmi != null && weight.category != null) {
            Text("BMI ${formatDecimal1(weight.bmi)} · ${weight.category.label}", style = MaterialTheme.typography.titleMedium)
        } else {
            Muted("Isi tinggi badan di Tentang untuk melihat BMI.")
        }
        if (weight.targetKg != null && weight.remainingKg != null) {
            Muted("Target ${formatDecimal1(weight.targetKg)} kg · ${describeRemaining(weight.remainingKg)}")
        }
        weight.trend?.let { Muted("4 minggu terakhir: ${it.describe()}") }
            ?: Muted("Tren 4 minggu butuh dua catatan atau lebih.")
    }
}

@Composable
private fun BloodPressureSummaryCard(state: HealthUiState) {
    val bp = state.bp
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (bp == null) {
            Text(
                "Belum ada catatan tensi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        Text("Terakhir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${bp.entry.systolic}/${bp.entry.diastolic}", style = MaterialTheme.typography.displayMedium)
        Text(bp.category.label, style = MaterialTheme.typography.titleMedium)
        bp.entry.pulse?.let { Muted("Nadi $it") }
        Muted(relativeDay(bp.entry.timeMillis, state.today))
        if (bp.needsAdvice) {
            Text(BP_ADVICE_TEXT, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Muted("Kategori adalah informasi, bukan diagnosis.")
    }
}

@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun weightChartDescription(entries: List<WeightEntry>, target: Double?): String {
    if (entries.isEmpty()) return "Grafik berat kosong"
    val targetText = target?.let { ", target ${formatDecimal1(it)} kg" }.orEmpty()
    return "Grafik berat: ${entries.size} catatan, dari ${formatDecimal1(entries.first().kg)} ke ${formatDecimal1(entries.last().kg)} kg$targetText"
}

private fun bpChartDescription(entries: List<BloodPressureEntry>): String {
    if (entries.isEmpty()) return "Grafik tensi kosong"
    val last = entries.last()
    return "Grafik tensi: ${entries.size} catatan, terakhir ${last.systolic}/${last.diastolic}, ${bpCategory(last.systolic, last.diastolic).label}"
}
