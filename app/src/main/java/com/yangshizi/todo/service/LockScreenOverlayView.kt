package com.yangshizi.todo.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.yangshizi.todo.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全屏锁机覆盖层 —— 第一层防护
 *
 * 通过 WindowManager 添加 TYPE_APPLICATION_OVERLAY 窗口，
 * 覆盖在整个屏幕上方，阻止用户操作其他应用。
 *
 * 功能：
 * - 显示剩余专注时间
 * - 显示当前时间
 * - 显示激励名言
 * - 退出按钮（带8秒倒计时防误触）
 * - 返回应用按钮
 *
 * 使用纯Kotlin实现，不依赖XML布局，更加轻量灵活。
 */
class LockScreenOverlayView private constructor(private val context: Context) {

    companion object {
        private var instance: LockScreenOverlayView? = null
        private const val MOTTOS = "保持专注,方能致远|心静则智生|今日不努力,明日努力找|自律给你自由|不积跬步无以至千里|少壮不努力老大徒伤悲|滴水穿石非一日之功"

        fun getInstance(context: Context): LockScreenOverlayView {
            if (instance == null) {
                instance = LockScreenOverlayView(context.applicationContext)
            }
            return instance!!
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var isShowing = false

    // UI elements
    private var timeDisplay: TextView? = null
    private var dateDisplay: TextView? = null
    private var countdownDisplay: TextView? = null
    private var mottoDisplay: TextView? = null
    private var returnButton: Button? = null
    private var exitButton: Button? = null
    private var cancelExitButton: TextView? = null
    private var whitelistButton: Button? = null

    // State
    private var remainingMs: Long = 0
    private var exitCountdownRunnable: Runnable? = null
    private var timeUpdateRunnable: Runnable? = null

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    fun show(remainingMillis: Long) {
        if (isShowing) {
            updateRemainingTime(remainingMillis)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        remainingMs = remainingMillis
        val view = buildView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(view, params)
            rootView = view
            isShowing = true
            startTimeUpdateLoop()
        } catch (e: Exception) {
            // Overlay permission may be denied
        }
    }

    fun updateRemainingTime(ms: Long) {
        remainingMs = ms
        handler.post {
            countdownDisplay?.text = formatTime(ms)
        }
    }

    fun dismiss() {
        stopTimeUpdateLoop()
        cancelExitCountdown()
        dismissWhitelistPopup()
        rootView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                // Already removed
            }
        }
        rootView = null
        isShowing = false
    }

    fun isShowing(): Boolean = isShowing

