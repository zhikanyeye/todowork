package com.yangshizi.todo.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val category: String = "工作", // "工作" (Work), "学习" (Study), "生活" (Life), "其他" (Other)
    val dueDate: Long? = null,
    val estimatedPomodoros: Int = 1,
    val completedPomodoros: Int = 0,
    val timerMode: String = "countdown", // "countdown" 倒计时 | "countup" 正向计时
    // 每任务自定义番茄时长（分钟），null 表示跟随系统设置里的全局时长
    val workDurationMin: Int? = null,
    val breakDurationMin: Int? = null,
    val longBreakDurationMin: Int? = null,
    val calendarEventId: Long? = null, // Holds phone calendar event ID if synced
    val lastUpdated: Long = System.currentTimeMillis()
)
