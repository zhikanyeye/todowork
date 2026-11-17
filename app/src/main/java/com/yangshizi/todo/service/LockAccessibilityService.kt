package com.yangshizi.todo.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import com.yangshizi.todo.MainActivity

/**
 * 无障碍服务拦截 —— 第二层防护
 *
 * 利用 AccessibilityService 监听窗口变化事件，当用户尝试切换到
 * 非白名单应用时，执行 BACK 操作将其拉回。
 *
 * 特性：
 * 1. 系统包名白名单，避免误拦截 launcher/设置/电话等
 * 2. 品牌特定适配（MIUI/EMUI/OPPO/Vivo等）
 * 3. 300ms 冷却防止频繁触发
 * 4. 仅在严格专注模式激活时生效
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LockA11y"
        private const val COOLDOWN_MS = 300L

        // 系统白名单包名 —— 这些包不会被拦截
        private val SYSTEM_WHITELIST = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.camera2",
            "com.android.camera",
            "com.android.incallui",
            "com.android.mms",
            "com.android.messaging",
            "com.android.chrome",
            "com.android.launcher3",
            "com.android.launcher",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.home",
            "com.miui.securitycenter",
            "com.huawei.android.launcher",
            "com.sec.android.app.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.meizu.flyme.launcher",
            // Our own app
            "com.yangshizi.todo"
        )

        // 品牌特定 UI 文本关键词，出现在这些文本时执行 BACK（覆盖多 OEM 措辞）
        private val BLOCK_TEXTS = setOf(
            "清除", "删除", "结束", "强制停止", "强行停止",
            "卸载", "强行卸载", "应用信息", "应用程序信息",
            "结束运行", "停止运行", "全部清除", "清除全部", "关闭应用",
            "强制关闭", "移除", "停用", "禁用"
        )

        // 最近任务/近期任务页包名 —— 在这些页面尝试清理时拦截并拉回（对标竞品多 OEM 适配）
        private val RECENT_TASK_PACKAGES = setOf(
            "com.android.systemui",            // AOSP / 多数机型 recents
            "com.miui.home",                   // MIUI 最近任务
            "com.coloros.recents",             // OPPO/ColorOS
            "com.oppo.recents",
            "com.vivo.upslide",                // Vivo 上滑多任务
            "com.bbk.launcher2",               // Vivo launcher recents
            "com.huawei.android.launcher",     // EMUI
            "com.hihonor.android.launcher",    // 荣耀
            "com.sec.android.app.launcher",    // 三星
            "com.meizu.flyme.launcher"         // 魅族
        )

        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val serviceName = "${context.packageName}/${LockAccessibilityService::class.java.name}"
            return enabledServices.contains(serviceName)
        }
    }

    private lateinit var prefs: SharedPreferences
    private var lastActionTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
        configureService()
        Log.d(TAG, "LockAccessibilityService connected")
    }

    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            // 监听窗口状态变化、窗口内容变化（竞品大量依赖 2048 内容变化捕捉清理弹窗）
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 80
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isStrictFocusActive()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                // 最近任务页：扫描清理类节点，命中则拦截拉回
                if (pkg in RECENT_TASK_PACKAGES) {
                    interceptRecentTasksIfCleaning(pkg)
                    return
                }
                handlePackageSwitch(pkg, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 内容变化时，若停在最近任务/系统页且出现清理/卸载文本，拦截
                val pkg = event.packageName?.toString() ?: return
                if (pkg in RECENT_TASK_PACKAGES) {
                    interceptRecentTasksIfCleaning(pkg)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // 检测危险操作（如清除最近任务、卸载等）
                checkDangerousClick(event)
            }
        }
    }

    private val whitelistManager by lazy { WhitelistManager.getInstance(this) }

    private fun handlePackageSwitch(pkg: String, event: AccessibilityEvent) {
        // 本应用不拦截
        if (pkg == packageName) return

        // 系统必需白名单不拦截
        if (pkg in SYSTEM_WHITELIST) return

        // 用户白名单不拦截
        if (whitelistManager.isWhitelisted(pkg)) return

        // 启动器（桌面）特殊处理：不执行BACK，而是拉回应用
        if (whitelistManager.isLauncher(pkg)) {
            if (SystemClock.uptimeMillis() - lastActionTime < COOLDOWN_MS) return
            lastActionTime = SystemClock.uptimeMillis()
            Log.d(TAG, "On launcher (home), bringing app to front")
            bringAppToFront()
            return
        }

        // 冷却检查
        if (SystemClock.uptimeMillis() - lastActionTime < COOLDOWN_MS) return
        lastActionTime = SystemClock.uptimeMillis()

        Log.d(TAG, "Blocking switch to: $pkg")

        // 执行返回操作，将用户拉回
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 延迟再次执行，确保拉回成功
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isStrictFocusActive()) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, 200)

        // 尝试拉起主界面
        bringAppToFront()
    }

    private fun checkDangerousClick(event: AccessibilityEvent) {
        val source = event.source ?: return
        val text = event.text?.joinToString(" ") ?: return
        if (text.isBlank()) return

        // 检测危险操作文本
        for (blockText in BLOCK_TEXTS) {
            if (text.contains(blockText)) {
                if (SystemClock.uptimeMillis() - lastActionTime < COOLDOWN_MS) return
                lastActionTime = SystemClock.uptimeMillis()

                Log.d(TAG, "Blocking dangerous action: $text")
                performGlobalAction(GLOBAL_ACTION_BACK)

                // 显示警告 Toast
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        this,
                        "🔒 严格模式：禁止执行此操作",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                break
            }
        }
    }

    /**
     * 在最近任务/近期任务页扫描清理类节点（"清除全部"、"关闭全部" 等），
     * 命中即执行 BACK 并把本应用拉回前台，阻止用户清掉计时进程。
     * 对标竞品 MyAccessibilityService 对各 OEM recents 页的拦截。
     */
    private fun interceptRecentTasksIfCleaning(pkg: String) {
        if (SystemClock.uptimeMillis() - lastActionTime < COOLDOWN_MS) return

        val root = rootInActiveWindow ?: run {
            // 拿不到节点时，保守起见直接拉回（最近任务页本身就不应久留）
            lastActionTime = SystemClock.uptimeMillis()
            bringAppToFront()
            return
        }

        val cleaningTexts = listOf(
            "清除全部", "全部清除", "清除所有", "一键清理", "清理全部",
            "关闭全部", "关闭所有", "结束全部", "清空"
        )
        val hit = cleaningTexts.any { t ->
            try {
                root.findAccessibilityNodeInfosByText(t)?.isNotEmpty() == true
            } catch (e: Exception) {
                false
            }
        }
        if (hit) {
            lastActionTime = SystemClock.uptimeMillis()
            Log.d(TAG, "Recent-tasks cleaning detected on $pkg, blocking")
            performGlobalAction(GLOBAL_ACTION_BACK)
            bringAppToFront()
        }
    }

    private fun isStrictFocusActive(): Boolean {        return prefs.getBoolean("strict_mode_enabled", false) &&
            prefs.getBoolean("strict_focus_active", false)
    }

    private fun bringAppToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        kotlin.runCatching { startActivity(intent) }
    }

    override fun onInterrupt() {
        Log.d(TAG, "LockAccessibilityService interrupted")
    }
}
