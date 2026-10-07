package com.roziqrizal.habitflow.notify

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.ui.TonalButton
import com.roziqrizal.habitflow.ui.formatMinute
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Layar alarm penuh di atas lock screen. Menutup sendiri begitu [AlarmService] tidak lagi berbunyi.
 * Pola tampilannya ada di docs/design/README.md bagian Layar alarm.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HabitFlowTheme(darkTheme = isSystemInDarkTheme()) {
                val ringing by AlarmService.ringing.collectAsState()
                // Layar ditutup saat alarm selesai. Nilai awal null diabaikan sebentar karena service
                // mungkin belum sempat menyala saat activity dibuka.
                var seenRinging by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing != null) {
                        seenRinging = true
                    } else {
                        delay(if (seenRinging) 0 else 1_500)
                        if (AlarmService.ringing.value == null) finish()
                    }
                }
                ringing?.let { AlarmScreen(it, onStop = ::stopAlarm, onSnooze = ::snoozeAlarm) }
            }
        }
    }

    private fun stopAlarm() = startService(AlarmService.intentFor(this, AlarmService.ACTION_STOP))

    private fun snoozeAlarm() = startService(AlarmService.intentFor(this, AlarmService.ACTION_SNOOZE))
}

@Composable
private fun AlarmScreen(alarm: RingingAlarm, onStop: () -> Unit, onSnooze: () -> Unit) {
    val now = LocalTime.now()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Alarm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMinute(now.hour * 60 + now.minute),
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
        )
        Text(alarm.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 32.dp))

        TonalButton(text = "Matikan", onClick = onStop, modifier = Modifier.fillMaxWidth().height(56.dp))
        if (alarm.snoozeCount < AlarmService.MAX_SNOOZES) {
            TextButton(onClick = onSnooze, modifier = Modifier.padding(top = 8.dp)) {
                Text("Tunda ${AlarmService.SNOOZE_MINUTES} menit")
            }
            Text(
                "Tunda ke-${alarm.snoozeCount + 1} dari ${AlarmService.MAX_SNOOZES}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
