package com.yangshizi.todo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.yangshizi.todo.data.audio.AmbientAudioSynth
import com.yangshizi.todo.data.database.PomodoroLogEntity
import com.yangshizi.todo.data.database.TaskEntity
import com.yangshizi.todo.ui.theme.CountUpBlue
import com.yangshizi.todo.ui.theme.CountdownPurple
import com.yangshizi.todo.ui.theme.TomatoRed
import com.yangshizi.todo.ui.theme.surfaceCardVariant
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel.WallpaperType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isBreak by viewModel.isBreak.collectAsStateWithLifecycle()
    val isLongBreak by viewModel.isLongBreak.collectAsStateWithLifecycle()
    val remainingTimeMs by viewModel.remainingTimeMs.collectAsStateWithLifecycle()
    val totalTimeMs by viewModel.totalTimeMs.collectAsStateWithLifecycle()

    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()

    val selectedWallpaper by viewModel.selectedWallpaper.collectAsStateWithLifecycle()
    val immersiveFocusEnabled by viewModel.immersiveFocusEnabled.collectAsStateWithLifecycle()
    val bgSoundType by viewModel.bgSoundType.collectAsStateWithLifecycle()
    val realSoundType by viewModel.realSoundType.collectAsStateWithLifecycle()
    val dndActiveActive by viewModel.dndEnabled.collectAsStateWithLifecycle()
    val strictActiveActive by viewModel.strictModeEnabled.collectAsStateWithLifecycle()
    val customWallpaperUri by viewModel.customWallpaperUri.collectAsStateWithLifecycle()
    val customMusicList by viewModel.customMusicList.collectAsStateWithLifecycle()
    val showResetConfirm by viewModel.showResetConfirm.collectAsStateWithLifecycle()
    val isCountUpMode by viewModel.isCountUpMode.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val scrollState = rememberScrollState()

    var showTaskSelector by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var immersiveControlsVisible by remember { mutableStateOf(false) }
    var showWhitelistLauncher by remember { mutableStateOf(false) }

    LaunchedEffect(immersiveFocusEnabled) {
        if (immersiveFocusEnabled) {
            immersiveControlsVisible = false
        }
    }

    // Format Remaining Time to MM:SS or H:MM:SS (正向计时超1小时自动切换)
    val formattedTime = remember(remainingTimeMs, isCountUpMode) {
        val totalSecs = remainingTimeMs / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        if (isCountUpMode && hours > 0) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    // Circular progress fraction
    val progressFraction = remember(remainingTimeMs, totalTimeMs, isCountUpMode) {
        if (isCountUpMode) {
            // 正向计时：无上限，圆环每分钟循环一次作为视觉脉冲
            ((remainingTimeMs % 60000L) / 60000f)
        } else if (totalTimeMs > 0) {
            remainingTimeMs.toFloat() / totalTimeMs.toFloat()
        } else {
            1f
        }
    }

    val primaryColor = if (isBreak) MaterialTheme.colorScheme.secondary else TomatoRed // 番茄红 for Work
    val progressColor by animateColorAsState(targetValue = primaryColor, label = "progressColor")
    val immersiveWallpaperChoices = remember(customWallpaperUri) {
        PomodoroViewModel.WallpaperType.values()
            .filter { it != PomodoroViewModel.WallpaperType.CUSTOM || !customWallpaperUri.isNullOrEmpty() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Procedural artistic wallpaper background
        FocusWallpaper(
            wallpaperType = selectedWallpaper,
            customWallpaperUri = customWallpaperUri
        )

        // 2. Immersive Focus Overlay when enabled
        if (immersiveFocusEnabled) {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.46f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            immersiveControlsVisible = !immersiveControlsVisible
                        }
                ) {
                    // 当前时钟（顶部）
                    val currentTime = remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        while (true) {
                            currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            delay(1000)
                        }
                    }
                    Text(
                        text = currentTime.value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                    )

                    // 中心区域：进度环 + 倍计时钟
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 圆形进度环背景
                        Canvas(
                            modifier = Modifier.size(280.dp)
                        ) {
                            val strokePx = 6.dp.toPx()
                            val diameter = size.minDimension
                            val arcRadius = diameter / 2f - strokePx / 2f
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = arcRadius,
                                center = center,
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                color = progressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * (1f - progressFraction),
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    center.x - arcRadius,
                                    center.y - arcRadius
                                ),
                                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                        // 倍计时钟
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 72.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.sp
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (isBreak) {
                                Text(
                                    text = if (isLongBreak) "深度休息中" else "短暂休息中",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            } else {
                                Text(
                                    text = selectedTask?.title ?: "自由专注",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .widthIn(max = 200.dp)
                                )
                            }
                        }
                    }

                    // 励志格言（底部）
                    val mottos = remember {
                        listOf(
                            "保持心流，世界尽在掌握",
                            "每一步都在靠近目标",
                            "此刻专注，未来可期",
                            "心无旁貣，方能远行",
                            "静水流深，厚积薄发",
                            "不积跬步，无以至千里",
                            "专注当下，成就非凡",
                            "心流所至，事半功倍"
                        )
                    }
                    val mottoIndex = remember {
                        mutableStateOf((System.currentTimeMillis() / 60000 % mottos.size).toInt())
                    }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(30000)
                            mottoIndex.value = (mottoIndex.value + 1) % mottos.size
                        }
                    }
                    Text(
                        text = mottos[mottoIndex.value],
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    )

                    AnimatedVisibility(
                        visible = immersiveControlsVisible,
                        enter = fadeIn(tween(180)) + slideInVertically(
                            animationSpec = tween(180),
                            initialOffsetY = { -it / 4 }
                        ),
                        exit = fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { viewModel.immersiveFocusEnabled.value = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "退出全屏")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("退出", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val currentIdx = immersiveWallpaperChoices.indexOf(selectedWallpaper).takeIf { it >= 0 } ?: 0
                                        val nextIdx = (currentIdx + 1) % immersiveWallpaperChoices.size
                                        viewModel.setWallpaper(immersiveWallpaperChoices[nextIdx])
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.14f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "切壁纸",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("壁纸", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        viewModel.cycleAllSounds(previewWhenIdle = true)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.14f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "切背景音",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = viewModel.getCurrentSoundName(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 80.dp)
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = immersiveControlsVisible,
                        enter = fadeIn(tween(180)) + slideInVertically(
                            animationSpec = tween(180),
                            initialOffsetY = { it / 3 }
                        ),
                        exit = fadeOut(tween(160)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 28.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.28f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = progressColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${selectedTask?.title ?: if (isBreak) "休息中" else "自由专注"} · ${viewModel.getCurrentSoundName()}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 300.dp)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.requestReset() },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "重置专注",
                                        tint = Color.White
                                    )
                                }

                                LargeFloatingActionButton(
                                    onClick = {
                                        if (isRunning) {
                                            viewModel.pauseTimer()
                                        } else {
                                            if (strictActiveActive && !viewModel.isOverlayPermissionGranted()) {
                                                showOverlayPermissionDialog = true
                                            } else {
                                                viewModel.startTimer()
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isRunning) "暂停" else "开始",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isBreak) viewModel.skipSession()
                                        else viewModel.earlyFinishSession()
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isBreak) Icons.Default.SkipNext else Icons.Default.Done,
                                        contentDescription = if (isBreak) "跳过休息" else "提前结束",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 3. Normal view flow wrapping layout on top of Wallpaper opacity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // State indicator badge and immersive switcher Row
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    // State indicator badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = progressColor.copy(alpha = 0.15f),
                            contentColor = progressColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    isLongBreak -> Icons.Default.Bedtime
                                    isBreak -> Icons.Default.Coffee
                                    else -> Icons.Default.LocalFireDepartment
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isLongBreak -> "长休息时间"
                                    isBreak -> "短休息中"
                                    else -> if (isCountUpMode) "正向专注中" else "专注工作中"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // 正向/倒计时模式切换（仅非运行、非休息时可用）
                    // 选中任务时同步更新任务配置，自由专注时仅切换全局模式
                    if (!isRunning && !isBreak) {
                        FilterChip(
                            selected = isCountUpMode,
                            onClick = { viewModel.toggleTimerMode() },
                            label = {
                                Text(
                                    if (isCountUpMode) "正向计时" else "倒计时",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCountUpMode) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = progressColor.copy(alpha = 0.15f),
                                selectedLabelColor = progressColor,
                                selectedLeadingIconColor = progressColor
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCountUpMode) progressColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }

                    // ENTER IMMERSIVE FOCUS MODE
                    Button(
                        onClick = { viewModel.immersiveFocusEnabled.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = progressColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "极简沉浸",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("极简沉浸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Circular Countdown Timer Widget card overlay
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = if (selectedWallpaper == WallpaperType.NONE) 1.0f else 0.85f
                        )
                    ),
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(bottom = 14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(220.dp)
                                .padding(8.dp)
                                .testTag("circular_timer_container")
                        ) {
                            // Draw background glow and arc progress
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 12.dp.toPx()
                                
                                // 1. Background grey track
                                drawCircle(
                                    color = progressColor.copy(alpha = 0.08f),
                                    radius = size.width / 2 - strokeWidth / 2,
                                    style = Stroke(width = strokeWidth)
                                )

                                // 2. Active countdown remaining arc gradient progress
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            progressColor.copy(alpha = 0.4f),
                                            progressColor,
                                            progressColor.copy(alpha = 0.6f)
                                        )
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = progressFraction * 360f,
                                    useCenter = false,
                                    style = Stroke(
                                        width = strokeWidth + 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }

                            // Central time text + mode
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-1).sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.testTag("timer_countdown_text")
                                )
                                
                                Text(
                                    text = if (isBreak) "休息中" else (if (isCountUpMode) "正向专注中" else "专注中"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Timer controller buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reset Button
                            IconButton(
                                onClick = { viewModel.requestReset() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .shadow(1.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .testTag("reset_timer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "重置专注",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Play / Pause FAB Big circle Button
                            LargeFloatingActionButton(
                                onClick = {
                                    if (isRunning) {
                                        viewModel.pauseTimer()
                                    } else {
                                        if (strictActiveActive && !viewModel.isOverlayPermissionGranted()) {
                                            showOverlayPermissionDialog = true
                                        } else {
                                            viewModel.startTimer()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                containerColor = progressColor,
                                contentColor = Color.White,
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("play_pause_fab")
                            ) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isRunning) "暂停" else "启动专注",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Skip / Fast-forward Button
                            IconButton(
                                onClick = {
                                    if (isBreak) viewModel.skipSession()
                                    else viewModel.earlyFinishSession()
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .shadow(1.dp, CircleShape)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                                    .testTag("skip_timer_button")
                            ) {
                                Icon(
                                    imageVector = if (isBreak) Icons.Default.SkipNext else Icons.Default.Done,
                                    contentDescription = if (isBreak) "跳过当期" else "提前结束",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selected Task focal Node Card block
                Text(
                    text = "正在专注的任务",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 4.dp, bottom = 4.dp)
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTask != null) {
                            MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = if (selectedWallpaper == WallpaperType.NONE) 0.3f else 0.85f
                            )
                        } else {
                            MaterialTheme.colorScheme.surfaceCardVariant.copy(
                                alpha = if (selectedWallpaper == WallpaperType.NONE) 1.0f else 0.85f
                            )
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTaskSelector = true }
                        .testTag("active_task_focus_card"),
                    border = if (selectedTask != null) BorderStroke(1.dp, progressColor) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTask != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (selectedTask != null) progressColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedTask?.title ?: "自由专注（未关联任务）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTask != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedTask?.let { "${it.category} | 已累积 🍅 x${it.completedPomodoros}" } ?: "点击选择你专注的清单任务，同步累加番茄数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "选择任务",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Flow Ambient Status row
                val hasActiveSound = bgSoundType != com.yangshizi.todo.data.audio.AmbientAudioSynth.SoundType.NONE ||
                    realSoundType != com.yangshizi.todo.data.audio.RealAudioPlayer.RealSound.NONE
                if (hasActiveSound || dndActiveActive || strictActiveActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasActiveSound) {
                            SuggestionChip(
                                onClick = { },
                                label = { Text("🔊 ${viewModel.getCurrentSoundName()}", fontSize = 11.sp, maxLines = 1) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = progressColor.copy(alpha = 0.08f),
                                    labelColor = progressColor
                                ),
                                border = null
                            )
                        }
                        if (dndActiveActive) {
                            SuggestionChip(
                                onClick = { },
                                label = { Text("📵 免打扰：通知已闭", fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = null
                            )
                        }
                        if (strictActiveActive) {
                            SuggestionChip(
                                onClick = { showWhitelistLauncher = true },
                                label = { Text("🔒 严格监控中 · 白名单", fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    labelColor = MaterialTheme.colorScheme.error
                                ),
                                border = null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Daily Completed Pomodoro sessions Bar-Chart (Outstanding Custom SVG aesthetics)
                DailyProductivityBarChart(logs = logs, color = progressColor)
            }
        }
    }

    // Modal popup to select focal task
    if (showTaskSelector) {
        val activeTasks = tasks.filter { !it.isCompleted }
        
        AlertDialog(
            onDismissRequest = { showTaskSelector = false },
            title = { Text("切换专注任务") },
            text = {
                if (activeTasks.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        Text(
                            text = "没有活动的专注任务，请先去任务栏新建",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        // Clear task Focus choice
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectTask(null)
                                    showTaskSelector = false
                                }
                        ) {
                            Text(
                                "自由专注（不关联具体任务）",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        activeTasks.forEach { task ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedTask?.id == task.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectTask(task)
                                        showTaskSelector = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "[${task.category}] ${task.title}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // 计时模式标识
                                    Text(
                                        text = if (task.timerMode == "countup") "正向" else "倒计时",
                                        fontSize = 9.sp,
                                        color = if (task.timerMode == "countup") CountUpBlue else CountdownPurple,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = "🍅 ${task.completedPomodoros}/${task.estimatedPomodoros}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskSelector = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = {
                Text(
                    text = "🔒 严格模式防切屏蔽激活提示",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "您已开启了「🔒严格锁定阻栏模式」，但尚未授予系统「悬浮窗/显示在其他应用上层」的权限。如果没有此权限，当您中途切出桌面或使用其他软件时，应用将无法强制拉回，使得锁定效果失效。\n\n建议点击去授权，并在系统列表中开启本软件对应的『悬浮窗 / 显示在其他应用上层』开关，或选择『普通模式』正常开始。",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
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
                                // Fallback
                            }
                        }
                    }
                ) {
                    Text("去开启悬浮窗")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        viewModel.startTimer()
                    }
                ) {
                    Text("以普通模式开始")
                }
            }
        )
    }

    // 重置确认对话框：运行中点击重置时弹出，防止误触
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelReset() },
            title = { Text("确认重置计时？") },
            text = { Text("当前专注进度将丢失，无法恢复。确定要重置吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmReset() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelReset() }) { Text("取消") }
            }
        )
    }

    // 白名单应用快捷启动弹窗：严格模式中无需退出即可使用允许的应用
    if (showWhitelistLauncher) {
        WhitelistLauncherDialog(onDismiss = { showWhitelistLauncher = false })
    }
}

/** 严格模式白名单应用启动器：图标网格 + 点击直接启动 */
@Composable
private fun WhitelistLauncherDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val whitelistManager = remember {
        com.yangshizi.todo.service.WhitelistManager.getInstance(context)
    }
    val apps = remember { whitelistManager.getUserWhitelistApps() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("白名单应用") },
        text = {
            if (apps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    Text(
                        "没有白名单应用，请在「系统」页添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps.size) { index ->
                        val app = apps[index]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    runCatching {
                                        val intent = context.packageManager
                                            .getLaunchIntentForPackage(app.packageName)
                                        intent?.addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                                android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                        )
                                        context.startActivity(intent)
                                        onDismiss()
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            val icon = remember(app.packageName) {
                                runCatching {
                                    context.packageManager.getApplicationIcon(app.packageName)
                                }.getOrNull()
                            }
                            if (icon != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = icon.toBitmap(48).asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(40.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// Custom Geometric Bar chart to plot completed work focus logs
@Composable
fun DailyProductivityBarChart(
    logs: List<PomodoroLogEntity>,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceCardVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("productivity_barchart")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "📊 专注力趋势 (近5天)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Compute last 5 days pomodoros session count
            // 手动映射周几标签，不依赖 SimpleDateFormat(Locale.CHINESE) 因为部分设备 ICU 不可靠
            val weekdayNames = arrayOf("日", "一", "二", "三", "四", "五", "六")

            val days = (4 downTo 0).map { i ->
                val startCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val count = logs.count { it.timestamp in startCal.timeInMillis..endCal.timeInMillis && it.category != "休息" }
                val label = "周" + weekdayNames[startCal.get(Calendar.DAY_OF_WEEK) - 1]
                Pair(label, count)
            }

            val maxCount = days.maxOfOrNull { it.second } ?: 1
            // Ensure we don't divide by zero and establish a minimum reference height
            val referenceMax = if (maxCount == 0) 5 else maxCount

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp)
            ) {
                days.forEach { dayPair ->
                    val (label, count) = dayPair
                    val percentage = count.toFloat() / referenceMax.toFloat()
                    val barHeight = remember(percentage) { percentage * 50f }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Count text above bar
                        Text(
                            text = if (count > 0) "$count" else "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        
                        // Geometric Rectangle representation
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(animateDpAsState(targetValue = barHeight.dp, label = "bar").value)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (count > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
