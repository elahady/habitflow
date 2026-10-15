package com.roziqrizal.habitflow.data.sync

import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.UnauthorizedListener
import com.roziqrizal.habitflow.domain.work.DailyBrief
import com.roziqrizal.habitflow.testing.FakeSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyBriefRepositoryTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val settings = AccountSettings(FakeSharedPreferences())
    private val store = DailyBriefStore(FakeSharedPreferences())
    private var response: HabitApiResult<List<DailyBrief>> = HabitApiResult.Success(emptyList())
    private var tokenUsed: String? = null

    private val repo = DailyBriefRepository(settings, store, UnauthorizedListener.None) { token ->
        DailyBriefSource {
            tokenUsed = token
            response
        }
    }

    private fun signIn() = settings.save(Account(1, "a@example.com", "A", null, "token-uji"))

    private fun brief(date: String, digest: String = "isi $date") = DailyBrief(LocalDate.parse(date), digest, 1L)

    @Test
    fun belumMasukTidakMemanggilServer() = runBlocking {
        assertEquals(HabitApiResult.Unauthorized, repo.refresh(today))
        assertNull(tokenUsed)
    }

    @Test
    fun berhasilMenyimpanRingkasanTerbaruYangTidakDiDepan() = runBlocking {
        signIn()
        response = HabitApiResult.Success(listOf(brief("2026-10-20"), brief("2026-10-15"), brief("2026-10-14")))

        assertEquals(HabitApiResult.Success(Unit), repo.refresh(today))

        assertEquals("token-uji", tokenUsed)
        assertEquals(listOf(brief("2026-10-15")), repo.briefs.value)
    }

    @Test
    fun serverTanpaRingkasanMengosongkanSalinanLama() = runBlocking {
        signIn()
        store.save(brief("2026-10-14"))
        response = HabitApiResult.Success(emptyList())

        repo.refresh(today)

        assertTrue(repo.briefs.value.isEmpty())
    }

    @Test
    fun gagalJaringanMembiarkanSalinanLama() = runBlocking {
        signIn()
        store.save(brief("2026-10-14"))
        response = HabitApiResult.NetworkError("tidak ada sinyal")

        assertEquals(HabitApiResult.NetworkError("tidak ada sinyal"), repo.refresh(today))

        assertEquals(listOf(brief("2026-10-14")), repo.briefs.value)
    }

    @Test
    fun galatServerDanTokenDitolakMembiarkanSalinanLama() = runBlocking {
        signIn()
        store.save(brief("2026-10-14"))

        response = HabitApiResult.ServerError(500)
        assertEquals(HabitApiResult.ServerError(500), repo.refresh(today))
        response = HabitApiResult.Unauthorized
        assertEquals(HabitApiResult.Unauthorized, repo.refresh(today))

        assertEquals(listOf(brief("2026-10-14")), repo.briefs.value)
    }
}
