package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.domain.work.DailyBriefCard
import java.time.Instant
import java.time.ZoneId

/** Baris yang ditampilkan sebelum "Selengkapnya". Ringkasan bisa memuat sampai 30 baris email. */
private const val COLLAPSED_LINES = 6

/** Keterangan di bawah judul kartu: "Hari ini · diperbarui 07.05", atau tanggal terakhir kalau belum ada yang hari ini. */
fun dailyBriefCaption(card: DailyBriefCard, zone: ZoneId = ZoneId.systemDefault()): String? {
    val brief = card.brief ?: return null
    return if (card.isToday) {
        "Hari ini · diperbarui ${formatTime(Instant.ofEpochMilli(brief.updatedAt).atZone(zone).toLocalTime())}"
    } else {
        "Belum ada ringkasan hari ini. Terakhir: ${formatShortDate(brief.date)}"
    }
}

/**
 * Kartu "Ringkasan dari Claude" di tab Kerja (tahap 32). Hanya dibaca, tanpa tombol ubah: ditulis otomatis oleh skill
 * `habitflow-morning-routine`, dipisah jelas dari catatan EOD yang ditulis sendiri. Teks ditampilkan apa adanya sebagai teks
 * polos (bukan HTML atau markdown).
 */
@Composable
fun DailyBriefCardView(card: DailyBriefCard, modifier: Modifier = Modifier) {
    val brief = card.brief
    var expanded by rememberSaveable { mutableStateOf(false) }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("Ringkasan dari Claude", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ditulis otomatis, hanya dibaca",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (brief == null) {
            Text(
                "Belum ada ringkasan. Ringkasan email belum dibaca muncul di sini setelah skill habitflow-morning-routine dijalankan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@AppCard
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            dailyBriefCaption(card)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                brief.emailDigest,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (brief.emailDigest.lines().size > COLLAPSED_LINES) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Lebih sedikit" else "Selengkapnya") }
            }
        }
    }
}
