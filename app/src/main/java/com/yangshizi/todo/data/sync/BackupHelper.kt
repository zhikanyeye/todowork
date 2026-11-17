package com.yangshizi.todo.data.sync

import android.net.Uri
import android.util.Log
import com.yangshizi.todo.data.database.PomodoroLogEntity
import com.yangshizi.todo.data.database.TaskEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object BackupHelper {
    private const val TAG = "BackupHelper"
    private const val MAX_IMPORTED_LOG_MINUTES = 24 * 60L
    private const val MIN_VALID_TIMESTAMP_MS = 946684800000L // 2000-01-01
    private const val MAX_VALID_TIMESTAMP_MS = 4102444799000L // 2099-12-31
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()

    // Serialize database lists to JSON string
    fun serializeData(tasks: List<TaskEntity>, logs: List<PomodoroLogEntity>): String {
        try {
            val root = JSONObject()
            
            val tasksArray = JSONArray()
            tasks.forEach { task ->
                val jt = JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("isCompleted", task.isCompleted)
                    put("category", task.category)
                    put("dueDate", task.dueDate ?: JSONObject.NULL)
                    put("estimatedPomodoros", task.estimatedPomodoros)
                    put("completedPomodoros", task.completedPomodoros)
                    put("timerMode", task.timerMode)
                    put("workDurationMin", task.workDurationMin ?: JSONObject.NULL)
                    put("breakDurationMin", task.breakDurationMin ?: JSONObject.NULL)
                    put("longBreakDurationMin", task.longBreakDurationMin ?: JSONObject.NULL)
                    put("calendarEventId", task.calendarEventId ?: JSONObject.NULL)
                    put("lastUpdated", task.lastUpdated)
                }
                tasksArray.put(jt)
            }
            root.put("tasks", tasksArray)

            val logsArray = JSONArray()
            logs.forEach { log ->
                val jlog = JSONObject().apply {
                    put("id", log.id)
                    put("taskId", log.taskId ?: JSONObject.NULL)
                    put("taskTitle", log.taskTitle)
                    put("timestamp", log.timestamp)
                    put("durationMinutes", log.durationMinutes)
                    put("category", log.category)
                }
                logsArray.put(jlog)
            }
            root.put("logs", logsArray)
            root.put("backupTime", System.currentTimeMillis())

            return root.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize backup data", e)
            return ""
        }
    }

    // Deserialize JSON string back to lists of entities
    fun deserializeData(jsonString: String): Pair<List<TaskEntity>, List<PomodoroLogEntity>>? {
        try {
            val trimmed = jsonString.trim()
            val root = if (trimmed.startsWith("[")) {
                JSONObject().put("logs", JSONArray(trimmed))
            } else {
                JSONObject(trimmed)
            }
            
            val tasksList = mutableListOf<TaskEntity>()
            val tasksArray = root.optArray("tasks", "taskList", "todos")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val jt = tasksArray.optJSONObject(i) ?: continue
                    parseTask(jt)?.let(tasksList::add)
                }
            }

            val logsList = mutableListOf<PomodoroLogEntity>()
            val logsArray = root.optArray("logs", "pomodoroLogs", "history", "records", "focusLogs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val jl = logsArray.optJSONObject(i) ?: continue
                    parseLog(jl)?.let(logsList::add)
                }
            }

            return Pair(tasksList, logsList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize backup data", e)
            return null
        }
    }

    private fun parseTask(jt: JSONObject): TaskEntity? {
        return kotlin.runCatching {
            val title = jt.cleanString("title", "name", "taskTitle", default = "未命名任务")
            TaskEntity(
                id = jt.intOrNull("id", "taskId")?.takeIf { it > 0 } ?: 0,
                title = title,
                description = jt.cleanString("description", "note", "remark", default = ""),
                isCompleted = jt.optBoolean("isCompleted", jt.optBoolean("completed", false)),
                category = jt.cleanString("category", "type", default = "工作"),
                dueDate = jt.longOrNull("dueDate", "deadline")?.let(::normalizeTimestamp),
                estimatedPomodoros = jt.intOrNull("estimatedPomodoros", "estimatePomodoros")
                    ?.coerceIn(1, 999)
                    ?: 1,
                completedPomodoros = jt.intOrNull("completedPomodoros", "pomodoros", "finishedPomodoros")
                    ?.coerceIn(0, 999_999)
                    ?: 0,
                timerMode = jt.cleanString("timerMode", default = "countdown").let {
                    if (it == "countup") "countup" else "countdown"
                },
                workDurationMin = jt.intOrNull("workDurationMin")?.takeIf { it > 0 },
                breakDurationMin = jt.intOrNull("breakDurationMin")?.takeIf { it > 0 },
                longBreakDurationMin = jt.intOrNull("longBreakDurationMin")?.takeIf { it > 0 },
                calendarEventId = jt.longOrNull("calendarEventId")?.takeIf { it > 0 },
                lastUpdated = jt.longOrNull("lastUpdated", "updatedAt")
                    ?.let(::normalizeTimestamp)
                    ?: System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun parseLog(jl: JSONObject): PomodoroLogEntity? {
        return kotlin.runCatching {
            val timestamp = readTimestamp(jl) ?: return null
            val durationMinutes = readDurationMinutes(jl) ?: return null
            PomodoroLogEntity(
                id = jl.intOrNull("id", "logId")?.takeIf { it > 0 } ?: 0,
                taskId = jl.intOrNull("taskId")?.takeIf { it > 0 },
                taskTitle = jl.cleanString("taskTitle", "title", "taskName", "name", default = "自由专注"),
                timestamp = timestamp,
                durationMinutes = durationMinutes,
                category = jl.cleanString("category", "type", "tag", default = "工作")
            )
        }.getOrNull()
    }

    private fun readTimestamp(obj: JSONObject): Long? {
        val raw = obj.longOrNull("timestamp", "time", "createdAt", "startTime", "completedAt", "endTime", "date")
        if (raw != null) return normalizeTimestamp(raw)

        val dateText = obj.cleanStringOrNull("timestamp", "time", "createdAt", "startTime", "completedAt", "endTime", "date")
        return dateText?.let(::parseDateTimestamp)?.let(::normalizeTimestamp)
    }

    private fun readDurationMinutes(obj: JSONObject): Int? {
        val explicitMinutes = obj.longOrNull(
            "durationMinutes",
            "minutes",
            "focusMinutes",
            "elapsedMinutes",
            "workMinutes"
        )
        if (explicitMinutes != null) return normalizeDurationMinutes(explicitMinutes)

        val seconds = obj.longOrNull("durationSeconds", "seconds", "elapsedSeconds")
        if (seconds != null) return normalizeDurationMinutes(ceilDiv(seconds, 60L))

        val millis = obj.longOrNull("durationMillis", "milliseconds", "elapsedMillis")
        if (millis != null) return normalizeDurationMinutes(ceilDiv(millis, 60_000L))

        val ambiguous = obj.longOrNull("duration", "elapsed", "length") ?: return 25
        val inferredMinutes = when {
            ambiguous > MAX_IMPORTED_LOG_MINUTES * 60L -> ceilDiv(ambiguous, 60_000L)
            ambiguous > MAX_IMPORTED_LOG_MINUTES -> ceilDiv(ambiguous, 60L)
            else -> ambiguous
        }
        return normalizeDurationMinutes(inferredMinutes)
    }

    private fun normalizeTimestamp(raw: Long): Long? {
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        val abs = if (raw < 0) -raw else raw
        val millis = when {
            abs in 1L..99_999_999_999L -> raw * 1000L
            abs > 99_999_999_999_999L -> raw / 1000L
            else -> raw
        }
        return millis.takeIf { it in MIN_VALID_TIMESTAMP_MS..MAX_VALID_TIMESTAMP_MS }
    }

    private fun normalizeDurationMinutes(minutes: Long): Int? {
        if (minutes <= 0L) return null
        return minutes.coerceAtMost(MAX_IMPORTED_LOG_MINUTES).toInt()
    }

    private fun parseDateTimestamp(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            kotlin.runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value)?.time
            }.getOrNull()
        }
    }

    private fun JSONObject.optArray(vararg names: String): JSONArray? {
        for (name in names) {
            val value = opt(name)
            if (value is JSONArray) return value
        }
        return null
    }

    private fun JSONObject.cleanString(vararg names: String, default: String): String {
        return cleanStringOrNull(*names) ?: default
    }

    private fun JSONObject.cleanStringOrNull(vararg names: String): String? {
        for (name in names) {
            if (!has(name) || isNull(name)) continue
            val raw = opt(name)?.toString()?.trim().orEmpty()
            if (raw.isNotBlank() && raw != "null" && raw != "undefined") {
                return raw.take(80)
            }
        }
        return null
    }

    private fun JSONObject.longOrNull(vararg names: String): Long? {
        for (name in names) {
            if (!has(name) || isNull(name)) continue
            val value = opt(name)
            when (value) {
                is Number -> return value.toLong()
                is String -> value.trim().toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun JSONObject.intOrNull(vararg names: String): Int? {
        val value = longOrNull(*names) ?: return null
        return value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
    }

    private fun ceilDiv(value: Long, divisor: Long): Long {
        if (value <= 0L) return 0L
        return (value + divisor - 1L) / divisor
    }

    // Web Backup Sync: 保存备份文本到公开 JSON/文本存储服务。
    // jsonblob.com 已被 Cloudflare 拦截(403)，改用多渠道回退链：
    // paste.c-net.org → paste.rs → jsonblob.com（保留兜底）。
    suspend fun uploadToCloud(jsonString: String, email: String): String? {
        return kotlin.runCatching {
            uploadToPasteCNet(jsonString)
                ?: uploadToPasteRs(jsonString)
                ?: uploadToJsonblob(jsonString)
        }.getOrElse {
            Log.e(TAG, "Network exception during upload to cloud", it)
            null
        }
    }

    /** paste.c-net.org：POST 原文，响应体直接返回取回 URL */
    private fun uploadToPasteCNet(jsonString: String): String? {
        return kotlin.runCatching {
            val request = Request.Builder()
                .url("https://paste.c-net.org/")
                .post(jsonString.toRequestBody(TEXT_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "paste.c-net upload failed: HTTP ${response.code}")
                    return@runCatching null
                }
                val url = response.body?.string()?.trim()
                extractBackupCode(url)
            }
        }.getOrElse {
            Log.w(TAG, "paste.c-net upload exception", it)
            null
        }
    }

    /** paste.rs：POST 原文，响应体返回取回 URL */
    private fun uploadToPasteRs(jsonString: String): String? {
        return kotlin.runCatching {
            val request = Request.Builder()
                .url("https://paste.rs/")
                .post(jsonString.toRequestBody(TEXT_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "paste.rs upload failed: HTTP ${response.code}")
                    return@runCatching null
                }
                val url = response.body?.string()?.trim()
                extractBackupCode(url)
            }
        }.getOrElse {
            Log.w(TAG, "paste.rs upload exception", it)
            null
        }
    }

    /** jsonblob.com（旧渠道兜底）：POST 取 Location 头 */
    private fun uploadToJsonblob(jsonString: String): String? {
        return kotlin.runCatching {
            val requestBody = jsonString.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://jsonblob.com/api/jsonBlob")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val locationHeader = response.header("Location") ?: response.header("location")
                    extractBackupCode(locationHeader)
                } else {
                    Log.w(TAG, "jsonblob upload failed: HTTP ${response.code}")
                    null
                }
            }
        }.getOrElse {
            Log.w(TAG, "jsonblob upload exception", it)
            null
        }
    }

    /** 从返回的 URL / Location 头提取末段作为备份码 */
    private fun extractBackupCode(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val code = Uri.parse(trimmed).lastPathSegment
        return code?.takeIf { it.isNotBlank() }
    }

    // Web Restore Sync: 用备份码拉取云端备份。
    // 兼容三种输入：完整 URL / 新渠道短码 / 旧 jsonblob 短码。
    suspend fun downloadFromCloud(blobId: String): String? {
        return kotlin.runCatching {
            val code = blobId.trim()
            if (code.isEmpty()) return@runCatching null

            // 完整 URL：识别主机后直接取回；未知主机也直接尝试
            if (code.startsWith("http://") || code.startsWith("https://")) {
                fetchText(code)
            } else {
                fetchText("https://paste.c-net.org/$code")
                    ?: fetchText("https://paste.rs/$code")
                    ?: fetchText("https://jsonblob.com/api/jsonBlob/$code")
            }
        }.getOrElse {
            Log.e(TAG, "Network exception during restore from cloud", it)
            null
        }
    }

    private fun fetchText(url: String): String? {
        return kotlin.runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) null else body
                } else {
                    Log.w(TAG, "fetch failed $url: HTTP ${response.code}")
                    null
                }
            }
        }.getOrElse {
            Log.w(TAG, "fetch exception $url", it)
            null
        }
    }
}
