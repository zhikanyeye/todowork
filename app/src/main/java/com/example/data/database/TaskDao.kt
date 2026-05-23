package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, lastUpdated DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET completedPomodoros = :completedCount, lastUpdated = :lastUpdated WHERE id = :taskId")
    suspend fun updatePomodoroCount(taskId: Int, completedCount: Int, lastUpdated: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    // Pomodoro Logs
    @Query("SELECT * FROM pomodoro_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<PomodoroLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PomodoroLogEntity)

    @Query("DELETE FROM pomodoro_logs")
    suspend fun clearAllLogs()
}

