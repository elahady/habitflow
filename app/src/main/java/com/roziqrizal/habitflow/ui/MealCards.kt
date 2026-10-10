package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.meals.Meal
import com.roziqrizal.habitflow.domain.meals.MealKind
import java.time.LocalDate
import java.time.LocalTime

/** Nama waktu makan untuk tampilan. */
val MealKind.title: String
    get() = when (this) {
        MealKind.BREAKFAST -> "Sarapan"
        MealKind.LUNCH -> "Siang"
        MealKind.DINNER -> "Malam"
        MealKind.SNACK -> "Camilan"
    }

/** Nama waktu makan dalam kalimat ("makan siang"), untuk pengingat dan keterangan. */
val MealKind.sentenceName: String
    get() = when (this) {
        MealKind.BREAKFAST -> "sarapan"
        MealKind.LUNCH -> "makan siang"
        MealKind.DINNER -> "makan malam"
        MealKind.SNACK -> "camilan"
    }

private fun minuteToTime(minute: Int): LocalTime = LocalTime.of((minute / 60).coerceIn(0, 23), minute % 60)

/**
 * Kartu Makan di dashboard (tahap 23): satu chip per waktu makan. Yang sudah dicatat berlatar `primaryFixed` dengan tanda centang
 * dan jamnya, yang belum memakai garis. Tap membuka sheet catat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealCard(meals: Map<MealKind, Meal>, onOpen: (MealKind) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Makan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            MealKind.entries.forEach { kind ->
                val meal = meals[kind]
                FilterChip(
                    colors = appFilterChipColors(),
                    selected = meal != null,
                    onClick = { onOpen(kind) },
                    label = {
                        Text(if (meal != null) "✓ ${kind.title} ${formatMinute(meal.minute)}" else kind.title)
                    },
                )
            }
        }
    }
}

/**
 * Sheet catat makan (tahap 23): jam (bisa diubah), komponen Isi Piringku (karbo, lauk, sayur, buah), tanda gorengan dan manis, dan
 * catatan opsional. [initial] kosong berarti catatan baru. Menyimpan satu catatan per tanggal dan jenis.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealSheet(
    kind: MealKind,
    date: LocalDate,
    initial: Meal?,
    defaultMinute: Int,
    onSave: (Meal) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minute by remember { mutableStateOf(initial?.minute ?: defaultMinute) }
    var carb by remember { mutableStateOf(initial?.carb ?: false) }
    var protein by remember { mutableStateOf(initial?.protein ?: false) }
    var vegetable by remember { mutableStateOf(initial?.vegetable ?: false) }
    var fruit by remember { mutableStateOf(initial?.fruit ?: false) }
    var fried by remember { mutableStateOf(initial?.fried ?: false) }
    var sweet by remember { mutableStateOf(initial?.sweet ?: false) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pickingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(kind.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingTime = true }) { Text(formatMinute(minute)) }
            }

            Text("Isi piring", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlateChip("Karbo", carb) { carb = !carb }
                PlateChip("Lauk", protein) { protein = !protein }
                PlateChip("Sayur", vegetable) { vegetable = !vegetable }
                PlateChip("Buah", fruit) { fruit = !fruit }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlateChip("Gorengan", fried) { fried = !fried }
                PlateChip("Manis", sweet) { sweet = !sweet }
            }

            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Catatan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            TonalButton(
                text = "Simpan",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(
                        Meal(
                            date = date, kind = kind, minute = minute,
                            carb = carb, protein = protein, vegetable = vegetable, fruit = fruit,
                            fried = fried, sweet = sweet, note = note.trim().ifEmpty { null },
                        ),
                    )
                },
            )
            if (initial != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Hapus catatan") }
            }
        }
    }

    if (pickingTime) {
        AppTimePickerDialog(
            initial = minuteToTime(minute),
            onDismiss = { pickingTime = false },
            onPick = {
                minute = it.hour * 60 + it.minute
                pickingTime = false
            },
        )
    }
}

@Composable
private fun PlateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(colors = appFilterChipColors(), selected = selected, onClick = onClick, label = { Text(label) })
}
