package com.roziqrizal.habitflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.ui.ContributionViewModel
import com.roziqrizal.habitflow.ui.HabitFlowApp
import com.roziqrizal.habitflow.ui.ManageHabitsViewModel
import com.roziqrizal.habitflow.ui.TodayViewModel
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HabitFlowTheme {
                val repo = remember { HabitRepository(HabitDatabase.get(applicationContext)) }
                val today: TodayViewModel = viewModel(
                    factory = viewModelFactory { initializer { TodayViewModel(repo) } },
                )
                val contribution: ContributionViewModel = viewModel(
                    factory = viewModelFactory { initializer { ContributionViewModel(repo) } },
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
                )
            }
        }
    }
}
