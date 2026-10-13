package com.roziqrizal.habitflow.session

import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountErrors
import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.data.account.LocalDataReset
import com.roziqrizal.habitflow.data.account.MeInfo
import com.roziqrizal.habitflow.data.account.SessionEnding
import com.roziqrizal.habitflow.ui.signOutBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Satu daftar kejadian bersama supaya urutan antar-komponen bisa diperiksa. */
private class Events {
    val list = mutableListOf<String>()
}

private class FakeChanges(private val events: Events) : PendingChangeSource {
    var counts = ChangeCounts()
    var backup = BackupState.Saved
    override suspend fun counts(): ChangeCounts { events.list += "counts"; return counts }
    override suspend fun backupState(): BackupState { events.list += "backup"; return backup }
}

private class FakeSession(private val events: Events) : SessionEnding {
    var revoke: AccountResult<Unit> = AccountResult.Success(Unit)
    var delete: AccountResult<Unit> = AccountResult.Success(Unit)
    override suspend fun revokeAndClear(): AccountResult<Unit> { events.list += "revoke"; return revoke }
    override suspend fun deleteAccount(password: String?, googleIdToken: String?): AccountResult<Unit> {
        events.list += "delete $password $googleIdToken"
        return delete
    }
    override suspend fun me(): AccountResult<MeInfo> = TODO()
}

private class FakeReset(private val events: Events) : LocalDataReset {
    override fun scheduleWipe(thenSignIn: Account?) { events.list += "wipe" }
    override fun restartProcess() { events.list += "restart" }
}

class SignOutCoordinatorTest {

    private val events = Events()
    private val changes = FakeChanges(events)
    private val session = FakeSession(events)
    private var syncResult: SyncOutcome = SyncOutcome.Done
    private val coordinator = SignOutCoordinator(
        changes, { events.list += "sync"; syncResult }, session, FakeReset(events),
    )

    // --- menghitung perubahan belum terkirim ---------------------------------------------------

    @Test
    fun jumlahPerubahanBelumTerkirimDihitungDariSemuaSumber() {
        val counts = ChangeCounts(habitEntryOutbox = 2, pendingDeletes = 1, unsyncedHabits = 3, unsyncedTodos = 4, unsyncedFollowUps = 5)

        assertEquals(15, counts.total)
        assertEquals(PendingChanges(15, BackupState.Stale), PendingChanges.of(counts, BackupState.Stale))
    }

    @Test
    fun tidakAdaPerubahanDanCadanganTersimpanBerartiTidakPerluPeringatan() {
        assertFalse(PendingChanges.of(ChangeCounts(), BackupState.Saved).any)
    }

    @Test
    fun setiapSumberSendirianSudahCukupMemicuPeringatan() {
        assertTrue(PendingChanges.of(ChangeCounts(habitEntryOutbox = 1), BackupState.Saved).any)
        assertTrue(PendingChanges.of(ChangeCounts(pendingDeletes = 1), BackupState.Saved).any)
        assertTrue(PendingChanges.of(ChangeCounts(unsyncedFollowUps = 1), BackupState.Saved).any)
        assertTrue("cadangan mati: data hanya di HP", PendingChanges.of(ChangeCounts(), BackupState.Off).any)
        assertTrue(PendingChanges.of(ChangeCounts(), BackupState.Stale).any)
    }

    @Test
    fun peringatanMenyebutJumlahDanKeadaanJaringan() {
        val offline = SignOutPlan(PendingChanges(3, BackupState.Saved), SyncOutcome.Offline)
        val text = signOutBody(offline)

        assertTrue(text, "3 perubahan belum terkirim" in text)
        assertTrue(text, "tidak tersambung ke internet" in text)
        assertTrue("data di HP ini akan dihapus" in text)
    }

    @Test
    fun keadaanBersihMengatakanSemuaSudahTersinkron() {
        val text = signOutBody(SignOutPlan(PendingChanges(0, BackupState.Saved), SyncOutcome.Done))

        assertTrue(text, "Semua perubahan sudah tersinkron" in text)
        assertFalse("belum terkirim" in text)
    }

