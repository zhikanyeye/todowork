package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.TaskRepository
import com.example.ui.screens.PomodoroScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PomodoroViewModel
import com.example.ui.viewmodel.PomodoroViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: PomodoroViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize DB and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = TaskRepository(database.taskDao())
        
        // 2. Build ViewModel using custom factory
        val factory = PomodoroViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[PomodoroViewModel::class.java]
        
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                MainAppLayout(viewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        triggerStrictLocker()
    }

    private fun triggerStrictLocker() {
        if (::viewModel.isInitialized) {
            if (viewModel.strictModeEnabled.value && viewModel.isRunning.value && !viewModel.isBreak.value) {
                viewModel.playStrictWarningAlarm()
                val intent = android.content.Intent(this, MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                             android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                             android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                startActivity(intent)
            }
        }
    }
}

@Composable
fun MainAppLayout(viewModel: PomodoroViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val navigationItems = listOf(
        NavigationItem("专注", Icons.Default.Timer, Icons.Outlined.Timer, "pomodoro_tab"),
        NavigationItem("任务", Icons.Default.List, Icons.Outlined.List, "tasks_tab"),
        NavigationItem("系统", Icons.Default.Settings, Icons.Outlined.Settings, "settings_tab")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_nav_bar")
            ) {
                navigationItems.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(text = item.title) },
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        // Switching tabs smoothly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> PomodoroScreen(viewModel = viewModel)
                1 -> TaskListScreen(viewModel = viewModel)
                2 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

data class NavigationItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)
