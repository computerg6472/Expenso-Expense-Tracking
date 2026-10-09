package com.spendwise.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable, physics-snapping Floating AI Button that never overlaps the "+" Add Expense button.
 * Snaps to nearest screen edge (left or right), respects system bars, keyboard and bottom bar,
 * and remembers orientation positions.
 */
@Composable
fun FloatingAiButton(
    visible: Boolean,
    hasBottomBar: Boolean,
    onClick: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current

    val buttonSize = 52.dp
    val buttonSizePx = with(density) { buttonSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val fabSizePx = with(density) { 56.dp.toPx() }
    val fabSpacingPx = with(density) { 14.dp.toPx() }
    val bottomBarHeightPx = with(density) { 80.dp.toPx() }

    val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topSafePx = with(density) { statusBarsTop.toPx() } + marginPx
    val bottomSafePx = with(density) { navBarsBottom.toPx() } + (if (hasBottomBar) bottomBarHeightPx else marginPx)

    var showMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Default initial placement: directly above the Add Expense FAB on the right
        val defaultX = screenWidthPx - marginPx - buttonSizePx
        val defaultY = (screenHeightPx - bottomSafePx - fabSizePx - fabSpacingPx - buttonSizePx)
            .coerceAtLeast(topSafePx)

        val offsetX = remember { Animatable(defaultX) }
        val offsetY = remember { Animatable(defaultY) }

        // Track per-orientation Y ratio to restore position cleanly
        var portraitYRatio by remember { mutableFloatStateOf(0.72f) }
        var landscapeYRatio by remember { mutableFloatStateOf(0.65f) }
        var dockedOnRight by remember { mutableStateOf(true) }

        // Adjust position on screen size / orientation changes
        LaunchedEffect(screenWidthPx, screenHeightPx, isLandscape) {
            val targetX = if (dockedOnRight) screenWidthPx - marginPx - buttonSizePx else marginPx
            val currentRatio = if (isLandscape) landscapeYRatio else portraitYRatio
            val rawY = screenHeightPx * currentRatio
            val clampedY = rawY.coerceIn(topSafePx, screenHeightPx - bottomSafePx - buttonSizePx)
            
            offsetX.snapTo(targetX)
            offsetY.snapTo(clampedY)
        }

        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .size(buttonSize)
                    .semantics { contentDescription = "Ask Expenso AI" }
                    .pointerInput(Unit) {
                        val touchSlop = viewConfig.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            var isDrag = false
                            var longPressTriggered = false

                            val longPressJob = coroutineScope.launch {
                                delay(400)
                                if (!isDrag) {
                                    longPressTriggered = true
                                    showMenu = true
                                }
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) {
                                        longPressJob.cancel()
                                        if (isDrag) {
                                            // Finger released after drag -> Snap to nearest edge!
                                            val isRight = offsetX.value + (buttonSizePx / 2) >= (screenWidthPx / 2)
                                            dockedOnRight = isRight
                                            val targetX = if (isRight) screenWidthPx - marginPx - buttonSizePx else marginPx

                                            var targetY = offsetY.value.coerceIn(
                                                topSafePx,
                                                screenHeightPx - bottomSafePx - buttonSizePx
                                            )

                                            // If on the right and bottom bar is showing, prevent overlapping the FAB
                                            if (isRight && hasBottomBar) {
                                                val fabTop = screenHeightPx - bottomSafePx - fabSizePx
                                                if (targetY + buttonSizePx > fabTop - fabSpacingPx) {
                                                    targetY = (fabTop - fabSpacingPx - buttonSizePx).coerceAtLeast(topSafePx)
                                                }
                                            }

                                            // Update orientation ratio
                                            val ratio = (targetY / screenHeightPx).coerceIn(0.1f, 0.9f)
                                            if (isLandscape) landscapeYRatio = ratio else portraitYRatio = ratio

                                            coroutineScope.launch {
                                                launch {
                                                    offsetX.animateTo(
                                                        targetX,
                                                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                                    )
                                                }
                                                launch {
                                                    offsetY.animateTo(
                                                        targetY,
                                                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                                    )
                                                }
                                            }
                                        } else if (!longPressTriggered) {
                                            onClick()
                                        }
                                        change.consume()
                                        break
                                    }

                                    val totalDelta = change.position - down.position
                                    if (!isDrag && totalDelta.getDistance() > touchSlop) {
                                        isDrag = true
                                        longPressJob.cancel()
                                    }

                                    if (isDrag) {
                                        val delta = change.positionChange()
                                        val newX = (offsetX.value + delta.x).coerceIn(0f, screenWidthPx - buttonSizePx)
                                        val newY = (offsetY.value + delta.y).coerceIn(topSafePx, screenHeightPx - bottomSafePx - buttonSizePx)
                                        coroutineScope.launch {
                                            offsetX.snapTo(newX)
                                            offsetY.snapTo(newY)
                                        }
                                        change.consume()
                                    }
                                }
                            } finally {
                                longPressJob.cancel()
                            }
                        }
                    }
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2563EB), // Primary Electric Blue
                                Color(0xFF4F46E5), // Indigo Accent
                                Color(0xFF7C3AED)  // Purple Accent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Ask Expenso AI",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )

                // Long-press Context Menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.widthIn(min = 180.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Ask AI") },
                        leadingIcon = {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hide floating button") },
                        leadingIcon = {
                            Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showMenu = false
                            onHide()
                        }
                    )
                }
            }
        }
    }
}
