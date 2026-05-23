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
                // Deep mysterious space nebula with twinkling stars
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // 1. Base cosmos dark blue to indigo black gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF050B1B),
                                Color(0xFF02040B)
                            )
                        )
                    )

                    // 2. Cosmic nebulae radial gas clouds
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF).copy(alpha = 0.25f), // Violet nebula
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f + offsetFactor, height * 0.4f),
                            radius = width * 0.8f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.18f), // Cyan secondary nebula
                                Color.Transparent
                            ),
                            center = Offset(width * 0.8f - offsetFactor, height * 0.6f + offsetFactor),
                            radius = width * 0.7f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF4081).copy(alpha = 0.12f * pulseAlpha), // Pink cosmic blush
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.2f),
                            radius = width * 0.5f
                        )
                    )

                    // 3. Draw standard twinkling stars
                    val starCoordinates = listOf(
                        Offset(width * 0.12f, height * 0.18f) to 3f,
                        Offset(width * 0.82f, height * 0.15f) to 2.5f,
                        Offset(width * 0.35f, height * 0.28f) to 4f,
                        Offset(width * 0.65f, height * 0.45f) to 1.8f,
                        Offset(width * 0.15f, height * 0.55f) to 3.5f,
                        Offset(width * 0.75f, height * 0.65f) to 5f,
                        Offset(width * 0.28f, height * 0.78f) to 2f,
                        Offset(width * 0.9f, height * 0.82f) to 4f,
                        Offset(width * 0.45f, height * 0.9f) to 3.2f,
                        Offset(width * 0.55f, height * 0.1f) to 3.8f,
                    )

                    starCoordinates.forEach { (pos, sizeVal) ->
                        // Calculate pulse scale specifically for this star to avoid uniformity
                        val pulse = ((Math.sin(((pos.x + pos.y) + (pulseAlpha * 10f)).toDouble()) + 1.0) / 2.0).toFloat()
                        val currentSize = sizeVal * (0.4f + pulse * 1.2f)

                        // Outer glowing halo
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f * pulse),
                            radius = currentSize * 3.5f,
                            center = pos
                        )
                        // Inner brilliant white core
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = currentSize,
                            center = pos
                        )
                    }

                    // 4. Subtle center galaxy dust
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.5f),
                            radius = width * 0.35f
                        )
                    )
                }
            }
            WallpaperType.RAINY -> {
                // Rainy Window glass bokeh looking at fuzzy warm streetlights
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // 1. Dark wet rainy night base background
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F172A), // Slate Grey Dark Blue
                                Color(0xFF020617)  // Deepest obsidian night
                            )
                        )
                    )

                    // 2. Huge blurry street glass lights (Bokeh circles)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD54F).copy(alpha = 0.15f + offsetFactor * 0.001f), // Fuzzy amber street lamp bokeh
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f, height * 0.35f),
                            radius = width * 0.42f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF14B8A6).copy(alpha = 0.12f), // Fuzzy teal light bokeh
                                Color.Transparent
                            ),
                            center = Offset(width * 0.75f, height * 0.52f),
                            radius = width * 0.5f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEC4899).copy(alpha = 0.08f), // Fuzzy pink signage light bokeh
                                Color.Transparent
                            ),
                            center = Offset(width * 0.55f, height * 0.72f),
                            radius = width * 0.35f
                        )
                    )

                    // 3. Render tiny crisp water condensation droplets on foreground pane
                    val waterDroplets = listOf(
                        Offset(width * 0.22f, height * 0.15f) to 6f,
                        Offset(width * 0.76f, height * 0.22f) to 9f,
                        Offset(width * 0.45f, height * 0.3f) to 5f,
                        Offset(width * 0.88f, height * 0.4f) to 7f,
                        Offset(width * 0.18f, height * 0.48f) to 11f,
                        Offset(width * 0.65f, height * 0.6f) to 5.5f,
                        Offset(width * 0.32f, height * 0.68f) to 8f,
                        Offset(width * 0.78f, height * 0.76f) to 12f,
                        Offset(width * 0.25f, height * 0.85f) to 4.5f,
                        Offset(width * 0.52f, height * 0.9f) to 10f,
                        Offset(width * 0.91f, height * 0.62f) to 7.5f,
                        Offset(width * 0.09f, height * 0.72f) to 6f
                    )

                    waterDroplets.forEach { (pos, radius) ->
                        // Simulate water droplet depth: a tiny dark shadow at the bottom right, and a tiny highlight at top-left
                        // Droplet background tint (inherits background glow with slight lens magnification)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = radius,
                            center = pos
                        )
                        // Dark border outline
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.2f),
                            radius = radius + 0.5f,
                            center = pos,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )
                        // Tiny sparkling light highlight on edge representing wet condensation
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f),
                            radius = radius * 0.3f,
                            center = pos + Offset(-radius * 0.35f, -radius * 0.35f)
                        )
                    }
                }
            }
            WallpaperType.COCOA -> {
                // Warm Organic Cocoa Clay Abstract Landscape shapes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // 1. Cozy warm oatmeal sand base background
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF7F2EB), // Oatmeal sand cream
                                Color(0xFFEADBCE)  // Warm beige/clay
                            )
                        )
                    )

                    // 2. Abstract smooth organic nested clay waves and circles
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFD7A88A).copy(alpha = 0.6f), // clay orange
                                Color.Transparent
                            ),
                            center = Offset(width * 0.2f + offsetFactor * 0.4f, height * 0.25f),
                            radius = width * 0.78f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFB18F7B).copy(alpha = 0.4f), // warm cocoa
                                Color.Transparent
                            ),
                            center = Offset(width * 0.85f, height * 0.7f - offsetFactor * 0.6f),
                            radius = width * 0.9f
                        )
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEFD2BD).copy(alpha = 0.75f), // peach clay
                                Color.Transparent
                            ),
                            center = Offset(width * 0.65f, height * 0.45f),
                            radius = width * 0.5f
                        )
                    )
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