    @Test
    fun cadanganMatiDijelaskanJujurBahwaDataHanyaDiHp() {
        val text = signOutBody(SignOutPlan(PendingChanges(0, BackupState.Off), SyncOutcome.Done))

        assertTrue(text, "Sinkron otomatis mati" in text)
        assertFalse("Semua perubahan sudah tersinkron" in text)
    }

    @Test
    fun sesiBerakhirSaatMengirimDijelaskanAlasannya() {
        val text = signOutBody(SignOutPlan(PendingChanges(2, BackupState.Saved), SyncOutcome.Failed("sesi berakhir, masuk lagi dulu untuk mengirimnya.")))

        assertTrue(text, "sesi berakhir" in text)
        assertTrue(text, "2 perubahan belum terkirim" in text)
    }

    // --- prepare -------------------------------------------------------------------------------

    @Test
    fun prepareMengirimDuluBaruMenghitungYangTersisa() = runBlocking {
        changes.counts = ChangeCounts(unsyncedHabits = 1)

        val plan = coordinator.prepare()

        assertEquals("kirim lebih dulu, hitung sesudahnya", listOf("sync", "counts", "backup"), events.list)
        assertEquals(PendingChanges(1, BackupState.Saved), plan.pending)
        assertEquals(SyncOutcome.Done, plan.sync)
    }

    @Test
    fun prepareTanpaInternetTetapMenghitungDanMelaporkanOffline() = runBlocking {
        syncResult = SyncOutcome.Offline
        changes.counts = ChangeCounts(habitEntryOutbox = 4)

        val plan = coordinator.prepare()

        assertEquals(SyncOutcome.Offline, plan.sync)
        assertEquals(4, plan.pending.items)
    }

    @Test
    fun prepareTidakMenyentuhSesiAtauData() = runBlocking {
        coordinator.prepare()

        assertFalse(events.list.any { it == "revoke" || it == "wipe" || it == "restart" })
    }

    // --- Keluar --------------------------------------------------------------------------------

    @Test
    fun keluarMenandaiHapusLebihDuluLaluMencabutSesiLaluMemulaiUlang() = runBlocking {
        coordinator.signOut()

        assertEquals(listOf("wipe", "revoke", "restart"), events.list)
    }

    @Test
    fun keluarTanpaInternetTetapMenghapusDanMemulaiUlang() = runBlocking {
        session.revoke = AccountResult.Offline

        coordinator.signOut()

        assertEquals("sesi di HP selalu ditutup dan data dihapus walau server tak terjangkau", listOf("wipe", "revoke", "restart"), events.list)
    }

    // --- Hapus akun ----------------------------------------------------------------------------

    @Test
    fun hapusAkunBerhasilBaruMenghapusDataHpSesudahServerBerhasil() = runBlocking {
        val result = coordinator.deleteAccount("rahasia123", null)

        assertEquals(AccountResult.Success(Unit), result)
        assertEquals(listOf("delete rahasia123 null", "wipe", "restart"), events.list)
    }

    @Test
    fun hapusAkunDenganGoogleMengirimIdToken() = runBlocking {
        coordinator.deleteAccount(null, "id-token")

        assertEquals("delete null id-token", events.list.first())
    }

    @Test
    fun hapusAkunKataSandiSalahTidakMenghapusApaPunDiHp() = runBlocking {
        val salah = AccountResult.Failed(422, AccountErrors.INVALID_CREDENTIALS, "Kata sandi salah.")
        session.delete = salah

        val result = coordinator.deleteAccount("salah", null)

        assertEquals(salah, result)
        assertEquals(listOf("delete salah null"), events.list)
    }

    @Test
    fun hapusAkunTanpaInternetTidakMenghapusApaPunDiHp() = runBlocking {
        session.delete = AccountResult.Offline

        assertEquals(AccountResult.Offline, coordinator.deleteAccount("rahasia123", null))

        assertEquals(listOf("delete rahasia123 null"), events.list)
    }

    @Test
    fun hapusAkunSaatSesiBerakhirTidakMenghapusApaPunDiHp() = runBlocking {
        session.delete = AccountResult.Unauthorized

        coordinator.deleteAccount("rahasia123", null)

        assertEquals(listOf("delete rahasia123 null"), events.list)
    }
}
