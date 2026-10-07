package com.roziqrizal.habitflow.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lokasi untuk menghitung waktu sholat. [name] hanya label untuk ditampilkan. */
data class PlaceLocation(val name: String, val latitude: Double, val longitude: Double)

/**
 * Lokasi tersimpan di SharedPreferences. Awalnya Surabaya, supaya waktu sholat langsung masuk
 * akal sebelum izin lokasi diberikan; ganti lewat layar Tentang (GPS atau manual).
 */
class LocationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _location = MutableStateFlow(read())
    val location: StateFlow<PlaceLocation> = _location.asStateFlow()

    fun set(place: PlaceLocation) {
        prefs.edit()
            .putString(KEY_NAME, place.name)
            .putString(KEY_LAT, place.latitude.toString())
            .putString(KEY_LNG, place.longitude.toString())
            .apply()
        _location.value = place
    }

    private fun read(): PlaceLocation {
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lng = prefs.getString(KEY_LNG, null)?.toDoubleOrNull()
        if (lat == null || lng == null) return DEFAULT
        return PlaceLocation(prefs.getString(KEY_NAME, null) ?: DEFAULT.name, lat, lng)
    }

    companion object {
        val DEFAULT = PlaceLocation("Surabaya", -7.2575, 112.7521)
        private const val KEY_NAME = "location_name"
        private const val KEY_LAT = "location_lat"
        private const val KEY_LNG = "location_lng"

        fun isValid(latitude: Double, longitude: Double): Boolean =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}

/** Lokasi kasar dari `LocationManager` bawaan, tanpa Google Play Services. */
object DeviceLocation {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Mengembalikan null kalau izin belum ada atau belum ada lokasi yang bisa dipakai. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
            val fresh = suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                    cont.resume(it)
                }
            }
            if (fresh != null) return fresh
        }
        // Belum ada lokasi baru: pakai lokasi terakhir yang diketahui sistem.
        return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}
