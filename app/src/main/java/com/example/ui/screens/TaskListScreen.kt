package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.TaskEntity
import com.example.ui.viewmodel.PomodoroViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskListScreen(
    viewModel: PomodoroViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("全部") }
    
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    val categories = listOf("全部", "工作", "学习", "生活", "其他")

    // Filter tasks
    val filteredTasks = remember(tasks, selectedCategoryFilter) {
        if (selectedCategoryFilter == "全部") {
            tasks
        } else {
            tasks.filter { it.category == selectedCategoryFilter }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_task_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建任务", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Header Title
            Text(
                text = "我的任务",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "关联番茄终，将专注落于实处",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Status Snackbar notification / Pill
            AnimatedVisibility(
                visible = syncStatus != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                syncStatus?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Category filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("category_filter_row")
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { selectedCategoryFilter = cat }
                            .testTag("category_filter_$cat")
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Task List
            if (filteredTasks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Task,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无相关任务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击右下角按钮，开启你的专注清单！",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task_list")
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        val isCurrentSelection = selectedTask?.id == task.id
                        
                        TaskItemCard(
                            task = task,
                            isCurrentSelection = isCurrentSelection,
                            onToggleCompletion = { viewModel.toggleCompletion(task) },
                            onSelect = { viewModel.selectTask(task) },
                            onDeselect = { viewModel.selectTask(null) },
                            onDelete = { viewModel.deleteTask(task) },
                            onSyncCalendar = { viewModel.syncTaskToCalendar(task) },
                            dateFormatter = dateFormatter
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Add Task Dialog
    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf("工作") }
        var estimatedPoms by remember { mutableIntStateOf(2) }
        var dueDateMs by remember { mutableStateOf<Long?>(null) }
        
        val itemCategories = listOf("工作", "学习", "生活", "其他")

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "添加任务") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("任务标题") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述属性 (可选)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Pill Selector
                    Text("分类", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemCategories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .clickable { selectedCategory = cat }
                                    .testTag("add_dialog_category_$cat")
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Target Tomato/Pomodoros selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("预估番茄钟: ", style = MaterialTheme.typography.bodyMedium)
                            Text("🍅 x$estimatedPoms", fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                        }
                        Row {
                            IconButton(onClick = { if (estimatedPoms > 1) estimatedPoms-- }) {
                                Icon(Icons.Default.Remove, contentDescription = "减少")
                            }
                            IconButton(onClick = { if (estimatedPoms < 10) estimatedPoms++ }) {
                                Icon(Icons.Default.Add, contentDescription = "增加")
                            }
                        }
                    }

                    // Date Picker Trigger for Calendar sync
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("到期日期", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = dueDateMs?.let { dateFormatter.format(Date(it)) } ?: "未设定 (不设定无法同步日历)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dueDateMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            set(Calendar.HOUR_OF_DAY, 9) // Default 9 AM
                                            set(Calendar.MINUTE, 0)
                                        }
                                        dueDateMs = selectedCal.timeInMillis
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Text("选择", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.insertTask(title, description, selectedCategory, dueDateMs, estimatedPoms)
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_task_button")
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TaskItemCard(
    task: TaskEntity,
    isCurrentSelection: Boolean,
    onToggleCompletion: () -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onDelete: () -> Unit,
    onSyncCalendar: () -> Unit,
    dateFormatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSelection) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceCardVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Checkbox
                IconButton(
                    onClick = onToggleCompletion,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("checkbox_${task.id}")
                ) {
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "切换完成状态",
                        tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title + Description column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Category Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = when (task.category) {
                                "工作" -> Color(0xFFE8F5E9)
                                "学习" -> Color(0xFFE3F2FD)
                                "生活" -> Color(0xFFFFF3E0)
                                else -> Color(0xFFECEFF1)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = task.category,
                        color = when (task.category) {
                            "工作" -> Color(0xFF2E7D32)
                            "学习" -> Color(0xFF1565C0)
                            "生活" -> Color(0xFFEF6C00)
                            else -> Color(0xFF37474F)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除任务",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // Action Details Bottom Row
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tomatoes estimation indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${task.completedPomodoros}/${task.estimatedPomodoros}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "番茄泡", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Integration action / State toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System Calendar Sync trigger
                    if (task.dueDate != null) {
                        IconButton(
                            onClick = onSyncCalendar,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (task.calendarEventId != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                        ) {
                            Icon(
                                imageVector = if (task.calendarEventId != null) Icons.Default.EventAvailable else Icons.Default.CalendarToday,
                                contentDescription = if (task.calendarEventId != null) "已同步日历" else "同步到系统日历",
                                tint = if (task.calendarEventId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Selection focus button
                    Button(
                        onClick = { if (isCurrentSelection) onDeselect() else onSelect() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentSelection) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isCurrentSelection) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("focus_toggle_${task.id}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCurrentSelection) Icons.Default.Check else Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isCurrentSelection) "进行中" else "设为焦点", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Utility extension for simplified theme card support
val ColorScheme.surfaceCardVariant: Color
    @Composable
    get() = if (isSystemInDarkTheme()) surface.copy(alpha = 0.12f) else surface.copy(alpha = 1f)

@Composable
fun isSystemInDarkTheme(): Boolean {
    return MaterialTheme.colorScheme.background.luminance() < 0.5f
}

fun Color.luminance(): Float {
    val r = red
    val g = green
    val b = blue
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
