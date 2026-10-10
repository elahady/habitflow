package com.roziqrizal.habitflow.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.calendar.CalendarSettings
import com.roziqrizal.habitflow.data.calendar.PhoneCalendarInfo
import com.roziqrizal.habitflow.data.calendar.PhoneCalendarSource
import com.roziqrizal.habitflow.domain.calendar.EventLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bagian Tentang untuk Kalender HP (tahap 22). Tanpa izin `READ_CALENDAR`: satu kalimat dan tombol izin. Dengan izin: satu baris
 * per kalender dengan `Switch`, dan kalau menyala dua `FilterChip` label Kerja dan Pribadi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarSettingsSection(phone: PhoneCalendarSource, settings: CalendarSettings) {
    var granted by remember { mutableStateOf(phone.hasPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val selection by settings.selection.collectAsState()

    if (!granted) {
        Text(
            "Izinkan akses kalender supaya acara dari kalender HP tampil di timeline dan Agenda. HabitFlow hanya membaca, " +
                "tidak mengubah dan tidak memberi notifikasi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TonalButton(text = "Izinkan akses kalender", onClick = { permission.launch(Manifest.permission.READ_CALENDAR) })
        return
    }

    val calendars by produceState(emptyList<PhoneCalendarInfo>(), granted) {
        value = withContext(Dispatchers.IO) { phone.calendars() }
    }
    if (calendars.isEmpty()) {
        Text(
            "Tidak ada kalender di HP ini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        calendars.forEach { calendar ->
            val label = selection[calendar.id]
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(calendar.name.ifBlank { "Kalender ${calendar.id}" }, style = MaterialTheme.typography.bodyLarge)
                        if (calendar.account.isNotBlank()) {
                            Text(
                                calendar.account,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(checked = label != null, onCheckedChange = { settings.setSelected(calendar.id, it) })
                }
                if (label != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        EventLabel.entries.forEach { option ->
                            FilterChip(
                                colors = appFilterChipColors(),
                                selected = option == label,
                                onClick = { settings.setLabel(calendar.id, option) },
                                label = { Text(option.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}
