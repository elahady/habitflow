package com.roziqrizal.habitflow.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.roziqrizal.habitflow.notify.ScheduleNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Khusus build debug: menjalankan `ScheduleNotifier.refresh(announce = true)` seolah-olah sekarang adalah waktu tertentu,
 * untuk menguji pengingat yang jatuhnya jauh (misalnya pengingat Senin pagi) tanpa menunggu atau mengubah jam HP.
 *
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugClockReceiver --es at 2026-10-12T05:20
 *
 * Alarm yang dijadwalkan ikut memakai jam palsu itu, jadi buka app sekali sesudahnya agar jadwal nyata dipasang ulang.
 */
class DebugClockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getStringExtra("at")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (at == null) {
            Log.w(TAG, "Pakai --es at yyyy-MM-ddTHH:mm")
            return
        }
        val zone = ZoneId.systemDefault()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ScheduleNotifier.refresh(context.applicationContext, announce = true, clock = Clock.fixed(at.atZone(zone).toInstant(), zone))
                Log.i(TAG, "refresh dijalankan untuk $at")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugClock"
    }
}
