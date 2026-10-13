package com.roziqrizal.habitflow.ui

import com.roziqrizal.habitflow.data.account.AccountErrors
import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.ui.auth.AuthMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordChangeRulesTest {

    @Test
    fun akunBersandiWajibMengisiKataSandiSaatIni() {
        assertEquals(PasswordChangeRules.CURRENT_REQUIRED, PasswordChangeRules.validate(true, "", "baru-12345", "baru-12345"))
    }

    @Test
    fun akunGoogleTanpaSandiTidakDimintaKataSandiSaatIni() {
        assertNull(PasswordChangeRules.validate(false, "", "baru-12345", "baru-12345"))
    }

    @Test
    fun kataSandiBaruMinimalDelapanKarakter() {
        assertEquals(AuthMessages.PASSWORD_TOO_SHORT, PasswordChangeRules.validate(true, "lama", "pendek", "pendek"))
        assertNull(PasswordChangeRules.validate(true, "lama", "12345678", "12345678"))
    }

    @Test
    fun ulangannyaHarusSama() {
        assertEquals(AuthMessages.CONFIRMATION, PasswordChangeRules.validate(true, "lama", "baru-12345", "baru-12346"))
    }

    @Test
    fun galatPertamaYangDilaporkanSesuaiUrutanPemeriksaan() {
        // Kolom saat ini kosong dan kata sandi baru pendek: yang kosong dulu.
        assertEquals(PasswordChangeRules.CURRENT_REQUIRED, PasswordChangeRules.validate(true, "", "x", "y"))
        // Kata sandi baru pendek dan tidak cocok: yang pendek dulu.
        assertEquals(AuthMessages.PASSWORD_TOO_SHORT, PasswordChangeRules.validate(true, "lama", "x", "y"))
    }

    @Test
    fun berhasilMenutupDialog() {
        assertNull(PasswordChangeRules.after(AccountResult.Success(Unit), true))
    }

    @Test
    fun sesiBerakhirMenutupDialogKarenaGerbangAuthYangMenanganinya() {
        assertNull(PasswordChangeRules.after(AccountResult.Unauthorized, true))
    }

    @Test
    fun tanpaInternetTetapDiDialogDenganWarnaNetral() {
        val state = PasswordChangeRules.after(AccountResult.Offline, true) as PasswordState.Editing

        assertEquals(PasswordChangeRules.OFFLINE, state.error)
        assertTrue(state.networkProblem)
        assertTrue(state.hasPassword)
    }

    @Test
    fun ditolakServerTetapDiDialogDenganAlasanDariServer() {
        val salah = AccountResult.Failed(422, AccountErrors.INVALID_CREDENTIALS, "Kata sandi saat ini salah.")

        val state = PasswordChangeRules.after(salah, true) as PasswordState.Editing

        assertEquals("Kata sandi saat ini salah.", state.error)
        assertTrue(!state.networkProblem)
    }

    @Test
    fun pesanBerhasilMembedakanMengubahDanMembuat() {
        assertEquals(PasswordChangeRules.CHANGED, PasswordChangeRules.successMessage(true))
        assertEquals(PasswordChangeRules.CREATED, PasswordChangeRules.successMessage(false))
    }
}
