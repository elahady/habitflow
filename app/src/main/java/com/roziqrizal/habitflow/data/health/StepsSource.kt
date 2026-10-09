package com.roziqrizal.habitflow.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Hasil membaca langkah hari ini dari Health Connect (tahap 21). */
sealed interface StepsReading {
    /**
     * [background] benar kalau izin membaca di latar belakang sudah diberikan, jadi pembacaan berkala bisa jalan.
     * [backgroundAvailable] salah kalau Health Connect di HP ini belum mendukung izin itu, jadi tidak ada yang bisa diminta.
     */
    data class Available(val steps: Long, val background: Boolean, val backgroundAvailable: Boolean) : StepsReading

    /** Health Connect ada, tapi izin langkah belum diberikan. */
    data object NeedsPermission : StepsReading

    /** Health Connect belum ada di HP (Android 13 ke bawah perlu app-nya). */
    data object NotInstalled : StepsReading

    /** Health Connect perlu diperbarui sebelum bisa dipakai. */
    data object UpdateRequired : StepsReading
}

/** Sumber langkah. Antarmuka supaya bisa diganti saat uji. */
interface StepsSource {
    /**
     * Langkah pada [date]. Dengan [inBackground] benar, pembacaan hanya dicoba kalau izin latar belakang ada, karena
     * Health Connect menolak pembacaan dari app yang tidak tampil tanpa izin itu.
     */
    suspend fun read(date: LocalDate, zone: ZoneId, inBackground: Boolean): StepsReading

    /** Izin yang diminta di layar izin Health Connect. Izin latar belakang hanya ikut kalau fiturnya tersedia. */
    suspend fun permissionsToRequest(): Set<String>
}

/** Membaca langkah lewat Health Connect. Izin: [READ_STEPS] dan, kalau ada, [READ_IN_BACKGROUND]. */
class HealthConnectStepsSource(private val context: Context) : StepsSource {

    private val appContext = context.applicationContext

    private fun status(): Int = HealthConnectClient.getSdkStatus(appContext)

    override suspend fun read(date: LocalDate, zone: ZoneId, inBackground: Boolean): StepsReading {
        when (status()) {
            HealthConnectClient.SDK_UNAVAILABLE -> return StepsReading.NotInstalled
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> return StepsReading.UpdateRequired
        }
        val client = HealthConnectClient.getOrCreate(appContext)
        val granted = client.permissionController.getGrantedPermissions()
        if (READ_STEPS !in granted) return StepsReading.NeedsPermission
        val background = READ_IN_BACKGROUND in granted
        if (inBackground && !background) return StepsReading.NeedsPermission

        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(Instant.now(), date.plusDays(1).atStartOfDay(zone).toInstant())
        val result: AggregationResult = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return StepsReading.Available(
            steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
            background = background,
            backgroundAvailable = background || isBackgroundReadAvailable(client),
        )
    }

    override suspend fun permissionsToRequest(): Set<String> {
        if (status() != HealthConnectClient.SDK_AVAILABLE) return setOf(READ_STEPS)
        val client = HealthConnectClient.getOrCreate(appContext)
        return if (isBackgroundReadAvailable(client)) setOf(READ_STEPS, READ_IN_BACKGROUND) else setOf(READ_STEPS)
    }

    /** Apakah Health Connect di HP ini mendukung izin baca di latar belakang. Belum ada di semua versi. */
    @OptIn(ExperimentalFeatureAvailabilityApi::class)
    private fun isBackgroundReadAvailable(client: HealthConnectClient): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_HEALTH_DATA_BACKGROUND_READ) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    companion object {
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        const val READ_IN_BACKGROUND: String = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        /** Kontrak untuk `registerForActivityResult`: meminta izin lewat layar izin Health Connect. */
        fun requestContract() = PermissionController.createRequestPermissionResultContract()
    }
}
