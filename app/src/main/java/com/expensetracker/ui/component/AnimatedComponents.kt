package com.expensetracker.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PulsingLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = PrimaryGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = color,
            strokeWidth = 3.dp
        )
    }
}

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    if (isVisible) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ShimmerBase,
                            ShimmerHighlight,
                            ShimmerBase
                        ),
                        startX = offset - 300f,
                        endX = offset + 300f
                    )
                )
        )
    }
}

@Composable
fun FadeInCard(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            animationSpec = tween(800, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 }
        ),
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun ScaleOnPressCard(
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.95f,
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    backgroundColor: Color = PrimaryGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val elevation by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "elevation"
    )
    
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = backgroundColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = elevation.dp
        )
    ) {
        icon()
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    typingDelayMs: Long = 50L
) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            delay(typingDelayMs)
            displayedText = text.substring(0, index + 1)
        }
    }
    
    Text(
        text = displayedText,
        modifier = modifier,
        style = style
    )
}

@Composable
fun WaveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = PrimaryGreen
) {
    val waves = listOf(
        remember { Animatable(0f) },
        remember { Animatable(0f) },
        remember { Animatable(0f) }
    )
    
    waves.forEachIndexed { index, wave ->
        LaunchedEffect(wave) {
            delay(index * 100L)
            wave.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        waves.forEach { wave ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scaleX = 1f, scaleY = wave.value)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun CountUpAnimation(
    targetValue: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    prefix: String = "",
    suffix: String = "",
    animationDurationMs: Int = 1000
) {
    var animatedValue by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(targetValue) {
        val targetFloat = targetValue.toFloat()
        animate(
            initialValue = 0f,
            targetValue = targetFloat,
            animationSpec = tween(
                durationMillis = animationDurationMs,
                easing = FastOutSlowInEasing
            )
        ) { value, _ ->
            animatedValue = value
        }
    }
    
    Text(
        text = "$prefix${String.format("%.0f", animatedValue)}$suffix",
        modifier = modifier,
        style = style
    )
} 