package app.pony.companion.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode(val wire: String, val label: String) {
    SYSTEM("system", "Match phone"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun of(wire: String?): ThemeMode = entries.firstOrNull { it.wire == wire } ?: SYSTEM
    }
}

val LocalPonyColors = staticCompositionLocalOf { Palette.Night }
val LocalPonyType = staticCompositionLocalOf { PonyTypeScale }

/** True when the owner turned animations off. Ambient motion stops; feedback stays. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Screenshot tests render ambient animation at a fixed, flattering phase. */
val LocalStillFrame = staticCompositionLocalOf { false }

object Pony {
    val colors: PonyColors
        @Composable @ReadOnlyComposable get() = LocalPonyColors.current

    val type: PonyType
        @Composable @ReadOnlyComposable get() = LocalPonyType.current
}

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PonyTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    PonyTheme(dark = resolveDark(mode), content = content)
}

@Composable
fun PonyTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) Palette.Night else Palette.Day
    val context = LocalContext.current
    val reduce = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.iris,
            onPrimary = colors.onPrimary,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.inkMuted,
            outline = colors.control,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.iris,
            onPrimary = colors.onIris,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.inkMuted,
            outline = colors.control,
            error = colors.danger,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = PonyTypeScale.body,
            bodyMedium = PonyTypeScale.bodySmall,
            labelLarge = PonyTypeScale.label,
            titleMedium = PonyTypeScale.titleSmall,
        ),
    ) {
        CompositionLocalProvider(
            LocalPonyColors provides colors,
            LocalPonyType provides PonyTypeScale,
            LocalContentColor provides colors.ink,
            LocalReduceMotion provides reduce,
            LocalTextSelectionColors provides TextSelectionColors(colors.iris, colors.irisSoft),
            content = content,
        )
    }
}
