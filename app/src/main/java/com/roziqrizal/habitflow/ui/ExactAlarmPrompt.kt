package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.roziqrizal.habitflow.data.AlarmSettings
import com.roziqrizal.habitflow.domain.schedule.AlarmTime
import com.roziqrizal.habitflow.notify.ExactAlarmAccess

/** Jeda sebelum ajakan "Nanti" muncul lagi selama izinnya belum diberikan. */
internal const val EXACT_PROMPT_REPEAT_MILLIS = 3 * 24 * 60 * 60 * 1000L

/**
 * Kapan ajakan izin alarm presisi tampil di layar Hari ini (31L). Alarm Subuh sudah aktif sejak instalasi pertama dan
 * `SCHEDULE_EXACT_ALARM` harus diberikan pengguna sendiri (Android 14 ke atas menolaknya secara bawaan), jadi tanpa ajakan
 * ini alarm pertama terlambat sampai satu jam tanpa ada yang memberi tahu. Tampil hanya kalau ada alarm yang akan berbunyi,
 * izinnya belum ada, dan pengguna tidak baru saja menekan "Nanti".
 */
internal fun shouldPromptExactAlarm(hasAlarm: Boolean, granted: Boolean, dismissedAt: Long?, now: Long): Boolean =
    hasAlarm && !granted && (dismissedAt == null || now - dismissedAt >= EXACT_PROMPT_REPEAT_MILLIS)

/** Ajakan yang siap ditampilkan, atau tidak ada (null) kalau tidak perlu. */
class ExactAlarmPrompt(val alarmName: String, val onAllow: () -> Unit, val onLater: () -> Unit)

@Composable
fun rememberExactAlarmPrompt(nextAlarm: AlarmTime?, settings: AlarmSettings): ExactAlarmPrompt? {
    val context: Context = LocalContext.current
    var granted by remember { mutableStateOf(ExactAlarmAccess.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = ExactAlarmAccess.isGranted(context)
    }
    val dismissedAt by settings.exactPromptDismissedAt.collectAsState()

    if (!shouldPromptExactAlarm(nextAlarm != null, granted, dismissedAt, System.currentTimeMillis())) return null
    val alarm = nextAlarm ?: return null
    val intent = ExactAlarmAccess.requestIntent(context) ?: return null
    return ExactAlarmPrompt(
        alarmName = alarm.block.name,
        onAllow = { launcher.launch(intent) },
        onLater = { settings.dismissExactPrompt(System.currentTimeMillis()) },
    )
}

@Composable
fun ExactAlarmPromptCard(prompt: ExactAlarmPrompt, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("Alarm ${prompt.alarmName} belum bisa berbunyi tepat waktu", style = MaterialTheme.typography.titleSmall)
        Text(
            "Android meminta izin \"Alarm & pengingat\" untuk Habitflow. Tanpanya alarm bisa terlambat sampai satu jam.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = prompt.onLater) { Text("Nanti") }
            TextButton(onClick = prompt.onAllow) { Text("Izinkan") }
        }
    }
}
