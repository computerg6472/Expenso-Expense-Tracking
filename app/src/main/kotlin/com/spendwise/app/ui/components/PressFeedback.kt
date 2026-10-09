package com.spendwise.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * GPU-accelerated touch press feedback modifier (subtle scale and opacity).
 * Responds instantaneously on touch down with zero layout recalculation.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    alphaDown: Float = 0.88f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "bounce_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) alphaDown else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "bounce_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * GPU-accelerated staggered entrance animation for cards/sections.
 * Animates only opacity and translationY (transform) to prevent CPU relayout passes.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelayMs: Int = 40
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (index > 0) {
            delay((index * baseDelayMs).toLong())
        }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "stagger_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "stagger_offset"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY
    }
}
