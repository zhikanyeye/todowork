package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.PomodoroLogEntity
import com.example.data.database.TaskEntity
import com.example.ui.viewmodel.PomodoroViewModel
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

    var showTaskSelector by remember { mutableStateOf(false) }

    // Format Remaining Time to MM:SS
    val formattedTime = remember(remainingTimeMs) {
        val totalSecs = remainingTimeMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        String.format("%02d:%02d", mins, secs)
    }

    // Circular progress fraction
    val progressFraction = remember(remainingTimeMs, totalTimeMs) {
        if (totalTimeMs > 0) {
            remainingTimeMs.toFloat() / totalTimeMs.toFloat()
        } else {
            1f
        }
    }

    val primaryColor = if (isBreak) MaterialTheme.colorScheme.secondary else Color(0xFFE53935) // Tomato Red for Work
    val progressColor by animateColorAsState(targetValue = primaryColor, label = "progressColor")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // State indicator badge
        Card(
            colors = CardDefaults.cardColors(
                containerColor = progressColor.copy(alpha = 0.12f),
                contentColor = progressColor
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
                        else -> "专注工作中"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Circular Countdown Timer Widget
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .padding(12.dp)
                .testTag("circular_timer_container")
        ) {
            // Draw background glow and arc progress
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                
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
                    text = if (isBreak) "Take a Break" else "Remain Focus",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Timer controller buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset Button
            IconButton(
                onClick = { viewModel.resetTimer() },
                modifier = Modifier
                    .size(48.dp)
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
                    if (isRunning) viewModel.pauseTimer() else viewModel.startTimer()
                },
                shape = CircleShape,
                containerColor = progressColor,
                contentColor = Color.White,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("play_pause_fab")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "暂停" else "启动专注",
                    modifier = Modifier.size(34.dp)
                )
            }

            // Skip / Fast-forward Button
            IconButton(
                onClick = { viewModel.skipSession() },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(1.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag("skip_timer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "跳过当期",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                containerColor = if (selectedTask != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                 else MaterialTheme.colorScheme.surfaceCardVariant
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
        val bgSoundSound by viewModel.bgSoundType.collectAsStateWithLifecycle()
        val dndActiveActive by viewModel.dndEnabled.collectAsStateWithLifecycle()
        val strictActiveActive by viewModel.strictModeEnabled.collectAsStateWithLifecycle()

        if (bgSoundSound != com.example.data.audio.AmbientAudioSynth.SoundType.NONE || dndActiveActive || strictActiveActive) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (bgSoundSound != com.example.data.audio.AmbientAudioSynth.SoundType.NONE) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text("🔊 ${bgSoundSound.displayName}", fontSize = 11.sp, maxLines = 1) },
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
                        onClick = { },
                        label = { Text("🔒 严格监控中", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            labelColor = MaterialTheme.colorScheme.error
                        ),
                        border = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Daily Completed Pomodoro sessions Bar-Chart (Outstanding Custom SVG aesthetics)
        DailyProductivityBarChart(logs = logs, color = progressColor)
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
            val calendar = Calendar.getInstance()
            val dayFormatter = SimpleDateFormat("E", Locale.CHINESE)
            val todayDateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

            val days = (4 downTo 0).map { i ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                val start = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                val end = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
                
                val count = logs.count { it.timestamp in start..end && it.category != "休息" }
                val label = dayFormatter.format(cal.time)
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
