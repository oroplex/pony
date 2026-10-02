package app.pony.companion.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pony.companion.R

val Serif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

val Mono = FontFamily(Font(R.font.geist_mono_medium, FontWeight.Medium))

/**
 * Editorial serif for moments that should feel calm and human, Geist for
 * everything you read and tap, and Geist Mono for codes. All sizes are sp,
 * so they follow the owner's font size.
 */
@Immutable
data class PonyType(
    val hero: TextStyle,
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val mono: TextStyle,
    val code: TextStyle,
)

private val tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val noPadding = PlatformTextStyle(includeFontPadding = false)

private fun serif(size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = tight,
    platformStyle = noPadding,
)

private fun sans(size: Double, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = tight,
    platformStyle = noPadding,
)

val PonyTypeScale = PonyType(
    hero = serif(52, 54, -0.02),
    display = serif(42, 46, -0.015),
    headline = serif(34, 38, -0.01),
    title = sans(20.0, 26, FontWeight.SemiBold, -0.01),
    titleSmall = sans(17.0, 22, FontWeight.SemiBold, -0.005),
    body = sans(16.0, 24, FontWeight.Normal),
    bodyStrong = sans(16.0, 24, FontWeight.Medium),
    bodySmall = sans(14.0, 20, FontWeight.Normal),
    label = sans(14.0, 18, FontWeight.Medium, 0.005),
    button = sans(16.0, 20, FontWeight.SemiBold, 0.0),
    caption = sans(12.5, 16, FontWeight.Normal, 0.01),
    overline = sans(11.5, 14, FontWeight.SemiBold, 0.12),
    mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, platformStyle = noPadding),
    code = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = 0.06.em, platformStyle = noPadding),
)
