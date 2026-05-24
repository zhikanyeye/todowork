package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.viewmodel.PomodoroViewModel.WallpaperType

@Composable
fun FocusWallpaper(
    wallpaperType: WallpaperType,
    customWallpaperUri: String?,
    modifier: Modifier = Modifier
) {
    // We can add subtle ambient slow animations to make the wallpapers live and mesmerizing!
    val infiniteTransition = rememberInfiniteTransition(label = "wallpaper_animation")
    
    // Slow breathing animation (values oscillating smoothly between 0f and 1f)
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val offsetFactor by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = SineBlock),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset_factor"
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (wallpaperType) {
            WallpaperType.NONE -> {
                // Minimalist gradient fallback using dynamic surface scheme colors
                val surfaceBg = MaterialTheme.colorScheme.background
                val surfaceVariantBg = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(surfaceBg, surfaceVariantBg)
                        )
                    )
                }
            }
            WallpaperType.FOREST -> {
                // Misty Forest Green Procedural Art Selection
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // 1. Background sky morning dawn gradient (soft amber to deep teal)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE3F2FD), // soft light blue sky
                                Color(0xFFC8E6C9), // soft dawn green
                                Color(0xFF1B5E20)  // deep pine green valley
                            )
                        )
                    )

                    // 2. Draw sun glowing behind trees
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF9C4).copy(alpha = 0.65f),
                                Color(0xFFFFF9C4).copy(alpha = 0.0f)
                            ),
                            center = Offset(width * 0.75f, height * 0.3f),
                            radius = width * 0.4f
                        )
                    )

                    // 3. Middle layer mist
                    drawRect(
                        color = Color(0xFFE8F5E9).copy(alpha = 0.35f * pulseAlpha),
                    )

                    // 4. Draw distant mountain/pine ridges (abstract layered triangles)
                    val p_far1 = Path().apply {
                        moveTo(width * 0.1f, height * 0.7f)
                        lineTo(width * 0.4f, height * 0.48f)
                        lineTo(width * 0.7f, height * 0.7f)
                        close()
                    }
                    drawPath(path = p_far1, color = Color(0xFF2E7D32).copy(alpha = 0.45f))

                    val p_far2 = Path().apply {
                        moveTo(width * 0.45f, height * 0.75f)
                        lineTo(width * 0.75f, height * 0.52f)
                        lineTo(width * 1.05f, height * 0.75f)
                        close()
                    }
                    drawPath(path = p_far2, color = Color(0xFF1B5E20).copy(alpha = 0.55f))

                    // 5. Draw foreground prominent abstract Pine Trees
                    drawPineTree(Offset(width * 0.25f, height * 0.85f), height * 0.32f, Color(0xFF0D5316))
                    drawPineTree(Offset(width * 0.5f, height * 0.9f), height * 0.38f, Color(0xFF073F0E))
                    drawPineTree(Offset(width * 0.78f, height * 0.87f), height * 0.35f, Color(0xFF042D09))

                    // Decorative ground silhouette
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF021B05)),
                            startY = height * 0.8f
                        )
                    )
                }
            }
            WallpaperType.COSMIC -> {
                // Quiet orbital sky with a clear planet horizon and crisp stars.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF071A2D),
                                Color(0xFF090B16),
                                Color(0xFF02030A)
                            )
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.18f + offsetFactor, height * 0.28f),
                            radius = width * 0.72f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF22C55E).copy(alpha = 0.10f * pulseAlpha),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.82f - offsetFactor, height * 0.58f),
                            radius = width * 0.56f
                        )
                    )

                    for (idx in 0 until 58) {
                        val x = width * (((idx * 37) % 100) / 100f)
                        val y = height * (0.06f + (((idx * 53) % 78) / 100f))
                        val twinkle = 0.45f + 0.45f * ((Math.sin(idx.toDouble() + pulseAlpha.toDouble() * 6.0) + 1.0) / 2.0).toFloat()
                        val starSize = 1.0f + ((idx % 5) * 0.45f)
                        drawCircle(
                            color = Color.White.copy(alpha = twinkle),
                            radius = starSize,
                            center = Offset(x, y)
                        )
                    }

                    drawLine(
                        color = Color(0xFFA7F3D0).copy(alpha = 0.55f),
                        start = Offset(width * 0.66f, height * 0.19f),
                        end = Offset(width * 0.92f, height * 0.10f),
                        strokeWidth = 2.2f
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE0F2FE).copy(alpha = 0.96f),
                                Color(0xFF7DD3FC).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.76f, height * 0.28f),
                            radius = width * 0.13f
                        )
                    )

                    val planetCenter = Offset(width * 0.5f, height * 1.12f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF67E8F9).copy(alpha = 0.44f),
                                Color(0xFF0F766E).copy(alpha = 0.78f),
                                Color(0xFF031B2E)
                            ),
                            center = Offset(width * 0.45f, height * 0.86f),
                            radius = width * 0.86f
                        ),
                        center = planetCenter,
                        radius = width * 0.78f
                    )
                    drawCircle(
                        color = Color(0xFFBAE6FD).copy(alpha = 0.23f),
                        center = planetCenter + Offset(0f, -height * 0.04f),
                        radius = width * 0.66f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )
                }
            }
            WallpaperType.RAINY -> {
                // Rainy window with city lights, glass streaks, and a readable horizon.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF14213D),
                                Color(0xFF0B1020),
                                Color(0xFF05060A)
                            )
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF59E0B).copy(alpha = 0.28f + offsetFactor * 0.001f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.24f, height * 0.34f),
                            radius = width * 0.34f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF22D3EE).copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.72f, height * 0.42f),
                            radius = width * 0.42f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFB7185).copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.52f, height * 0.66f),
                            radius = width * 0.28f
                        )
                    )

                    val buildings = listOf(
                        0.04f to 0.28f, 0.14f to 0.38f, 0.26f to 0.24f, 0.36f to 0.33f,
                        0.49f to 0.45f, 0.62f to 0.30f, 0.75f to 0.40f, 0.88f to 0.26f
                    )
                    buildings.forEachIndexed { index, (xStart, buildingHeight) ->
                        val left = width * xStart
                        val top = height * (0.82f - buildingHeight)
                        val buildingWidth = width * (0.08f + (index % 3) * 0.018f)
                        drawRect(
                            color = Color(0xFF050814).copy(alpha = 0.90f),
                            topLeft = Offset(left, top),
                            size = Size(buildingWidth, height - top)
                        )
                        for (row in 0 until 5) {
                            val windowY = top + height * 0.035f + row * height * 0.055f
                            if (windowY < height * 0.78f && (row + index) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFFFDE68A).copy(alpha = 0.50f),
                                    topLeft = Offset(left + buildingWidth * 0.28f, windowY),
                                    size = Size(buildingWidth * 0.18f, height * 0.014f)
                                )
                            }
                        }
                    }

                    for (idx in 0 until 30) {
                        val x = width * (((idx * 29) % 100) / 100f)
                        val y = height * (((idx * 41) % 94) / 100f)
                        val len = height * (0.045f + (idx % 4) * 0.014f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = Offset(x + offsetFactor * 0.12f, y),
                            end = Offset(x + width * 0.018f + offsetFactor * 0.12f, y + len),
                            strokeWidth = 1.4f
                        )
                    }

                    drawLine(Color.White.copy(alpha = 0.16f), Offset(width * 0.50f, 0f), Offset(width * 0.50f, height), 3f)
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, height * 0.53f), Offset(width, height * 0.53f), 3f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                            startY = height * 0.66f
                        )
                    )
                }
            }
            WallpaperType.COCOA -> {
                // Warm study desk with an open book and cocoa cup.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAE8C8),
                                Color(0xFFC76D4A),
                                Color(0xFF582C3A)
                            )
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF7ED).copy(alpha = 0.70f * pulseAlpha),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.22f + offsetFactor * 0.25f, height * 0.22f),
                            radius = width * 0.46f
                        )
                    )

                    val desk = Path().apply {
                        moveTo(0f, height * 0.62f)
                        cubicTo(width * 0.28f, height * 0.55f, width * 0.70f, height * 0.58f, width, height * 0.50f)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(desk, Color(0xFF2A171C).copy(alpha = 0.78f))

                    val leftPage = Path().apply {
                        moveTo(width * 0.12f, height * 0.67f)
                        cubicTo(width * 0.28f, height * 0.58f, width * 0.43f, height * 0.60f, width * 0.50f, height * 0.70f)
                        lineTo(width * 0.48f, height * 0.91f)
                        cubicTo(width * 0.34f, height * 0.82f, width * 0.22f, height * 0.84f, width * 0.08f, height * 0.91f)
                        close()
                    }
                    val rightPage = Path().apply {
                        moveTo(width * 0.50f, height * 0.70f)
                        cubicTo(width * 0.60f, height * 0.58f, width * 0.78f, height * 0.58f, width * 0.92f, height * 0.67f)
                        lineTo(width * 0.94f, height * 0.91f)
                        cubicTo(width * 0.75f, height * 0.84f, width * 0.62f, height * 0.82f, width * 0.52f, height * 0.91f)
                        close()
                    }
                    drawPath(leftPage, Color(0xFFFFF7ED))
                    drawPath(rightPage, Color(0xFFFFEDD5))
                    drawLine(
                        color = Color(0xFF7C2D12).copy(alpha = 0.25f),
                        start = Offset(width * 0.50f, height * 0.70f),
                        end = Offset(width * 0.50f, height * 0.92f),
                        strokeWidth = 2f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFEDD5),
                                Color(0xFF7C2D12)
                            ),
                            center = Offset(width * 0.78f, height * 0.62f),
                            radius = width * 0.13f
                        ),
                        center = Offset(width * 0.78f, height * 0.64f),
                        radius = width * 0.115f
                    )
                    drawCircle(
                        color = Color(0xFF3B1710).copy(alpha = 0.78f),
                        center = Offset(width * 0.78f, height * 0.64f),
                        radius = width * 0.075f
                    )
                    drawCircle(
                        color = Color(0xFFFFF7ED).copy(alpha = 0.36f),
                        center = Offset(width * 0.88f, height * 0.64f),
                        radius = width * 0.038f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                    )
                    listOf(0.72f, 0.78f, 0.84f).forEachIndexed { index, x ->
                        val wave = Path().apply {
                            moveTo(width * x, height * 0.54f)
                            cubicTo(width * (x - 0.03f), height * (0.50f - index * 0.01f), width * (x + 0.04f), height * 0.46f, width * x, height * 0.42f)
                        }
                        drawPath(
                            path = wave,
                            color = Color.White.copy(alpha = 0.32f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                        )
                    }
                }
            }
            WallpaperType.CUSTOM -> {
                if (!customWallpaperUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = customWallpaperUri,
                        contentDescription = "自定义背景壁纸",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val surfaceBg = MaterialTheme.colorScheme.background
                    val surfaceVariantBg = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(surfaceBg, surfaceVariantBg)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = "未设置自定义壁纸\n请于「底栏设置」中上传导入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// Draw a simple stacked geometric pine tree for procedural forest art
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPineTree(
    baseOffset: Offset,
    totalHeight: Float,
    treeColor: Color
) {
    val x = baseOffset.x
    val y = baseOffset.y
    val trunkWidth = totalHeight * 0.08f
    val trunkHeight = totalHeight * 0.15f

    // 1. Central woody trunk
    drawRect(
        color = Color(0xFF3E2723).copy(alpha = 0.7f), // dark brown
        topLeft = Offset(x - trunkWidth / 2f, y - trunkHeight),
        size = Size(trunkWidth, trunkHeight)
    )

    // 2. Three sets of stacked abstract triangle foils
    val leafBaseline = y - trunkHeight
    val stackHeights = listOf(totalHeight * 0.32f, totalHeight * 0.28f, totalHeight * 0.22f)
    val stackWidths = listOf(totalHeight * 0.5f, totalHeight * 0.38f, totalHeight * 0.26f)

    var currentY = leafBaseline
    for (i in 0..2) {
        val h = stackHeights[i]
        val w = stackWidths[i]

        val path = Path().apply {
            moveTo(x, currentY - h) // vertex tip
            lineTo(x - w / 2f, currentY) // bottom left
            lineTo(x + w / 2f, currentY) // bottom right
            close()
        }
        drawPath(path = path, color = treeColor)

        // Overlay small white light highlight on each branch leaf to add visual dimension
        val highlightPath = Path().apply {
            moveTo(x, currentY - h)
            lineTo(x - w * 0.15f, currentY)
            lineTo(x, currentY - h * 0.5f)
            close()
        }
        drawPath(path = highlightPath, color = Color.White.copy(alpha = 0.08f))

        // Slide upwards slightly for overlap
        currentY -= h * 0.55f
    }
}

// Clean simple Bezier/Sine transition block to prevent dependencies
private val SineBlock = Easing { fraction ->
    ((Math.sin(fraction * Math.PI - Math.PI / 2) + 1.0) / 2.0).toFloat()
}
