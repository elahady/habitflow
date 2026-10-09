package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.calendar.EventOccurrence
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.ui.theme.tokens

/** Menit sejak 00.00 ke teks jam, misalnya 395 menjadi "06.35". */
fun formatMinute(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 24 * 60)
    return "%02d.%02d".format(m / 60 % 24, m % 60)
}

private fun ResolvedBlock.range(): String =
    if (isPoint) formatMinute(startMinute) else "${formatMinute(startMinute)}–${formatMinute(endMinute)}"

/** "Sisa 45 menit" atau "Sisa 1 jam 20 menit". */
private fun remaining(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "Sisa $rest menit"
        rest == 0 -> "Sisa $hours jam"
        else -> "Sisa $hours jam $rest menit"
    }
}

/**
 * Kartu hero dashboard: blok yang sedang berjalan dan berikutnya, lalu timeline hari ini yang
 * bisa dibuka. Pola visualnya ada di docs/design/README.md bagian Dashboard di Hari ini.
 */
@Composable
fun ScheduleCard(
    state: ScheduleUiState,
    onSetDayOff: (Boolean) -> Unit,
    onSetHolidayCancelled: (Boolean) -> Unit,
    onOpenEditor: () -> Unit,
) {
    var showTimeline by rememberSaveable { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth(), hero = true) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sekarang",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenEditor) { Text("Atur") }
        }
        val now = state.now
        if (now != null) {
            Text(now.block.name, style = MaterialTheme.typography.titleLarge)
            now.eventCaption()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "${now.range()} · ${remaining(now.endMinute - state.nowMinute)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("Tidak ada blok sekarang", style = MaterialTheme.typography.titleLarge)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Text("Berikutnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val next = state.next
        if (next != null) {
            Text(next.block.name, style = MaterialTheme.typography.titleMedium)
            next.eventCaption()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Mulai ${formatMinute(next.startMinute)}", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                if (state.timeline.isEmpty()) "Belum ada blok hari ini" else "Selesai untuk hari ini",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimeline = !showTimeline }
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lihat jadwal hari ini", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (showTimeline) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }

        if (showTimeline) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                state.allDayEvents.forEach { AllDayRow(it) }
                state.timeline.forEach { item -> TimelineRow(item) }
            }
        }

        val dayOff = state.dayOff
        val holiday = dayOff.holiday
        when {
            holiday != null && dayOff.holidayActive -> DayOffRow(
                text = (if (holiday.cutiBersama) holiday.label else "Libur nasional: ${holiday.name}") + ", blok kantor dimatikan",
                action = "Batalkan",
                onAction = { onSetHolidayCancelled(true) },
            )
            dayOff.manual -> DayOffRow(
                text = "Hari libur, blok kantor dimatikan",
                action = "Batalkan",
                onAction = { onSetDayOff(false) },
            )
            holiday != null && dayOff.holidayCancelled -> DayOffRow(
                text = "Libur nasional dibatalkan, blok kantor jalan",
                action = "Libur lagi",
                onAction = { onSetHolidayCancelled(false) },
            )
            else -> TextButton(onClick = { onSetDayOff(true) }) { Text("Hari ini libur") }
        }
    }
}

@Composable
private fun DayOffRow(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** "Acara · Kerja" atau "Acara · Pribadi · Kalender kantor" untuk blok yang berasal dari acara, atau null untuk blok jadwal. */
fun ResolvedBlock.eventCaption(): String? = event?.let { marker ->
    listOfNotNull("Acara", marker.label.title, marker.source).joinToString(" · ")
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    val current = item.status == BlockStatus.CURRENT
    val color = if (item.status == BlockStatus.PAST) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (current) MaterialTheme.tokens.primaryFixed else Color.Transparent, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.resolved.range(),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = color,
            modifier = Modifier.width(96.dp),
        )
        Column {
            Text(
                item.resolved.block.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
            )
            item.resolved.eventCaption()?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Acara sepanjang hari: di awal timeline, tanpa jam. */
@Composable
private fun AllDayRow(event: EventOccurrence) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Sepanjang hari",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Column {
            Text(event.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull("Acara", event.label.title, event.calendarName).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
