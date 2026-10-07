package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.schedule.ResolvedBlock
import com.roziqrizal.habitflow.ui.theme.tokens

/** Menit sejak 00.00 ke teks jam, misalnya 395 menjadi "06.35". */
fun formatMinute(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 24 * 60)
    return "%02d.%02d".format(m / 60 % 24, m % 60)
}

private fun ResolvedBlock.range(): String =
    if (isPoint) formatMinute(startMinute) else "${formatMinute(startMinute)}–${formatMinute(endMinute)}"

/**
 * Kartu dashboard: blok yang sedang berjalan, blok berikutnya, dan timeline hari ini yang bisa
 * dibuka. Aturannya ada di docs/rancangan.md bagian Jadwal harian.
 */
@Composable
fun ScheduleCard(
    state: ScheduleUiState,
    onSetDayOff: (Boolean) -> Unit,
    onOpenEditor: () -> Unit,
) {
    var showTimeline by rememberSaveable { mutableStateOf(false) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Sekarang", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val now = state.now
        if (now != null) {
            Text(now.block.name, style = MaterialTheme.typography.titleMedium)
            Text(now.range(), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Tidak ada blok sekarang", style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Berikutnya",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        val next = state.next
        if (next != null) {
            Text(next.block.name, style = MaterialTheme.typography.titleMedium)
            Text("Mulai ${formatMinute(next.startMinute)}", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                if (state.timeline.isEmpty()) "Belum ada blok hari ini" else "Selesai untuk hari ini",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isDayOff) {
            Text(
                "Hari ini libur: blok hari kerja dimatikan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { showTimeline = !showTimeline }) {
                Text(if (showTimeline) "Sembunyikan jadwal" else "Lihat jadwal")
            }
            TextButton(onClick = { onSetDayOff(!state.isDayOff) }) {
                Text(if (state.isDayOff) "Batalkan libur" else "Hari ini libur")
            }
        }
        TextButton(onClick = onOpenEditor) { Text("Atur jadwal") }

        if (showTimeline) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                state.timeline.forEach { item -> TimelineRow(item) }
            }
        }
    }
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
            .background(
                if (current) MaterialTheme.tokens.primaryFixed else androidx.compose.ui.graphics.Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.resolved.range(), style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.width(96.dp))
        Text(
            item.resolved.block.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
    }
}
