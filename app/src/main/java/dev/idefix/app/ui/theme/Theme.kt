package dev.idefix.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.idefix.app.domain.model.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A2E15),
    onPrimaryContainer = AmberLight,
    secondary = Teal,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF173B38),
    onSecondaryContainer = Color(0xFFB8F1EA),
    tertiary = Coral,
    onTertiary = Ink,
    background = Ink,
    onBackground = Color(0xFFE7EAF0),
    surface = InkElevated,
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = InkElevated2,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkElevated,
    surfaceContainer = InkElevated2,
    surfaceContainerHigh = Color(0xFF1D232C),
    surfaceContainerHighest = Color(0xFF242B35),
    outline = InkOutline,
    outlineVariant = Color(0xFF1F252E),
    error = Coral,
    onError = Ink,
    errorContainer = Color(0xFF4A1F1B),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE7B3),
    onPrimaryContainer = Color(0xFF3D2B00),
    secondary = Color(0xFF206A62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F1EA),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF9E3D31),
    onTertiary = Color(0xFFFFFFFF),
    background = Paper,
    onBackground = Color(0xFF191C20),
    surface = PaperElevated,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFEDE8DE),
    onSurfaceVariant = Color(0xFF4E5560),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = PaperElevated,
    surfaceContainer = Color(0xFFF1EDE4),
    surfaceContainerHigh = Color(0xFFEAE5DA),
    surfaceContainerHighest = Color(0xFFE3DDD1),
    outline = Color(0xFF7C838D),
    outlineVariant = Color(0xFFD7D1C5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun IdefixTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = IdefixTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

