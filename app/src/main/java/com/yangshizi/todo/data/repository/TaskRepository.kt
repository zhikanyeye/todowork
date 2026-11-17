package com.yangshizi.todo.data.repository

import com.yangshizi.todo.data.database.PomodoroLogEntity
import com.yangshizi.todo.data.database.TaskDao
import com.yangshizi.todo.data.database.TaskEntity
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

    /** 原子化恢复：单事务内清空并重写全部数据，失败整体回滚。 */
    suspend fun replaceAll(tasks: List<TaskEntity>, logs: List<PomodoroLogEntity>) {
        taskDao.replaceAll(tasks, logs)
    }
}
