package com.roziqrizal.habitflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.data.ThemeMode
import com.roziqrizal.habitflow.data.ThemeSettings
import com.roziqrizal.habitflow.ui.ContributionViewModel
import com.roziqrizal.habitflow.ui.DayClock
import com.roziqrizal.habitflow.ui.HabitFlowApp
import com.roziqrizal.habitflow.ui.ManageHabitsViewModel
import com.roziqrizal.habitflow.ui.TodayViewModel
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme

class MainActivity : ComponentActivity() {

    // Pergantian hari, jam, atau zona waktu saat app terbuka langsung memperbarui tanggal.
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = clock.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeSettings = ThemeSettings(applicationContext)
        themeSettings.syncWithSystem()
        setContent {
            val themeMode by themeSettings.mode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Edge-to-edge: app digambar di belakang status bar dan bar gesture, jadi warnanya
            // sama dengan latar layar. Warna ikon system bar mengikuti tema app, bukan tema HP.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            HabitFlowTheme(darkTheme = darkTheme) {
                val repo = remember { HabitRepository(HabitDatabase.get(applicationContext)) }
                val today: TodayViewModel = viewModel(
                    factory = viewModelFactory { initializer { TodayViewModel(repo, clock) } },
                )
                val contribution: ContributionViewModel = viewModel(
                    factory = viewModelFactory { initializer { ContributionViewModel(repo, clock) } },
                )
                val manage: ManageHabitsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ManageHabitsViewModel(repo) } },
                )
                val versionName = remember {
                    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                }

                HabitFlowApp(
                    today = today,
                    contribution = contribution,
                    manage = manage,
                    versionName = versionName,
                    themeMode = themeMode,
                    onThemeModeChange = themeSettings::setMode,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Broadcast tidak diterima saat app di latar belakang, jadi cek ulang tanggal setiap kembali.
        clock.refresh()
    }

    override fun onStop() {
        unregisterReceiver(timeChangeReceiver)
        super.onStop()
    }

    companion object {
        // Disimpan di luar activity supaya ViewModel yang selamat dari rotasi tetap memakai jam yang sama.
        private val clock = DayClock()
    }
}
