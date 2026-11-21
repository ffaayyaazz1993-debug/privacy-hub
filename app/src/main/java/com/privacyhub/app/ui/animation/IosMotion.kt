package com.privacyhub.app.ui.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
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

/** Short, GPU-only transitions: alpha + translation, no layout or canvas work. */
object IosMotion {
    val enterDuration = 180
    val pressSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)
}

@Composable
fun IosScreenEnter(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(IosMotion.enterDuration, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(IosMotion.enterDuration, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 14 },
            ),
    ) { content() }
}

/** iOS-style press feedback without a ripple; only scale (graphicsLayer) animates. */
fun Modifier.iosPress(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, IosMotion.pressSpring, label = "iosPress")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}
