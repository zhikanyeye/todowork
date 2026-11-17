package com.yangshizi.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.yangshizi.todo.data.database.AppDatabase
import com.yangshizi.todo.data.repository.TaskRepository
import com.yangshizi.todo.ui.screens.PomodoroScreen
import com.yangshizi.todo.ui.screens.SettingsScreen
import com.yangshizi.todo.ui.screens.StatisticsScreen
import com.yangshizi.todo.ui.screens.TaskListScreen
import com.yangshizi.todo.ui.theme.MyApplicationTheme
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModelFactory

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

        // Android 13+ 需运行时授权才能展示前台服务常驻通知
        requestNotificationPermissionIfNeeded()

        setContent {
            MyApplicationTheme {
                MainAppLayout(viewModel, returnBufferSeconds)
            }
        }
    }

    private var lockObserverJob: kotlinx.coroutines.Job? = null
    private var serviceObserverJob: kotlinx.coroutines.Job? = null
    private var immersiveUiObserverJob: kotlinx.coroutines.Job? = null
    private var keepScreenOnJob: kotlinx.coroutines.Job? = null
    private var isServiceRunning = false
    private var currentServiceStrictGuard = false
    private var immersiveActive = false

    // 严格模式警报：延迟触发（音量条等瞬时窗口不再误报）+ 回页立即静音
    private val warningHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingWarningRunnable: Runnable? = null

    // 回页缓冲倒计时（秒）：0 表示不显示
    private val returnBufferSeconds = mutableIntStateOf(0)

    private var focusAbandonedReceiverRegistered = false
    private val focusAbandonedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action != com.yangshizi.todo.service.FocusTimerService.ACTION_FOCUS_ABANDONED) return
            if (::viewModel.isInitialized) {
                viewModel.abandonStrictFocusFromGuard()
            }
            stopFocusService()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setImmersiveSystemUi(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun startFocusService(strictGuard: Boolean) {
        if (isServiceRunning && currentServiceStrictGuard == strictGuard) return
        com.yangshizi.todo.service.FocusTimerService.start(
            this,
            if (strictGuard) "严格锁机中，切出会被拉回" else "专注进行中，保持心流",
            strictGuard
        )
        isServiceRunning = true
        currentServiceStrictGuard = strictGuard
    }

    private fun stopFocusService() {
        if (!isServiceRunning) return
        com.yangshizi.todo.service.FocusTimerService.stop(this)
        isServiceRunning = false
        currentServiceStrictGuard = false
    }

    override fun onStart() {
        super.onStart()
        if (!::viewModel.isInitialized) return
        registerFocusAbandonedReceiver()

        // 计时进行时启动前台服务保活；结束/暂停时撤下。
        serviceObserverJob = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.isRunning,
                viewModel.isBreak,
                viewModel.strictModeEnabled
            ) { running, isBreak, strict -> Triple(running && !isBreak, strict && running && !isBreak, strict) }
                .collect { (workFocusActive, strictGuardActive, _) ->
                    if (workFocusActive) startFocusService(strictGuardActive) else stopFocusService()
                }
        }

        // 严格模式参考番茄 ToDo 的思路：前台服务保活 + 切出后拉回/遮挡，
        // 不再使用系统屏幕固定，避免出现系统 pinning 提示和体验割裂。
        lockObserverJob = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                viewModel.strictModeEnabled,
                viewModel.isRunning,
                viewModel.isBreak
            ) { strict, running, isBreak -> strict && running && !isBreak }
                .collect { shouldLock ->
                    if (shouldLock) {
                        getSharedPreferences("pomodoro_settings", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("strict_focus_active", true)
                            .apply()
                    }
                }
        }

        immersiveUiObserverJob = lifecycleScope.launch {
            viewModel.immersiveFocusEnabled.collect { enabled ->
                immersiveActive = enabled
                setImmersiveSystemUi(enabled)
            }
        }

        // Keep Screen On: 计时运行时保持屏幕常亮
        keepScreenOnJob = lifecycleScope.launch {
            viewModel.isRunning.collect { running ->
                if (running) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lockObserverJob?.cancel()
        serviceObserverJob?.cancel()
        immersiveUiObserverJob?.cancel()
        keepScreenOnJob?.cancel()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 窗口重新获得焦点时重新应用沉浸式模式（防止系统 UI 恢复后不消失）
        if (hasFocus && immersiveActive) {
            window.decorView.post {
                setImmersiveSystemUi(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台：取消待发的延迟警报，并立即静音任何残留警报
        pendingWarningRunnable?.let { warningHandler.removeCallbacks(it) }
        pendingWarningRunnable = null
        if (::viewModel.isInitialized) {
            viewModel.stopStrictWarningAlarm()
        }
        getSharedPreferences("pomodoro_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("activity_visible", true)
            .apply()
        maybeShowReturnBuffer()
    }

    /** 最近 15 秒内发生过违规拉回时，展示 3 秒「已回到专注」缓冲倒计时 */
    private fun maybeShowReturnBuffer() {
        val lastViolation = getSharedPreferences("pomodoro_settings", android.content.Context.MODE_PRIVATE)
            .getLong("last_violation_at", 0L)
        if (lastViolation <= 0L || System.currentTimeMillis() - lastViolation > 15_000L) return
        returnBufferSeconds.intValue = 3
        warningHandler.post(object : Runnable {
            override fun run() {
                if (returnBufferSeconds.intValue > 0) {
                    returnBufferSeconds.intValue--
                    warningHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("pomodoro_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean("activity_visible", false)
            .apply()
        if (::viewModel.isInitialized && viewModel.isStrictFocusActive()) {
            // 延迟 600ms 再确认：若期间回到前台（onResume 会取消本任务），
            // 音量条/系统弹窗等瞬时 onPause 不再误响警报
            val check = Runnable {
                if (::viewModel.isInitialized && viewModel.isStrictFocusActive()) {
                    viewModel.playStrictWarningAlarm()
                    val relaunch = android.content.Intent(this, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
                    }
                    kotlin.runCatching { startActivity(relaunch) }
                }
            }
            pendingWarningRunnable = check
            warningHandler.postDelayed(check, 600)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingWarningRunnable?.let { warningHandler.removeCallbacks(it) }
        pendingWarningRunnable = null
        unregisterFocusAbandonedReceiver()
        setImmersiveSystemUi(false)
        stopFocusService()
    }

    private fun registerFocusAbandonedReceiver() {
        if (focusAbandonedReceiverRegistered) return
        val filter = android.content.IntentFilter(
            com.yangshizi.todo.service.FocusTimerService.ACTION_FOCUS_ABANDONED
        )
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            focusAbandonedReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        focusAbandonedReceiverRegistered = true
    }

    private fun unregisterFocusAbandonedReceiver() {
        if (!focusAbandonedReceiverRegistered) return
        try {
            unregisterReceiver(focusAbandonedReceiver)
        } catch (e: Exception) {
            // Receiver may already have been removed by the platform during teardown.
        }
        focusAbandonedReceiverRegistered = false
    }
}


@Composable
fun MainAppLayout(viewModel: PomodoroViewModel, returnBufferSeconds: MutableState<Int> = mutableIntStateOf(0)) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val isRunning by viewModel.isRunning.collectAsState()
    val isBreak by viewModel.isBreak.collectAsState()
    val strictEnabled by viewModel.strictModeEnabled.collectAsState()
    val immersiveEnabled by viewModel.immersiveFocusEnabled.collectAsState()
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
        NavigationItem("统计", Icons.Default.BarChart, Icons.Outlined.BarChart, "stats_tab"),
        NavigationItem("任务", Icons.Default.List, Icons.Outlined.List, "tasks_tab"),
        NavigationItem("系统", Icons.Default.Settings, Icons.Outlined.Settings, "settings_tab")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!immersiveEnabled) {
                // Material3 NavigationBar 默认就处理底部系统栏 inset（导航栏本体延伸到屏幕底），
                // 不要再手动 navigationBarsPadding，否则双重 padding 会露出背景色、显得没贴底。
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
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
        val contentPadding = if (immersiveEnabled) PaddingValues(0.dp) else innerPadding
        // Switching tabs smoothly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (selectedTab) {
                0 -> PomodoroScreen(viewModel = viewModel)
                1 -> StatisticsScreen(viewModel = viewModel)
                2 -> TaskListScreen(viewModel = viewModel)
                3 -> SettingsScreen(viewModel = viewModel)
            }

            // 严格模式回页缓冲倒计时：非阻塞横幅，几秒后自动消失
            if (returnBufferSeconds.value > 0) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopCenter)
                        .padding(top = 48.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = "✅ 已回到专注（${returnBufferSeconds.value} 秒后解除提醒）",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
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
