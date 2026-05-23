package com.example.data.database

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
    val calendarEventId: Long? = null, // Holds phone calendar event ID if synced
    val lastUpdated: Long = System.currentTimeMillis()
)
