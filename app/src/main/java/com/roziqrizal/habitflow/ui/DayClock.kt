package com.roziqrizal.habitflow.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Tanggal hari ini yang bisa diamati. Nilainya hanya berubah lewat [refresh], yang dipanggil
 * MainActivity saat app kembali aktif dan saat tanggal, jam, atau zona waktu perangkat berubah.
 * Dengan begitu pergantian hari saat app terbuka ikut terbaca oleh layar dan carry-over.
 */
class DayClock(private val now: () -> LocalDate = { LocalDate.now() }) {
    private val _date = MutableStateFlow(now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    fun refresh() {
        _date.value = now()
    }
}
