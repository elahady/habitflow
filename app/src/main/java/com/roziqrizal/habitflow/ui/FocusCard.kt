package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roziqrizal.habitflow.domain.health.Focus
import com.roziqrizal.habitflow.domain.health.FocusBp
import com.roziqrizal.habitflow.domain.health.FocusInput
import com.roziqrizal.habitflow.domain.health.FocusRule
import com.roziqrizal.habitflow.domain.health.STEPS_TARGET
import com.roziqrizal.habitflow.domain.health.TrendDirection
import com.roziqrizal.habitflow.domain.health.computeFocus
import com.roziqrizal.habitflow.ui.theme.tokens
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Teks kartu Fokus: judul, satu kalimat, dan alasan untuk "Kenapa ini?". */
data class FocusContent(
    val title: String,
    val sentence: String,
    val reasons: List<Pair<String, String>>,
    val note: String,
)

/** Bahan fokus dari data yang sudah ada di Hari ini. Langkah yang belum terbaca dan tensi tunggal dibiarkan null. */
fun focusInputOf(
    health: HealthUiState,
    coffee: Int,
    sweet: Int,
    mealLoggedToday: Boolean,
    minuteOfDay: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FocusInput {
    val weight = health.weight
    val bps = health.bps
    val latestBp = health.bp?.entry
    // Tensi sebelumnya: catatan tepat sebelum yang terakhir. Daftar 90 hari berurut dari yang paling lama.
    val previousBp = if (latestBp != null && bps.size >= 2 && bps.last().id == latestBp.id) bps[bps.size - 2] else null
    return FocusInput(
        weightKg = weight?.kg,
        heightCm = health.heightCm,
        targetKg = weight?.targetKg,
        weightAgeDays = weight?.let {
            ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate(), health.today).toInt()
        },
        trend = weight?.trend,
        latestBp = latestBp?.let { FocusBp(it.systolic, it.diastolic) },
        previousBp = previousBp?.let { FocusBp(it.systolic, it.diastolic) },
        steps = (health.steps as? StepsUiState.Ready)?.steps,
        coffee = coffee,
        sweet = sweet,
        mealLoggedToday = mealLoggedToday,
        minuteOfDay = minuteOfDay,
    )
}

private fun targetPosition(weight: WeightSummary): String? {
    val remaining = weight.remainingKg ?: return null
    return when {
        remaining > 0.0 -> "${formatDecimal1(remaining)} kg di atas target"
        remaining < 0.0 -> "${formatDecimal1(abs(remaining))} kg di bawah target"
        else -> "sudah di target"
    }
}

