package com.applivity.fieldsafesolar.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Always dark — RealWear HMT field use, no light mode, no dynamic color
private val FieldSafeDarkColorScheme = darkColorScheme(
    primary = FieldSafeColors.Primary,
    onPrimary = FieldSafeColors.OnPrimary,
    primaryContainer = FieldSafeColors.PrimaryContainer,
    onPrimaryContainer = FieldSafeColors.OnPrimaryContainer,
    secondary = FieldSafeColors.Secondary,
    onSecondary = FieldSafeColors.OnSecondary,
    secondaryContainer = FieldSafeColors.SecondaryContainer,
    onSecondaryContainer = FieldSafeColors.OnSecondaryContainer,
    background = FieldSafeColors.Background,
    onBackground = FieldSafeColors.OnBackground,
    surface = FieldSafeColors.Surface,
    onSurface = FieldSafeColors.OnSurface,
    surfaceVariant = FieldSafeColors.SurfaceVariant,
    onSurfaceVariant = FieldSafeColors.OnSurfaceVariant,
    outline = FieldSafeColors.Outline,
    error = FieldSafeColors.Error,
    onError = FieldSafeColors.OnError,
    errorContainer = FieldSafeColors.ErrorContainer,
    onErrorContainer = FieldSafeColors.OnErrorContainer,
    scrim = FieldSafeColors.Scrim,
)

@Composable
fun FieldSafeSolarTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = FieldSafeColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = FieldSafeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
