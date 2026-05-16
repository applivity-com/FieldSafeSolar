package com.applivity.fieldsafesolar.ui.theme

import androidx.compose.ui.graphics.Color

// FieldSafe Solar — 4-color RealWear safety palette
// Rules: dark background, safety yellow-green primary, amber warning, red danger

object FieldSafeColors {
    // Backgrounds
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceVariant = Color(0xFF2A2A2A)

    // Primary — safety yellow-green (safe/go/confirm)
    val Primary = Color(0xFF8BC34A)
    val OnPrimary = Color(0xFF1A2600)
    val PrimaryContainer = Color(0xFF2D3D0E)
    val OnPrimaryContainer = Color(0xFFC5E87A)

    // Secondary — neutral dark (secondary actions)
    val Secondary = Color(0xFF3A3A3A)
    val OnSecondary = Color(0xFFE0E0E0)
    val SecondaryContainer = Color(0xFF2A2A2A)
    val OnSecondaryContainer = Color(0xFFBDBDBD)

    // Warning — amber (caution/custom answer)
    val Warning = Color(0xFFFF9800)
    val OnWarning = Color(0xFF1A0E00)
    val WarningContainer = Color(0xFF3D2500)
    val OnWarningContainer = Color(0xFFFFCC80)

    // Danger — red (no/stop/fail)
    val Danger = Color(0xFFD32F2F)
    val OnDanger = Color(0xFFFFFFFF)
    val DangerContainer = Color(0xFF4A0A0A)
    val OnDangerContainer = Color(0xFFFF8A80)

    // Safety state aliases (used across the app)
    val SafeGreen = Color(0xFF4CAF50)
    val WarningAmber = Color(0xFFFF9800)
    val DangerRed = Color(0xFFD32F2F)
    val StopWorkRed = Color(0xFFB71C1C)

    // Text
    val OnBackground = Color(0xFFEEEEEE)
    val OnSurface = Color(0xFFE0E0E0)
    val OnSurfaceVariant = Color(0xFF9E9E9E)
    val OnPrimary2 = Color(0xFFFFFFFF)

    // Misc
    val Outline = Color(0xFF4A4A4A)
    val OutlineVariant = Color(0xFF333333)
    val Scrim = Color(0xFF000000)
    val Error = Color(0xFFCF6679)
    val OnError = Color(0xFF1A0008)
    val ErrorContainer = Color(0xFF4A0D1A)
    val OnErrorContainer = Color(0xFFFF99B3)
}

// Legacy aliases — keep so existing screens don't break
val Purple80 = Color(0xFF8BC34A)
val PurpleGrey80 = Color(0xFF9E9E9E)
val Pink80 = Color(0xFFFF9800)
val Purple40 = Color(0xFF558B2F)
val PurpleGrey40 = Color(0xFF616161)
val Pink40 = Color(0xFFE65100)
