package com.yangshizi.todo.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangshizi.todo.data.database.PomodoroLogEntity
import com.yangshizi.todo.ui.viewmodel.PomodoroViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

private const val MAX_LOG_MINUTES_FOR_STATS = 24 * 60L
private const val MIN_STATS_TIMESTAMP_MS = 946684800000L // 2000-01-01
private const val MAX_STATS_TIMESTAMP_MS = 4102444799000L // 2099-12-31

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: PomodoroViewModel) {
    val logs by viewModel.allLogs.collectAsStateWithLifecycle()
    val workLogs = remember(logs) { logs.toFocusSamples() }

    val now = remember { Calendar.getInstance() }
    var heatmapMode by remember { mutableStateOf(HeatmapMode.MONTH) }
    var lineMode by remember { mutableStateOf(TrendMode.MONTH) }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("专注统计", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeatmapCard(
                logs = workLogs,
                mode = heatmapMode,
                year = selectedYear,
                month = selectedMonth,
                onModeChange = { heatmapMode = it },
                onPrevious = {
                    when (heatmapMode) {
                        HeatmapMode.MONTH -> {
                            if (selectedMonth == 0) {
                                selectedMonth = 11
                                selectedYear--
                            } else {
                                selectedMonth--
                            }
                        }
                        HeatmapMode.YEAR -> selectedYear--
                    }
                },
                onNext = {
                    when (heatmapMode) {
                        HeatmapMode.MONTH -> {
                            if (selectedMonth == 11) {
                                selectedMonth = 0
                                selectedYear++
                            } else {
                                selectedMonth++
                            }
                        }
                        HeatmapMode.YEAR -> selectedYear++
                    }
                }
            )

            TrendCard(
                logs = workLogs,
                mode = lineMode,
                year = selectedYear,
                month = selectedMonth,
                onModeChange = { lineMode = it }
            )

            DurationPieCard(logs = workLogs)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private enum class HeatmapMode(val label: String) {
    MONTH("月度"),
    YEAR("年度")
}

private enum class TrendMode(val label: String) {
    MONTH("月度"),
    YEAR("年度")
}

private data class FocusSample(
    val timestamp: Long,
    val minutes: Long,
    val category: String
)

@Composable
private fun HeatmapCard(
    logs: List<FocusSample>,
    mode: HeatmapMode,
    year: Int,
    month: Int,
    onModeChange: (HeatmapMode) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val title = when (mode) {
        HeatmapMode.MONTH -> monthLabel(year, month)
        HeatmapMode.YEAR -> "${year}年"
    }
    val totalMinutes = remember(logs, mode, year, month) {
        when (mode) {
            HeatmapMode.MONTH -> logs
                .filter { it.isInMonth(year, month) }
                .sumOf { it.minutes }
            HeatmapMode.YEAR -> logs
                .filter { it.isInYear(year) }
                .sumOf { it.minutes }
        }
    }

    StatCard {
        SectionHeader(
            title = "热力图",
            subtitle = "${formatDuration(totalMinutes)} 专注",
            modeContent = {
                ModeChips(
                    selected = mode,
                    values = HeatmapMode.entries,
                    onSelected = onModeChange
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PeriodSwitcher(
            title = title,
            onPrevious = onPrevious,
            onNext = onNext
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (mode) {
            HeatmapMode.MONTH -> MonthHeatmap(
                logs = logs,
                year = year,
                month = month
            )
            HeatmapMode.YEAR -> YearHeatmap(
                logs = logs,
                year = year
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HeatLegend(accentColor = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TrendCard(
    logs: List<FocusSample>,
    mode: TrendMode,
    year: Int,
    month: Int,
    onModeChange: (TrendMode) -> Unit
) {
    val chartData = remember(logs, mode, year, month) {
        when (mode) {
            TrendMode.MONTH -> buildMonthlyTrend(logs, year, month)
            TrendMode.YEAR -> buildYearlyTrend(logs, year)
        }
    }
    val totalMinutes = chartData.values.sum()

    StatCard {
        SectionHeader(
            title = "折线图",
            subtitle = "${formatDuration(totalMinutes)} 专注",
            modeContent = {
                ModeChips(
                    selected = mode,
                    values = TrendMode.entries,
                    onSelected = onModeChange
                )
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        LineChart(
            data = chartData,
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        )
    }
}

@Composable
private fun DurationPieCard(logs: List<FocusSample>) {
    val slices = remember(logs) {
        val rawSlices = logs.groupBy { it.category.trim().ifBlank { "未分类" } }
            .map { (category, items) ->
                PieSliceData(
                    label = category,
                    minutes = items.sumOf { it.minutes }
                )
            }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }
        val visible = rawSlices.take(7)
        val otherMinutes = rawSlices.drop(7).sumOf { it.minutes }
        if (otherMinutes > 0L) visible + PieSliceData("其他", otherMinutes) else visible
    }
    val totalMinutes = slices.sumOf { it.minutes }
    val palette = chartPalette()

    StatCard {
        SectionHeader(
            title = "总时长饼图",
            subtitle = formatDuration(totalMinutes)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (slices.isEmpty()) {
            EmptyChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DurationPieChart(
                    slices = slices,
                    palette = palette,
                    centerText = formatDuration(totalMinutes),
                    modifier = Modifier.size(150.dp)
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    slices.take(6).forEachIndexed { index, slice ->
                        PieLegendRow(
                            slice = slice,
                            color = palette[index % palette.size],
                            totalMinutes = totalMinutes
                        )
                    }
                    if (slices.size > 6) {
                        Text(
                            text = "其余 ${slices.size - 6} 类",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modeContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (modeContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            modeContent()
        }
    }
}

@Composable
private inline fun <reified T : Enum<T>> ModeChips(
    selected: T,
    values: List<T>,
    noinline onSelected: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            val label = when (value) {
                is HeatmapMode -> value.label
                is TrendMode -> value.label
                else -> value.name
            }
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun PeriodSwitcher(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一段")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一段")
        }
    }
}

@Composable
private fun MonthHeatmap(
    logs: List<FocusSample>,
    year: Int,
    month: Int
) {
    val dailyMinutes = remember(logs, year, month) { dailyMinutes(logs, year, month) }
    val maxMinutes = dailyMinutes.maxOrNull().orZero().coerceAtLeast(1L)
    val calendar = remember(year, month) {
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val firstDayOffset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val rows = ceil((firstDayOffset + daysInMonth) / 7f).toInt().coerceAtLeast(1)
    val weekDays = listOf("一", "二", "三", "四", "五", "六", "日")

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val day = index - firstDayOffset + 1
                    if (day in 1..daysInMonth) {
                        val minutes = dailyMinutes.getOrElse(day - 1) { 0 }
                        HeatCell(
                            label = day.toString(),
                            minutes = minutes,
                            level = heatLevel(minutes, maxMinutes),
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeatmap(
    logs: List<FocusSample>,
    year: Int
) {
    val monthlyMinutes = remember(logs, year) { monthlyMinutes(logs, year) }
    val maxMinutes = monthlyMinutes.maxOrNull().orZero().coerceAtLeast(1L)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { column ->
                    val monthIndex = row * 3 + column
                    val minutes = monthlyMinutes.getOrElse(monthIndex) { 0 }
                    HeatCell(
                        label = "${monthIndex + 1}月",
                        minutes = minutes,
                        level = heatLevel(minutes, maxMinutes),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    label: String,
    minutes: Long,
    level: Int,
    modifier: Modifier = Modifier
) {
    val background = heatColor(level, MaterialTheme.colorScheme.primary)
    val textColor = if (level >= 3) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (minutes > 0) {
                Text(
                    text = shortDuration(minutes),
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = textColor.copy(alpha = 0.86f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeatLegend(accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        repeat(5) { level ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(heatColor(level, accentColor))
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LineChart(
    data: TrendChartData,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.values.isEmpty() || data.values.all { it <= 0L }) {
        EmptyChart(modifier = modifier)
        return
    }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 550),
        label = "trend_chart_progress"
    )
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val labelTextSize = with(LocalDensity.current) { 10.sp.toPx() }

    Canvas(modifier = modifier) {
        val leftPadding = 40f
        val rightPadding = 10f
        val topPadding = 14f
        val bottomPadding = 28f
        val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
        val maxValue = data.values.maxOrNull().orZero().coerceAtLeast(1L).toFloat()

        repeat(5) { index ->
            val y = topPadding + chartHeight * index / 4f
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.16f),
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1f
            )
        }

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = onSurfaceVariant.copy(alpha = 0.78f).toArgb()
                textSize = labelTextSize
            }

            paint.textAlign = android.graphics.Paint.Align.RIGHT
            repeat(5) { index ->
                val value = (maxValue * (4 - index) / 4f).toLong()
                val y = topPadding + chartHeight * index / 4f
                canvas.nativeCanvas.drawText(
                    compactNumber(value),
                    leftPadding - 8f,
                    y + labelTextSize / 3f,
                    paint
                )
            }

            paint.textAlign = android.graphics.Paint.Align.CENTER
            val labelStep = when {
                data.labels.size > 24 -> 6
                data.labels.size > 12 -> 3
                else -> 1
            }
            data.labels.forEachIndexed { index, label ->
                if (index % labelStep == 0 || index == data.labels.lastIndex) {
                    val x = xForIndex(index, data.values.size, leftPadding, chartWidth)
                    canvas.nativeCanvas.drawText(
                        label,
                        x,
                        size.height - 7f,
                        paint
                    )
                }
            }
        }

        val points = data.values.mapIndexed { index, value ->
            val safeValue = min(value, Int.MAX_VALUE.toLong()).toFloat()
            Offset(
                x = xForIndex(index, data.values.size, leftPadding, chartWidth),
                y = topPadding + chartHeight * (1f - safeValue / maxValue)
            )
        }
        val visibleCount = (points.size * progress).toInt().coerceIn(1, points.size)

        if (visibleCount > 1) {
            val fillPath = Path().apply {
                moveTo(points.first().x, topPadding + chartHeight)
                for (index in 0 until visibleCount) {
                    lineTo(points[index].x, points[index].y)
                }
                lineTo(points[visibleCount - 1].x, topPadding + chartHeight)
                close()
            }
            drawPath(fillPath, color = accentColor.copy(alpha = 0.13f))
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until visibleCount) {
                lineTo(points[index].x, points[index].y)
            }
        }
        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        for (index in 0 until visibleCount) {
            drawCircle(
                color = surface,
                radius = 6.5f,
                center = points[index]
            )
            drawCircle(
                color = accentColor,
                radius = 4.2f,
                center = points[index]
            )
        }
    }
}

@Composable
private fun EmptyChart(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无专注数据",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DurationPieChart(
    slices: List<PieSliceData>,
    palette: List<Color>,
    centerText: String,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650),
        label = "duration_pie_progress"
    )
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val totalMinutes = slices.sumOf { it.minutes }.coerceAtLeast(1L)
    val labelTextSize = with(LocalDensity.current) { 13.sp.toPx() }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val rect = Rect(
            offset = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )

        var startAngle = -90f
        slices.forEachIndexed { index, slice ->
            val sweepAngle = (slice.minutes.toDouble() / totalMinutes.toDouble() * 360.0 * progress)
                .toFloat()
                .coerceIn(0f, 360f)
            drawArc(
                color = palette[index % palette.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = rect.topLeft,
                size = rect.size
            )
            startAngle += sweepAngle
        }

        drawCircle(
            color = surface,
            radius = radius * 0.56f,
            center = center
        )

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = onSurface.toArgb()
                textSize = labelTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.nativeCanvas.drawText(
                centerText,
                center.x,
                center.y + labelTextSize / 3f,
                paint
            )
        }
    }
}

@Composable
private fun PieLegendRow(
    slice: PieSliceData,
    color: Color,
    totalMinutes: Long
) {
    val percent = if (totalMinutes <= 0L) 0 else (slice.minutes * 100.0 / totalMinutes).toInt()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = slice.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatDuration(slice.minutes)}  $percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )
    }
}

private data class TrendChartData(
    val values: List<Long>,
    val labels: List<String>
)

private data class PieSliceData(
    val label: String,
    val minutes: Long
)

private fun buildMonthlyTrend(
    logs: List<FocusSample>,
    year: Int,
    month: Int
): TrendChartData {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val values = MutableList(daysInMonth) { 0L }
    logs.forEach { log ->
        val logCalendar = log.calendar()
        if (logCalendar.get(Calendar.YEAR) == year && logCalendar.get(Calendar.MONTH) == month) {
            val day = logCalendar.get(Calendar.DAY_OF_MONTH)
            if (day in 1..daysInMonth) {
                values[day - 1] += log.minutes
            }
        }
    }
    return TrendChartData(
        values = values,
        labels = (1..daysInMonth).map { it.toString() }
    )
}

private fun buildYearlyTrend(
    logs: List<FocusSample>,
    year: Int
): TrendChartData {
    val values = MutableList(12) { 0L }
    logs.forEach { log ->
        val calendar = log.calendar()
        if (calendar.get(Calendar.YEAR) == year) {
            values[calendar.get(Calendar.MONTH)] += log.minutes
        }
    }
    return TrendChartData(
        values = values,
        labels = (1..12).map { "${it}月" }
    )
}

private fun dailyMinutes(
    logs: List<FocusSample>,
    year: Int,
    month: Int
): List<Long> {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
    val values = MutableList(daysInMonth) { 0L }
    logs.forEach { log ->
        val logCalendar = log.calendar()
        if (logCalendar.get(Calendar.YEAR) == year && logCalendar.get(Calendar.MONTH) == month) {
            val day = logCalendar.get(Calendar.DAY_OF_MONTH)
            if (day in 1..daysInMonth) {
                values[day - 1] += log.minutes
            }
        }
    }
    return values
}

private fun monthlyMinutes(
    logs: List<FocusSample>,
    year: Int
): List<Long> {
    val values = MutableList(12) { 0L }
    logs.forEach { log ->
        val calendar = log.calendar()
        if (calendar.get(Calendar.YEAR) == year) {
            values[calendar.get(Calendar.MONTH)] += log.minutes
        }
    }
    return values
}

private fun heatLevel(value: Long, maxValue: Long): Int {
    if (value <= 0L || maxValue <= 0L) return 0
    val ratio = value.toDouble() / maxValue.toDouble()
    return when {
        ratio <= 0.25 -> 1
        ratio <= 0.50 -> 2
        ratio <= 0.75 -> 3
        else -> 4
    }
}

private fun heatColor(level: Int, accentColor: Color): Color {
    return when (level) {
        0 -> accentColor.copy(alpha = 0.07f)
        1 -> accentColor.copy(alpha = 0.24f)
        2 -> accentColor.copy(alpha = 0.43f)
        3 -> accentColor.copy(alpha = 0.66f)
        else -> accentColor.copy(alpha = 0.92f)
    }
}

private fun chartPalette(): List<Color> {
    return listOf(
        Color(0xFF3F7CAC),
        Color(0xFFE76F51),
        Color(0xFF2A9D8F),
        Color(0xFFF4A261),
        Color(0xFF7C5CC4),
        Color(0xFF5A8F29),
        Color(0xFFC44E75),
        Color(0xFF607D8B)
    )
}

private fun List<PomodoroLogEntity>.toFocusSamples(): List<FocusSample> {
    return asSequence()
        .mapNotNull { log ->
            val category = cleanCategory(log.category)
            if (category == "休息") return@mapNotNull null
            val timestamp = normalizeStatsTimestamp(log.timestamp) ?: return@mapNotNull null
            val minutes = log.durationMinutes
                .toLong()
                .takeIf { it > 0L }
                ?.coerceAtMost(MAX_LOG_MINUTES_FOR_STATS)
                ?: return@mapNotNull null
            FocusSample(
                timestamp = timestamp,
                minutes = minutes,
                category = category
            )
        }
        .toList()
}

private fun FocusSample.isInYear(year: Int): Boolean {
    return calendar().get(Calendar.YEAR) == year
}

private fun FocusSample.isInMonth(year: Int, month: Int): Boolean {
    val calendar = calendar()
    return calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) == month
}

private fun FocusSample.calendar(): Calendar {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
}

private fun monthLabel(year: Int, month: Int): String {
    return SimpleDateFormat("yyyy年M月", Locale.CHINESE).format(
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }.time
    )
}

private fun formatDuration(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0L)
    val hours = safeMinutes / 60
    val mins = safeMinutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

private fun shortDuration(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0L)
    val hours = safeMinutes / 60
    return if (hours > 0) "${hours}h" else "${safeMinutes}m"
}

private fun cleanCategory(category: String): String {
    val clean = category.trim()
    return if (clean.isBlank() || clean == "null" || clean == "undefined") "工作" else clean.take(40)
}

private fun normalizeStatsTimestamp(raw: Long): Long? {
    if (raw == 0L || raw == Long.MIN_VALUE) return null
    val abs = if (raw < 0L) -raw else raw
    val millis = when {
        abs in 1L..99_999_999_999L -> raw * 1000L
        abs > 99_999_999_999_999L -> raw / 1000L
        else -> raw
    }
    return millis.takeIf { it in MIN_STATS_TIMESTAMP_MS..MAX_STATS_TIMESTAMP_MS }
}

private fun compactNumber(value: Long): String {
    return when {
        value >= 100_000_000L -> "${value / 100_000_000L}亿"
        value >= 10_000L -> "${value / 10_000L}万"
        else -> value.toString()
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun Float?.orZero(): Float = this ?: 0f

private fun xForIndex(
    index: Int,
    count: Int,
    leftPadding: Float,
    chartWidth: Float
): Float {
    return if (count <= 1) {
        leftPadding + chartWidth / 2f
    } else {
        leftPadding + chartWidth * index / (count - 1).toFloat()
    }
}
