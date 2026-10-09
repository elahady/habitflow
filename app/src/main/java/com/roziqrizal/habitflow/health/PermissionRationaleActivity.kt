package com.roziqrizal.habitflow.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roziqrizal.habitflow.ui.TonalButton
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme

/**
 * Penjelasan izin yang diminta Health Connect ("privacy rationale"). Dibuka dari layar izin Health Connect atau dari
 * pengaturan privasi Android. Isinya menyebut apa yang dibaca, untuk apa, dan ke mana data pergi.
 */
class PermissionRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitFlowTheme(darkTheme = isSystemInDarkTheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Akses langkah", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "HabitFlow membaca jumlah langkah harianmu dari Health Connect, hanya untuk menampilkan " +
                                "kemajuan menuju 8.000 langkah dan mencentang habit \"8.000 langkah\" saat targetnya tercapai.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Hanya langkah yang dibaca. HabitFlow tidak menulis ke Health Connect dan tidak menyimpan " +
                                "jumlah langkahmu; yang tersimpan hanya centang habit. Data langkah tidak ikut cadangan " +
                                "ke server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Izin latar belakang membuat habit tercentang walau app sedang tertutup. Tanpa izin itu, " +
                                "langkah baru dihitung saat app dibuka. Kamu bisa mencabut izin kapan saja dari " +
                                "pengaturan Health Connect.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TonalButton(text = "Tutup", onClick = { finish() })
                    }
                }
            }
        }
    }
}
