package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.health.STEPS_TARGET
import androidx.compose.foundation.layout.PaddingValues

/** Kartu Langkah di Hari ini (tahap 21): kemajuan menuju 8.000, atau satu kalimat tenang tentang cara mengaktifkan. */
@Composable
fun StepsCard(state: StepsUiState, onRequestAccess: () -> Unit, onOpenHealthConnectStore: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Langkah", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            StepsUiState.Loading -> Muted("Membaca langkah...")
            is StepsUiState.Ready -> {
                Text(
                    text = "${formatSteps(state.steps)} / ${formatSteps(STEPS_TARGET.toLong())}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = if (state.steps >= STEPS_TARGET) "langkah hari ini · Target tercapai" else "langkah hari ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (state.steps.toFloat() / STEPS_TARGET).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                if (!state.backgroundOk) {
                    if (state.backgroundAvailable) {
                        Muted("Habit tercentang saat app dibuka. Izinkan akses latar belakang supaya tercentang sendiri.")
                        ActionText("Izinkan akses latar belakang", onRequestAccess)
                    } else {
                        // Health Connect di HP ini belum bisa memberi izin latar belakang: tidak ada tombol yang bisa dipenuhi.
                        Muted("Langkah dibaca dan habit dicentang saat app dibuka.")
                    }
                }
            }
            StepsUiState.NeedsPermission -> {
                Muted("Izinkan HabitFlow membaca langkah dari Health Connect supaya habit tercentang sendiri.")
                ActionText("Izinkan akses langkah", onRequestAccess)
            }
            StepsUiState.NotInstalled -> {
                Muted("Health Connect belum terpasang di HP ini. Pasang dulu untuk membaca langkah.")
                ActionText("Pasang Health Connect", onOpenHealthConnectStore)
            }
            StepsUiState.UpdateRequired -> {
                Muted("Health Connect perlu diperbarui sebelum bisa dipakai.")
                ActionText("Perbarui Health Connect", onOpenHealthConnectStore)
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(text) }
}

/** Kartu Tensi dan Berat berdampingan (tahap 21): angka terakhir, kategori, kapan, dan tombol "+ Catat". */
@Composable
fun HealthSummaryCards(state: HealthUiState, onRecordBp: () -> Unit, onRecordWeight: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bp = state.bp
        SummaryCard(
            label = "Tensi",
            value = bp?.let { "${it.entry.systolic}/${it.entry.diastolic}" },
            category = bp?.category?.label,
            whenText = bp?.let { relativeDay(it.entry.timeMillis, state.today) },
            onRecord = onRecordBp,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        val weight = state.weight
        SummaryCard(
            label = "Berat",
            value = weight?.let { "${formatDecimal1(it.kg)} kg" },
            category = weight?.category?.let { "BMI ${formatDecimal1(weight.bmi ?: 0.0)} · ${it.label}" },
            whenText = weight?.let { relativeDay(it.timeMillis, state.today) },
            onRecord = onRecordWeight,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String?,
    category: String?,
    whenText: String?,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value == null) {
            Text(
                "Belum ada catatan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (category != null) {
                Text(category, style = MaterialTheme.typography.bodySmall)
            }
            if (whenText != null) {
                Text(
                    whenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRecord, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("+ Catat") }
        }
    }
}
