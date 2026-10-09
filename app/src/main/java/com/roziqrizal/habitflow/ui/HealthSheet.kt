package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.health.DIASTOLIC_MAX
import com.roziqrizal.habitflow.domain.health.DIASTOLIC_MIN
import com.roziqrizal.habitflow.domain.health.HEIGHT_MAX_CM
import com.roziqrizal.habitflow.domain.health.HEIGHT_MIN_CM
import com.roziqrizal.habitflow.domain.health.PULSE_MAX
import com.roziqrizal.habitflow.domain.health.PULSE_MIN
import com.roziqrizal.habitflow.domain.health.SYSTOLIC_MAX
import com.roziqrizal.habitflow.domain.health.SYSTOLIC_MIN
import com.roziqrizal.habitflow.domain.health.WEIGHT_MAX_KG
import com.roziqrizal.habitflow.domain.health.WEIGHT_MIN_KG
import com.roziqrizal.habitflow.domain.health.isValidBloodPressure
import com.roziqrizal.habitflow.domain.health.isValidHeight
import com.roziqrizal.habitflow.domain.health.isValidWeight

/** Mode sheet catat: satu komponen untuk berat dan tensi. */
enum class HealthRecordMode { WEIGHT, BLOOD_PRESSURE }

/**
 * Sheet catat berat atau tensi (tahap 21). Setelah menyimpan, isinya berganti hasil: kategori sebagai teks, dan saran
 * tenang kalau tensi ≥ 180/110. Tanpa warna merah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthRecordSheet(
    mode: HealthRecordMode,
    heightCm: Double?,
    saved: HealthSaveResult?,
    onSaveWeight: (kg: Double, heightCm: Double?) -> Boolean,
    onSaveBloodPressure: (systolic: Int, diastolic: Int, pulse: Int?, note: String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val matchesMode = when (saved) {
                is HealthSaveResult.Weight -> mode == HealthRecordMode.WEIGHT
                is HealthSaveResult.BloodPressure -> mode == HealthRecordMode.BLOOD_PRESSURE
                null -> false
            }
            if (saved != null && matchesMode) {
                SavedResult(saved, onDismiss)
            } else when (mode) {
                HealthRecordMode.WEIGHT -> WeightForm(heightCm, onSaveWeight)
                HealthRecordMode.BLOOD_PRESSURE -> BloodPressureForm(onSaveBloodPressure)
            }
        }
    }
}

@Composable
private fun WeightForm(heightCm: Double?, onSave: (Double, Double?) -> Boolean) {
    var weightText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    val weight = parseDecimal(weightText)
    val height = parseDecimal(heightText)
    val needsHeight = heightCm == null
    val weightValid = weight != null && isValidWeight(weight)
    val heightValid = !needsHeight || (height != null && isValidHeight(height))

    Text("Catat berat", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = weightText,
        onValueChange = { weightText = it },
        label = { Text("Berat (kg)") },
        singleLine = true,
        supportingText = {
            if (weightText.isNotBlank() && !weightValid) {
                Text("Isi ${formatDecimal1(WEIGHT_MIN_KG)} sampai ${formatDecimal1(WEIGHT_MAX_KG)} kg.")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    if (needsHeight) {
        OutlinedTextField(
            value = heightText,
            onValueChange = { heightText = it },
            label = { Text("Tinggi badan (cm)") },
            singleLine = true,
            supportingText = {
                Text(
                    if (heightText.isNotBlank() && !heightValid) {
                        "Isi ${HEIGHT_MIN_CM.toInt()} sampai ${HEIGHT_MAX_CM.toInt()} cm."
                    } else {
                        "Diisi sekali, untuk menghitung BMI."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    TonalButton(
        text = "Simpan",
        enabled = weightValid && heightValid,
        onClick = { onSave(weight!!, if (needsHeight) height else null) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BloodPressureForm(onSave: (Int, Int, Int?, String?) -> Boolean) {
    var systolicText by remember { mutableStateOf("") }
    var diastolicText by remember { mutableStateOf("") }
    var pulseText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val systolic = systolicText.trim().toIntOrNull()
    val diastolic = diastolicText.trim().toIntOrNull()
    val pulse = pulseText.trim().toIntOrNull()
    val pulseBlank = pulseText.isBlank()
    val pulseValid = pulseBlank || (pulse != null && pulse in PULSE_MIN..PULSE_MAX)
    val valid = systolic != null && diastolic != null && pulseValid &&
        isValidBloodPressure(systolic, diastolic, if (pulseBlank) null else pulse)
    val bothFilled = systolicText.isNotBlank() && diastolicText.isNotBlank()

    Text("Catat tensi", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = systolicText,
            onValueChange = { systolicText = it },
            label = { Text("Sistolik") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = diastolicText,
            onValueChange = { diastolicText = it },
            label = { Text("Diastolik") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    if (bothFilled && !valid && pulseValid) {
        Text(
            "Sistolik $SYSTOLIC_MIN sampai $SYSTOLIC_MAX dan diastolik $DIASTOLIC_MIN sampai $DIASTOLIC_MAX, sistolik harus lebih besar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedTextField(
        value = pulseText,
        onValueChange = { pulseText = it },
        label = { Text("Nadi (opsional)") },
        singleLine = true,
        supportingText = { if (!pulseValid) Text("Isi $PULSE_MIN sampai $PULSE_MAX.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Catatan (opsional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    TonalButton(
        text = "Simpan",
        enabled = valid,
        onClick = { onSave(systolic!!, diastolic!!, if (pulseBlank) null else pulse, note) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SavedResult(result: HealthSaveResult, onDone: () -> Unit) {
    when (result) {
        is HealthSaveResult.Weight -> {
            Text("Berat tersimpan", style = MaterialTheme.typography.titleLarge)
            val headline = if (result.bmi != null && result.category != null) {
                "BMI ${formatDecimal1(result.bmi)} · ${result.category.label}"
            } else {
                "${formatDecimal1(result.kg)} kg"
            }
            Text(headline, style = MaterialTheme.typography.titleMedium)
            val detail = when {
                result.remainingKg != null -> describeRemaining(result.remainingKg)
                else -> "Isi tinggi badan di Tentang untuk melihat BMI."
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is HealthSaveResult.BloodPressure -> {
            Text("Tensi tersimpan", style = MaterialTheme.typography.titleLarge)
            Text("${result.systolic}/${result.diastolic} · ${result.category.label}", style = MaterialTheme.typography.titleMedium)
            if (result.needsAdvice) {
                Text(BP_ADVICE_TEXT, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Kategori adalah informasi, bukan diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDone) { Text("Selesai") }
    }
}
