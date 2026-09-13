package com.expensetracker.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.theme.*
import java.text.NumberFormat
import java.util.*

@Composable
fun OverviewCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var isHovered by remember { mutableStateOf(false) }
    
    val animatedElevation by animateFloatAsState(
        targetValue = if (isHovered) 20f else 12f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    // Create gradient based on the color
    val gradientColors = listOf(
        color.copy(alpha = 0.1f),
        color.copy(alpha = 0.05f),
        Color.Transparent
    )
    
    val iconBackgroundGradient = listOf(
        color.copy(alpha = 0.2f),
        color.copy(alpha = 0.1f)
    )
    
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clickable(enabled = onClick != null) { 
                onClick?.invoke()
                isHovered = !isHovered
            }
            .shadow(
                elevation = animatedElevation.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = color.copy(alpha = 0.3f),
                spotColor = color.copy(alpha = 0.3f)
            ),
        cornerRadius = 24.dp,
        elevation = animatedElevation.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = gradientColors,
                        radius = 400f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        TypewriterText(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            ),
                            typingDelayMs = 30L
                        )
                    }
                    
                    // Animated icon with glassmorphism background
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                brush = Brush.radialGradient(iconBackgroundGradient),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = color.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                // Amount with animation
                CountUpAnimation(
                    targetValue = amount,
                    prefix = "₹",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    animationDurationMs = 1200
                )
                
                // Progress indicator
                LinearProgressIndicator(
                    progress = (amount / 100000).coerceAtMost(1.0).toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                )
            }
            
            // Floating decoration
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
                    .size(40.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun EnhancedOverviewCard(
    title: String,
    amount: Double,
    previousAmount: Double = 0.0,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val changePercent = if (previousAmount > 0) {
        ((amount - previousAmount) / previousAmount * 100)
    } else 0.0
    
    val isPositive = changePercent >= 0
    val changeColor = if (isPositive) SuccessGreen else ErrorRed
    
    FloatingGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )
                
                // Gradient icon background
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.1f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = color.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Amount
            CountUpAnimation(
                targetValue = amount,
                prefix = "₹",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                animationDurationMs = 1500
            )
            
            // Change indicator
            if (previousAmount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        shape = CircleShape,
                        color = changeColor.copy(alpha = 0.2f)
                    ) {
                        Icon(
                            imageVector = if (isPositive) 
                                Icons.Filled.TrendingUp 
                            else 
                                Icons.Filled.TrendingDown,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(4.dp)
                        )
                    }
                    
                    Text(
                        text = "${if (isPositive) "+" else ""}${String.format("%.1f", changePercent)}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = changeColor
                    )
                    
                    Text(
                        text = "vs last period",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
} 