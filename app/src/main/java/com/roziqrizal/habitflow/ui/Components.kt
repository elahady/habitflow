package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (hero) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
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
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.tokens.primaryFixed,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) { Text(text) }
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
