package com.roziqrizal.habitflow.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Menerima alarm jadwal, tombol "Sudah" di notifikasi, dan perubahan yang membuat jadwal harus
 * dihitung ulang (boot, jam, zona waktu, pembaruan app).
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ScheduleNotifier.ACTION_DONE -> markDone(app, intent)
                    ScheduleNotifier.ACTION_ALARM -> ScheduleNotifier.refresh(app, announce = true)
                    else -> ScheduleNotifier.refresh(app, announce = false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun markDone(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(ScheduleNotifier.EXTRA_BLOCK_ID, -1)
        val date = intent.getStringExtra(ScheduleNotifier.EXTRA_DATE)?.let { LocalDate.parse(it) } ?: return
        if (blockId < 0) return
        ScheduleRepository(HabitDatabase.get(context)).markDone(blockId, date)
        ScheduleNotifier.cancelBlockNotification(context, blockId)
    }
}
