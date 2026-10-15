package com.roziqrizal.habitflow.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenAlarmAccessTest {

    private fun granted(sdk: Int, canUse: Boolean = false, declared: Boolean = false) =
        FullScreenAlarmAccess.evaluate(sdk, canUseOnApi34 = { canUse }, permissionGranted = { declared })

    @Test
    fun android14KeAtasMengikutiCanUseFullScreenIntentBukanManifest() {
        // Izin ada di manifest tetapi dicabut pengguna atau ditolak Google: tidak boleh dianggap diberikan.
        assertFalse(granted(sdk = 34, canUse = false, declared = true))
        assertTrue(granted(sdk = 34, canUse = true, declared = false))
        assertFalse(granted(sdk = 36, canUse = false, declared = true))
    }

    @Test
    fun android13KeBawahMengikutiKeberadaanIzinDiManifest() {
        assertTrue(granted(sdk = 33, canUse = false, declared = true))
        // Izin dicabut dari manifest (misalnya Google menolak deklarasinya): tidak ada layar penuh, alarm tetap lewat notifikasi.
        assertFalse(granted(sdk = 33, canUse = true, declared = false))
        assertFalse(granted(sdk = 26, canUse = true, declared = false))
    }

    @Test
    fun tawaranIzinHanyaKalauSistemBisaMemberikannya() {
        assertTrue(FullScreenAlarmAccess.canOfferSettings(sdk = 34, permissionDeclared = true))
        // Izin tidak dideklarasikan (build uji dan rilis pertama): layar sistem tidak bisa memberi apa-apa.
        assertFalse(FullScreenAlarmAccess.canOfferSettings(sdk = 34, permissionDeclared = false))
        assertFalse(FullScreenAlarmAccess.canOfferSettings(sdk = 36, permissionDeclared = false))
        // Android 13 ke bawah tidak punya layar izinnya.
        assertFalse(FullScreenAlarmAccess.canOfferSettings(sdk = 33, permissionDeclared = true))
    }

    @Test
    fun hanyaCabangYangDipakaiYangDievaluasi() {
        var api34Dipanggil = false
        var manifestDipanggil = false
        FullScreenAlarmAccess.evaluate(33, { api34Dipanggil = true; true }, { manifestDipanggil = true; true })
        assertEquals(false to true, api34Dipanggil to manifestDipanggil)
        api34Dipanggil = false; manifestDipanggil = false
        FullScreenAlarmAccess.evaluate(34, { api34Dipanggil = true; true }, { manifestDipanggil = true; true })
        assertEquals(true to false, api34Dipanggil to manifestDipanggil)
    }
}
