package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.viewmodel.PomodoroViewModel
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
    val customMusicName by viewModel.customMusicName.collectAsStateWithLifecycle()

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
            viewModel.saveCustomWallpaper(it.toString())
            viewModel.setWallpaper(PomodoroViewModel.WallpaperType.CUSTOM)
            Toast.makeText(context, "自定义壁纸已成功导入！", Toast.LENGTH_SHORT).show()
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
            
            viewModel.saveCustomMusic(it.toString(), displayName)
            viewModel.setSoundType(com.example.data.audio.AmbientAudioSynth.SoundType.CUSTOM)
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

                // Work Duration Config Section with Edit Option
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                durationTextInput = workMins.toString()
                                activeEditingDurationType = "work"
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Text(text = "工作周期时长 (点击手动修改)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$workMins 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "手动输入工作周期",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Slider(
                        value = workMins.toFloat().coerceIn(5f, 60f),
                        onValueChange = { viewModel.workDurationMinutes.value = it.roundToInt() },
                        valueRange = 5f..60f,
                        steps = 11,
                        modifier = Modifier.testTag("work_duration_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Break Duration Config Section with Edit Option
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                durationTextInput = breakMins.toString()
                                activeEditingDurationType = "break"
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Text(text = "短休息周期时长 (点击手动修改)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$breakMins 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "手动输入短休息周期",
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Slider(
                        value = breakMins.toFloat().coerceIn(1f, 20f),
                        onValueChange = { viewModel.breakDurationMinutes.value = it.roundToInt() },
                        valueRange = 1f..20f,
                        steps = 19,
                        modifier = Modifier.testTag("break_duration_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Long Break Duration Config Section with Edit Option
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                durationTextInput = longBreakMins.toString()
                                activeEditingDurationType = "long_break"
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    ) {
                        Text(text = "长休息周期时长 (点击手动修改)", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$longBreakMins 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "手动输入长休息周期",
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Slider(
                        value = longBreakMins.toFloat().coerceIn(5f, 40f),
                        onValueChange = { viewModel.longBreakDurationMinutes.value = it.roundToInt() },
                        valueRange = 5f..40f,
                        steps = 7,
                        modifier = Modifier.testTag("long_break_duration_slider")
                    )
                }
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
            val bgSound by viewModel.bgSoundType.collectAsStateWithLifecycle()
            val dndActive by viewModel.dndEnabled.collectAsStateWithLifecycle()
            val strictActive by viewModel.strictModeEnabled.collectAsStateWithLifecycle()

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🏆 真实心流番茄体验 (沉浸强化)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "自定义环境白噪音、免打扰自动静音及应用锁定限流",
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
                    com.example.ui.viewmodel.PomodoroViewModel.WallpaperType.values().forEach { wp ->
                        val isWpSelected = currentWallpaper == wp
                        FilterChip(
                            selected = isWpSelected,
                            onClick = { viewModel.setWallpaper(wp) },
                            label = { Text(wp.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (currentWallpaper == com.example.ui.viewmodel.PomodoroViewModel.WallpaperType.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义壁纸源",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (!customWallpaperUri.isNullOrEmpty()) "已导入图片：${customWallpaperUri?.takeLast(35)}..." else "暂未选择，显示渐变色默认底图",
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
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("上传本地图片", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Background noises Selection
                Text(
                    text = "🌲 环境背景白噪音",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                com.example.data.audio.AmbientAudioSynth.SoundType.values().forEach { sound ->
                    val isSelected = bgSound == sound
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setSoundType(sound) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setSoundType(sound) }
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
                                    com.example.data.audio.AmbientAudioSynth.SoundType.NONE -> "安静沉思，无杂音打扰"
                                    com.example.data.audio.AmbientAudioSynth.SoundType.WHITE_NOISE -> "低频软灰音，有效拦截高频耳部嘈杂"
                                    com.example.data.audio.AmbientAudioSynth.SoundType.RAIN -> "细雨拍打小木屋，舒缓大脑焦虑思绪"
                                    com.example.data.audio.AmbientAudioSynth.SoundType.OCEAN -> "心流海浪随呼吸规律潮汐波动，增强心肺协同"
                                    com.example.data.audio.AmbientAudioSynth.SoundType.SPACE_DRONE -> "低声部合成正弦波，开启全脑深港意识流"
                                    com.example.data.audio.AmbientAudioSynth.SoundType.CUSTOM -> "播放导入的本地 MP3/WAV 专注曲目，无限心流循环"
                                }
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (sound == com.example.data.audio.AmbientAudioSynth.SoundType.CUSTOM && isSelected) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 4.dp, bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (!customMusicName.isNullOrEmpty()) "已导入音频：$customMusicName" else "暂未选择音频文件，播放静音",
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
                                    Text("选择本地音频", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: DND Shield Toggle
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
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
                            text = "开启后，中途若尝试切出应用（如看微信、合影），番茄钟会播放高频警鸣并自动强制弹回手机最前端，极速封印退缩意志！",
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
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