    private fun buildView(): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val root = FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"), Color.parseColor("#0F3460"))
            )
            isClickable = true
            isFocusable = true
        }

        val centerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40f), dp(60f), dp(40f), dp(60f))
        }

        // Current time display
        dateDisplay = TextView(context).apply {
            text = SimpleDateFormat("MM月dd日 EEEE", Locale.CHINESE).format(Date())
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 16f
            gravity = Gravity.CENTER
            alpha = 0.8f
        }
        centerContainer.addView(dateDisplay)

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(20f))
        })

        // Current clock
        timeDisplay = TextView(context).apply {
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            setTextColor(Color.WHITE)
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0.9f
        }
        centerContainer.addView(timeDisplay)

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40f))
        })

        // Remaining focus time
        val remainLabel = TextView(context).apply {
            text = "剩余专注时间"
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        centerContainer.addView(remainLabel)

        countdownDisplay = TextView(context).apply {
            text = formatTime(remainingMs)
            setTextColor(Color.parseColor("#E94560"))
            textSize = 48f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        centerContainer.addView(countdownDisplay)

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(50f))
        })

        // Motivational motto
        mottoDisplay = TextView(context).apply {
            text = getRandomMotto()
            setTextColor(Color.parseColor("#AABBCC"))
            textSize = 18f
            gravity = Gravity.CENTER
            setLineSpacing(dp(6f).toFloat(), 1f)
            setPadding(dp(20f), 0, dp(20f), 0)
        }
        centerContainer.addView(mottoDisplay)

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(60f))
        })

        // Return button
        returnButton = Button(context).apply {
            text = "返回专注"
            setTextColor(Color.WHITE)
            background = createButtonBackground(Color.parseColor("#E94560"))
            textSize = 16f
            setPadding(dp(60f), dp(14f), dp(60f), dp(14f))
            setOnClickListener {
                bringAppToFront()
            }
        }
        centerContainer.addView(returnButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(12f))
        })

        // Whitelist apps button
        whitelistButton = Button(context).apply {
            text = "白名单应用"
            setTextColor(Color.parseColor("#AABBCC"))
            background = createButtonBackground(Color.parseColor("#1A1A3E"))
            textSize = 13f
            setPadding(dp(36f), dp(10f), dp(36f), dp(10f))
            setOnClickListener {
                showWhitelistPopup()
            }
        }
        centerContainer.addView(whitelistButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // Spacer
        centerContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(12f))
        })

        // Exit button (with countdown)
        exitButton = Button(context).apply {
            text = "放弃专注"
            setTextColor(Color.parseColor("#8899AA"))
            background = createButtonBackground(Color.parseColor("#2A2A4E"))
            textSize = 14f
            setPadding(dp(40f), dp(10f), dp(40f), dp(10f))
            setOnClickListener {
                startExitCountdown()
            }
        }
        centerContainer.addView(exitButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        // 取消按钮：倒计时期间可撤销放弃操作（默认隐藏）
        cancelExitButton = TextView(context).apply {
            text = "取消"
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 13f
            setPadding(dp(30f), dp(8f), dp(30f), dp(8f))
            visibility = View.GONE
            setOnClickListener { cancelExitCountdown() }
        }
        centerContainer.addView(cancelExitButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        root.addView(centerContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        return root
    }

    private fun createButtonBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(color)
        }
    }

    private fun startTimeUpdateLoop() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                // Update clock
                timeDisplay?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                // 剩余时间与 ViewModel 同源：读墙钟锚点 focus_end_elapsed，按真实流逝计算，
                // 避免锁屏页独立累加导致与主页计时漂移不一致。
                val prefs = context.getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
                val endElapsed = prefs.getLong("focus_end_elapsed", 0L)
                if (endElapsed > 0L) {
                    remainingMs = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                } else {
                    // 未运行/无锚点时回退到 prefs 的剩余值快照
                    remainingMs = prefs.getLong("remaining_time_ms", remainingMs).coerceAtLeast(0L)
                }
                countdownDisplay?.text = formatTime(remainingMs)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timeUpdateRunnable!!)
    }

    private fun stopTimeUpdateLoop() {
        timeUpdateRunnable?.let { handler.removeCallbacks(it) }
        timeUpdateRunnable = null
    }

    private fun startExitCountdown() {
        val exitBtn = exitButton ?: return
        exitBtn.isEnabled = false
        cancelExitButton?.visibility = View.VISIBLE
        var countdown = 8

        exitCountdownRunnable = object : Runnable {
            override fun run() {
                if (countdown > 0) {
                    exitBtn.text = "确认放弃 (${countdown}s)"
                    countdown--
                    handler.postDelayed(this, 1000)
                } else {
                    exitBtn.text = "放弃专注"
                    exitBtn.isEnabled = true
                    cancelExitButton?.visibility = View.GONE
                    // 发送内部广播通知前台服务处理退出（接收方已限定 NOT_EXPORTED + setPackage）。
                    val intent = Intent(FocusTimerService.ACTION_FORCE_EXIT).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                }
            }
        }
        handler.post(exitCountdownRunnable!!)
    }

    private fun cancelExitCountdown() {
        exitCountdownRunnable?.let { handler.removeCallbacks(it) }
        exitCountdownRunnable = null
        exitButton?.apply {
            text = "放弃专注"
            isEnabled = true
        }
        cancelExitButton?.visibility = View.GONE
    }

    private fun bringAppToFront() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        kotlin.runCatching { context.startActivity(intent) }
    }

    /**
     * 显示白名单应用弹窗
     * 学习竞品 WhiteListGridDialog 的做法，在锁机界面提供快捷入口。
     * 注意：不能用 PopupWindow（overlay token 不支持子窗口，会 BadTokenException），
     * 改为直接挂第二个 TYPE_APPLICATION_OVERLAY 窗口。
     */
    private fun showWhitelistPopup() {
        if (whitelistPopupView != null) return
        val whitelistManager = WhitelistManager.getInstance(context)
        val apps = whitelistManager.getUserWhitelistApps()
        if (apps.isEmpty()) {
            android.widget.Toast.makeText(context, "没有白名单应用，请在设置中添加", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return
        }

        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        // 全屏半透明遮罩：点击空白处关闭
        val overlayRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#88000000"))
            setOnClickListener { dismissWhitelistPopup() }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"))
            ).apply { cornerRadius = 20f }
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        }

        val title = TextView(context).apply {
            text = "可使用的应用"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(8f), 0, dp(12f))
        }
        container.addView(title)

        val scroll = ScrollView(context)
        // Launcher 风格网格：3 列图标 + 名称，触区更大更直观
        val grid = GridLayout(context).apply {
            columnCount = 3
            useDefaultMargins = true
        }
        val pm = context.packageManager
        for (app in apps) {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
                isClickable = true
                isFocusable = true
                background = GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(Color.parseColor("#2A2A4E"))
                }
                setOnClickListener {
                    launchApp(app.packageName)
                    dismissWhitelistPopup()
                }
            }
            val iconView = ImageView(context).apply {
                val icon = kotlin.runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                if (icon != null) setImageDrawable(icon) else setImageResource(android.R.drawable.ic_menu_manage)
                layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
            }
            cell.addView(iconView)
            val nameView = TextView(context).apply {
                text = app.appName
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, 0)
            }
            cell.addView(nameView)
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        scroll.addView(grid)
        container.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            val maxListHeight = (context.resources.displayMetrics.heightPixels * 0.5).toInt()
            height = maxListHeight.coerceAtMost(dp(400f))
        })

        val closeButton = TextView(context).apply {
            text = "关闭"
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, 0)
            setOnClickListener { dismissWhitelistPopup() }
        }
        container.addView(closeButton)

        overlayRoot.addView(container, FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.82).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager.addView(overlayRoot, params)
            whitelistPopupView = overlayRoot
        } catch (e: Exception) {
            Log.e("LockOverlay", "Failed to show whitelist popup", e)
        }
    }

    private var whitelistPopupView: View? = null

    /** 关闭白名单弹窗（移除 overlay 窗口） */
    private fun dismissWhitelistPopup() {
        whitelistPopupView?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {
                // Already removed
            }
        }
        whitelistPopupView = null
    }

    /**
     * 启动白名单应用
     * 学习竞品 JumpToWhiteAppActivity 的做法：用 PendingIntent 启动目标应用
     */
    private fun launchApp(packageName: String) {
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            Log.e("LockOverlay", "Failed to launch $packageName", e)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun getRandomMotto(): String {
        val mottos = MOTTOS.split("|")
        return mottos.random()
    }
}
