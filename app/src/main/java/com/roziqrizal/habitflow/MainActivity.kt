package com.roziqrizal.habitflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.roziqrizal.habitflow.data.HabitDatabase
import com.roziqrizal.habitflow.data.HabitRepository
import com.roziqrizal.habitflow.ui.TodayScreen
import com.roziqrizal.habitflow.ui.TodayViewModel
import com.roziqrizal.habitflow.ui.theme.HabitFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HabitFlowTheme {
                val vm: TodayViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            TodayViewModel(HabitRepository(HabitDatabase.get(applicationContext)))
                        }
                    },
                )
                val state by vm.state.collectAsState()

                TodayScreen(
                    state = state,
                    onToggleHabit = vm::toggleHabit,
                    onAddTodo = vm::addTodo,
                    onToggleTodo = vm::toggleTodo,
                    onDeleteTodo = vm::deleteTodo,
                )
            }
        }
    }
}
