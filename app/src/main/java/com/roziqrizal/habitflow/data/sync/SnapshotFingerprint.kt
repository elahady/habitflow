package com.roziqrizal.habitflow.data.sync

import java.security.MessageDigest

/**
 * Sidik jari isi snapshot (tahap 31H), tanpa metadata yang selalu berubah (waktu ekspor, id perangkat, versi app).
 * Dipakai untuk tahu apakah data di HP berbeda dari cadangan terakhir yang berhasil terkirim, supaya Keluar bisa memperingatkan
 * perubahan yang **hanya** ada di snapshot (jadwal, kesehatan, makan, kalender, pengaturan), yang tidak punya antrean kirim sendiri.
 */
object SnapshotFingerprint {
    fun of(snapshot: Snapshot): String {
        val content = SnapshotCodec.encode(snapshot.copy(createdAt = 0L, deviceId = "", appVersion = ""))
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
