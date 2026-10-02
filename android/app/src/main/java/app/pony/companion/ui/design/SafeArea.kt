package app.pony.companion.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The area Pony may draw content in. On a phone this is the real safe
 * drawing area (status bar, navigation bar, cutout, keyboard). Screenshot
 * tests provide fixed insets the size of a Galaxy's bars.
 */
val LocalSafeInsets = staticCompositionLocalOf<WindowInsets?> { null }

@Composable
fun safeInsets(): WindowInsets = LocalSafeInsets.current ?: WindowInsets.safeDrawing

@Composable
fun safeTop(): WindowInsets = safeInsets().only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun safeBottom(): WindowInsets = safeInsets().only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

@Composable
fun safeHorizontal(): WindowInsets = safeInsets().only(WindowInsetsSides.Horizontal)

/**
 * An opaque band behind the status bar. Content never scrolls under the
 * clock, signal, or battery; the band matches the screen so the bar reads
 * as part of Pony.
 */
@Composable
fun StatusBarBand(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(safeInsets())
            .background(color),
    )
}

/** The same band behind the navigation bar (and the keyboard): scrolling content stops above it. */
@Composable
fun NavBarBand(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(safeInsets())
            .background(color),
    )
}
