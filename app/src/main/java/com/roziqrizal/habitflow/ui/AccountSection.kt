package com.roziqrizal.habitflow.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Aksi bagian Akun di Tentang. [Context] dibutuhkan Credential Manager untuk menampilkan pemilih akun. */
data class AccountActions(val onSignIn: (Context) -> Unit, val onSignOut: (Context) -> Unit)

/**
 * Login/logout Google (tahap 25) - prasyarat untuk To-Do Tim (tahap 26) dan sinkron habit/to-do
 * harian (tahap 28).
 */
@Composable
fun AccountSection(state: AccountUiState, actions: AccountActions) {
    val context = LocalContext.current
    val account = state.account

    if (account == null) {
        Text(
            "Masuk dengan Google untuk pakai To-Do Tim dan sinkron habit ke web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TonalButton(
            text = "Masuk dengan Google",
            enabled = !state.busy,
            onClick = { actions.onSignIn(context) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { actions.onSignOut(context) }, enabled = !state.busy) { Text("Keluar") }
        }
    }

    state.message?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
