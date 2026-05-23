package com.example.data.repository

import com.example.data.database.PomodoroLogEntity
import com.example.data.database.TaskDao
import com.example.data.database.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val allLogs: Flow<List<PomodoroLogEntity>> = taskDao.getAllLogs()

    suspend fun getTaskById(id: Int): TaskEntity? {
        return taskDao.getTaskById(id)
    }

    suspend fun insertTask(task: TaskEntity): Long {
        return taskDao.insertTask(task)
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    suspend fun updatePomodoroCount(taskId: Int, completedCount: Int) {
        taskDao.updatePomodoroCount(taskId, completedCount)
    }

    suspend fun insertLog(log: PomodoroLogEntity) {
        taskDao.insertLog(log)
    }

    suspend fun clearAll() {
        taskDao.clearAllTasks()
        taskDao.clearAllLogs()
    }
}
