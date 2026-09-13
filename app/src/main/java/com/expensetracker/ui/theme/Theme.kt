package com.expensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = NeutralGray50,
    primaryContainer = PrimaryGreenDark,
    onPrimaryContainer = NeutralGray50,
    secondary = SecondaryBlue,
    onSecondary = NeutralGray50,
    secondaryContainer = SecondaryBlueDark,
    onSecondaryContainer = NeutralGray50,
    tertiary = AccentPurple,
    onTertiary = NeutralGray50,
    background = BackgroundGradientStartDark,
    onBackground = NeutralGray100,
    surface = SurfaceGlassDark,
    onSurface = NeutralGray100,
    surfaceVariant = CardGradientStartDark,
    onSurfaceVariant = NeutralGray300,
    error = ErrorRed,
    onError = NeutralGray50,
    errorContainer = ErrorRedLight,
    onErrorContainer = NeutralGray900,
    outline = NeutralGray600,
    outlineVariant = NeutralGray700
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = NeutralGray50,
    primaryContainer = SuccessGreenLight,
    onPrimaryContainer = NeutralGray900,
    secondary = SecondaryBlue,
    onSecondary = NeutralGray50,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDBEAFE),
    onSecondaryContainer = NeutralGray900,
    tertiary = AccentPurple,
    onTertiary = NeutralGray50,
    background = BackgroundGradientStart,
    onBackground = NeutralGray900,
    surface = SurfaceGlass,
    onSurface = NeutralGray900,
    surfaceVariant = CardGradientStart,
    onSurfaceVariant = NeutralGray600,
    error = ErrorRed,
    onError = NeutralGray50,
    errorContainer = ErrorRedLight,
    onErrorContainer = NeutralGray900,
    outline = NeutralGray400,
    outlineVariant = NeutralGray300
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 