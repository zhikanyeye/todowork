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

    private var isActivityVisible = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastLockerTriggerTimeMs = 0L
    private var lastActivityStartTimeMs = 0L

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private val strictLockRunnable = object : Runnable {
        override fun run() {
            if (::viewModel.isInitialized) {
                if (viewModel.strictModeEnabled.value && viewModel.isRunning.value && !viewModel.isBreak.value && !isActivityVisible) {
                    // Only continue the background loop if overlay permission is granted
                    // This prevents app loop spam if permission is not available or if they are in settings
                    if (viewModel.isOverlayPermissionGranted()) {
                        triggerStrictLocker()
                        handler.postDelayed(this, 4000)
                    } else {
                        android.util.Log.w("MainActivity", "Overlay permission not granted; pausing strict background loop.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        handler.removeCallbacks(strictLockRunnable)
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(strictLockRunnable)
        if (isFinishing && ::viewModel.isInitialized) {
            viewModel.releaseFocusResources()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Asynchronously schedule the check instead of immediately launching activity during gesture transition.
        // This completely eliminates window manager race conditions and InputDispatcher broken channels.
        scheduleStrictLockLoop()
    }

    private fun scheduleStrictLockLoop() {
        if (::viewModel.isInitialized) {
            if (viewModel.strictModeEnabled.value && viewModel.isRunning.value && !viewModel.isBreak.value) {
                // Only schedule the continuous lock loop if the permission is actually granted
                if (viewModel.isOverlayPermissionGranted()) {
                    handler.removeCallbacks(strictLockRunnable)
                    handler.postDelayed(strictLockRunnable, 2000)
                }
            }
        }
    }

    private fun triggerStrictLocker() {
        if (::viewModel.isInitialized) {
            try {
                if (viewModel.strictModeEnabled.value && viewModel.isRunning.value && !viewModel.isBreak.value) {
                    val currentTime = System.currentTimeMillis()
                    // 7-second cooldown for Toast and alarm feedback to prevent thread/message queue flooding
                    val shouldAlert = (currentTime - lastLockerTriggerTimeMs) > 7000L

                    if (shouldAlert) {
                        lastLockerTriggerTimeMs = currentTime
                        viewModel.playStrictWarningAlarm()
                        android.widget.Toast.makeText(
                            this, 
                            "🔒 专注严格模式已开启！请专注于当前任务！", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    if (viewModel.isOverlayPermissionGranted()) {
                        val currentMs = System.currentTimeMillis()
                        if (currentMs - lastActivityStartTimeMs > 3000L) {
                            lastActivityStartTimeMs = currentMs
                            val intent = android.content.Intent(this, MainActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or 
                                         android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                                         android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            startActivity(intent)
                        } else {
                            android.util.Log.d("MainActivity", "Throttled background activity launch to avoid WindowManager/InputDispatcher stress.")
                        }
                    } else {
                        android.util.Log.w("MainActivity", "Overlay permission not granted; skipped background activity re-entry to avoid crash/OS termination.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Strict locker trigger failed due to permission or background start restriction", e)
            }
        }
    }
}

@Composable
fun MainAppLayout(viewModel: PomodoroViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val isRunning by viewModel.isRunning.collectAsState()
    val isBreak by viewModel.isBreak.collectAsState()
    val strictEnabled by viewModel.strictModeEnabled.collectAsState()
    val immersiveFocusEnabled by viewModel.immersiveFocusEnabled.collectAsState()
    val isStrictActive = strictEnabled && isRunning && !isBreak

    // Intercept back button during active strict mode focus
    if (isStrictActive) {
        androidx.activity.compose.BackHandler(enabled = true) {
            android.widget.Toast.makeText(
                context,
                "🔒 专注严格模式生效中，禁止退出！请保持心流！",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    val navigationItems = listOf(
        NavigationItem("专注", Icons.Default.Timer, Icons.Outlined.Timer, "pomodoro_tab"),
        NavigationItem("任务", Icons.Default.List, Icons.Outlined.List, "tasks_tab"),
        NavigationItem("系统", Icons.Default.Settings, Icons.Outlined.Settings, "settings_tab")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!immersiveFocusEnabled) {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar")
                ) {
                    navigationItems.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (isStrictActive && index != 0) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "🔒 严格专注模式运行中，禁止离开专注屏！",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    selectedTab = index
                                }
                            },
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
