package com.yangshizi.todo.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.yangshizi.todo.data.database.TaskEntity
import com.yangshizi.todo.ui.theme.CountUpBlue
import com.yangshizi.todo.ui.theme.CountdownPurple
import com.yangshizi.todo.ui.theme.TomatoRed
import com.yangshizi.todo.ui.theme.categoryColors
import com.yangshizi.todo.ui.theme.surfaceCardVariant
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel
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
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("全部") }
    var pendingCalendarTask by remember { mutableStateOf<TaskEntity?>(null) }
    
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
            grants[Manifest.permission.WRITE_CALENDAR] == true
        val taskToSync = pendingCalendarTask
        pendingCalendarTask = null
        if (granted && taskToSync != null) {
            viewModel.syncTaskToCalendar(taskToSync)
        } else {
            Toast.makeText(context, "未授权日历权限，无法同步任务", Toast.LENGTH_SHORT).show()
        }
    }

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
                            onEdit = { editingTask = task },
                            onDelete = { viewModel.deleteTask(task) },
                            onSyncCalendar = {
                                if (hasCalendarPermissions()) {
                                    viewModel.syncTaskToCalendar(task)
                                } else {
                                    pendingCalendarTask = task
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            },
                            dateFormatter = dateFormatter
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showAddDialog) {
        TaskEditorDialog(
            task = null,
            dateFormatter = dateFormatter,
            onDismiss = { showAddDialog = false },
            onSave = { result ->
                viewModel.insertTask(
                    result.title, result.description, result.category, result.dueDate,
                    result.estimatedPoms, result.timerMode, result.workDurationMin,
                    result.breakDurationMin, result.longBreakDurationMin
                )
                showAddDialog = false
            }
        )
    }
    editingTask?.let { task ->
        TaskEditorDialog(
            task = task,
            dateFormatter = dateFormatter,
            onDismiss = { editingTask = null },
            onSave = { result ->
                viewModel.updateTask(
                    task.copy(
                        title = result.title,
                        description = result.description,
                        category = result.category,
                        dueDate = result.dueDate,
                        estimatedPomodoros = result.estimatedPoms,
                        timerMode = result.timerMode,
                        workDurationMin = result.workDurationMin,
                        breakDurationMin = result.breakDurationMin,
                        longBreakDurationMin = result.longBreakDurationMin
                    )
                )
                editingTask = null
            }
        )
    }
}

private data class TaskEditorResult(
    val title: String,
    val description: String,
    val category: String,
    val dueDate: Long?,
    val estimatedPoms: Int,
    val timerMode: String,
    val workDurationMin: Int?,
    val breakDurationMin: Int?,
    val longBreakDurationMin: Int?
)

@Composable
private fun TaskEditorDialog(
    task: TaskEntity?,
    dateFormatter: SimpleDateFormat,
    onDismiss: () -> Unit,
    onSave: (TaskEditorResult) -> Unit
) {
    val context = LocalContext.current
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var selectedCategory by remember(task?.id) { mutableStateOf(task?.category ?: "工作") }
    var estimatedPoms by remember(task?.id) { mutableIntStateOf(task?.estimatedPomodoros ?: 2) }
    var dueDateMs by remember(task?.id) { mutableStateOf(task?.dueDate) }
    var timerMode by remember(task?.id) { mutableStateOf(task?.timerMode ?: "countdown") }
    var useGlobalDurations by remember(task?.id) {
        mutableStateOf(task?.workDurationMin == null && task?.breakDurationMin == null && task?.longBreakDurationMin == null)
    }
    var workDurationMin by remember(task?.id) { mutableIntStateOf(task?.workDurationMin ?: 25) }
    var breakDurationMin by remember(task?.id) { mutableIntStateOf(task?.breakDurationMin ?: 5) }
    var longBreakDurationMin by remember(task?.id) { mutableIntStateOf(task?.longBreakDurationMin ?: 15) }

    val itemCategories = listOf("工作", "学习", "生活", "其他")
    val timerModes = listOf("countdown" to "倒计时", "countup" to "正向计时")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (task == null) "添加任务" else "编辑任务") },
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

                Text("分类", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    itemCategories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .clickable { selectedCategory = cat }
                                .testTag("task_dialog_category_$cat")
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

                Text("计时模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    timerModes.forEach { (mode, label) ->
                        val isSelected = timerMode == mode
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .clickable { timerMode = mode }
                                .testTag("task_dialog_timer_$mode")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (mode == "countup") Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Text("番茄时长", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useGlobalDurations, onCheckedChange = { useGlobalDurations = it })
                    Spacer(Modifier.width(8.dp))
                    Text("跟随系统设置", style = MaterialTheme.typography.bodyMedium)
                }
                if (!useGlobalDurations) {
                    DurationStepperRow("工作", workDurationMin, 1, 180) { workDurationMin = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 45).forEach { minutes ->
                            TextButton(onClick = { workDurationMin = minutes }) { Text("${minutes}分") }
                        }
                    }
                    DurationStepperRow("短休息", breakDurationMin, 1, 60) { breakDurationMin = it }
                    DurationStepperRow("长休息", longBreakDurationMin, 1, 120) { longBreakDurationMin = it }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("预估番茄钟: ", style = MaterialTheme.typography.bodyMedium)
                        Text("x$estimatedPoms", fontWeight = FontWeight.Bold, color = TomatoRed)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("到期日期", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = dueDateMs?.let { dateFormatter.format(Date(it)) } ?: "未设定",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dueDateMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { dueDateMs = null }, enabled = dueDateMs != null) {
                        Text("清除", fontSize = 12.sp)
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
                                        set(Calendar.HOUR_OF_DAY, 9)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
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
                    val normalizedTitle = title.trim()
                    if (normalizedTitle.isNotBlank()) {
                        onSave(
                            TaskEditorResult(
                                title = normalizedTitle,
                                description = description.trim(),
                                category = selectedCategory,
                                dueDate = dueDateMs,
                                estimatedPoms = estimatedPoms,
                                timerMode = timerMode,
                                workDurationMin = if (useGlobalDurations) null else workDurationMin,
                                breakDurationMin = if (useGlobalDurations) null else breakDurationMin,
                                longBreakDurationMin = if (useGlobalDurations) null else longBreakDurationMin
                            )
                        )
                    }
                },
                modifier = Modifier.testTag(if (task == null) "confirm_add_task_button" else "confirm_edit_task_button")
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DurationStepperRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onChange(value - 1) }) {
                Icon(Icons.Default.Remove, contentDescription = "减少${label}时长")
            }
            Text("${value}分", modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { if (value < max) onChange(value + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "增加${label}时长")
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: TaskEntity,
    isCurrentSelection: Boolean,
    onToggleCompletion: () -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit,
    onEdit: () -> Unit,
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
                val badgeColors = categoryColors(task.category)
                Box(
                    modifier = Modifier
                        .background(
                            color = badgeColors.container,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = task.category,
                        color = badgeColors.content,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑任务",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

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

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

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
                        tint = TomatoRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${task.completedPomodoros}/${task.estimatedPomodoros}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "番茄泡", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // 计时模式徽章
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (task.timerMode == "countup") Color(0xFFE3F2FD) else Color(0xFFF3E5F5),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (task.timerMode == "countup") "正向" else "倒计时",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (task.timerMode == "countup") CountUpBlue else CountdownPurple
                        )
                    }
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

// 设计令牌（surfaceCardVariant / categoryColors / TomatoRed 等）已统一迁移到 ui/theme/UiKit.kt
