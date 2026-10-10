package com.roziqrizal.habitflow.data.account

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SignInResult {
    data class Success(val account: Account) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Menggabungkan Credential Manager, tukar ID token ke token API, dan penyimpanan akun (tahap 25).
 * Prasyarat bersama untuk fitur yang butuh login: To-Do Tim (tahap 26) dan sinkron habit/to-do
 * harian (tahap 28). Operasi satu per satu ([mutex]) supaya tombol dobel tidak saling menimpa.
 */
class AccountManager(
    private val settings: AccountSettings,
    private val googleSignIn: GoogleSignIn,
    private val client: AccountClient = AccountClient(),
) {
    private val mutex = Mutex()

    suspend fun signIn(context: Context): SignInResult = mutex.withLock {
        when (val idToken = googleSignIn.requestIdToken(context)) {
            is GoogleIdTokenResult.Success -> when (val result = client.login(idToken.idToken)) {
                is LoginResult.Success -> {
                    settings.save(result.account)
                    SignInResult.Success(result.account)
                }
                else -> SignInResult.Failed(result.message())
            }
            GoogleIdTokenResult.Cancelled -> SignInResult.Cancelled
            is GoogleIdTokenResult.Failed -> SignInResult.Failed(idToken.detail)
        }
    }

    suspend fun signOut(context: Context) = mutex.withLock {
        googleSignIn.clearState(context)
        settings.clear()
    }
}
