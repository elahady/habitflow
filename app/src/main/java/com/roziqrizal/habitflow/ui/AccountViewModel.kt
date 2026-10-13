package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountManager
import com.roziqrizal.habitflow.data.account.AccountResult
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.GoogleIdTokenResult
import com.roziqrizal.habitflow.data.account.GoogleSignIn
import com.roziqrizal.habitflow.data.account.SignInResult
import com.roziqrizal.habitflow.data.sync.HabitSyncManager
import com.roziqrizal.habitflow.data.sync.HabitSyncStatus
import com.roziqrizal.habitflow.session.SignOutCoordinator
import com.roziqrizal.habitflow.session.SignOutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dialog Keluar (tahap 31H). */
sealed interface SignOutState {
    data object Hidden : SignOutState

    /** Mengirim perubahan yang tertunda dan menghitung yang tersisa. */
    data object Preparing : SignOutState

    data class Ready(val plan: SignOutPlan) : SignOutState

    data object SigningOut : SignOutState
}

/** Dialog Hapus akun (tahap 31H): konfirmasi ulang wajib, kata sandi atau akun Google untuk akun tanpa kata sandi. */
sealed interface DeleteState {
    data object Hidden : DeleteState

    /** Memeriksa lewat `GET me` apakah akun punya kata sandi. */
    data object Checking : DeleteState

    data class NeedsPassword(val error: String? = null) : DeleteState

    data class NeedsGoogle(val error: String? = null) : DeleteState

    data object Deleting : DeleteState
}

data class AccountUiState(
    val account: Account?,
    val busy: Boolean = false,
    val message: String? = null,
    val habitSync: HabitSyncStatus = HabitSyncStatus.Idle,
    val signOut: SignOutState = SignOutState.Hidden,
    val delete: DeleteState = DeleteState.Hidden,
)

/** Akun di Tentang (tahap 25), Keluar dan Hapus akun (31H). 31I memindahkannya ke layar Profil. */
class AccountViewModel(
    private val manager: AccountManager,
    settings: AccountSettings,
    private val habitSync: HabitSyncManager,
    private val coordinator: SignOutCoordinator,
    private val googleSignIn: GoogleSignIn,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val signOutState = MutableStateFlow<SignOutState>(SignOutState.Hidden)
    private val deleteState = MutableStateFlow<DeleteState>(DeleteState.Hidden)

    private val dialogs = combine(signOutState, deleteState) { signOut, delete -> signOut to delete }

    val state: StateFlow<AccountUiState> = combine(settings.account, busy, message, habitSync.status, dialogs) { account, busy, message, sync, dialogs ->
        AccountUiState(account, busy, message, sync, dialogs.first, dialogs.second)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(settings.account.value, habitSync = habitSync.status.value),
    )

    fun syncHabitsNow() {
        viewModelScope.launch { habitSync.syncNow() }
    }

    fun signIn(context: Context) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                when (val result = manager.signIn(context)) {
                    is SignInResult.Success -> message.value = "Masuk sebagai ${result.account.name}."
                    SignInResult.Cancelled -> Unit
                    is SignInResult.Failed -> message.value = result.message
                    SignInResult.Offline -> message.value = "Tidak ada internet. Coba lagi nanti."
                    SignInResult.OwnerConflict -> message.value = "HP ini masih menyimpan data akun lain."
                }
            } finally {
                busy.value = false
            }
        }
    }

    // --- Keluar ---------------------------------------------------------------------------------

    /** Membuka dialog Keluar. Dengan internet, perubahan yang tertunda dikirim dulu; hasilnya menentukan isi dialog. */
    fun requestSignOut() {
        if (signOutState.value != SignOutState.Hidden) return
        signOutState.value = SignOutState.Preparing
        viewModelScope.launch {
            val plan = coordinator.prepare()
            // Pengguna bisa menutup dialog selagi menunggu: jangan membukanya lagi.
            if (signOutState.value == SignOutState.Preparing) signOutState.value = SignOutState.Ready(plan)
        }
    }

    fun dismissSignOut() {
        if (signOutState.value != SignOutState.SigningOut) signOutState.value = SignOutState.Hidden
    }

    /** Memulai ulang proses app, jadi tidak kembali kalau berhasil. */
    fun confirmSignOut() {
        if (signOutState.value !is SignOutState.Ready) return
        signOutState.value = SignOutState.SigningOut
        viewModelScope.launch { coordinator.signOut() }
    }

    // --- Hapus akun -----------------------------------------------------------------------------

    fun requestDeleteAccount() {
        if (deleteState.value != DeleteState.Hidden) return
        deleteState.value = DeleteState.Checking
        viewModelScope.launch {
            when (val me = manager.me()) {
                is AccountResult.Success ->
                    deleteState.value = if (me.value.hasPassword) DeleteState.NeedsPassword() else DeleteState.NeedsGoogle()
                AccountResult.Offline -> failToOpenDelete("Tidak ada internet. Hapus akun butuh koneksi.")
                AccountResult.Unauthorized -> failToOpenDelete("Sesi berakhir. Masuk lagi dulu.")
                is AccountResult.Failed -> failToOpenDelete(me.message)
            }
        }
    }

    private fun failToOpenDelete(text: String) {
        deleteState.value = DeleteState.Hidden
        message.value = text
    }

    fun dismissDelete() {
        if (deleteState.value != DeleteState.Deleting) deleteState.value = DeleteState.Hidden
    }

    fun deleteWithPassword(password: String) {
        if (password.isEmpty()) {
            deleteState.value = DeleteState.NeedsPassword("Isi kata sandi untuk mengonfirmasi.")
            return
        }
        runDelete(onFailure = { DeleteState.NeedsPassword(it) }) { coordinator.deleteAccount(password, null) }
    }

    /** Akun tanpa kata sandi (masuk lewat Google) mengonfirmasi dengan memilih akun Google lagi. [context] harus Activity. */
    fun deleteWithGoogle(context: Context) {
        runDelete(onFailure = { DeleteState.NeedsGoogle(it) }) {
            when (val token = googleSignIn.requestIdToken(context)) {
                is GoogleIdTokenResult.Success -> coordinator.deleteAccount(null, token.idToken)
                GoogleIdTokenResult.Cancelled -> null
                is GoogleIdTokenResult.Failed -> AccountResult.Failed(0, null, "Memilih akun Google belum berhasil. Coba lagi.")
            }
        }
    }

    /** Menjalankan [block]; null berarti dibatalkan pengguna. Kalau berhasil proses dimulai ulang dan ini tidak kembali. */
    private fun runDelete(onFailure: (String) -> DeleteState, block: suspend () -> AccountResult<Unit>?) {
        val before = deleteState.value
        if (before == DeleteState.Deleting || before == DeleteState.Hidden || before == DeleteState.Checking) return
        deleteState.value = DeleteState.Deleting
        viewModelScope.launch {
            val result = block()
            deleteState.value = when (result) {
                null -> before
                is AccountResult.Success -> DeleteState.Deleting
                AccountResult.Offline -> onFailure("Tidak ada internet. Hapus akun butuh koneksi.")
                AccountResult.Unauthorized -> DeleteState.Hidden.also { message.value = "Sesi berakhir. Masuk lagi dulu." }
                is AccountResult.Failed -> onFailure(result.message)
            }
        }
    }
}
