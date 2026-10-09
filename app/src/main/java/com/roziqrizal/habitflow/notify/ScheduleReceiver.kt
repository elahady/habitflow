package com.roziqrizal.habitflow.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.roziqrizal.habitflow.data.DrinkRepository
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Menerima alarm jadwal, tombol "Sudah" dan "Sudah minum" di notifikasi, dan perubahan yang membuat jadwal harus
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
                    ScheduleNotifier.ACTION_WATER -> drankGlass(app)
                    ScheduleNotifier.ACTION_ALARM -> ScheduleNotifier.refresh(app, announce = true)
                    ScheduleNotifier.ACTION_RING -> ring(app, intent)
                    else -> ScheduleNotifier.refresh(app, announce = false)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Alarm jam berbunyi: nyalakan service, lalu jadwalkan alarm berikutnya dan umumkan blok lain yang mulai. */
    private suspend fun ring(context: Context, intent: Intent) {
        val service = Intent(context, AlarmService::class.java)
            .putExtra(AlarmService.EXTRA_NAME, intent.getStringExtra(AlarmService.EXTRA_NAME))
            .putExtra(AlarmService.EXTRA_SNOOZE_COUNT, intent.getIntExtra(AlarmService.EXTRA_SNOOZE_COUNT, 0))
        ContextCompat.startForegroundService(context, service)
        ScheduleNotifier.refresh(context, announce = true)
    }

    /** Tombol "Sudah minum" di notifikasi pengingat: tambah satu gelas hari ini dan tutup notifikasinya. */
    private suspend fun drankGlass(context: Context) {
        DrinkRepository(HabitDatabase.get(context)).addGlass(LocalDate.now())
        ScheduleNotifier.cancelWorkReminder(context)
    }

    private suspend fun markDone(context: Context, intent: Intent) {
        val blockId = intent.getLongExtra(ScheduleNotifier.EXTRA_BLOCK_ID, -1)
        val date = intent.getStringExtra(ScheduleNotifier.EXTRA_DATE)?.let { LocalDate.parse(it) } ?: return
        if (blockId < 0) return
        ScheduleRepository(HabitDatabase.get(context)).markDone(blockId, date)
        ScheduleNotifier.cancelBlockNotification(context, blockId)
    }
}
