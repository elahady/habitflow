package com.roziqrizal.habitflow.domain.prayer

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.searchHourAngle
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/** Waktu sholat satu hari, dibulatkan ke menit. Null kalau matahari tidak mencapai ketinggiannya di lintang itu. */
data class PrayerTimes(
    val imsak: LocalTime?,
    val subuh: LocalTime?,
    val terbit: LocalTime?,
    val dhuha: LocalTime?,
    val dzuhur: LocalTime?,
    val ashar: LocalTime?,
    val maghrib: LocalTime?,
    val isya: LocalTime?,
)

/** Nama waktu sholat yang bisa dijadikan patokan blok jadwal. */
enum class PrayerName { SUBUH, DZUHUR, ASHAR, MAGHRIB, ISYA }

fun PrayerTimes.of(name: PrayerName): LocalTime? = when (name) {
    PrayerName.SUBUH -> subuh
    PrayerName.DZUHUR -> dzuhur
    PrayerName.ASHAR -> ashar
    PrayerName.MAGHRIB -> maghrib
    PrayerName.ISYA -> isya
}

/**
 * Waktu sholat metode Ephemeris Al Hasib, sama dengan Al-Kaukaba
 * (lihat docs/visi-super-app.md dan rumus-hisab-ephemeris.md di repo Al-Kaukaba).
 *
 * Deklinasi dan waktu istiwa' (transit matahari) diambil dari Astronomy Engine. Karena
 * `12 - e + Kwd` sama dengan waktu transit di zona waktu setempat, rumus gabungan
 * `12 - e ± t + Kwd + i` ditulis sebagai `transit ± t + i`.
 */
object EphemerisPrayerCalculator {

    private const val IKHTIYAT_HOURS = 2.0 / 60.0

    fun calculate(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): PrayerTimes {
        val startOfDay = date.atStartOfDay(zone)
        val timeZoneHours = zone.rules.getOffset(startOfDay.toInstant()).totalSeconds / 3600.0

        val observer = Observer(latitude, longitude, 0.0)
        val transit = searchHourAngle(
            Body.Sun, observer, 0.0,
            Time.fromMillisecondsSince1970(startOfDay.toInstant().toEpochMilli()), +1,
        )
        val declination = equator(Body.Sun, transit.time, observer, EquatorEpoch.OfDate, Aberration.Corrected).dec
        val transitHours = utHours(transit.time) + timeZoneHours

        fun at(altitude: Double, beforeTransit: Boolean, ikhtiyat: Double): LocalTime? {
            val t = hourAngleHours(latitude, declination, altitude) ?: return null
            val hours = if (beforeTransit) transitHours - t else transitHours + t
            return toLocalTime(hours + ikhtiyat)
        }

        return PrayerTimes(
            imsak = at(-22.0, true, 0.0),
            subuh = at(-20.0, true, IKHTIYAT_HOURS),
            terbit = at(1.0, true, -IKHTIYAT_HOURS),
            dhuha = at(4.5, true, IKHTIYAT_HOURS),
            dzuhur = toLocalTime(transitHours + IKHTIYAT_HOURS),
            ashar = at(asharAltitude(latitude, declination), false, IKHTIYAT_HOURS),
            maghrib = at(-1.0, false, IKHTIYAT_HOURS),
            isya = at(-18.0, false, IKHTIYAT_HOURS),
        )
    }

    /** Cotan h = tan|lintang - deklinasi| + 1 (bayangan sepanjang benda ditambah bayangan saat istiwa'). */
    private fun asharAltitude(latitude: Double, declination: Double): Double =
        Math.toDegrees(atan(1.0 / (1.0 + tan(Math.toRadians(abs(latitude - declination))))))

    /** Cos t = (sin h - sin lintang sin deklinasi) / (cos lintang cos deklinasi), dalam jam. Null kalau tidak tercapai. */
    private fun hourAngleHours(latitude: Double, declination: Double, altitude: Double): Double? {
        val lat = Math.toRadians(latitude)
        val dec = Math.toRadians(declination)
        val cosT = (sin(Math.toRadians(altitude)) - sin(lat) * sin(dec)) / (cos(lat) * cos(dec))
        if (cosT < -1.0 || cosT > 1.0) return null
        return Math.toDegrees(acos(cosT)) / 15.0
    }

    private fun utHours(time: Time): Double {
        val millisOfDay = Math.floorMod(time.toMillisecondsSince1970(), 24L * 3600_000L)
        return millisOfDay / 3_600_000.0
    }

    private fun toLocalTime(hoursRaw: Double): LocalTime {
        val minutesOfDay = Math.floorMod(round(hoursRaw * 60.0).toLong(), 1440L).toInt()
        return LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
    }
}
