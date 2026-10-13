package com.roziqrizal.habitflow.data.team

import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingInviteStoreTest {

    private val prefs = FakeSharedPreferences()

    private fun restarted() = PendingInviteStore(FakeSharedPreferences(prefs.snapshot()))

    @Test
    fun awalnyaKosong() {
        assertNull(PendingInviteStore(prefs).read())
    }

    @Test
    fun tokenBertahanSetelahProsesDimulaiUlang() {
        PendingInviteStore(prefs).save("abc123")

        assertEquals("abc123", restarted().read())
    }

    @Test
    fun undanganBaruMenggantikanYangLama() {
        val store = PendingInviteStore(prefs)
        store.save("lama")
        store.save("baru")

        assertEquals("baru", store.read())
    }

    @Test
    fun hapusMengosongkan() {
        val store = PendingInviteStore(prefs)
        store.save("abc123")

        store.clear()

        assertNull(store.read())
        assertNull(restarted().read())
    }

    @Test
    fun tokenKosongTidakDisimpan() {
        val store = PendingInviteStore(prefs)
        store.save("   ")

        assertNull(store.read())
    }
}