/** Menyusun teks dari fokus yang dipilih. Nada tenang, tanpa kalori atau diet, tanpa warna peringatan. */
fun focusContent(focus: Focus, health: HealthUiState, coffee: Int, sweet: Int): FocusContent {
    val weight = health.weight
    val steps = (health.steps as? StepsUiState.Ready)?.steps
    val reasons = buildList {
        if (weight != null) add("Berat" to (targetPosition(weight) ?: "${formatDecimal1(weight.kg)} kg"))
        weight?.trend?.let { trend ->
            add(
                "Tren 4 minggu" to when (trend.direction) {
                    TrendDirection.NAIK -> "naik ${formatDecimal1(abs(trend.deltaKg))} kg"
                    TrendDirection.TURUN -> "turun ${formatDecimal1(abs(trend.deltaKg))} kg"
                    TrendDirection.STABIL -> "stabil"
                },
            )
        }
        health.bp?.let { add("Tensi" to "${it.entry.systolic}/${it.entry.diastolic} · ${it.category.label}") }
    }
    val closing = " Kategori adalah informasi, bukan diagnosis."
    val aboveTarget = (weight?.remainingKg ?: 0.0) > 0.0

    return when (focus.rule) {
        FocusRule.BP_ADVICE -> FocusContent(
            title = "Istirahat sebentar, lalu ukur ulang tensi",
            sentence = BP_ADVICE_TEXT,
            reasons = reasons,
            note = "Tensi 180/110 atau lebih selalu didahulukan dari fokus lain.$closing",
        )
        FocusRule.BP_RECHECK -> FocusContent(
            title = "Ukur ulang tensi besok pagi",
            sentence = "Duduk tenang 5 menit dulu. Kalau tetap tinggi, temui dokter.",
            reasons = reasons,
            note = "Dipilih karena dua catatan tensi terakhir sama-sama 140/90 atau lebih.$closing",
        )
        FocusRule.FIRST_WEIGHT -> FocusContent(
            title = "Catat berat pertama",
            sentence = "Dengan berat dan tinggi badan, fokus harian bisa disesuaikan denganmu.",
            reasons = reasons,
            note = "Dipilih karena belum ada data berat atau tinggi badan. Tidak ada saran lain sampai data dasar ada.",
        )
        FocusRule.STALE_WEIGHT -> {
            val days = weight?.let { ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate(), health.today) }
            FocusContent(
                title = "Timbang berat hari ini",
                sentence = "Berat terakhir tercatat ${days ?: "beberapa"} hari lalu. Angka baru membuat saran lain tidak bersandar pada data lama.",
                reasons = reasons,
                note = "Dipilih karena berat terakhir lebih dari 10 hari lalu.",
            )
        }
        FocusRule.FAST_LOSS -> FocusContent(
            title = "Cukup, pertahankan porsi makan",
            sentence = "Berat turun ${formatDecimal1(abs(weight?.trend?.deltaKg ?: 0.0))} kg dalam 4 minggu, sudah lebih cepat dari laju yang wajar (0,25 sampai 0,5 kg per minggu).",
            reasons = reasons,
            note = "Dipilih karena berat turun 2,0 kg atau lebih dalam 4 minggu. Tidak ada saran menambah usaha menurun.$closing",
        )
        FocusRule.DRINKS -> FocusContent(
            title = "Ganti minuman berikutnya dengan air",
            sentence = "Kopi hari ini sudah $coffee gelas dan minuman manis $sweet.",
            reasons = reasons,
            note = "Dipilih karena kopi 3 gelas atau lebih, atau ada minuman manis hari ini.$closing",
        )
        FocusRule.LOW_STEPS -> FocusContent(
            title = "Jalan santai ${focus.walkMinutes} menit sore ini",
            sentence = "Langkah baru ${formatSteps(steps ?: 0)} dari ${formatSteps(STEPS_TARGET.toLong())}. Jalan santai ${focus.walkMinutes} menit sudah menutup target hari ini.",
            reasons = reasons,
            note = "Dipilih karena langkah di bawah separuh target" + (if (aboveTarget) " saat berat di atas target" else "") + ".$closing",
        )
        FocusRule.LOG_MEAL -> FocusContent(
            title = "Catat makan hari ini",
            sentence = "Pakai pola Isi Piringku. Cukup beberapa ketukan, tanpa hitung kalori.",
            reasons = reasons,
            note = "Dipilih karena berat di atas target, tren naik, dan belum ada catatan makan hari ini.$closing",
        )
        FocusRule.ON_TRACK -> FocusContent(
            title = "Hari ini sudah di jalur",
            sentence = when {
                weight?.remainingKg != null && weight.remainingKg > 0.0 && aboveTarget ->
                    "Berat ${formatDecimal1(weight.remainingKg)} kg di atas target. Tidak ada tugas tambahan hari ini."
                weight?.remainingKg != null && weight.remainingKg <= 0.0 -> "Berat sudah di target. Tidak ada tugas tambahan hari ini."
                else -> "Tidak ada tugas tambahan hari ini."
            },
            reasons = reasons,
            note = "Tidak ada aturan lain yang cocok hari ini.$closing",
        )
    }
}

/**
 * Kartu hero "Fokus hari ini" (tahap 30): satu hal paling berguna untuk target kesehatan hari ini, kemajuan langkah, dan
 * "Kenapa ini?" yang membuka angka dasarnya. Pola visualnya ada di docs/design/README.md bagian Kartu Fokus hari ini.
 */
@Composable
fun FocusCard(focus: Focus, health: HealthUiState, coffee: Int, sweet: Int) {
    var showWhy by rememberSaveable { mutableStateOf(false) }
    val content = focusContent(focus, health, coffee, sweet)
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.tokens.primaryFixed, contentColor = onContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Heart, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("FOKUS HARI INI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Text(content.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp, lineHeight = 30.sp))
            Text(content.sentence, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))

            (health.steps as? StepsUiState.Ready)?.let { ready ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text("Langkah hari ini", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${formatSteps(ready.steps)} / ${formatSteps(STEPS_TARGET.toLong())}",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (ready.steps.toFloat() / STEPS_TARGET).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }

            if (showWhy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = onContainer)
                    content.reasons.forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(content.note, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp))
                }
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) { showWhy = !showWhy }
                    .semantics { stateDescription = if (showWhy) "Terbuka" else "Tertutup" },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (showWhy) "Tutup alasan" else "Kenapa ini?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                )
            }
        }
    }
}
