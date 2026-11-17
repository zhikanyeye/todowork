package com.yangshizi.todo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 保活伴随服务 —— 与 [FocusTimerService] 配对的第二个前台服务。
 *
 * 思路对标竞品 番茄ToDo 的 CountService + InnerService 双服务互拉：
 * 严格专注期间同时存在两个前台服务，系统回收其中一个时，另一个的
 * onDestroy/自愈逻辑会把它重新拉起，降低计时/锁机进程被杀概率。
 *
 * 注意：targetSdk 36 下后台启动前台服务受限，本服务只在
 * [FocusTimerService] 已处于前台（严格守护激活）时由其启动，属于合规链路。
 */
class InnerService : Service() {

    companion object {
        private const val TAG = "InnerService"
        const val CHANNEL_ID = "focus_keepalive_channel"
        const val NOTIFICATION_ID = 1002

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, InnerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start InnerService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, InnerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop InnerService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        // 互拉：若严格守护仍在进行，被杀后请求自己重启（配合 START_STICKY 与配对服务）。
        if (shouldKeepAlive()) {
            try {
                start(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Restart on destroy failed", e)
            }
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉时，严格守护中则尝试重启自身
        if (shouldKeepAlive()) {
            start(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun shouldKeepAlive(): Boolean {
        val prefs = getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("strict_mode_enabled", false) &&
            prefs.getBoolean("strict_focus_active", false)
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("洋柿子ToDo")
            .setContentText("专注守护运行中")
            .setSmallIcon(com.yangshizi.todo.R.drawable.ic_focus_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setGroup("focus_keepalive_group")
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            try { startForeground(NOTIFICATION_ID, notification) } catch (e2: Exception) {}
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "专注守护",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "严格专注期间的进程保活通知"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
