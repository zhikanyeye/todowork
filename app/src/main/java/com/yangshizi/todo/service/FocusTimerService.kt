package com.yangshizi.todo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yangshizi.todo.MainActivity
import com.yangshizi.todo.R

/**
 * 增强型专注计时前台服务 —— 第三层防护
 *
 * 在原有保活基础上增加：
 * 1. 屏幕状态监听（SCREEN_ON/OFF）—— 锁屏后重新拉起
 * 2. 自愈机制 —— 服务被杀后自动重启（START_STICKY + 定时检查）
 * 3. 与无障碍服务联动 —— 接收广播后显示覆盖层
 * 4. 退出次数限制 —— 每日最多退出3次，超出后强制锁定
 * 5. 增强的覆盖层 —— 使用 LockScreenOverlayView 替代简单 FrameLayout
 *
 * 前台保活服务增强，配合锁机覆盖层和无障碍服务使用
 */
class FocusTimerService : Service() {

    companion object {
        private const val TAG = "FocusTimerService"

        const val CHANNEL_ID = "focus_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.yangshizi.todo.action.START_FOCUS"
        const val ACTION_STOP = "com.yangshizi.todo.action.STOP_FOCUS"
        const val ACTION_FORCE_EXIT = "com.yangshizi.todo.action.FORCE_EXIT"
        const val ACTION_SHOW_OVERLAY = "com.yangshizi.todo.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.yangshizi.todo.action.HIDE_OVERLAY"
        const val ACTION_ACCESSIBILITY_READY = "com.yangshizi.todo.action.A11Y_READY"
        const val ACTION_UPDATE_REMAINING = "com.yangshizi.todo.action.UPDATE_REMAINING"
        const val ACTION_FOCUS_ABANDONED = "com.yangshizi.todo.action.FOCUS_ABANDONED"

        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_STRICT_GUARD = "extra_strict_guard"
        const val EXTRA_REMAINING_MS = "extra_remaining_ms"

        // 退出限制配置
        const val MAX_DAILY_EXITS = 3
        private const val PREF_EXIT_COUNT_PREFIX = "exit_count_"
        private const val PREF_EXIT_DATE_PREFIX = "exit_date_"

        fun start(context: Context, content: String, strictGuard: Boolean = false) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_STRICT_GUARD, strictGuard)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+ 后台时 startService 会抛 IllegalStateException
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    try { context.startService(intent) } catch (e2: Exception) {}
                }
            } else {
                context.startService(intent)
            }
        }

        fun updateRemainingTime(context: Context, remainingMs: Long) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_UPDATE_REMAINING
                putExtra(EXTRA_REMAINING_MS, remainingMs)
            }
            context.startService(intent)
        }

        /**
         * 获取今日剩余退出次数
         */
        fun getRemainingExits(context: Context): Int {
            val prefs = context.getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
            val today = todayStr()
            val savedDate = prefs.getString(PREF_EXIT_DATE_PREFIX, "") ?: ""
            if (today != savedDate) {
                // 新的一天，重置
                return MAX_DAILY_EXITS
            }
            val count = prefs.getInt(PREF_EXIT_COUNT_PREFIX, 0)
            return (MAX_DAILY_EXITS - count).coerceAtLeast(0)
        }

        /**
         * 记录一次退出
         */
        fun recordExit(context: Context) {
            val prefs = context.getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
            val today = todayStr()
            val savedDate = prefs.getString(PREF_EXIT_DATE_PREFIX, "") ?: ""
            if (today != savedDate) {
                prefs.edit()
                    .putString(PREF_EXIT_DATE_PREFIX, today)
                    .putInt(PREF_EXIT_COUNT_PREFIX, 1)
                    .apply()
            } else {
                val count = prefs.getInt(PREF_EXIT_COUNT_PREFIX, 0)
                prefs.edit().putInt(PREF_EXIT_COUNT_PREFIX, count + 1).apply()
            }
        }

        private fun todayStr(): String {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            return sdf.format(java.util.Date())
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE) }
    private var lastWarningAt = 0L
    private var isStrictGuardActive = false

    // 违规判定：连续 N 次轮询都检测到非白名单前台才判真（约1.2s），
    // 避免音量条/系统弹窗等瞬时窗口导致 UsageStats 查询失败而误报。
    private var violationStreak = 0
    private var warnCountInEpisode = 0
    private var consecutiveNullTopPolls = 0
    private val violationThreshold = 3
    private val maxWarnsPerEpisode = 2

    // WakeLock: PARTIAL_WAKE_LOCK 保持 CPU 运行，防止息屏后计时停止
    private var wakeLock: PowerManager.WakeLock? = null

    // Overlay view (enhanced)
    private val lockOverlay by lazy { LockScreenOverlayView.getInstance(this) }

    // Screen state receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Screen turned off during strict mode
                    if (isStrictGuardActive) {
                        Log.d(TAG, "Screen off during strict mode")
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // Screen unlocked - re-show overlay if strict mode active
                    if (isStrictGuardActive) {
                        handler.postDelayed({
                            if (isStrictGuardActive && !isOurAppForeground()) {
                                showLockOverlay()
                                bringMainToFront()
                            }
                        }, 300)
                    }
                }
            }
        }
    }

    // Overlay broadcast receiver (from accessibility service and lock overlay)
    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SHOW_OVERLAY -> {
                    if (isStrictGuardActive) {
                        showLockOverlay()
                    }
                }
                ACTION_FORCE_EXIT -> {
                    handleForceExit()
                }
                ACTION_ACCESSIBILITY_READY -> {
                    Log.d(TAG, "Accessibility service is ready")
                }
            }
        }
    }

    private val whitelistManager by lazy { WhitelistManager.getInstance(this) }

    private val strictGuardRunnable = object : Runnable {
        override fun run() {
            if (!shouldStrictGuard()) {
                hideLockOverlay()
                resetViolationState()
                return
            }

            val topPackage = getTopPackageFromUsageStats()

            // Our app is in foreground — hide overlay, all good
            if (topPackage == packageName) {
                if (lockOverlay.isShowing()) {
                    hideLockOverlay()
                }
                resetViolationState()
                handler.postDelayed(this, 400)
                return
            }

            // Check if the foreground app is whitelisted
            if (topPackage != null && whitelistManager.isWhitelisted(topPackage)) {
                // Whitelisted app is running — hide overlay, let user use it
                if (lockOverlay.isShowing()) {
                    hideLockOverlay()
                }
                resetViolationState()
                handler.postDelayed(this, 400)
                return
            }

            // Check if it's the launcher (home screen)
            if (topPackage != null && whitelistManager.isLauncher(topPackage)) {
                // On home screen — show overlay and bring app to front
                showLockOverlay()
                bringMainToFront()
                resetViolationState()
                handler.postDelayed(this, 400)
                return
            }

            // 查不到前台包（音量条/系统弹窗/权限丢失等）：视为未知状态。
            // 只把主界面拉回，不弹浮层、不响警报，避免误触。
            if (topPackage == null) {
                consecutiveNullTopPolls++
                if (consecutiveNullTopPolls >= violationThreshold) {
                    bringMainToFront()
                }
                handler.postDelayed(this, 400)
                return
            }
            consecutiveNullTopPolls = 0

            // Non-whitelisted app is in foreground — require consecutive confirmations
            violationStreak++
            if (violationStreak < violationThreshold) {
                handler.postDelayed(this, 400)
                return
            }

            // Confirmed violation — block it
            showLockOverlay()
            warnIfNeeded()
            bringMainToFront()
            handler.postDelayed(this, 400)
        }
    }

    /** 回到本应用/白名单/桌面后清零违规计数，同时确保警报静音 */
    private fun resetViolationState() {
        violationStreak = 0
        warnCountInEpisode = 0
        consecutiveNullTopPolls = 0
    }

    // Self-healing check runnable
    private val selfHealRunnable = object : Runnable {
        override fun run() {
            if (isStrictGuardActive) {
                // Ensure foreground service is still running
                ensureForeground()
                // Check if accessibility service is still enabled
                checkAccessibilityService()
                // 互拉：配对保活服务若被杀，重新拉起
                if (!InnerService.isRunning) {
                    InnerService.start(this@FocusTimerService)
                }
            }
            handler.postDelayed(this, 5000) // Every 5 seconds
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 所有路径都必须先 startForeground，否则 Android 8+ startForegroundService 会 crash
        val content = when (intent?.action) {
            ACTION_STOP -> "专注结束"
            ACTION_FORCE_EXIT -> "处理退出请求"
            else -> intent?.getStringExtra(EXTRA_CONTENT) ?: "专注进行中"
        }
        startForegroundCompat(NOTIFICATION_ID, buildNotification(content))

        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopStrictGuardLoop()
                InnerService.stop(this)
                stopForegroundCompat()
                stopSelf()
            }
            ACTION_FORCE_EXIT -> {
                handleForceExit()
            }
            ACTION_SHOW_OVERLAY -> {
                if (isStrictGuardActive) showLockOverlay()
            }
            ACTION_UPDATE_REMAINING -> {
                val remainingMs = intent.getLongExtra(EXTRA_REMAINING_MS, 0)
                if (lockOverlay.isShowing()) {
                    lockOverlay.updateRemainingTime(remainingMs)
                }
            }
            else -> {
                acquireWakeLock()
                val strictGuard = intent?.getBooleanExtra(EXTRA_STRICT_GUARD, false) == true || shouldStrictGuard()
                if (strictGuard) {
                    isStrictGuardActive = true
                    startStrictGuardLoop()
                    startSelfHeal()
                    // 启动配对保活服务（双前台服务互拉）
                    InnerService.start(this)
                } else {
                    stopStrictGuardLoop()
                    InnerService.stop(this)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopStrictGuardLoop()
        handler.removeCallbacks(selfHealRunnable)
        unregisterReceivers()
        hideLockOverlay()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // PARTIAL_WAKE_LOCK: 仅保持 CPU 运行，不亮屏，最省电
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yangshizi:focus_timer_wakelock")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock acquired (PARTIAL_WAKE_LOCK)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
        wakeLock = null
    }

    // ---- Notification ----

    private fun buildNotification(content: String): Notification {
        createChannel()
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPi = PendingIntent.getActivity(this, 0, tapIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("洋柿子ToDo")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "专注计时",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "番茄钟专注进行时的常驻通知"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * 兼容 Android 14+ 的 startForeground
     * Android 14 (API 34) 强制要求传入 foregroundServiceType
     */
    private fun startForegroundCompat(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(id, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            // 即使 startForeground 失败也要尝试基础调用
            try { startForeground(id, notification) } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback also failed", e2)
            }
        }
    }

    // ---- Strict Guard Loop ----

    private fun startStrictGuardLoop() {
        handler.removeCallbacks(strictGuardRunnable)
        handler.post(strictGuardRunnable)
    }

    private fun stopStrictGuardLoop() {
        handler.removeCallbacks(strictGuardRunnable)
        isStrictGuardActive = false
        hideLockOverlay()
        InnerService.stop(this)
    }

    private fun shouldStrictGuard(): Boolean {
        return prefs.getBoolean("strict_mode_enabled", false) &&
            prefs.getBoolean("strict_focus_active", false)
    }

    // ---- App Foreground Detection ----

    private fun isOurAppForeground(): Boolean {
        val topPackage = getTopPackageFromUsageStats()
        if (topPackage != null) {
            return topPackage == packageName
        }
        return prefs.getBoolean("activity_visible", false)
    }

    /**
     * 多方法获取前台应用包名
     * 方法1: UsageEvents.queryEvents（最准确，需要 PACKAGE_USAGE_STATS 权限）
     * 方法2: UsageStatsManager.queryUsageStats（按最后使用时间排序）
     * 方法3: ActivityManager.getRunningTasks（Android 5+ 仅返回自己的应用，作为兜底）
     */
    private fun getTopPackageFromUsageStats(): String? {
        // 方法1: UsageEvents
        val fromEvents = getTopPackageFromUsageEvents()
        if (fromEvents != null) return fromEvents

        // 方法2: UsageStats
        val fromStats = getTopPackageFromUsageStats2()
        if (fromStats != null) return fromStats

        // 方法3: ActivityManager fallback
        return getTopPackageFromActivityManager()
    }

    private fun getTopPackageFromUsageEvents(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(end - 4000, end)
            val event = UsageEvents.Event()
            var topPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        topPackage = event.packageName
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        topPackage = event.packageName
                    }
                }
            }
            topPackage
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getTopPackageFromUsageStats2(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(0, end - 20000, end) ?: return null
            var topPackage: String? = null
            var lastTimeUsed: Long = 0
            for (stat in stats) {
                if (stat.lastTimeUsed > lastTimeUsed) {
                    topPackage = stat.packageName
                    lastTimeUsed = stat.lastTimeUsed
                }
            }
            topPackage
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getTopPackageFromActivityManager(): String? {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                tasks[0].topActivity?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Bring App to Front ----

    private fun bringMainToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        kotlin.runCatching { startActivity(intent) }
    }

    // ---- Lock Overlay ----

    private fun showLockOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !Settings.canDrawOverlays(this)) return
        val remainingMs = prefs.getLong("remaining_time_ms", 25 * 60 * 1000L)
        lockOverlay.show(remainingMs)
        // 记录最近一次违规时刻，供 MainActivity 回页后展示缓冲倒计时
        prefs.edit().putLong("last_violation_at", System.currentTimeMillis()).apply()
    }

    private fun hideLockOverlay() {
        lockOverlay.dismiss()
    }

    // ---- Warning ----

    private fun warnIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastWarningAt < 3500) return
        // 每个违规回合最多响 maxWarnsPerEpisode 次，之后保持静默浮层，防止警报长响不停
        if (warnCountInEpisode >= maxWarnsPerEpisode) return
        lastWarningAt = now
        warnCountInEpisode++
        kotlin.runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, 90).apply {
                startTone(ToneGenerator.TONE_CDMA_HIGH_L, 600)
                handler.postDelayed({
                    runCatching { stopTone() }
                    release()
                }, 700)
            }
        }
        kotlin.runCatching {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(650, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(650)
            }
        }
    }

    // ---- Self-Healing ----

    private fun startSelfHeal() {
        handler.removeCallbacks(selfHealRunnable)
        handler.postDelayed(selfHealRunnable, 5000)
    }

    private fun ensureForeground() {
        // Wake lock to prevent deep sleep during strict mode
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                // Screen is on, ensure we're in foreground
                if (!isOurAppForeground()) {
                    bringMainToFront()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureForeground failed", e)
        }
    }

    private fun checkAccessibilityService() {
        val enabled = LockAccessibilityService.isAccessibilityEnabled(this)
        if (!enabled && isStrictGuardActive) {
            // Accessibility service not enabled, rely on UsageStats fallback
            Log.w(TAG, "Accessibility service not enabled, using fallback polling")
        }
    }

    // ---- Force Exit ----

    private fun handleForceExit() {
        val remaining = getRemainingExits(this)
        if (remaining > 0) {
            recordExit(this)
            prefs.edit().putBoolean("strict_focus_active", false).apply()
            sendBroadcast(Intent(ACTION_FOCUS_ABANDONED).apply { setPackage(packageName) })
            stopStrictGuardLoop()
            stopForegroundCompat()
            stopSelf()
        } else {
            // No more exits allowed today
            handler.post {
                android.widget.Toast.makeText(
                    this,
                    "今日退出次数已用完，请完成当前专注！",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            // Re-show overlay
            showLockOverlay()
        }
    }

    // ---- Receivers ----

    private fun registerReceivers() {
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // 系统广播（SCREEN_ON 等）必须可接收系统来源，用 RECEIVER_EXPORTED。
        ContextCompat.registerReceiver(
            this, screenReceiver, screenFilter, ContextCompat.RECEIVER_EXPORTED
        )

        val overlayFilter = IntentFilter().apply {
            addAction(ACTION_SHOW_OVERLAY)
            addAction(ACTION_ACCESSIBILITY_READY)
            addAction(ACTION_FORCE_EXIT)
        }
        // 内部控制广播（FORCE_EXIT 等）必须 NOT_EXPORTED，否则第三方 App 可发广播
        // 终止严格锁机。ContextCompat 在所有 API 级别（含 <33）都正确应用该标志。
        ContextCompat.registerReceiver(
            this, overlayReceiver, overlayFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterReceivers() {
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(overlayReceiver) } catch (e: Exception) {}
    }
}
