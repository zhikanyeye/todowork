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

    // Dynamic procedural wallpapers and immersive screen focus preferences
    enum class WallpaperType(val displayName: String) {
        NONE("简约极客 (纯色/渐变)"),
        FOREST("晨曦森林 (松影幽静)"),
        COSMIC("深空星云 (绚烂繁星)"),
        RAINY("窗前夜雨 (氤氲霓虹)"),
        COCOA("温暖可可 (秋日暖泥)"),
        CUSTOM("自定义壁纸 (选择图片)")
    }
    var selectedWallpaper = MutableStateFlow(WallpaperType.NONE)
    var immersiveFocusEnabled = MutableStateFlow(false)

    // Customize storage paths
    var customWallpaperUri = MutableStateFlow<String?>(null)
    var customMusicUri = MutableStateFlow<String?>(null)
    var customMusicName = MutableStateFlow<String?>(null)

    private val prefs = context.getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
    private var customMediaPlayer: android.media.MediaPlayer? = null
    private var toneGenerator: android.media.ToneGenerator? = null

    // Sync State
    private val _backupCode = MutableStateFlow<String?>(null)
    val backupCode: StateFlow<String?> = _backupCode.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Load custom values
        workDurationMinutes.value = prefs.getInt("work_duration_minutes", 25)
        breakDurationMinutes.value = prefs.getInt("break_duration_minutes", 5)
        longBreakDurationMinutes.value = prefs.getInt("long_break_duration_minutes", 15)
        
        customWallpaperUri.value = prefs.getString("custom_wallpaper_uri", null)
        customMusicUri.value = prefs.getString("custom_music_uri", null)
        customMusicName.value = prefs.getString("custom_music_name", null)

        val savedWp = prefs.getString("selected_wallpaper", WallpaperType.NONE.name)
        selectedWallpaper.value = kotlin.runCatching { WallpaperType.valueOf(savedWp ?: "NONE") }.getOrDefault(WallpaperType.NONE)

        val savedSound = prefs.getString("selected_sound", AmbientAudioSynth.SoundType.NONE.name)
        bgSoundType.value = kotlin.runCatching { AmbientAudioSynth.SoundType.valueOf(savedSound ?: "NONE") }.getOrDefault(AmbientAudioSynth.SoundType.NONE)

        // Reset timer when work duration changes and persist updates
        viewModelScope.launch {
            workDurationMinutes.collect { mins ->
                prefs.edit().putInt("work_duration_minutes", mins).apply()
                if (!_isRunning.value && !_isBreak.value) {
                    resetTimer()
                }
            }
        }
        viewModelScope.launch {
            breakDurationMinutes.collect { mins ->
                prefs.edit().putInt("break_duration_minutes", mins).apply()
            }
        }
        viewModelScope.launch {
            longBreakDurationMinutes.collect { mins ->
                prefs.edit().putInt("long_break_duration_minutes", mins).apply()
            }
        }
    }

    fun saveCustomWallpaper(uri: String?) {
        customWallpaperUri.value = uri
        prefs.edit().putString("custom_wallpaper_uri", uri).apply()
    }

    fun saveCustomMusic(uri: String?, name: String?) {
        customMusicUri.value = uri
        customMusicName.value = name
        prefs.edit()
            .putString("custom_music_uri", uri)
            .putString("custom_music_name", name)
            .apply()
    }

    fun setWallpaper(wp: WallpaperType) {
        selectedWallpaper.value = wp
        prefs.edit().putString("selected_wallpaper", wp.name).apply()
    }

    fun setSoundType(sound: AmbientAudioSynth.SoundType) {
        val oldSound = bgSoundType.value
        bgSoundType.value = sound
        prefs.edit().putString("selected_sound", sound.name).apply()

        // If the timer is actually running, hot-swap the sound playback immediately
        if (_isRunning.value) {
            // Unconditionally stop ongoing synthesis and custom MediaPlayer playback
            AmbientAudioSynth.stop()
            stopCustomMusic()

            // Play the newly selected audio style
            if (sound != AmbientAudioSynth.SoundType.NONE) {
                if (sound == AmbientAudioSynth.SoundType.CUSTOM) {
                    playCustomMusic()
                } else {
                    AmbientAudioSynth.start(sound)
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

    fun playCustomMusic() {
        stopCustomMusic()
        val uriStr = customMusicUri.value
        if (uriStr.isNullOrEmpty()) {
            _syncStatus.value = "尚未导入任何自定义音频文件"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
            return
        }
        try {
            val mp = android.media.MediaPlayer().apply {
                setDataSource(context, android.net.Uri.parse(uriStr))
                isLooping = true
                prepare()
                start()
            }
            customMediaPlayer = mp
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Failed to play custom music file: $uriStr", e)
            _syncStatus.value = "播放自定义音频失败，请在设置中重新选择"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
        }
    }

    fun stopCustomMusic() {
        try {
            customMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Error stopping custom media player", e)
        } finally {
            customMediaPlayer = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCustomMusic()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignored
        }
        toneGenerator = null
    }

    // Timer Controls
    fun startTimer() {
        if (_isRunning.value) return
        _isRunning.value = true

        // Play synthetic background noise or custom music
        if (bgSoundType.value != AmbientAudioSynth.SoundType.NONE) {
            if (bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM) {
                playCustomMusic()
            } else {
                AmbientAudioSynth.start(bgSoundType.value)
            }
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
        stopCustomMusic()

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
        stopCustomMusic()
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
        stopCustomMusic()
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
                if (toneGenerator == null) {
                    toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                }
                toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 1000)
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to play strict warning tone", e)
                toneGenerator = null
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
