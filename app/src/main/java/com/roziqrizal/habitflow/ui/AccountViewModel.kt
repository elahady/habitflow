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
import com.roziqrizal.habitflow.data.account.MeInfo
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
    /** True kalau dialog Keluar yang terbuka adalah "Keluar dari semua perangkat". */
    val signOutAllDevices: Boolean = false,
    val delete: DeleteState = DeleteState.Hidden,
    val password: PasswordState = PasswordState.Hidden,
    /** Cara masuk akun (kata sandi, Google). Null selagi belum berhasil dimuat. */
    val me: MeInfo? = null,
    val meLoading: Boolean = false,
)

/** Layar Profil (tahap 31I): identitas, cara masuk, ubah kata sandi, Keluar, Keluar dari semua perangkat, dan Hapus akun. */
class AccountViewModel(
    private val manager: AccountManager,
    settings: AccountSettings,
    private val habitSync: HabitSyncManager,
    private val coordinator: SignOutCoordinator,
    private val googleSignIn: GoogleSignIn,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val signOutState = MutableStateFlow<SignOutState>(SignOutState.Hidden)
    private val signOutAllMode = MutableStateFlow(false)
    private val deleteState = MutableStateFlow<DeleteState>(DeleteState.Hidden)
    private val passwordState = MutableStateFlow<PasswordState>(PasswordState.Hidden)
    private val me = MutableStateFlow<MeInfo?>(null)
    private val meLoading = MutableStateFlow(false)

    private data class Dialogs(
        val signOut: SignOutState,
        val signOutAll: Boolean,
        val delete: DeleteState,
        val password: PasswordState,
    )

    private data class Identity(val account: Account?, val me: MeInfo?, val meLoading: Boolean)

    private val dialogs = combine(signOutState, signOutAllMode, deleteState, passwordState, ::Dialogs)
    private val identity = combine(settings.account, me, meLoading, ::Identity)

    val state: StateFlow<AccountUiState> = combine(identity, message, habitSync.status, dialogs) { identity, message, sync, dialogs ->
        AccountUiState(
            account = identity.account,
            message = message,
            habitSync = sync,
            signOut = dialogs.signOut,
            signOutAllDevices = dialogs.signOutAll,
            delete = dialogs.delete,
            password = dialogs.password,
            me = identity.me,
            meLoading = identity.meLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState(settings.account.value, habitSync = habitSync.status.value),
    )

    fun syncHabitsNow() {
        viewModelScope.launch { habitSync.syncNow() }
    }

    fun clearMessage() {
        message.value = null
    }

    // --- Cara masuk -----------------------------------------------------------------------------

    /** Memuat cara masuk akun (`GET me`). Dipanggil saat Profil dibuka; gagal membiarkan nilai sebelumnya. */
    fun loadMe() {
        if (meLoading.value) return
        meLoading.value = true
        viewModelScope.launch {
            try {
                (manager.me() as? AccountResult.Success)?.let { me.value = it.value }
            } finally {
                meLoading.value = false
            }
        }
    }

    // --- Ubah kata sandi ------------------------------------------------------------------------

    fun openChangePassword() {
        val info = me.value ?: return
        if (passwordState.value != PasswordState.Hidden) return
        message.value = null
        passwordState.value = PasswordState.Editing(hasPassword = info.hasPassword)
    }

    fun dismissChangePassword() {
        if (passwordState.value != PasswordState.Saving) passwordState.value = PasswordState.Hidden
    }

    fun changePassword(current: String, password: String, confirmation: String) {
        val editing = passwordState.value as? PasswordState.Editing ?: return
        PasswordChangeRules.validate(editing.hasPassword, current, password, confirmation)?.let {
            passwordState.value = editing.copy(error = it, networkProblem = false)
            return
        }
        passwordState.value = PasswordState.Saving
        viewModelScope.launch {
            val result = manager.changePassword(current.takeIf { editing.hasPassword }, password, confirmation)
            passwordState.value = PasswordChangeRules.after(result, editing.hasPassword) ?: PasswordState.Hidden
            when (result) {
                is AccountResult.Success -> {
                    message.value = PasswordChangeRules.successMessage(editing.hasPassword)
                    loadMe()
                }
                AccountResult.Unauthorized -> message.value = PasswordChangeRules.SESSION_ENDED
                else -> Unit
            }
        }
    }

    // --- Keluar ---------------------------------------------------------------------------------

    /** Membuka dialog Keluar. Dengan internet, perubahan yang tertunda dikirim dulu; hasilnya menentukan isi dialog. */
    fun requestSignOut() = openSignOut(allDevices = false)

    /** Seperti [requestSignOut], tapi yang dicabut adalah semua sesi akun, termasuk di perangkat lain. */
    fun requestSignOutAllDevices() = openSignOut(allDevices = true)

    private fun openSignOut(allDevices: Boolean) {
        if (signOutState.value != SignOutState.Hidden) return
        message.value = null
        signOutAllMode.value = allDevices
        signOutState.value = SignOutState.Preparing
        viewModelScope.launch {
            val plan = coordinator.prepare()
            // Pengguna bisa menutup dialog selagi menunggu: jangan membukanya lagi.
            if (signOutState.value == SignOutState.Preparing) signOutState.value = SignOutState.Ready(plan)
        }
    }

    fun dismissSignOut() {
        if (signOutState.value != SignOutState.SigningOut) {
            signOutState.value = SignOutState.Hidden
            signOutAllMode.value = false
        }
    }

    /** Memulai ulang proses app, jadi tidak kembali kalau berhasil. */
    fun confirmSignOut() {
        if (signOutState.value !is SignOutState.Ready) return
        signOutState.value = SignOutState.SigningOut
        viewModelScope.launch {
            if (!signOutAllMode.value) {
                coordinator.signOut()
                return@launch
            }
            // Berhasil memulai ulang proses dan tidak kembali. Yang kembali berarti server belum mencabut: tidak ada yang berubah.
            val failure = when (val result = coordinator.signOutAllDevices()) {
                is AccountResult.Success -> return@launch
                AccountResult.Offline -> "Tidak ada internet. Keluar dari semua perangkat butuh koneksi."
                AccountResult.Unauthorized -> PasswordChangeRules.SESSION_ENDED
                is AccountResult.Failed -> result.message
            }
            signOutState.value = SignOutState.Hidden
            signOutAllMode.value = false
            message.value = failure
        }
    }

    // --- Hapus akun -----------------------------------------------------------------------------

    fun requestDeleteAccount() {
        if (deleteState.value != DeleteState.Hidden) return
        message.value = null
        deleteState.value = DeleteState.Checking
        viewModelScope.launch {
            when (val info = manager.me()) {
                is AccountResult.Success ->
                    deleteState.value = if (info.value.hasPassword) DeleteState.NeedsPassword() else DeleteState.NeedsGoogle()
                AccountResult.Offline -> failToOpenDelete("Tidak ada internet. Hapus akun butuh koneksi.")
                AccountResult.Unauthorized -> failToOpenDelete("Sesi berakhir. Masuk lagi dulu.")
                is AccountResult.Failed -> failToOpenDelete(info.message)
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
