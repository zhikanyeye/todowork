package com.yangshizi.todo.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toBitmap
import com.yangshizi.todo.service.WhitelistManager
import com.yangshizi.todo.ui.theme.surfaceCardVariant
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: PomodoroViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val workMins by viewModel.workDurationMinutes.collectAsStateWithLifecycle()
    val breakMins by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()
    val longBreakMins by viewModel.longBreakDurationMinutes.collectAsStateWithLifecycle()

    var activeEditingDurationType by remember { mutableStateOf<String?>(null) } // "work", "break", "long_break"
    var durationTextInput by remember { mutableStateOf("") }

    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()

    val backupCode by viewModel.backupCode.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val customWallpaperUri by viewModel.customWallpaperUri.collectAsStateWithLifecycle()
    val customWallpaperList by viewModel.customWallpaperList.collectAsStateWithLifecycle()
    val selectedCustomWallpaperIndex by viewModel.selectedCustomWallpaperIndex.collectAsStateWithLifecycle()
    val customMusicList by viewModel.customMusicList.collectAsStateWithLifecycle()
    val selectedCustomMusicIndex by viewModel.selectedCustomMusicIndex.collectAsStateWithLifecycle()

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            var displayName = "自定义壁纸"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIdx)
                }
            }
            viewModel.addCustomWallpaper(it.toString(), displayName)
            Toast.makeText(context, "自定义壁纸 [ $displayName ] 已导入", Toast.LENGTH_SHORT).show()
        }
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            
            var displayName = "自定义背景音乐"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIdx)
                }
            }
            
            viewModel.addCustomMusic(it.toString(), displayName)
            Toast.makeText(context, "自定义音乐 [ $displayName ] 导入成功！", Toast.LENGTH_SHORT).show()
        }
    }

    var manualJsonRestoreText by remember { mutableStateOf("") }
    var restoreCodeInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "专注设置",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "工作周期调优与跨平台云同步",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Timer durations configuration Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "工作节律配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                DurationSettingItem(
                    title = "工作周期",
                    helperText = "点击卡片可手动输入分钟数",
                    minutes = workMins,
                    accentColor = MaterialTheme.colorScheme.primary,
                    valueRange = 5f..60f,
                    steps = 11,
                    testTag = "work_duration_slider",
                    onEdit = {
                        durationTextInput = workMins.toString()
                        activeEditingDurationType = "work"
                    },
                    onSliderChange = { viewModel.workDurationMinutes.value = it.roundToInt() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DurationSettingItem(
                    title = "短休息周期",
                    helperText = "完成一轮专注后的短暂恢复",
                    minutes = breakMins,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    valueRange = 1f..20f,
                    steps = 19,
                    testTag = "break_duration_slider",
                    onEdit = {
                        durationTextInput = breakMins.toString()
                        activeEditingDurationType = "break"
                    },
                    onSliderChange = { viewModel.breakDurationMinutes.value = it.roundToInt() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                DurationSettingItem(
                    title = "长休息周期",
                    helperText = "连续专注后的深度恢复时长",
                    minutes = longBreakMins,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    valueRange = 5f..40f,
                    steps = 7,
                    testTag = "long_break_duration_slider",
                    onEdit = {
                        durationTextInput = longBreakMins.toString()
                        activeEditingDurationType = "long_break"
                    },
                    onSliderChange = { viewModel.longBreakDurationMinutes.value = it.roundToInt() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Alert Feedbacks Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "通知与提醒反馈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Sound Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "铃声震鸣", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "专注到期时播放高頻CDMA警标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { viewModel.soundEnabled.value = it },
                        modifier = Modifier.testTag("sound_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Vibration Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "触感反馈", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "专注到期时触发三次复合周期震感", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { viewModel.vibrationEnabled.value = it },
                        modifier = Modifier.testTag("vibration_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ambient Sound, DND and App Locker Configuration Card ("沉浸式专注增强")
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎨 专注氛围",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "专注时的动态壁纸与自定义背景，营造沉浸环境",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 0: Ambient focus wallpaper preset
                Text(
                    text = "🎨 艺术氛围心流壁纸",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "在番茄专注时充当背景屏，消除繁杂视觉噪音，提供优雅的环境氛围图层",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val currentWallpaper by viewModel.selectedWallpaper.collectAsStateWithLifecycle()
                val wallpaperScrollState = rememberScrollState()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(wallpaperScrollState)
                        .padding(vertical = 4.dp)
                ) {
                    com.yangshizi.todo.ui.viewmodel.PomodoroViewModel.WallpaperType.values().forEach { wp ->
                        val isWpSelected = currentWallpaper == wp
                        val isWpDisabled = wp == com.yangshizi.todo.ui.viewmodel.PomodoroViewModel.WallpaperType.CUSTOM &&
                            customWallpaperUri.isNullOrEmpty()
                        FilterChip(
                            selected = isWpSelected,
                            onClick = { viewModel.setWallpaper(wp) },
                            enabled = !isWpDisabled,
                            label = { Text(wp.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义壁纸",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "已导入 ${customWallpaperList.size}/5 张",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                try {
                                    wallpaperPickerLauncher.launch(arrayOf("image/*"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "打开系统相册失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = customWallpaperList.size < 5,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("上传本地图片", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (customWallpaperList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        customWallpaperList.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (idx == selectedCustomWallpaperIndex)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        else
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    )
                                    .clickable {
                                        viewModel.selectCustomWallpaper(idx)
                                        viewModel.setWallpaper(PomodoroViewModel.WallpaperType.CUSTOM)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (idx == selectedCustomWallpaperIndex)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.name,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (idx == selectedCustomWallpaperIndex)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeCustomWallpaper(idx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除壁纸",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点击上传后会自动切换到自定义壁纸",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 声音环境：背景音与提示音
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            val bgSound by viewModel.bgSoundType.collectAsStateWithLifecycle()

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎵 声音环境",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "切换背景音会自动试听 3 秒，开始专注后循环播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: 统一背景音选择
                Text(
                    text = "🎵 专注背景音",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "合成音零体积无限循环，真实录音高保真更自然，按需选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val realSound by viewModel.realSoundType.collectAsStateWithLifecycle()
                val currentSelection = if (realSound != com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NONE) {
                    "real:${realSound.name}"
                } else {
                    "synth:${bgSound.name}"
                }

                // 无背景音选项（共享）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setRealSound(com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NONE, previewWhenIdle = false)
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSelection == "synth:NONE",
                        onClick = {
                            viewModel.setRealSound(com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NONE, previewWhenIdle = false)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "无背景音",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (currentSelection == "synth:NONE") FontWeight.Bold else FontWeight.Normal,
                        color = if (currentSelection == "synth:NONE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "━━ 合成音 (实时生成，零体积) ━━",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.values().filter { it != com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.NONE }.forEach { sound ->
                    val isSelected = currentSelection == "synth:${sound.name}"
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSoundType(sound)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setSoundType(sound)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                val desc = when (sound) {
                                    com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.WHITE_NOISE -> "全频段均匀遮蔽，有效拦截环境嘈杂"
                                    com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.BROWN_NOISE -> "低频厚重深色噪音，削弱环境人声和尖锐杂音"
                                    com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.FOCUS_PAD -> "实时合成的原创和弦与轻微琶音，可听旋律性氛围"
                                    com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.CUSTOM -> if (customMusicList.isEmpty()) "请先添加本地音频文件" else "已导入 ${customMusicList.size} 首自定义音乐"
                                    else -> ""
                                }
                                if (desc.isNotEmpty()) {
                                    Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (sound == com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.CUSTOM) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 4.dp, bottom = 8.dp)
                            ) {
                                // 已导入的音乐列表
                                customMusicList.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (idx == selectedCustomMusicIndex)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                else
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .clickable { viewModel.selectCustomMusic(idx) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.AudioFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (idx == selectedCustomMusicIndex)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item.name,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (idx == selectedCustomMusicIndex)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeCustomMusic(idx) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // 添加按钮
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (customMusicList.isEmpty()) "暂未添加音频文件（上限 10 首）" else "共 ${customMusicList.size} 首 / 上限 10 首",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    TextButton(
                                        onClick = {
                                            try {
                                                musicPickerLauncher.launch(arrayOf("audio/*"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "打开文件选择器失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("添加本地音频", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "━━ 真实录音 (高保真MP3) ━━",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.values().filter { it != com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NONE }.forEach { sound ->
                    val isSelected = currentSelection == "real:${sound.name}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setRealSound(sound)
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                viewModel.setRealSound(sound)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = sound.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            val realDesc = when (sound) {
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.BONFIRE -> "火焰燃烧的温暖底噪"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.OCEAN_WAVES -> "海浪随潮汐起伏"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.FOREST_STREAM -> "清澈溪流穿过林间"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.GENTLE_RAIN -> "细雨敲打窗台"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NATURE_WIND -> "自然风穿过树叶"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.BIRDS_GARDEN -> "鸟鸣与花香交织"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.MORNING_COFFEE -> "咖啡馆清晨氛围"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.PAGE_TURNING -> "翻动书页的沙沙声"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.WIND_CHIME -> "风铃清脆的声"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.PIANO -> "治愈系轻柔钢琴"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.MUSIC_BOX -> "童话般的八音盒旋律"
                                com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.PURE_MUSIC -> "纯净轻音乐氛围"
                                else -> ""
                            }
                            if (realDesc.isNotEmpty()) {
                                Text(text = realDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Section 1.6: 合成滴答声 + 完成提示音
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "⏰ 专注滴答声 (合成)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(text = "工作时每秒合成滴答提示，增强时间感", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val ticking by viewModel.tickingEnabled.collectAsStateWithLifecycle()
                    Switch(
                        checked = ticking,
                        onCheckedChange = { viewModel.tickingEnabled.value = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = "🔔 提示音已自合成", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "最后10秒滴滴倒计时 + 完成叮咚声，无需下载铃声文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 专注模式：免打扰、严格锁定与通知拦截
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            val dndActive by viewModel.dndEnabled.collectAsStateWithLifecycle()
            val strictActive by viewModel.strictModeEnabled.collectAsStateWithLifecycle()

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔒 专注模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "免打扰、严格锁机与通知拦截，越界即被拉回",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "📵 开启系统免打扰 (DND)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "开启时启动系统全静音过滤，退出重置时自动复原，给你无干扰的幽闭环境",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dndActive,
                        onCheckedChange = { viewModel.dndEnabled.value = it },
                        modifier = Modifier.testTag("dnd_switch")
                    )
                }

                if (dndActive && !viewModel.isDndPermissionGranted()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "跳转设置失败，请手动在设置中开启免打扰读取权限", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("点击授予系统『勿扰模式』屏蔽通知权限", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: App Blocking Switch (Strict limit locker)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "🔒 严格锁定阻栏模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "开启后，专注中切出应用会触发警鸣、悬浮遮挡页并自动拉回专注页。建议同时授予使用情况和悬浮窗权限，效果接近番茄 ToDo 锁机。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictActive,
                        onCheckedChange = { viewModel.strictModeEnabled.value = it },
                        modifier = Modifier.testTag("strict_mode_switch")
                    )
                }

                if (strictActive && !viewModel.isUsageAccessGranted()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "跳转设置失败，请手动在设置中开启使用情况访问权限", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("点击授予系统『使用情况访问』权限", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (strictActive && !viewModel.isOverlayPermissionGranted()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "跳转设置失败，请手动在设置中开启悬浮窗/画中画权限", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("点击授予系统『悬浮窗 / 显示在其他应用上层』权限", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // 无障碍服务授权引导
                if (strictActive && !viewModel.isAccessibilityEnabled()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                                Toast.makeText(context, "请在列表中找到「洋柿子ToDo专注锁」并开启", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "跳转失败，请手动在设置 > 无障碍中开启", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔑 点击授予『无障碍服务』(第二层拦截防护)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (strictActive && viewModel.isAccessibilityEnabled()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✅ 无障碍服务已启用，第二层拦截防护已生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 白名单管理
                if (strictActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val whitelistManager = remember { com.yangshizi.todo.service.WhitelistManager.getInstance(context) }
                    var showWhitelistDialog by remember { mutableStateOf(false) }
                    var whitelistCount by remember { mutableStateOf(whitelistManager.getUserWhitelistPackages().size) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showWhitelistDialog = true }
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📱 白名单应用管理",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "设置专注期间允许使用的应用（当前 $whitelistCount 个）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "进入白名单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showWhitelistDialog) {
                        WhitelistAppDialog(
                            whitelistManager = whitelistManager,
                            onDismiss = {
                                whitelistCount = whitelistManager.getUserWhitelistPackages().size
                                showWhitelistDialog = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // 通知拦截开关
                val notifBlockActive by viewModel.notificationBlockEnabled.collectAsStateWithLifecycle()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "🔕 通知拦截 (严格模式增强)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "专注期间自动拦截其他App的通知推送，避免分心。需授予通知访问权限。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notifBlockActive,
                        onCheckedChange = { viewModel.notificationBlockEnabled.value = it },
                        modifier = Modifier.testTag("notif_block_switch")
                    )
                }

                if (notifBlockActive && !viewModel.isNotificationListenerEnabled()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            com.yangshizi.todo.service.NotificationBlockService.openNotificationListenerSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("点击授予『通知访问权限』", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📊 今日剩余退出次数: ${viewModel.getRemainingExits()}/3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloud Backup terminal Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "☁️ 实时跨平台数据备份 & 恢复",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "支持通过云端密钥瞬间将清单和历史同步至其他设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons for Cloud Backup
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.uploadBackup() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloud_backup_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("上传云端备份", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val backupString = viewModel.copyBackupString()
                            clipboardManager.setText(AnnotatedString(backupString))
                            Toast.makeText(context, "备份代码已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_backup_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("拷贝备份文本", fontSize = 12.sp)
                        }
                    }
                }

                // Show generated backup code for instant cross-platform restoration
                if (backupCode != null || syncStatus != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "【云状态反馈】",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = syncStatus ?: "备份生成成功！密钥如下：",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (backupCode != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = backupCode ?: "",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp
                                    )
                                    IconButton(
                                        onClick = {
                                            backupCode?.let {
                                                clipboardManager.setText(AnnotatedString(it))
                                                Toast.makeText(context, "云密钥已复制！", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制密钥", modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = "* 请保存该密钥，并在其他设备的恢复框中粘贴。",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                // Cloud restore box picker
                Text("从云密钥恢复", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = restoreCodeInput,
                        onValueChange = { restoreCodeInput = it },
                        placeholder = { Text("例: 6a8276f2...") },
                        maxLines = 1,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("restore_code_input"),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Button(
                        onClick = {
                            if (restoreCodeInput.isNotBlank()) {
                                viewModel.restoreFromBackupCode(restoreCodeInput.trim())
                                restoreCodeInput = ""
                            }
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("restore_code_button")
                    ) {
                        Text("一键拉取", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Local text configuration restorer
                Text("从 JSON 文本本地快速恢复", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualJsonRestoreText,
                    onValueChange = { manualJsonRestoreText = it },
                    placeholder = { Text("请在此粘贴导出的备份 JSON 字符段...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("manual_json_input"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (manualJsonRestoreText.isNotBlank()) {
                            val success = viewModel.restoreFromJson(manualJsonRestoreText.trim())
                            if (success) {
                                Toast.makeText(context, "本地 JSON 恢复成功！", Toast.LENGTH_SHORT).show()
                                manualJsonRestoreText = ""
                            } else {
                                Toast.makeText(context, "备份文本格式不正确，校验失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_restore_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Text("校验并导入本地备份", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }

    activeEditingDurationType?.let { type ->
        val titleText = when (type) {
            "work" -> "手动设置：工作周期时长"
            "break" -> "手动设置：短休息周期时长"
            "long_break" -> "手动设置：长休息周期时长"
            else -> ""
        }
        val labelText = "输入分钟数 (大于 0)"
        
        AlertDialog(
            onDismissRequest = { activeEditingDurationType = null },
            title = { Text(text = titleText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        text = "您可以在此输入任意分钟时长（不受下方滑动条范围限制），输入后将即时生效并自动云同步：", 
                        fontSize = 13.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = durationTextInput,
                        onValueChange = { input ->
                            durationTextInput = input.filter { it.isDigit() }
                        },
                        label = { Text(labelText) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_duration_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedVal = durationTextInput.trim().toIntOrNull()
                        if (parsedVal != null && parsedVal > 0) {
                            when (type) {
                                "work" -> viewModel.workDurationMinutes.value = parsedVal
                                "break" -> viewModel.breakDurationMinutes.value = parsedVal
                                "long_break" -> viewModel.longBreakDurationMinutes.value = parsedVal
                            }
                            activeEditingDurationType = null
                        } else {
                            Toast.makeText(context, "请输入大于 0 的有效整数分钟！", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeEditingDurationType = null }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DurationSettingItem(
    title: String,
    helperText: String,
    minutes: Int,
    accentColor: Color,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    onEdit: () -> Unit,
    onSliderChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.14f),
                contentColor = accentColor
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .widthIn(min = 82.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$minutes 分钟",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "手动输入$title",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Slider(
            value = minutes.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onSliderChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}

/**
 * 白名单应用管理对话框
 *
 * 学习竞品 WhiteListActivity 的交互模式：
 * - 展示已安装应用列表
 * - 搜索过滤
 * - 勾选/取消勾选白名单状态
 */
@Composable
fun WhitelistAppDialog(
    whitelistManager: WhitelistManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<WhitelistManager.AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var whitelistedSet by remember { mutableStateOf(whitelistManager.getUserWhitelistPackages()) }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        whitelistManager.loadInstalledApps { apps ->
            installedApps = apps
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("白名单应用管理", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("搜索应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 显示系统应用开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "显示系统应用",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filteredApps = installedApps.filter { app ->
                        (showSystemApps || !app.isSystemApp) &&
                            (searchText.isEmpty() ||
                             app.appName.contains(searchText, ignoreCase = true) ||
                             app.packageName.contains(searchText, ignoreCase = true))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApps) { app ->
                            val isWhitelisted = app.packageName in whitelistedSet
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isWhitelisted) {
                                            whitelistManager.removeFromWhitelist(app.packageName)
                                        } else {
                                            whitelistManager.addToWhitelist(app.appName, app.packageName)
                                        }
                                        whitelistedSet = whitelistManager.getUserWhitelistPackages()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Checkbox(
                                    checked = isWhitelisted,
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val appIcon = remember(app.packageName) {
                                    kotlin.runCatching {
                                        context.packageManager.getApplicationIcon(app.packageName)
                                    }.getOrNull()
                                }
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon.toBitmap(40).asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (app.isSystemApp) {
                                    Text(
                                        text = "系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (filteredApps.isEmpty()) {
                        Text(
                            text = "没有找到匹配的应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}
