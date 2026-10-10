package com.roziqrizal.habitflow.data.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult
    data object Cancelled : GoogleIdTokenResult
    data class Failed(val detail: String) : GoogleIdTokenResult
}

/**
 * Minta ID token Google lewat Credential Manager (tahap 25). [serverClientId] harus Client ID
 * tipe Web yang sama dengan `GOOGLE_ANDROID_CLIENT_ID` di server (lihat AccountClient).
 * `requestIdToken` butuh Activity context karena Credential Manager menampilkan UI pemilih akun.
 */
class GoogleSignIn(private val serverClientId: String) {

    suspend fun requestIdToken(context: Context): GoogleIdTokenResult {
        if (serverClientId.isBlank()) {
            return GoogleIdTokenResult.Failed("GOOGLE_SERVER_CLIENT_ID belum diisi di local.properties.")
        }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleIdTokenResult.Failed("Jenis kredensial tidak dikenal.")
            }
        } catch (e: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Failed("ID token Google tidak bisa dibaca.")
        } catch (e: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleIdTokenResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Lupakan pilihan akun di Credential Manager supaya pemilih muncul lagi lain kali. Gagal diam-diam (tidak fatal). */
    suspend fun clearState(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            // Token lokal tetap dihapus oleh pemanggil (AccountManager.signOut) walau ini gagal.
        }
    }
}
