package com.yangshizi.todo.service

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 通知拦截服务 —— 严格模式增强
 *
 * 利用 NotificationListenerService 在严格专注模式期间
 * 拦截其他应用的通知，避免分心。
 *
 * 功能：
 * - 专注模式激活时自动取消其他应用的通知
 * - 保留自身App的通知
 * - 可配置白名单（允许特定App通知通过）
 *
 * 使用方式：
 * 1. 用户需在系统设置中授权通知访问权限
 * 2. 在设置页开启"通知拦截"开关
 */
class NotificationBlockService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifBlock"
        private const val PREFS = "pomodoro_settings"

        /**
         * 检查通知监听服务是否已授权
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val target = "${context.packageName}/${NotificationBlockService::class.java.name}"
            return flat.split(":").any { it == target }
        }

        /**
         * 跳转到通知访问设置页
         */
        fun openNotificationListenerSettings(context: Context) {
            val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private lateinit var prefs: SharedPreferences

    override fun onListenerConnected() {
        super.onListenerConnected()
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Log.d(TAG, "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!shouldBlockNotifications()) return

        // Don't block our own notifications
        if (sbn.packageName == packageName) return

        // Check user whitelist
        val whitelist = prefs.getStringSet("notification_whitelist", emptySet()) ?: emptySet()
        if (sbn.packageName in whitelist) return

        // Cancel the notification
        try {
            cancelNotification(sbn.key)
            Log.d(TAG, "Blocked notification from: ${sbn.packageName}")
        } catch (e: Exception) {
            // Fallback for older API
            try {
                @Suppress("DEPRECATION")
                cancelNotification(sbn.packageName, sbn.tag, sbn.id)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to cancel notification", e2)
            }
        }
    }

    private fun shouldBlockNotifications(): Boolean {
        return prefs.getBoolean("notification_block_enabled", false) &&
            prefs.getBoolean("strict_focus_active", false)
    }
}
