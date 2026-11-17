package com.yangshizi.todo.ui.viewmodel

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangshizi.todo.data.audio.AmbientAudioSynth
import com.yangshizi.todo.data.audio.RealAudioPlayer
import com.yangshizi.todo.data.audio.ToneSynth
import com.yangshizi.todo.data.database.AppDatabase
import com.yangshizi.todo.data.database.PomodoroLogEntity
import com.yangshizi.todo.data.database.TaskEntity
import com.yangshizi.todo.data.repository.TaskRepository
import com.yangshizi.todo.data.sync.BackupHelper
import com.yangshizi.todo.data.sync.CalendarSyncHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

    // 正向计时模式：从 0 开始往上计，不设上限
    private val _isCountUpMode = MutableStateFlow(false)
    val isCountUpMode: StateFlow<Boolean> = _isCountUpMode.asStateFlow()

    // 重置确认标志：运行中点击重置时弹出确认对话框
    private val _showResetConfirm = MutableStateFlow(false)
    val showResetConfirm: StateFlow<Boolean> = _showResetConfirm.asStateFlow()

    // Vibration / Sound toggles
    var soundEnabled = MutableStateFlow(true)
    var vibrationEnabled = MutableStateFlow(true)

    // Background ambient sound / DND / Strict mode settings
    var bgSoundType = MutableStateFlow(AmbientAudioSynth.SoundType.NONE)
    var realSoundType = MutableStateFlow(RealAudioPlayer.RealSound.NONE) // 真实MP3背景音
    var tickingEnabled = MutableStateFlow(false) // 专注滴答声（合成）
    var dndEnabled = MutableStateFlow(false)
    var strictModeEnabled = MutableStateFlow(false)
    var notificationBlockEnabled = MutableStateFlow(false) // 通知拦截开关

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
    data class CustomWallpaperItem(val uri: String, val name: String)
    var customWallpaperList = MutableStateFlow<List<CustomWallpaperItem>>(emptyList())
    var selectedCustomWallpaperIndex = MutableStateFlow(0)

    // 自定义音乐列表（支持多个，上限 10 个）
    data class CustomMusicItem(val uri: String, val name: String)
    var customMusicList = MutableStateFlow<List<CustomMusicItem>>(emptyList())
    var selectedCustomMusicIndex = MutableStateFlow(0)
    // 兼容旧代码的属性
    val customMusicUri: StateFlow<String?> = customMusicList
        .map { it.getOrNull(selectedCustomMusicIndex.value)?.uri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val customMusicName: StateFlow<String?> = customMusicList
        .map { it.getOrNull(selectedCustomMusicIndex.value)?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val prefs = context.getSharedPreferences("pomodoro_settings", Context.MODE_PRIVATE)
    private var customMediaPlayer: android.media.MediaPlayer? = null
    private var toneGenerator: android.media.ToneGenerator? = null
    private var soundPreviewJob: Job? = null

    // Sync State
    private val _backupCode = MutableStateFlow<String?>(null)
    val backupCode: StateFlow<String?> = _backupCode.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private var timerJob: Job? = null

    // 墙钟锚点：计时运行时的目标结束时刻（基于 elapsedRealtime，不受系统时间修改影响）。
    // 剩余时间 = endTimeElapsedMs - SystemClock.elapsedRealtime()，息屏/Doze 下 delay 被批量
    // 补发也只会让 UI 慢一拍，绝不会提前归零误判完成。0 表示未运行。
    private var endTimeElapsedMs: Long = 0L

    init {
        // Load custom values
        workDurationMinutes.value = prefs.getInt("work_duration_minutes", 25)
        breakDurationMinutes.value = prefs.getInt("break_duration_minutes", 5)
        longBreakDurationMinutes.value = prefs.getInt("long_break_duration_minutes", 15)
        
        loadCustomWallpaperList()

        // 加载自定义音乐列表（JSON 格式），兼容旧版单文件迁移
        loadCustomMusicList()

        // 加载正向计时模式设置
        _isCountUpMode.value = prefs.getBoolean("is_count_up_mode", false)

        val savedWp = prefs.getString("selected_wallpaper", WallpaperType.NONE.name)
        selectedWallpaper.value = kotlin.runCatching { WallpaperType.valueOf(savedWp ?: "NONE") }.getOrDefault(WallpaperType.NONE)

        val savedSound = prefs.getString("selected_sound", AmbientAudioSynth.SoundType.NONE.name)
        bgSoundType.value = kotlin.runCatching { AmbientAudioSynth.SoundType.valueOf(savedSound ?: "NONE") }.getOrDefault(AmbientAudioSynth.SoundType.NONE)
        realSoundType.value = RealAudioPlayer.RealSound.fromName(prefs.getString("real_sound", RealAudioPlayer.RealSound.NONE.name) ?: "NONE")
        tickingEnabled.value = prefs.getBoolean("ticking_enabled", false)
        dndEnabled.value = prefs.getBoolean("dnd_enabled", false)
        strictModeEnabled.value = prefs.getBoolean("strict_mode_enabled", false)
        notificationBlockEnabled.value = prefs.getBoolean("notification_block_enabled", false)

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
        viewModelScope.launch {
            dndEnabled.collect { enabled ->
                prefs.edit().putBoolean("dnd_enabled", enabled).apply()
            }
        }
        viewModelScope.launch {
            strictModeEnabled.collect { enabled ->
                prefs.edit()
                    .putBoolean("strict_mode_enabled", enabled)
                    .putBoolean("strict_focus_active", enabled && _isRunning.value && !_isBreak.value)
                    .apply()
            }
        }
        viewModelScope.launch {
            notificationBlockEnabled.collect { enabled ->
                prefs.edit().putBoolean("notification_block_enabled", enabled).apply()
            }
        }
        viewModelScope.launch {
            realSoundType.collect { sound ->
                prefs.edit().putString("real_sound", sound.name).apply()
            }
        }
        viewModelScope.launch {
            tickingEnabled.collect { enabled ->
                prefs.edit().putBoolean("ticking_enabled", enabled).apply()
            }
        }
        viewModelScope.launch {
            _isCountUpMode.collect { enabled ->
                prefs.edit().putBoolean("is_count_up_mode", enabled).apply()
            }
        }
        viewModelScope.launch {
            combine(_isRunning, _isBreak, strictModeEnabled) { running, isBreak, strict ->
                strict && running && !isBreak
            }.collect { active ->
                prefs.edit().putBoolean("strict_focus_active", active).apply()
            }
        }
        viewModelScope.launch {
            allTasks.collect { tasks ->
                val selectedId = prefs.getInt("selected_task_id", 0)
                if (selectedId <= 0) {
                    if (_selectedTask.value != null) _selectedTask.value = null
                    return@collect
                }
                if (tasks.isEmpty()) return@collect
                val fresh = tasks.firstOrNull { it.id == selectedId }
                if (fresh != null) {
                    _selectedTask.value = fresh
                } else {
                    prefs.edit().remove("selected_task_id").apply()
                    _selectedTask.value = null
                }
            }
        }
    }

    fun saveCustomWallpaper(uri: String?) {
        if (uri == null) {
            customWallpaperList.value = emptyList()
            selectedCustomWallpaperIndex.value = 0
            customWallpaperUri.value = null
            persistCustomWallpaperList()
            prefs.edit().remove("custom_wallpaper_uri").apply()
        } else {
            addCustomWallpaper(uri, "自定义壁纸")
        }
    }

    private fun loadCustomWallpaperList() {
        val json = prefs.getString("custom_wallpaper_list_json", null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<CustomWallpaperItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CustomWallpaperItem(
                            obj.getString("uri"),
                            obj.optString("name", "自定义壁纸 ${i + 1}")
                        )
                    )
                }
                customWallpaperList.value = list.take(5)
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to parse custom wallpaper list", e)
            }
        } else {
            val oldUri = prefs.getString("custom_wallpaper_uri", null)
            if (!oldUri.isNullOrEmpty()) {
                customWallpaperList.value = listOf(CustomWallpaperItem(oldUri, "自定义壁纸"))
                persistCustomWallpaperList()
            }
        }

        selectedCustomWallpaperIndex.value = prefs.getInt("selected_custom_wallpaper_index", 0)
            .coerceIn(0, (customWallpaperList.value.size - 1).coerceAtLeast(0))
        customWallpaperUri.value = customWallpaperList.value
            .getOrNull(selectedCustomWallpaperIndex.value)
            ?.uri
    }

    private fun persistCustomWallpaperList() {
        val array = JSONArray()
        customWallpaperList.value.forEach { item ->
            val obj = JSONObject()
            obj.put("uri", item.uri)
            obj.put("name", item.name)
            array.put(obj)
        }
        prefs.edit()
            .putString("custom_wallpaper_list_json", array.toString())
            .putInt("selected_custom_wallpaper_index", selectedCustomWallpaperIndex.value)
            .putString("custom_wallpaper_uri", customWallpaperUri.value)
            .apply()
    }

    fun addCustomWallpaper(uri: String, name: String) {
        val current = customWallpaperList.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.uri == uri }
        if (existingIndex >= 0) {
            selectCustomWallpaper(existingIndex)
            selectedWallpaper.value = WallpaperType.CUSTOM
            prefs.edit().putString("selected_wallpaper", WallpaperType.CUSTOM.name).apply()
            return
        }
        if (current.size >= 5) {
            _syncStatus.value = "自定义壁纸已达上限 (5 张)"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
            return
        }
        current.add(CustomWallpaperItem(uri, name))
        customWallpaperList.value = current
        selectedCustomWallpaperIndex.value = current.lastIndex
        customWallpaperUri.value = uri
        selectedWallpaper.value = WallpaperType.CUSTOM
        prefs.edit().putString("selected_wallpaper", WallpaperType.CUSTOM.name).apply()
        persistCustomWallpaperList()
    }

    fun selectCustomWallpaper(index: Int) {
        if (index !in customWallpaperList.value.indices) return
        selectedCustomWallpaperIndex.value = index
        customWallpaperUri.value = customWallpaperList.value[index].uri
        persistCustomWallpaperList()
    }

    fun removeCustomWallpaper(index: Int) {
        val current = customWallpaperList.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        customWallpaperList.value = current
        val newIndex = selectedCustomWallpaperIndex.value.coerceIn(0, (current.size - 1).coerceAtLeast(0))
        selectedCustomWallpaperIndex.value = newIndex
        customWallpaperUri.value = current.getOrNull(newIndex)?.uri
        if (current.isEmpty() && selectedWallpaper.value == WallpaperType.CUSTOM) {
            selectedWallpaper.value = WallpaperType.NONE
            prefs.edit().putString("selected_wallpaper", WallpaperType.NONE.name).apply()
        }
        persistCustomWallpaperList()
    }

    /**
     * 加载自定义音乐列表（JSON 格式），兼容旧版单文件迁移。
     * 如果新格式数据不存在但旧格式 key 存在，自动迁移。
     */
    private fun loadCustomMusicList() {
        val json = prefs.getString("custom_music_list_json", null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<CustomMusicItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(CustomMusicItem(
                        obj.getString("uri"),
                        obj.optString("name", "未知音乐")
                    ))
                }
                customMusicList.value = list
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to parse custom music list", e)
            }
        } else {
            // 旧版迁移：从单文件 key 迁移到列表
            val oldUri = prefs.getString("custom_music_uri", null)
            val oldName = prefs.getString("custom_music_name", null)
            if (oldUri != null) {
                val item = CustomMusicItem(oldUri, oldName ?: "自定义音乐")
                customMusicList.value = listOf(item)
                persistCustomMusicList()
                // 清除旧 key
                prefs.edit()
                    .remove("custom_music_uri")
                    .remove("custom_music_name")
                    .apply()
            }
        }
        selectedCustomMusicIndex.value = prefs.getInt("selected_custom_music_index", 0)
            .coerceIn(0, (customMusicList.value.size - 1).coerceAtLeast(0))
    }

    /**
     * 持久化自定义音乐列表到 SharedPreferences（JSON 格式）
     */
    private fun persistCustomMusicList() {
        val array = JSONArray()
        customMusicList.value.forEach { item ->
            val obj = JSONObject()
            obj.put("uri", item.uri)
            obj.put("name", item.name)
            array.put(obj)
        }
        prefs.edit().putString("custom_music_list_json", array.toString()).apply()
    }

    /**
     * 添加自定义音乐到列表（上限 10 个）
     */
    fun addCustomMusic(uri: String, name: String) {
        val current = customMusicList.value.toMutableList()
        if (current.size >= 10) {
            _syncStatus.value = "自定义音乐已达上限 (10 首)"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
            return
        }
        // 去重：相同 URI 不重复添加
        if (current.any { it.uri == uri }) {
            _syncStatus.value = "该音乐已在列表中"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
            return
        }
        current.add(CustomMusicItem(uri, name))
        customMusicList.value = current
        selectedCustomMusicIndex.value = current.size - 1
        prefs.edit().putInt("selected_custom_music_index", selectedCustomMusicIndex.value).apply()
        persistCustomMusicList()

        // 导入即选中“自定义音乐”
        realSoundType.value = RealAudioPlayer.RealSound.NONE
        prefs.edit().putString("real_sound", RealAudioPlayer.RealSound.NONE.name).apply()
        bgSoundType.value = AmbientAudioSynth.SoundType.CUSTOM
        prefs.edit().putString("selected_sound", AmbientAudioSynth.SoundType.CUSTOM.name).apply()
        soundPreviewJob?.cancel()
        soundPreviewJob = null
        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()
        if (isAudioActive()) {
            playCustomMusic()
        } else {
            previewSoundSelection(AmbientAudioSynth.SoundType.CUSTOM)
        }
    }

    /**
     * 删除指定索引的自定义音乐
     */
    fun removeCustomMusic(index: Int) {
        val current = customMusicList.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        customMusicList.value = current
        // 调整选中索引
        val newIdx = (selectedCustomMusicIndex.value).coerceIn(0, (current.size - 1).coerceAtLeast(0))
        selectedCustomMusicIndex.value = newIdx
        prefs.edit().putInt("selected_custom_music_index", newIdx).apply()
        persistCustomMusicList()

        // 如果删的是当前播放项或列表变空，停止播放
        if (current.isEmpty()) {
            stopCustomMusic()
            bgSoundType.value = AmbientAudioSynth.SoundType.NONE
            prefs.edit().putString("selected_sound", AmbientAudioSynth.SoundType.NONE.name).apply()
        } else if (bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM) {
            stopCustomMusic()
            if (isAudioActive()) {
                playCustomMusic()
            }
        }
    }

    /**
     * 选择指定的自定义音乐（切换播放）
     */
    fun selectCustomMusic(index: Int) {
        if (index !in customMusicList.value.indices) return
        selectedCustomMusicIndex.value = index
        prefs.edit().putInt("selected_custom_music_index", index).apply()
        if (bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM && isAudioActive()) {
            stopCustomMusic()
            playCustomMusic()
        }
    }

    /**
     * 兼容旧接口：保留 saveCustomMusic 方法名，内部调用 addCustomMusic
     */
    fun saveCustomMusic(uri: String?, name: String?) {
        if (uri != null && name != null) {
            addCustomMusic(uri, name)
        }
    }

    /**
     * 设置正向计时模式
     */
    fun setCountUpMode(enabled: Boolean) {
        if (_isRunning.value) return // 计时中不允许切换
        _isCountUpMode.value = enabled
        prefs.edit().putBoolean("is_count_up_mode", enabled).apply()
        if (enabled) {
            _remainingTimeMs.value = 0L
            _totalTimeMs.value = 0L
        } else {
            resetTimer()
        }
    }

    /**
     * 请求重置计时器（运行中触发确认对话框）
     */
    fun requestReset() {
        if (_isRunning.value) {
            _showResetConfirm.value = true
        } else {
            resetTimer()
        }
    }

    fun confirmReset() {
        _showResetConfirm.value = false
        resetTimer()
    }

    fun cancelReset() {
        _showResetConfirm.value = false
    }

    fun setWallpaper(wp: WallpaperType) {
        // 自定义壁纸需要先选择图片，未选择时不允许切换
        if (wp == WallpaperType.CUSTOM && customWallpaperUri.value.isNullOrEmpty()) return
        selectedWallpaper.value = wp
        prefs.edit().putString("selected_wallpaper", wp.name).apply()
    }

    fun setSoundType(sound: AmbientAudioSynth.SoundType, previewWhenIdle: Boolean = true) {
        // 自定义音乐需要先导入文件，未导入时不允许切换
        if (sound == AmbientAudioSynth.SoundType.CUSTOM && customMusicList.value.isEmpty()) return

        bgSoundType.value = sound
        prefs.edit().putString("selected_sound", sound.name).apply()

        // 切换到合成音/自定义时，清除真实音选择（互斥）
        if (sound != AmbientAudioSynth.SoundType.NONE) {
            realSoundType.value = RealAudioPlayer.RealSound.NONE
            prefs.edit().putString("real_sound", RealAudioPlayer.RealSound.NONE.name).apply()
        }

        // 运行中或沉浸专注态：持续循环播放（含选“无”时停止）；否则空闲预览。
        if (isAudioActive()) {
            playCurrentBackground()
        } else if (previewWhenIdle) {
            previewSoundSelection(sound)
        }
    }

    /**
     * 切换真实MP3背景音（带热加载和预览）
     */
    fun setRealSound(sound: RealAudioPlayer.RealSound, previewWhenIdle: Boolean = true) {
        realSoundType.value = sound
        prefs.edit().putString("real_sound", sound.name).apply()

        // 始终清除合成音选择（互斥）
        bgSoundType.value = AmbientAudioSynth.SoundType.NONE
        prefs.edit().putString("selected_sound", AmbientAudioSynth.SoundType.NONE.name).apply()

        if (isAudioActive()) {
            // 运行中或沉浸专注态：持续循环播放，不做 3 秒预览
            playCurrentBackground()
        } else if (previewWhenIdle && sound != RealAudioPlayer.RealSound.NONE) {
            // 预览真实音3秒
            soundPreviewJob?.cancel()
            soundPreviewJob = null
            AmbientAudioSynth.stop()
            stopCustomMusic()
            RealAudioPlayer.startBackground(context, sound)

            soundPreviewJob = viewModelScope.launch {
                delay(3000)
                if (!isAudioActive()) {
                    RealAudioPlayer.stopBackground()
                }
            }
        } else {
            // 选择了“无背景音”，停止所有播放
            soundPreviewJob?.cancel()
            soundPreviewJob = null
            AmbientAudioSynth.stop()
            stopCustomMusic()
            RealAudioPlayer.stopBackground()
        }
    }

    /**
     * 统一循环切换所有背景音（合成音 + 真实MP3 + 自定义）
     *
     * 构建完整列表：无背景音 → 合成音 → 真实音 → 自定义（如有）
     * 找到当前选中项，切换到下一个。
     */
    fun cycleAllSounds(previewWhenIdle: Boolean = false) {
        // 构建统一列表：每个元素是 Pair<类型标识, 名称>
        // "none” → 无背景音
        // "synth:XXX” → 合成音
        // "real:XXX” → 真实MP3
        // "custom” → 自定义音乐
        val allSounds = mutableListOf<Pair<String, String>>()
        allSounds.add("none" to "无背景音")

        // 合成音（排除 NONE 和 CUSTOM，CUSTOM 单独处理）
        AmbientAudioSynth.SoundType.values()
            .filter { it != AmbientAudioSynth.SoundType.NONE && it != AmbientAudioSynth.SoundType.CUSTOM }
            .forEach { allSounds.add("synth:${it.name}" to it.displayName) }

        // 真实MP3
        RealAudioPlayer.RealSound.values()
            .filter { it != RealAudioPlayer.RealSound.NONE }
            .forEach { allSounds.add("real:${it.name}" to it.displayName) }

        // 自定义音乐（每首作为独立条目，仅当有文件时）
        customMusicList.value.forEachIndexed { i, item ->
            allSounds.add("custom:$i" to item.name)
        }

        // 确定当前选中项的标识
        val currentKey = when {
            realSoundType.value != RealAudioPlayer.RealSound.NONE -> "real:${realSoundType.value.name}"
            bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM -> "custom:${selectedCustomMusicIndex.value}"
            bgSoundType.value != AmbientAudioSynth.SoundType.NONE -> "synth:${bgSoundType.value.name}"
            else -> "none"
        }

        // 找到当前索引，切换到下一个
        val currentIdx = allSounds.indexOfFirst { it.first == currentKey }.takeIf { it >= 0 } ?: 0
        val nextIdx = (currentIdx + 1) % allSounds.size
        val nextKey = allSounds[nextIdx].first

        // 调用对应的切换方法
        when {
            nextKey == "none" -> {
                setRealSound(RealAudioPlayer.RealSound.NONE, previewWhenIdle)
            }
            nextKey.startsWith("custom:") -> {
                val idx = nextKey.removePrefix("custom:").toIntOrNull() ?: 0
                selectedCustomMusicIndex.value = idx
                prefs.edit().putInt("selected_custom_music_index", idx).apply()
                setSoundType(AmbientAudioSynth.SoundType.CUSTOM, previewWhenIdle)
            }
            nextKey.startsWith("synth:") -> {
                val synthName = nextKey.removePrefix("synth:")
                val synth = AmbientAudioSynth.SoundType.valueOf(synthName)
                setSoundType(synth, previewWhenIdle)
            }
            nextKey.startsWith("real:") -> {
                val realName = nextKey.removePrefix("real:")
                val real = RealAudioPlayer.RealSound.valueOf(realName)
                setRealSound(real, previewWhenIdle)
            }
        }
    }

    /**
     * 获取当前背景音的显示名称
     */
    fun getCurrentSoundName(): String {
        return when {
            realSoundType.value != RealAudioPlayer.RealSound.NONE -> realSoundType.value.displayName
            bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM -> {
                val list = customMusicList.value
                val idx = selectedCustomMusicIndex.value
                list.getOrNull(idx)?.name ?: "自定义音乐"
            }
            bgSoundType.value != AmbientAudioSynth.SoundType.NONE -> bgSoundType.value.displayName
            else -> "无背景音"
        }
    }

    /**
     * 音频应持续播放的活跃态：计时运行中，或处于沉浸专注屏。
     * 用于区分「持续循环播放」与「空闲 3 秒预览」。
     */
    private fun isAudioActive(): Boolean = _isRunning.value || immersiveFocusEnabled.value

    /**
     * 按当前选择的背景音（优先级：真实MP3 → 合成音 → 自定义音乐）启动持续循环播放。
     * 供 startTimer() 和「运行中/沉浸态切换背景音」复用，保证选了音即持续有声。
     */
    private fun playCurrentBackground() {
        soundPreviewJob?.cancel()
        soundPreviewJob = null
        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()

        when {
            realSoundType.value != RealAudioPlayer.RealSound.NONE ->
                RealAudioPlayer.startBackground(context, realSoundType.value)
            bgSoundType.value == AmbientAudioSynth.SoundType.CUSTOM ->
                playCustomMusic()
            bgSoundType.value != AmbientAudioSynth.SoundType.NONE ->
                AmbientAudioSynth.start(bgSoundType.value)
        }
    }

    private fun previewSoundSelection(sound: AmbientAudioSynth.SoundType) {        soundPreviewJob?.cancel()
        soundPreviewJob = null
        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()

        if (sound == AmbientAudioSynth.SoundType.NONE) return

        if (sound == AmbientAudioSynth.SoundType.CUSTOM) {
            playCustomMusic()
        } else {
            AmbientAudioSynth.start(sound)
        }

        soundPreviewJob = viewModelScope.launch {
            delay(3000)
            if (!_isRunning.value) {
                AmbientAudioSynth.stop()
                stopCustomMusic()
            }
        }
    }

    // Task Actions
    fun insertTask(
        title: String,
        description: String,
        category: String,
        dueDate: Long?,
        estimatedPoms: Int,
        timerMode: String = "countdown",
        workDurationMin: Int? = null,
        breakDurationMin: Int? = null,
        longBreakDurationMin: Int? = null
    ) {
        viewModelScope.launch {
            var task = TaskEntity(
                title = title,
                description = description,
                category = category,
                dueDate = dueDate,
                estimatedPomodoros = estimatedPoms,
                timerMode = timerMode,
                workDurationMin = workDurationMin,
                breakDurationMin = breakDurationMin,
                longBreakDurationMin = longBreakDurationMin,
                lastUpdated = System.currentTimeMillis()
            )
            task = task.copy(id = repository.insertTask(task).toInt())
            if (dueDate != null) syncTaskToCalendar(task, showStatus = false)
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            var updated = task.copy(lastUpdated = System.currentTimeMillis())
            repository.updateTask(updated)
            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = updated
                if (!_isRunning.value && !_isBreak.value) resetTimer()
            }
            if (updated.dueDate == null) {
                updated.calendarEventId?.let { CalendarSyncHelper.removeCalendarEvent(context, it) }
                if (updated.calendarEventId != null) {
                    updated = updated.copy(calendarEventId = null)
                    repository.updateTask(updated)
                }
            } else {
                syncTaskToCalendar(updated, showStatus = false)
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
                prefs.edit().remove("selected_task_id").apply()
            }
        }
    }

    fun selectTask(task: TaskEntity?) {
        _selectedTask.value = task
        prefs.edit().apply {
            if (task == null) remove("selected_task_id") else putInt("selected_task_id", task.id)
        }.apply()
        // 切换任务时自动应用该任务的计时模式
        if (!_isRunning.value) {
            val isCountUp = task?.timerMode == "countup"
            if (_isCountUpMode.value != isCountUp) {
                setCountUpMode(isCountUp)
            }
            if (!isCountUp && !_isBreak.value) resetTimer()
        }
    }

    /** 切换当前计时模式，并同步更新选中任务的 timerMode */
    fun toggleTimerMode() {
        if (_isRunning.value) return
        val newMode = !isCountUpMode.value
        setCountUpMode(newMode)
        // 如果选中了任务，同步更新任务的计时模式
        val task = _selectedTask.value
        if (task != null) {
            val updated = task.copy(timerMode = if (newMode) "countup" else "countdown")
            viewModelScope.launch {
                repository.updateTask(updated.copy(lastUpdated = System.currentTimeMillis()))
                _selectedTask.value = updated
            }
        }
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

    private suspend fun syncTaskToCalendar(task: TaskEntity, showStatus: Boolean) {
        val dueDate = task.dueDate ?: return
        val titleWithCategory = "[${task.category}] ${task.title}"
        val desc = "${task.description}\n设计番茄量: ${task.estimatedPomodoros} | 已完成番茄量: ${task.completedPomodoros}"
        val eventId = withContext(Dispatchers.IO) {
            CalendarSyncHelper.syncTaskToCalendar(context, titleWithCategory, desc, dueDate, task.calendarEventId)
        }
        if (eventId != null && eventId != task.calendarEventId) {
            val updated = task.copy(calendarEventId = eventId, lastUpdated = System.currentTimeMillis())
            repository.updateTask(updated)
            if (_selectedTask.value?.id == task.id) _selectedTask.value = updated
        }
        if (showStatus) {
            _syncStatus.value = if (eventId != null) "已同步到手机日历（从今天起每日重复）" else "未授权限或日历同步失败"
            delay(3000)
            _syncStatus.value = null
        }
    }

    fun syncTaskToCalendar(task: TaskEntity) {
        viewModelScope.launch {
            if (task.dueDate == null) {
                _syncStatus.value = "该任务没有设定到期日期"
                return@launch
            }
            syncTaskToCalendar(task, showStatus = true)
        }
    }

    fun playCustomMusic() {
        stopCustomMusic()
        val list = customMusicList.value
        val idx = selectedCustomMusicIndex.value
        val uriStr = list.getOrNull(idx)?.uri
        if (uriStr.isNullOrEmpty() || idx >= list.size) {
            _syncStatus.value = "尚未导入任何自定义音频文件"
            viewModelScope.launch {
                delay(3000)
                _syncStatus.value = null
            }
            return
        }
        try {
            // 用 prepareAsync 避免 content:// 慢速 provider 在主线程阻塞导致 ANR。
            val mp = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, android.net.Uri.parse(uriStr))
                isLooping = true
                setOnPreparedListener { player ->
                    // 准备完成时用户可能已切走/停止；仅当它仍是当前播放器时才播放。
                    if (customMediaPlayer === player) {
                        player.start()
                    } else {
                        player.release()
                    }
                }
                setOnErrorListener { player, _, _ ->
                    Log.e("PomodoroViewModel", "MediaPlayer error for: $uriStr")
                    if (customMediaPlayer === player) {
                        customMediaPlayer = null
                    }
                    player.release()
                    _syncStatus.value = "播放自定义音频失败，请在设置中重新选择"
                    viewModelScope.launch {
                        delay(3000)
                        _syncStatus.value = null
                    }
                    true
                }
            }
            customMediaPlayer = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Failed to play custom music file: $uriStr", e)
            stopCustomMusic()
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
                // prepareAsync 期间 isPlaying 为 false 也需 reset，避免非法状态崩溃。
                reset()
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
        soundPreviewJob?.cancel()
        stopCustomMusic()
        RealAudioPlayer.stopAll()
        ToneSynth.stopAll()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignored
        }
        toneGenerator = null
    }

    // 正向计时的起始锚点（elapsedRealtime），0 表示未运行
    private var startElapsedMs: Long = 0L

    // Timer Controls
    fun startTimer() {
        if (_isRunning.value) return
        // 防重入：确保只有一个计时协程，杜绝双计时器并发导致剩余时间被多次扣减。
        timerJob?.cancel()
        _isRunning.value = true

        // 工作模式自动进入沉浸式全屏
        if (!_isBreak.value) {
            immersiveFocusEnabled.value = true
        }

        // 播放背景音：优先真实MP3，其次合成音，最后自定义音乐
        playCurrentBackground()

        // 专注期间播放合成滴答声（仅工作模式）
        if (tickingEnabled.value && !_isBreak.value) {
            ToneSynth.startTicking()
        }

        // Toggle system Do Not Disturb
        setSystemDnd(true)

        if (_isCountUpMode.value && !_isBreak.value) {
            // 正向计时：从 0 开始往上计
            startElapsedMs = SystemClock.elapsedRealtime() - _remainingTimeMs.value
            _totalTimeMs.value = 0L
            prefs.edit()
                .putLong("remaining_time_ms", _remainingTimeMs.value)
                .putLong("focus_start_elapsed", startElapsedMs)
                .apply()

            timerJob = viewModelScope.launch {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startElapsedMs
                    _remainingTimeMs.value = elapsed
                    prefs.edit().putLong("remaining_time_ms", elapsed).apply()
                    delay(250)
                }
            }
        } else {
            // 倒计时模式：墙钟锚定
            endTimeElapsedMs = SystemClock.elapsedRealtime() + _remainingTimeMs.value
            prefs.edit()
                .putLong("remaining_time_ms", _remainingTimeMs.value)
                .putLong("focus_end_elapsed", endTimeElapsedMs)
                .apply()

            timerJob = viewModelScope.launch {
                var lastBeepSec = -1L
                while (true) {
                    val remain = (endTimeElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    _remainingTimeMs.value = remain
                    prefs.edit().putLong("remaining_time_ms", remain).apply()

                    if (remain <= 0L) break

                    // 最后10秒倒计时蜂鸣（仅工作模式，每秒最多一次）
                    val remainingSecs = remain / 1000
                    if (!_isBreak.value && remainingSecs in 1..10 && soundEnabled.value && remainingSecs != lastBeepSec) {
                        lastBeepSec = remainingSecs
                        ToneSynth.playCountdownBeep()
                    }
                    delay(250)
                }
                onTimerComplete()
            }
        }
    }

    fun pauseTimer() {
        _isRunning.value = false
        immersiveFocusEnabled.value = false
        timerJob?.cancel()
        soundPreviewJob?.cancel()
        soundPreviewJob = null

        // 清除墙钟锚点
        endTimeElapsedMs = 0L
        startElapsedMs = 0L
        prefs.edit().remove("focus_end_elapsed").remove("focus_start_elapsed").apply()

        // Stop background sound synthesis
        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()
        ToneSynth.stopTicking()
        ToneSynth.stopCountdown()

        // Deactivate system Do Not Disturb
        setSystemDnd(false)
    }

    /** Resolve the active phase duration, preferring the selected task override. */
    private fun resolvedDurationMinutes(): Int {
        val task = _selectedTask.value
        return when {
            _isLongBreak.value -> task?.longBreakDurationMin ?: longBreakDurationMinutes.value
            _isBreak.value -> task?.breakDurationMin ?: breakDurationMinutes.value
            else -> task?.workDurationMin ?: workDurationMinutes.value
        }
    }

    private fun resolvedWorkDurationMinutes(): Int =
        _selectedTask.value?.workDurationMin ?: workDurationMinutes.value

    fun resetTimer() {
        pauseTimer()
        immersiveFocusEnabled.value = false
        if (_isCountUpMode.value && !_isBreak.value) {
            // 正向计时模式重置为 0
            _remainingTimeMs.value = 0L
            _totalTimeMs.value = 0L
        } else {
            val minutes = resolvedDurationMinutes()
            _remainingTimeMs.value = minutes * 60 * 1000L
            _totalTimeMs.value = minutes * 60 * 1000L
        }
        prefs.edit().putLong("remaining_time_ms", _remainingTimeMs.value).apply()

        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()
        ToneSynth.stopTicking()
        ToneSynth.stopCountdown()
        setSystemDnd(false)
    }

    fun abandonStrictFocusFromGuard() {
        _showResetConfirm.value = false
        prefs.edit().putBoolean("strict_focus_active", false).apply()
        _isBreak.value = false
        _isLongBreak.value = false
        resetTimer()
    }

    /** 统计今日（自午夜起）已完成的工作番茄数，用于长休息判断 */
    private fun countTodayWorkSessions(): Int {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return allLogs.value.count { it.category != "休息" && it.timestamp >= todayStart }
    }

    fun skipSession() {
        pauseTimer()
        if (!_isBreak.value) {
            // 工作中跳过：切换到休息（不记录日志）
            _isBreak.value = true
            val doneToday = countTodayWorkSessions()
            if (doneToday > 0 && doneToday % 4 == 0) {
                _isLongBreak.value = true
            }
        } else {
            _isBreak.value = false
            _isLongBreak.value = false
        }
        resetTimer()
    }

    /**
     * 提前结束当前工作计时：记录实际已专注时长，切换到休息。
     * 倒计时模式：实际时长 = 总时长 - 剩余时长
     * 正向计时模式：实际时长 = 已计时长
     */
    fun earlyFinishSession() {
        if (_isBreak.value) return // 休息中不允许提前结束

        val elapsedMs = if (_isCountUpMode.value) {
            _remainingTimeMs.value
        } else {
            _totalTimeMs.value - _remainingTimeMs.value
        }
        if (elapsedMs <= 0L) {
            _syncStatus.value = "尚未开始专注，不会记录番茄"
            viewModelScope.launch {
                delay(2500)
                _syncStatus.value = null
            }
            return
        }
        val elapsedMinutes = ((elapsedMs + 59999L) / 60000L).toInt()

        pauseTimer()

        // 播放合成完成提示音
        if (soundEnabled.value) {
            ToneSynth.playCompletionSound()
        }

        // 记录日志（使用实际时长）
        val currentTask = _selectedTask.value
        val category = currentTask?.category ?: "工作"
        val taskTitle = currentTask?.title ?: "自由专注"

        val log = PomodoroLogEntity(
            taskId = currentTask?.id,
            taskTitle = taskTitle,
            category = category,
            durationMinutes = elapsedMinutes
        )
        viewModelScope.launch {
            repository.insertLog(log)

            // 更新任务完成数
            if (currentTask != null) {
                val updatedPomCount = currentTask.completedPomodoros + 1
                repository.updatePomodoroCount(currentTask.id, updatedPomCount)
                val fresh = repository.getTaskById(currentTask.id)
                if (fresh != null) {
                    _selectedTask.value = fresh
                }
            }

            // 切换到休息
            _isBreak.value = true
            // +1 因为刚插入的日志可能还未反映到 allLogs 中（Flow 异步发射）
            val doneToday = countTodayWorkSessions() + 1
            if (doneToday > 0 && doneToday % 4 == 0) {
                _isLongBreak.value = true
            }
            resetTimer()
            startTimer()
        }
    }

    private suspend fun onTimerComplete() {
        _isRunning.value = false
        triggerAlertNotification()

        AmbientAudioSynth.stop()
        stopCustomMusic()
        RealAudioPlayer.stopBackground()
        ToneSynth.stopTicking()
        ToneSynth.stopCountdown()
        setSystemDnd(false)

        if (!_isBreak.value) {
            // 工作完成！播放合成完成提示音
            if (soundEnabled.value) {
                ToneSynth.playCompletionSound()
            }

            // 1. Record Log - 使用实际专注时长
            val currentTask = _selectedTask.value
            val category = currentTask?.category ?: "工作"
            val taskTitle = currentTask?.title ?: "自由专注"

            // 倒计时完成用当前生效时长（任务自定义优先，回退全局），正向计时不走此路径（正向无自动完成）
            val actualMinutes = if (_isCountUpMode.value) {
                (_remainingTimeMs.value / 60000L).toInt().coerceAtLeast(1)
            } else {
                resolvedWorkDurationMinutes()
            }

            val log = PomodoroLogEntity(
                taskId = currentTask?.id,
                taskTitle = taskTitle,
                category = category,
                durationMinutes = actualMinutes
            )
            repository.insertLog(log)

            // 2. Increment active task's completed counter
            if (currentTask != null) {
                val updatedPomCount = currentTask.completedPomodoros + 1
                repository.updatePomodoroCount(currentTask.id, updatedPomCount)
                val fresh = repository.getTaskById(currentTask.id)
                if (fresh != null) {
                    _selectedTask.value = fresh
                }
            }

            // 3. 自动切换到休息（双计时器设计）
            _isBreak.value = true
            // +1 因为刚插入的日志可能还未反映到 allLogs 中（Flow 异步发射）
            val doneToday = countTodayWorkSessions() + 1
            if (doneToday > 0 && doneToday % 4 == 0) {
                _isLongBreak.value = true
            }

            // 4. 自动开始休息计时
            resetTimer()
            startTimer()
        } else {
            // 休息完成！播放合成完成提示音，切回工作
            if (soundEnabled.value) {
                ToneSynth.playCompletionSound()
            }

            _isBreak.value = false
            _isLongBreak.value = false
            resetTimer()
            // 不自动开始工作，等用户手动点击
        }
    }

    private fun triggerAlertNotification() {
        // Sound and vibration feedback
        if (soundEnabled.value) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                }
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 1200) // Vibrate beep sound
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to trigger ToneGenerator sound", e)
                toneGenerator = null
            }
        }

        if (vibrationEnabled.value) {
            try {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                
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

    fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun isStrictFocusActive(): Boolean {
        return strictModeEnabled.value && _isRunning.value && !_isBreak.value
    }

    // 检查无障碍服务是否已启用
    fun isAccessibilityEnabled(): Boolean {
        return com.yangshizi.todo.service.LockAccessibilityService.isAccessibilityEnabled(context)
    }

    // 检查通知监听服务是否已授权
    fun isNotificationListenerEnabled(): Boolean {
        return com.yangshizi.todo.service.NotificationBlockService.isNotificationListenerEnabled(context)
    }

    // 获取今日剩余退出次数
    fun getRemainingExits(): Int {
        return com.yangshizi.todo.service.FocusTimerService.getRemainingExits(context)
    }

    private fun setSystemDnd(enabled: Boolean) {
        if (!dndEnabled.value) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    val targetFilter = if (enabled) {
                        // 用 PRIORITY 而非 NONE：NONE 在多数机型会连媒体音/闹钟一起静音，
                        // 导致专注背景音乐被掐断；PRIORITY 拦截普通通知但放行媒体播放。
                        android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
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
                // 先停掉可能残留的警报音，避免 TONE_CDMA_HIGH_L 循环音型叠加长响
                runCatching { toneGenerator?.stopTone() }
                toneGenerator?.startTone(android.media.ToneGenerator.TONE_CDMA_HIGH_L, 1000)
            } catch (e: Exception) {
                Log.e("PomodoroViewModel", "Failed to play strict warning tone", e)
                toneGenerator = null
            }
        }
        if (vibrationEnabled.value) {
            try {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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

    /** 立即停止严格模式警报音（回到前台时调用，防止长响不止） */
    fun stopStrictWarningAlarm() {
        runCatching { toneGenerator?.stopTone() }
        runCatching {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.cancel()
        }
    }

    // Cross-platform Backup / Sync Functions
    fun copyBackupString(): String {
        return BackupHelper.serializeData(allTasks.value, allLogs.value)
    }

    fun restoreFromJson(jsonString: String): Boolean {
        val parsed = BackupHelper.deserializeData(jsonString) ?: return false
        // 防呆：解析成功但任务与日志都为空，视为无效备份，不清空现有数据。
        if (parsed.first.isEmpty() && parsed.second.isEmpty()) return false
        viewModelScope.launch {
            // 原子化恢复：清空+写入在单事务内完成，失败整体回滚，避免丢光旧数据。
            repository.replaceAll(parsed.first, parsed.second)
            val selectedId = prefs.getInt("selected_task_id", 0)
            if (selectedId > 0 && parsed.first.none { it.id == selectedId }) {
                prefs.edit().remove("selected_task_id").apply()
                _selectedTask.value = null
            }
        }
        return true
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
