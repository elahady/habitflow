package com.roziqrizal.habitflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.data.Habit

@Composable
fun ManageHabitsScreen(
    habits: List<Habit>,
    onAdd: (String) -> Unit,
    onRename: (Habit, String) -> Unit,
    onSetMandatory: (Habit, Boolean) -> Unit,
    onDelete: (Habit) -> Unit,
) {
    // Dialog menyimpan id habit, bukan objeknya, supaya tetap ada setelah rotasi layar.
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var releasingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val editing = habits.find { it.id == editingId }
    val deleting = habits.find { it.id == deletingId }
    val releasing = habits.find { it.id == releasingId }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Kelola habit",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            TonalButton(
                text = "+ Habit",
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(habits, key = { it.id }) { habit ->
            HabitManageRow(
                habit = habit,
                onRename = { editingId = habit.id },
                // Menyalakan wajib langsung berlaku. Mematikan wajib minta konfirmasi dulu,
                // karena setelah itu habit bisa dihapus.
                onMandatoryChange = { mandatory ->
                    if (mandatory) onSetMandatory(habit, true) else releasingId = habit.id
                },
                onDelete = { deletingId = habit.id },
            )
        }
    }

    if (showAddDialog) {
        HabitNameDialog(
            title = "Habit baru",
            initialName = "",
            confirmLabel = "Simpan",
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                onAdd(name)
                showAddDialog = false
            },
        )
    }

    editing?.let { habit ->
        HabitNameDialog(
            title = "Ubah nama habit",
            initialName = habit.name,
            confirmLabel = "Simpan",
            onDismiss = { editingId = null },
            onConfirm = { name ->
                onRename(habit, name)
                editingId = null
            },
        )
    }

    releasing?.let { habit ->
        AlertDialog(
            onDismissRequest = { releasingId = null },
            title = { Text("Lepas status wajib?") },
            text = {
                Text("\"${habit.name}\" tidak lagi dikunci di urutan atas dan bisa dihapus. Riwayat centangnya tetap tersimpan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetMandatory(habit, false)
                        releasingId = null
                    },
                ) { Text("Lepas") }
            },
            dismissButton = {
                TextButton(onClick = { releasingId = null }) { Text("Batal") }
            },
        )
    }

    deleting?.let { habit ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Hapus habit?") },
            text = {
                Text("\"${habit.name}\" dan seluruh riwayat centangnya akan dihapus. Tindakan ini tidak bisa dibatalkan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(habit)
                        deletingId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun HabitManageRow(
    habit: Habit,
    onRename: () -> Unit,
    onMandatoryChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRename)
                        .padding(vertical = 8.dp),
                )
                // Seluruh baris bisa ditekan, bukan hanya switch-nya, supaya mudah dijangkau.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.toggleable(
                        value = habit.isMandatory,
                        role = Role.Switch,
                        onValueChange = onMandatoryChange,
                    ),
                ) {
                    Text(
                        text = "Wajib",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = habit.isMandatory, onCheckedChange = null)
                }
            }
            // Habit wajib tidak punya tombol hapus, sesuai aturan di rancangan.
            if (!habit.isMandatory) {
                TextButton(onClick = onDelete) { Text("Hapus") }
            }
        }
    }
}

@Composable
private fun HabitNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama habit") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
