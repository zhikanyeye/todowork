package com.example.data.sync

import android.net.Uri
import android.util.Log
import com.example.data.database.PomodoroLogEntity
import com.example.data.database.TaskEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BackupHelper {
    private const val TAG = "BackupHelper"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
            val root = JSONObject(jsonString)
            
            val tasksList = mutableListOf<TaskEntity>()
            if (root.has("tasks")) {
                val tasksArray = root.getJSONArray("tasks")
                for (i in 0 until tasksArray.length()) {
                    val jt = tasksArray.getJSONObject(i)
                    val task = TaskEntity(
                        id = if (jt.has("id")) jt.getInt("id") else 0,
                        title = jt.getString("title"),
                        description = jt.optString("description", ""),
                        isCompleted = jt.optBoolean("isCompleted", false),
                        category = jt.optString("category", "工作"),
                        dueDate = if (jt.isNull("dueDate")) null else jt.getLong("dueDate"),
                        estimatedPomodoros = jt.optInt("estimatedPomodoros", 1),
                        completedPomodoros = jt.optInt("completedPomodoros", 0),
                        calendarEventId = if (jt.isNull("calendarEventId")) null else jt.getLong("calendarEventId"),
                        lastUpdated = jt.optLong("lastUpdated", System.currentTimeMillis())
                    )
                    tasksList.add(task)
                }
            }

            val logsList = mutableListOf<PomodoroLogEntity>()
            if (root.has("logs")) {
                val logsArray = root.getJSONArray("logs")
                for (i in 0 until logsArray.length()) {
                    val jl = logsArray.getJSONObject(i)
                    val log = PomodoroLogEntity(
                        id = if (jl.has("id")) jl.getInt("id") else 0,
                        taskId = if (jl.isNull("taskId")) null else jl.getInt("taskId"),
                        taskTitle = jl.getString("taskTitle"),
                        timestamp = jl.getLong("timestamp"),
                        durationMinutes = jl.optInt("durationMinutes", 25),
                        category = jl.optString("category", "工作")
                    )
                    logsList.add(log)
                }
            }

            return Pair(tasksList, logsList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize backup data", e)
            return null
        }
    }

    // Web Backup Sync: Save backup data to a cloud storage REST endpoint
    // Uses a public and free JSON storage paste service (kvstore or jsonbin style)
    suspend fun uploadToCloud(jsonString: String, email: String): String? {
        return kotlin.runCatching {
            // We can post to a dynamic paste service that stores text data.
            // Best public anonymous pastebin is paste.c-net.org or simple mock bins online that are fast.
            // Let's use kvstore or a public text bin like "https://jsonblob.com/api/jsonBlob" or "https://httpbin.org/post"
            // For a robust implementation without needing fragile api keys, we use jsonblob.com which supports full permanent JSON reading.
            val requestBody = jsonString.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://jsonblob.com/api/jsonBlob")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // JsonBlob returns location header of the stored blob
                    val locationHeader = response.header("Location") ?: response.header("location")
                    if (locationHeader != null) {
                        // Extract blob ID (last segment of URL)
                        val id = Uri.parse(locationHeader).lastPathSegment
                        Log.d(TAG, "Uploaded successfully, Blob ID: $id")
                        id
                    } else {
                        // Try reading body block
                        val body = response.body?.string() ?: ""
                        Log.d(TAG, "Uploaded response body: $body")
                        null
                    }
                } else {
                    Log.e(TAG, "Upload failed with HTTP code: ${response.code}")
                    null
                }
            }
        }.getOrElse {
            Log.e(TAG, "Network exception during upload to cloud", it)
            null
        }
    }

    // Web Restore Sync: Retrieve backup config from cloud using Blob ID
    suspend fun downloadFromCloud(blobId: String): String? {
        return kotlin.runCatching {
            val url = "https://jsonblob.com/api/jsonBlob/$blobId"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Download failed with HTTP code: ${response.code}")
                    null
                }
            }
        }.getOrElse {
            Log.e(TAG, "Network exception during restore from cloud", it)
            null
        }
    }
}
