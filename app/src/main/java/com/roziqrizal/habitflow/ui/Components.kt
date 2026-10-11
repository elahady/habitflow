package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.roziqrizal.habitflow.ui.theme.tokens

/*
 * Komponen bersama, dengan gaya dari Rizqflow: kartu putih dengan garis tipis, judul bagian
 * serif, dan tombol tonal hijau muda.
 */

/**
 * Kartu putih dengan garis tipis. [hero] memakai sudut besar untuk kartu ringkasan. Kalau
 * [onClick] diisi, seluruh kartu bisa ditekan.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    /** Isi mengisi tinggi kartu, supaya `Spacer(weight)` bisa mendorong tombol ke dasar kartu berdampingan. */
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (hero) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = (if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(contentPadding),
            content = content,
        )
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = inner)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = inner)
    }
}

/** Judul bagian dengan serif, seperti "Habit" dan "To-do" di layar. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 12.dp, start = 4.dp),
    )
}

/** Angka besar dengan label kecil di bawahnya, untuk skor dan streak. */
@Composable
fun StatBlock(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tombol utama layar: tonal hijau muda seperti prototipe Rizqflow. */
@Composable
fun TonalButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = contentPadding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.tokens.primaryFixed,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Text(text, maxLines = 1) }
}

/** Warna `FilterChip` mengikuti palet sage: chip terpilih memakai latar `primaryFixed`, bukan biru bawaan Material. */
@Composable
fun appFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.tokens.primaryFixed,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/** Warna tombol segmen mengikuti palet sage, sama dengan pemilih tampilan di Tentang. */
@Composable
fun appSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.tokens.primaryFixed,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/*
 * Bahan kartu Hari ini bergaya mockup (docs/design/README.md bagian Kartu Hari ini): label dengan ikon, angka serif,
 * dan batang kemajuan tipis.
 */

/** Label kartu dengan ikon garis di kiri, misalnya "Berat" atau "Langkah". */
@Composable
fun IconLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Angka besar serif (26sp) dengan satuan kecil opsional, misalnya "76,8 kg". */
@Composable
fun SerifValue(value: String, modifier: Modifier = Modifier, unit: String? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 30.sp), color = MaterialTheme.colorScheme.onSurface)
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

/** Batang kemajuan 6dp berujung bulat. [progress] 0 sampai 1. */
@Composable
fun ThinProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
