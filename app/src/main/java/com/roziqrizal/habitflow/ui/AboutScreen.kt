package com.roziqrizal.habitflow.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.DeviceLocation
import com.roziqrizal.habitflow.data.LocationSettings
import com.roziqrizal.habitflow.data.PlaceLocation
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.ui.theme.tokens
import kotlinx.coroutines.launch

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Sistem"
        ThemeMode.LIGHT -> "Terang"
        ThemeMode.DARK -> "Gelap"
    }

@Composable
fun AboutScreen(
    versionName: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    location: PlaceLocation,
    onLocationChange: (PlaceLocation) -> Unit,
    persistentNotification: Boolean,
    onPersistentNotificationChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "HabitFlow",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Versi $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Dibuat oleh Roziq Rizal",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )

        SettingLabel("Tampilan")
        ThemeModeSelector(selected = themeMode, onSelect = onThemeModeChange)

        SettingLabel("Lokasi untuk waktu sholat")
        LocationSection(location = location, onLocationChange = onLocationChange)

        SettingLabel("Notifikasi")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Notifikasi tetap", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Sekarang dan berikutnya, dari blok pertama sampai batas tidur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = persistentNotification, onCheckedChange = onPersistentNotificationChange)
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
    )
}

@Composable
private fun LocationSection(location: PlaceLocation, onLocationChange: (PlaceLocation) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showManual by remember { mutableStateOf(false) }

    fun useDevice() {
        scope.launch {
            val found = DeviceLocation.current(context)
            if (found == null) {
                Toast.makeText(
                    context, "Lokasi belum tersedia. Nyalakan lokasi di HP atau isi manual.", Toast.LENGTH_LONG,
                ).show()
            } else {
                onLocationChange(PlaceLocation("Lokasi perangkat", found.latitude, found.longitude))
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            useDevice()
        } else {
            Toast.makeText(context, "Izin lokasi ditolak. Isi lokasi secara manual.", Toast.LENGTH_LONG).show()
        }
    }

    Text(location.name, style = MaterialTheme.typography.bodyLarge)
    Text(
        "%.4f, %.4f".format(location.latitude, location.longitude),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TonalButton(
            text = "Pakai lokasi saat ini",
            onClick = {
                if (DeviceLocation.hasPermission(context)) {
                    useDevice()
                } else {
                    permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )
        TextButton(onClick = { showManual = true }) { Text("Atur manual") }
    }

    if (showManual) {
        ManualLocationDialog(
            initial = location,
            onDismiss = { showManual = false },
            onConfirm = {
                onLocationChange(it)
                showManual = false
            },
        )
    }
}

@Composable
private fun ManualLocationDialog(initial: PlaceLocation, onDismiss: () -> Unit, onConfirm: (PlaceLocation) -> Unit) {
    var name by remember { mutableStateOf(if (initial.name == "Lokasi perangkat") "" else initial.name) }
    var lat by remember { mutableStateOf(initial.latitude.toString()) }
    var lng by remember { mutableStateOf(initial.longitude.toString()) }

    val latitude = lat.trim().replace(',', '.').toDoubleOrNull()
    val longitude = lng.trim().replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && latitude != null && longitude != null &&
        LocationSettings.isValid(latitude, longitude)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lokasi manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nama kota") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it },
                    label = { Text("Lintang (selatan negatif)") }, singleLine = true,
                    isError = latitude == null || latitude !in -90.0..90.0,
                )
                OutlinedTextField(
                    value = lng, onValueChange = { lng = it },
                    label = { Text("Bujur (timur positif)") }, singleLine = true,
                    isError = longitude == null || longitude !in -180.0..180.0,
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onConfirm(PlaceLocation(name.trim(), latitude!!, longitude!!)) }) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.tokens.primaryFixed,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                label = { Text(mode.label) },
            )
        }
    }
}
