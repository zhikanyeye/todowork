package com.yangshizi.todo.data.update

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 远程更新检测：查询 GitHub Releases 最新版本并与当前版本比较。
 * 仓库发布新 Release（tag 如 v1.2.0）后，旧版本应用会收到更新提示。
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_API = "https://api.github.com/repos/zhikanyeye/todowork/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/zhikanyeye/todowork/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ReleaseInfo(
        val versionName: String,
        val changelog: String,
        val pageUrl: String,
        val apkUrl: String?
    )

    /**
     * 拉取最新 Release 信息。
     * 仓库尚未发布过任何 Release 时（HTTP 404）返回 null。
     */
    fun fetchLatestRelease(): ReleaseInfo? {
        return kotlin.runCatching {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "yangshizi-todo-app")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use null // 仓库还没有 Release
                if (!response.isSuccessful) {
                    Log.w(TAG, "Check update failed: HTTP ${response.code}")
                    return@use null
                }
                val body = response.body?.string() ?: return@use null
                parseRelease(body)
            }
        }.getOrElse {
            Log.w(TAG, "Check update network error", it)
            null
        }
    }

    private fun parseRelease(json: String): ReleaseInfo? {
        return kotlin.runCatching {
            val root = org.json.JSONObject(json)
            val tag = root.optString("tag_name").trim()
            val version = normalizeVersion(tag).ifBlank {
                normalizeVersion(root.optString("name").trim())
            }
            if (version.isBlank()) return@runCatching null

            // 从 assets 里找 APK 附件（优先 universal/release 后缀）
            var apkUrl: String? = null
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val url = asset.optString("browser_download_url")
                    if (url.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = url
                        break
                    }
                }
            }
            ReleaseInfo(
                versionName = version,
                changelog = root.optString("body").trim(),
                pageUrl = root.optString("html_url").ifBlank { RELEASES_PAGE },
                apkUrl = apkUrl
            )
        }.getOrElse {
            Log.w(TAG, "Parse release JSON failed", it)
            null
        }
    }

    /** 去掉 tag 常见的 v/V/r 前缀，只留版本数字部分 */
    private fun normalizeVersion(raw: String): String {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V").removePrefix("r").trim()
        return trimmed.takeIf { Regex("^\\d+(\\.\\d+)*$").matches(it) } ?: ""
    }

    /** 语义化比较：返回 >0 表示 a 更新，<0 表示 b 更新，0 表示相同 */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
