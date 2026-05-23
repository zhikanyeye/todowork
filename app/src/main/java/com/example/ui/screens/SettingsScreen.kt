package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsStateWithLifecycle()

    val backupCode by viewModel.backupCode.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

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

                // Work Duration Slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "工作周期时长", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$workMins 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = workMins.toFloat(),
                        onValueChange = { viewModel.workDurationMinutes.value = it.roundToInt() },
                        valueRange = 5f..60f,
                        steps = 11,
                        modifier = Modifier.testTag("work_duration_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Break Duration Slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "短休息周期时长", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$breakMins 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = breakMins.toFloat(),
                        onValueChange = { viewModel.breakDurationMinutes.value = it.roundToInt() },
                        valueRange = 1f..20f,
                        steps = 19,
                        modifier = Modifier.testTag("break_duration_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Long Break Duration
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "长休息周期时长", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "$longBreakMins 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Slider(
                        value = longBreakMins.toFloat(),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.bgSoundType.value = sound }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.bgSoundType.value = sound }
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
                            }
                            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}
