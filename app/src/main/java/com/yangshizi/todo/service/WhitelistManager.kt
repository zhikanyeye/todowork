package com.yangshizi.todo.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 白名单管理器
 *
 * 管理用户在严格专注模式下允许使用的应用列表。
 * 白名单数据持久化在 SharedPreferences 中，以 JSON 格式存储。
 *
 * 学习要点：
 * - 竞品通过 l0.getInstance().addToWhiteList/deleteFromWhiteList/isAppPkgInWhiteList 管理
 * - 竞品预置了系统应用白名单（电话、短信、设置、启动器等）
 * - 用户可自由添加/删除白名单应用
 * - 锁机时仅允许白名单应用，非白名单应用被拦截
 */
class WhitelistManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WhitelistManager"
        private const val PREF_NAME = "pomodoro_settings"
        private const val KEY_WHITELIST = "app_whitelist_json"
        private const val KEY_WHITELIST_VERSION = "whitelist_version"
        private const val CURRENT_VERSION = 2

        // 系统必需白名单 —— 这些包始终放行，不可删除
        private val SYSTEM_REQUIRED = setOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.incallui",
            "com.android.mms",
            "com.android.messaging",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.camera2",
            "com.android.camera",
            "com.yangshizi.todo"
        )

        // 常见启动器包名
        private val LAUNCHER_KEYWORDS = listOf("launcher", "home")

        @Volatile
        private var instance: WhitelistManager? = null

        fun getInstance(context: Context): WhitelistManager {
            return instance ?: synchronized(this) {
                instance ?: WhitelistManager(context.applicationContext).also {
                    instance = it
                    it.initDefaultWhitelist()
                }
            }
        }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val icon: Drawable? = null
    )

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 获取白名单中的所有包名（包括系统必需）
     */
    fun getWhitelistPackages(): Set<String> {
        val userWhitelist = getUserWhitelistPackages()
        return SYSTEM_REQUIRED + userWhitelist
    }

    /**
     * 获取用户手动添加的白名单包名
     */
    fun getUserWhitelistPackages(): Set<String> {
        val json = prefs.getString(KEY_WHITELIST, null) ?: return emptySet()
        return try {
            val array = JSONArray(json)
            val result = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(obj.getString("packageName"))
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse whitelist", e)
            emptySet()
        }
    }

    /**
     * 获取用户白名单的完整信息（包名+应用名）
     */
    fun getUserWhitelistApps(): List<AppInfo> {
        val json = prefs.getString(KEY_WHITELIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<AppInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(AppInfo(
                    packageName = obj.getString("packageName"),
                    appName = obj.optString("appName", obj.getString("packageName")),
                    isSystemApp = false
                ))
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse whitelist apps", e)
            emptyList()
        }
    }

    /**
     * 检查包名是否在白名单中
     */
    fun isWhitelisted(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        if (packageName in SYSTEM_REQUIRED) return true
        return packageName in getUserWhitelistPackages()
    }

    /**
     * 添加应用到白名单
     */
    fun addToWhitelist(appName: String, packageName: String) {
        val current = getUserWhitelistApps().toMutableList()
        // 去重
        if (current.any { it.packageName == packageName }) return
        current.add(AppInfo(packageName, appName, false))
        saveWhitelist(current)
    }

    /**
     * 从白名单移除应用
     */
    fun removeFromWhitelist(packageName: String) {
        val current = getUserWhitelistApps().toMutableList()
        current.removeAll { it.packageName == packageName }
        saveWhitelist(current)
    }

    /**
     * 判断包名是否是启动器（桌面）
     */
    fun isLauncher(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        // 精确匹配常见启动器
        val knownLaunchers = setOf(
            "com.android.launcher3",
            "com.android.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.sec.android.app.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.meizu.flyme.launcher",
            "com.google.android.apps.nexuslauncher"
        )
        if (packageName in knownLaunchers) return true
        // 关键词模糊匹配
        return LAUNCHER_KEYWORDS.any { packageName.contains(it, ignoreCase = true) }
    }

    /**
     * 获取已安装应用列表（异步）
     */
    fun loadInstalledApps(callback: (List<AppInfo>) -> Unit) {
        executor.execute {
            val apps = getInstalledAppsSync()
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(apps) }
        }
    }

    /**
     * 同步获取已安装应用列表
     *
     * 采用双重获取策略确保不遗漏：     * 1. queryIntentActivities(flags=0) 获取所有有启动图标的应用（不限制 DEFAULT 分类）
     * 2. getInstalledApplications 兜底获取无图标但已安装的包
     * 合并去重后返回完整列表。
     */
    fun getInstalledAppsSync(): List<AppInfo> {
        val pm = context.packageManager
        val seenPackages = mutableSetOf<String>()
        val result = mutableListOf<AppInfo>()

        // 第一轮：queryIntentActivities(flags=0) 获取所有有启动图标的应用
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        for (resolveInfo in resolveInfos) {
            try {
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) continue
                if (packageName in seenPackages) continue
                seenPackages.add(packageName)
                val appName = resolveInfo.loadLabel(pm).toString()
                val isSystem = try {
                    val ai = pm.getApplicationInfo(packageName, 0)
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) { false }
                val icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }
                result.add(AppInfo(packageName, appName, isSystem, icon))
            } catch (e: Exception) {
                // skip
            }
        }

        // 第二轮：getInstalledApplications 兜底获取无启动图标的应用
        val allApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        for (appInfo in allApps) {
            val packageName = appInfo.packageName
            if (packageName == context.packageName) continue
            if (packageName in seenPackages) continue
            seenPackages.add(packageName)
            try {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                result.add(AppInfo(packageName, appName, isSystem, icon))
            } catch (e: Exception) {
                // skip
            }
        }

        result.sortBy { it.appName.lowercase() }
        return result
    }

    /**
     * 初始化默认白名单（首次运行时）
     */
    private fun initDefaultWhitelist() {
        val version = prefs.getInt(KEY_WHITELIST_VERSION, 0)
        if (version < CURRENT_VERSION) {
            // 首次初始化：预置一些常用白名单
            val defaults = listOf(
                AppInfo("com.android.settings", "设置", true),
                AppInfo("com.android.chrome", "Chrome浏览器", true)
            )
            saveWhitelist(defaults)
            prefs.edit().putInt(KEY_WHITELIST_VERSION, CURRENT_VERSION).apply()
        }
    }

    /**
     * 保存白名单到 SharedPreferences
     */
    private fun saveWhitelist(apps: List<AppInfo>) {
        val array = JSONArray()
        for (app in apps) {
            val obj = JSONObject()
            obj.put("packageName", app.packageName)
            obj.put("appName", app.appName)
            array.put(obj)
        }
        prefs.edit().putString(KEY_WHITELIST, array.toString()).apply()
        Log.d(TAG, "Whitelist saved: ${apps.size} apps")
    }
}
