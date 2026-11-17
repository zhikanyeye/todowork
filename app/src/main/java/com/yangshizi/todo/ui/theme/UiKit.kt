package com.yangshizi.todo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 统一设计令牌：颜色、卡片容器色、分区标题。
 * 各页面从这里取样式，避免散落的硬编码值。
 */

// 品牌主色：番茄红（工作计时/番茄计数专用）
val TomatoRed = Color(0xFFE53935)

// 计时模式徽章色
val CountUpBlue = Color(0xFF1565C0)
val CountdownPurple = Color(0xFF6A1B9A)

// 分类徽章色板（明暗两态，按背景亮度切换）
data class CategoryColors(val container: Color, val content: Color)

private val DarkThemeCategories = mapOf(
    "工作" to CategoryColors(Color(0xFF5B2320), Color(0xFFFFB4AB)),
    "学习" to CategoryColors(Color(0xFF1B3A5C), Color(0xFFA6C8FF)),
    "生活" to CategoryColors(Color(0xFF1C4A2E), Color(0xFF97DFA4)),
    "其他" to CategoryColors(Color(0xFF463B62), Color(0xFFCFC2FF))
)

private val LightThemeCategories = mapOf(
    "工作" to CategoryColors(Color(0xFFFFDAD6), Color(0xFF93000A)),
    "学习" to CategoryColors(Color(0xFFD3E4FF), Color(0xFF004A9F)),
    "生活" to CategoryColors(Color(0xFFCFF5D3), Color(0xFF005322)),
    "其他" to CategoryColors(Color(0xFFE5DEFF), Color(0xFF432B8F))
)

@Composable
fun categoryColors(category: String): CategoryColors {
    val palette = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DarkThemeCategories
    } else {
        LightThemeCategories
    }
    return palette[category] ?: CategoryColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer
    )
}

/**
 * 卡片容器色的统一取值：
 * 亮色主题用不透明 surface，暗色主题用 12% 透明 surface 叠加在壁纸上。
 */
val ColorScheme.surfaceCardVariant: Color
    @Composable
    get() = if (isSystemInDarkTheme()) surface.copy(alpha = 0.12f) else surface.copy(alpha = 1f)

/** 统一的分区标题：小号加粗 + 主色小圆点 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
