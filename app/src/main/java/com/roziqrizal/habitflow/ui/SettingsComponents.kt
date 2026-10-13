package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Bahan layar Pengaturan dan Profil (tahap 31I). Nilainya dicatat di docs/design/README.md bagian Pengaturan dan Profil:
 * bagian berkartu (sudut 16dp, garis `outlineVariant`) dengan label kecil tebal di atasnya, dan baris setinggi minimal 48dp.
 */

private val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

/** Label kecil tebal di atas sebuah kartu. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** Kartu tanpa padding dalam supaya barisnya penuh lebar; baris dipisah [SettingsDivider]. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), content = content)
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * Baris pengaturan: judul (bodyLarge) dan keterangan satu baris (bodySmall) di kiri, [value] (opsional, serif kecil) dan
 * chevron di kanan kalau [onClick] diisi. Tanpa [onClick] barisnya hanya informasi.
 */
@Composable
fun ChevronRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(clickable).padding(RowPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (onClick != null) {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Baris dengan `Switch` di kanan, di dalam [SettingsCard]. */
@Composable
fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(RowPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Dialog untuk sub-pengaturan yang dibuka dari baris chevron. Isinya bisa digulir karena layar kecil. */
@Composable
fun SettingsDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } },
    )
}
