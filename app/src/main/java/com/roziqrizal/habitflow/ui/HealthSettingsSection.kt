package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.HealthSettings
import com.roziqrizal.habitflow.domain.health.BMI_NORMAL_MAX
import com.roziqrizal.habitflow.domain.health.BpFrequency
import com.roziqrizal.habitflow.domain.health.HEIGHT_MAX_CM
import com.roziqrizal.habitflow.domain.health.HEIGHT_MIN_CM
import com.roziqrizal.habitflow.domain.health.WEIGHT_MAX_KG
import com.roziqrizal.habitflow.domain.health.WEIGHT_MIN_KG
import com.roziqrizal.habitflow.domain.health.isValidHeight
import com.roziqrizal.habitflow.domain.health.isValidWeight

private enum class HealthField { HEIGHT, TARGET }

private val BpFrequency.label: String
    get() = when (this) {
        BpFrequency.DAILY -> "Harian"
        BpFrequency.WEEKLY -> "Mingguan"
        BpFrequency.OFF -> "Mati"
    }

/**
 * Kartu "Data tubuh" di Profil (tahap 31I, sebelumnya bagian Kesehatan di Tentang): tinggi badan dan target berat.
 * Tinggi dipakai menghitung BMI; target berat kosong berarti otomatis dari batas atas BMI normal.
 */
@Composable
fun BodyDataCard(settings: HealthSettings) {
    val height by settings.heightCm.collectAsState()
    val target by settings.targetKg.collectAsState()
    var editing by remember { mutableStateOf<HealthField?>(null) }

    SettingsCard {
        ChevronRow(
            title = "Tinggi badan",
            description = "Untuk menghitung BMI",
            value = height?.let { "${formatDecimal1(it)} cm" } ?: "Belum diisi",
            onClick = { editing = HealthField.HEIGHT },
        )
        SettingsDivider()
        ChevronRow(
            title = "Target berat",
            description = "Kosong berarti otomatis (BMI ${formatDecimal1(BMI_NORMAL_MAX)})",
            value = target?.let { "${formatDecimal1(it)} kg" } ?: "Otomatis",
            onClick = { editing = HealthField.TARGET },
        )
    }

    editing?.let { field ->
        NumberDialog(
            title = if (field == HealthField.HEIGHT) "Tinggi badan" else "Target berat",
            unit = if (field == HealthField.HEIGHT) "cm" else "kg",
            hint = if (field == HealthField.HEIGHT) {
                "Isi ${HEIGHT_MIN_CM.toInt()} sampai ${HEIGHT_MAX_CM.toInt()} cm."
            } else {
                "Isi ${formatDecimal1(WEIGHT_MIN_KG)} sampai ${formatDecimal1(WEIGHT_MAX_KG)} kg."
            },
            initial = (if (field == HealthField.HEIGHT) height else target)?.let { formatDecimal1(it) }.orEmpty(),
            isValid = { if (field == HealthField.HEIGHT) isValidHeight(it) else isValidWeight(it) },
            onSave = {
                if (field == HealthField.HEIGHT) settings.setHeightCm(it) else settings.setTargetKg(it)
                editing = null
            },
            onClear = if (field == HealthField.TARGET && target != null) {
                {
                    settings.setTargetKg(null)
                    editing = null
                }
            } else {
                null
            },
            clearLabel = "Otomatis",
            onDismiss = { editing = null },
        )
    }
}

/** Pengingat kesehatan: switch timbang dan pilihan frekuensi tensi. Isi dialog "Berat dan tensi" di Pengaturan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthReminderSection(settings: HealthSettings) {
    val weightReminder by settings.weightReminder.collectAsState()
    val bpFrequency by settings.bpFrequency.collectAsState()

    SwitchRow(
        title = "Timbang berat",
        description = "Setiap Senin pagi, sebelum aktivitas.",
        checked = weightReminder,
        onCheckedChange = settings::setWeightReminder,
    )
    Spacer(Modifier.height(12.dp))
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Ukur tensi", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Pagi sebelum aktivitas. Mingguan jatuh di hari Senin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            BpFrequency.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == bpFrequency,
                    onClick = { settings.setBpFrequency(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, BpFrequency.entries.size),
                    colors = appSegmentedColors(),
                    label = { Text(option.label) },
                )
            }
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    unit: String,
    hint: String,
    initial: String,
    isValid: (Double) -> Boolean,
    onSave: (Double) -> Unit,
    onClear: (() -> Unit)?,
    clearLabel: String,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = parseDecimal(text)
    val valid = value != null && isValid(value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(unit) },
                    singleLine = true,
                    supportingText = { Text(hint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(value!!) }, enabled = valid) { Text("Simpan") } },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text(clearLabel) }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}
