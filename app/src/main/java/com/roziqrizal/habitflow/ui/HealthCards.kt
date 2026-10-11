package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.domain.health.STEPS_TARGET

/**
 * Kartu Langkah di Hari ini (tahap 21): ikon, sumber dan jam baca, angka serif, dan kemajuan menuju 8.000, atau satu
 * kalimat tenang tentang cara mengaktifkan.
 */
@Composable
fun StepsCard(state: StepsUiState, onRequestAccess: () -> Unit, onOpenHealthConnectStore: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconLabel(AppIcons.Activity, "Langkah", modifier = Modifier.weight(1f))
            if (state is StepsUiState.Ready) {
                Text(
                    "Health Connect · ${formatMinute(state.readMinute)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (state) {
            StepsUiState.Loading -> Muted("Membaca langkah...")
            is StepsUiState.Ready -> {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SerifValue(formatSteps(state.steps))
                    val target = formatSteps(STEPS_TARGET.toLong())
                    Text(
                        text = if (state.steps >= STEPS_TARGET) "dari $target · Target tercapai" else "dari $target",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                ThinProgress(state.steps.toFloat() / STEPS_TARGET, modifier = Modifier.padding(top = 10.dp))
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

/** Kartu Berat dan Tensi berdampingan (tahap 21): angka terakhir, kategori, kapan, dan tombol "+ Catat". */
@Composable
fun HealthSummaryCards(state: HealthUiState, onRecordBp: () -> Unit, onRecordWeight: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val weight = state.weight
        SummaryCard(
            icon = AppIcons.Gauge,
            label = "Berat",
            value = weight?.let { formatDecimal1(it.kg) },
            unit = "kg",
            category = weight?.category?.let { "BMI ${formatDecimal1(weight.bmi ?: 0.0)} · ${it.label}" },
            whenText = weight?.let { relativeDay(it.timeMillis, state.today) },
            onRecord = onRecordWeight,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        val bp = state.bp
        SummaryCard(
            icon = AppIcons.Droplet,
            label = "Tensi",
            value = bp?.let { "${it.entry.systolic}/${it.entry.diastolic}" },
            unit = null,
            category = bp?.category?.label,
            whenText = bp?.let { relativeDay(it.entry.timeMillis, state.today) },
            onRecord = onRecordBp,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    label: String,
    value: String?,
    unit: String?,
    category: String?,
    whenText: String?,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(14.dp), fillHeight = true) {
        IconLabel(icon, label)
        if (value == null) {
            Text(
                "Belum ada catatan",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
        } else {
            SerifValue(value, unit = unit, modifier = Modifier.padding(top = 6.dp))
            if (category != null) {
                Text(
                    category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (whenText != null) {
                Text(
                    whenText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TonalButton(
            text = "+ Catat",
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        )
    }
}
