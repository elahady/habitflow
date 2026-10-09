package com.roziqrizal.habitflow.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Khusus build debug: menulis langkah uji ke Health Connect supaya kartu langkah bisa diuji di emulator.
 *
 *     adb shell pm grant com.roziqrizal.habitflow android.permission.health.WRITE_STEPS
 *     adb shell am broadcast -n com.roziqrizal.habitflow/.debug.DebugStepsReceiver --ei steps 8200
 *
 * Langkah ditulis sebagai satu rentang dari sejam lalu (atau tengah malam kalau belum sejam) sampai sekarang, dan
 * mengganti catatan uji sebelumnya dengan id klien yang sama.
 */
class DebugStepsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val steps = intent.getIntExtra("steps", -1).toLong()
        if (steps < 0) {
            Log.w(TAG, "Pakai --ei steps <jumlah>")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val now = Instant.now()
                val midnight = java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val start = maxOf(midnight, now.minusSeconds(3600))
                val offset = ZoneOffset.systemDefault().rules.getOffset(now)
                val record = StepsRecord(
                    count = steps,
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = now,
                    endZoneOffset = offset,
                    metadata = Metadata(clientRecordId = "habitflow-debug-steps", recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY),
                )
                HealthConnectClient.getOrCreate(context).insertRecords(listOf(record))
                Log.i(TAG, "Menulis $steps langkah ke Health Connect")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menulis langkah uji", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugSteps"
    }
}
