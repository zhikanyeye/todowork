package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_logs")
data class PomodoroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val taskTitle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 25,
    val category: String = "工作"
)
