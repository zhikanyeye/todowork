package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AmbientAudioSynth
import com.example.data.database.AppDatabase
import com.example.data.database.PomodoroLogEntity
import com.example.data.database.TaskEntity
import com.example.data.repository.TaskRepository
import com.example.data.sync.BackupHelper
import com.example.data.sync.CalendarSyncHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PomodoroViewModel(
    application: Application,
    private val repository: TaskRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // App State - Tasks
    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<PomodoroLogEntity>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App State - Active selected task
    private val _selectedTask = MutableStateFlow<TaskEntity?>(null)
    val selectedTask: StateFlow<TaskEntity?> = _selectedTask.asStateFlow()

    // Timer configuration and state
    var workDurationMinutes = MutableStateFlow(25)
    var breakDurationMinutes = MutableStateFlow(5)
    var longBreakDurationMinutes = MutableStateFlow(15)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isBreak = MutableStateFlow(false)
    val isBreak: StateFlow<Boolean> = _isBreak.asStateFlow()

    private val _isLongBreak = MutableStateFlow(false)
    val isLongBreak: StateFlow<Boolean> = _isLongBreak.asStateFlow()

    private val _remainingTimeMs = MutableStateFlow(25 * 60 * 1000L)
    val remainingTimeMs: StateFlow<Long> = _remainingTimeMs.asStateFlow()

    private val _totalTimeMs = MutableStateFlow(25 * 60 * 1000L)
    val totalTimeMs: StateFlow<Long> = _totalTimeMs.asStateFlow()

    // Vibration / Sound toggles
    var soundEnabled = MutableStateFlow(true)
    var vibrationEnabled = MutableStateFlow(true)

    // Background ambient sound / DND / Strict mode settings
    var bgSoundType = MutableStateFlow(AmbientAudioSynth.SoundType.NONE)
    var dndEnabled = MutableStateFlow(false)
    var strictModeEnabled = MutableStateFlow(false)

    // Sync State
    private val _backupCode = MutableStateFlow<String?>(null)
    val backupCode: StateFlow<String?> = _backupCode.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Reset timer whenwork duration changes
        viewModelScope.launch {
            workDurationMinutes.collect { mins ->
                if (!_isRunning.value && !_isBreak.value) {
                    resetTimer()
                }
            }
        }
    }

    // Task Actions
    fun insertTask(title: String, description: String, category: String, dueDate: Long?, estimatedPoms: Int) {
        viewModelScope.launch {
            val newTask = TaskEntity(
                title = title,
                description = description,
                category = category,
                dueDate = dueDate,
                estimatedPomodoros = estimatedPoms,
                completedPomodoros = 0,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertTask(newTask)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            val updated = task.copy(lastUpdated = System.currentTimeMillis())
            repository.updateTask(updated)
            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = updated
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            // Remove calendar event if it exists
            task.calendarEventId?.let { eventId ->
                CalendarSyncHelper.removeCalendarEvent(context, eventId)
            }
            repository.deleteTask(task)
            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = null
            }
        }
    }

    fun selectTask(task: TaskEntity?) {
        _selectedTask.value = task
    }

    // Toggle Task completion
    fun toggleCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val completed = !task.isCompleted
            val updated = task.copy(isCompleted = completed, lastUpdated = System.currentTimeMillis())
            repository.updateTask(updated)
            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = updated
            }
        }
    }

    // Sync Task to Calendar
    fun syncTaskToCalendar(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val dueDate = task.dueDate
            if (dueDate == null) {
                _syncStatus.value = "该任务没有设定到期日期"
                return@launch
            }

            val titleWithCategory = "[${task.category}] ${task.title}"
            val desc = "${task.description}\n设计番茄量: ${task.estimatedPomodoros} | 已完成番茄量: ${task.completedPomodoros}"
            
            val eventId = CalendarSyncHelper.syncTaskToCalendar(
                context, 
                titleWithCategory, 
                desc, 
                dueDate, 
                task.calendarEventId
            )
            
            if (eventId != null) {
                val updated = task.copy(calendarEventId = eventId, lastUpdated = System.currentTimeMillis())
                repository.updateTask(updated)
                if (_selectedTask.value?.id == task.id) {
                    _selectedTask.value = updated
                }
                _syncStatus.value = "同步至手机日历成功！"
            } else {
                _syncStatus.value = "未授权限或日历同步失败"
            }
            
            delay(3000)
            _syncStatus.value = null
        }
    }

    // Timer Controls
    fun startTimer() {
        if (_isRunning.value) return
        _isRunning.value = true

        // Play synthetic background noise
        if (bgSoundType.value != AmbientAudioSynth.SoundType.NONE) {
            AmbientAudioSynth.start(bgSoundType.value)
        }

        // Toggle system Do Not Disturb
        setSystemDnd(true)

        timerJob = viewModelScope.launch {
            while (_remainingTimeMs.value > 0) {
                delay(1000)
                _remainingTimeMs.value -= 1000
            }
            onTimerComplete()
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()

        // Stop background sound synthesis
        AmbientAudioSynth.stop()

        // Deactivate system Do Not Disturb
        setSystemDnd(false)
    }

    fun resetTimer() {
        pauseTimer()
        val minutes = when {
            _isLongBreak.value -> longBreakDurationMinutes.value
            _isBreak.value -> breakDurationMinutes.value
            else -> workDurationMinutes.value
        }
        _remainingTimeMs.value = minutes * 60 * 1000L
        _totalTimeMs.value = minutes * 60 * 1000L

        AmbientAudioSynth.stop()
        setSystemDnd(false)
    }

    fun skipSession() {
        pauseTimer()
        if (!_isBreak.value) {
            // Completed Work, toggle to short or long break
            _isBreak.value = true
            // Check if user has done 4 work sessions, if so, trigger a long break
            val doneToday = allLogs.value.count { 
                it.category == "工作" && (System.currentTimeMillis() - it.timestamp < 12 * 60 * 60 * 1000)
            }
            if (doneToday > 0 && doneToday % 4 == 0) {
                _isLongBreak.value = true
            }
        } else {
            _isBreak.value = false
            _isLongBreak.value = false
        }
        resetTimer()
    }

    private suspend fun onTimerComplete() {
        _isRunning.value = false
        triggerAlertNotification()

        AmbientAudioSynth.stop()
        setSystemDnd(false)

        if (!_isBreak.value) {
            // Work complete! Record pomodoro
            val currentTask = _selectedTask.value
            val category = currentTask?.category ?: "工作"
            val taskTitle = currentTask?.title ?: "自由专注"
            
            // 1. Record Log
            val log = PomodoroLogEntity(
                taskId = currentTask?.id,
                taskTitle = taskTitle,
                category = category,
                durationMinutes = workDurationMinutes.value
            )
            repository.insertLog(log)

            // 2. Increment active task's completed counter
            if (currentTask != null) {
                val updatedPomCount = currentTask.completedPomodoros + 1
                repository.updatePomodoroCount(currentTask.id, updatedPomCount)
                // Refetch full task payload to keep synced
                val fresh = repository.getTaskById(currentTask.id)
                if (fresh != null) {
                    _selectedTask.value = fresh
                }
            }

            // 3. Prompt Break choice or toggle automatically
            _isBreak.value = true
            val doneToday = allLogs.value.count { 
                it.category == "工作" && (System.currentTimeMillis() - it.timestamp < 12 * 60 * 60 * 1000)
            }
            if (doneToday > 0 && doneToday % 4 == 0) {
                _isLongBreak.value = true
            }
        } else {
            // Break complete! Toggle back to Work
            _isBreak.value = false
            _isLongBreak.value = false
        }
        resetTimer()
    }

    private fun triggerAlertNotification() {
        // Sound and vibration feedback
        if (soundEnabled.value) {
            try {
                val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneG.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 1200) // Vibrate beep sound
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to trigger ToneGenerator sound", e)
            }
        }

        if (vibrationEnabled.value) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 300, 200, 300, 200, 400)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(800)
                }
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to trigger physical vibration", e)
            }
        }
    }

    // Permission and System interaction helpers
    fun isDndPermissionGranted(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun setSystemDnd(enabled: Boolean) {
        if (!dndEnabled.value) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    val targetFilter = if (enabled) {
                        android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    } else {
                        android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                    notificationManager.setInterruptionFilter(targetFilter)
                }
            }
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Failed to set DND filter: $enabled", e)
        }
    }

    fun playStrictWarningAlarm() {
        if (soundEnabled.value) {
            try {
                val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneG.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 1000)
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to play strict warning tone", e)
            }
        }
        if (vibrationEnabled.value) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(800)
                }
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to vibrate strict warning", e)
            }
        }
    }

    // Cross-platform Backup / Sync Functions
    fun copyBackupString(): String {
        return BackupHelper.serializeData(allTasks.value, allLogs.value)
    }

    fun restoreFromJson(jsonString: String): Boolean {
        val parsed = BackupHelper.deserializeData(jsonString)
        if (parsed != null) {
            viewModelScope.launch {
                repository.clearAll()
                parsed.first.forEach { task -> repository.insertTask(task) }
                parsed.second.forEach { log -> repository.insertLog(log) }
            }
            return true
        }
        return false
    }

    fun uploadBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = "备份中..."
            val json = BackupHelper.serializeData(allTasks.value, allLogs.value)
            val code = BackupHelper.uploadToCloud(json, "user@aistudio.com")
            
            if (code != null) {
                _backupCode.value = code
                _syncStatus.value = "备份成功！备份码: $code"
            } else {
                _syncStatus.value = "备份失败，请检查网络后再试"
            }
            
            delay(5000)
            _syncStatus.value = null
        }
    }

    fun restoreFromBackupCode(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncStatus.value = "正在拉取云端备份..."
            val json = BackupHelper.downloadFromCloud(code)
            
            if (json != null) {
                val restoreSuccess = restoreFromJson(json)
                if (restoreSuccess) {
                    _syncStatus.value = "快照恢复成功！已拉取所有任务和记录"
                } else {
                    _syncStatus.value = "快照格式解析失败"
                }
            } else {
                _syncStatus.value = "拉取失败，请检查备份码或网络"
            }
            
            delay(5000)
            _syncStatus.value = null
        }
    }
}

class PomodoroViewModelFactory(
    private val application: Application,
    private val repository: TaskRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PomodoroViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PomodoroViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
