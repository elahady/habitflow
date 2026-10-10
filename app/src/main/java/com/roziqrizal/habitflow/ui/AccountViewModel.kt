package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roziqrizal.habitflow.data.account.Account
import com.roziqrizal.habitflow.data.account.AccountManager
import com.roziqrizal.habitflow.data.account.AccountSettings
import com.roziqrizal.habitflow.data.account.SignInResult
import com.roziqrizal.habitflow.data.sync.HabitSyncManager
import com.roziqrizal.habitflow.data.sync.HabitSyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUiState(
    val account: Account?,
    val busy: Boolean = false,
    val message: String? = null,
    val habitSync: HabitSyncStatus = HabitSyncStatus.Idle,
)

/** Login/logout Google (tahap 25) - dipakai dari Tentang, prasyarat untuk To-Do Tim dan sinkron habit. */
class AccountViewModel(
    private val manager: AccountManager,
    settings: AccountSettings,
    private val habitSync: HabitSyncManager,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountUiState> = combine(settings.account, busy, message, habitSync.status) { account, busy, message, sync ->
        AccountUiState(account, busy, message, sync)
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
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun signOut(context: Context) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try {
                manager.signOut(context)
            } finally {
                busy.value = false
            }
        }
    }
}
