package app.pony.companion.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Nightfall, Pony's color system. Ink and moonlight carry the interface,
 * iris is Pony's own voice, and mane gold is kept for moments of trust:
 * the safety code, a verified connection, and success.
 * Every text pair here meets WCAG AA; ContrastTest keeps it that way.
 */
@Immutable
data class PonyColors(
    val dark: Boolean,
    val canvas: Color,
    val canvasGlow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val iris: Color,
    val irisFill: Color,
    val onIris: Color,
    val irisSoft: Color,
    val azure: Color,
    val rose: Color,
    val mane: Color,
    val maneSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerFill: Color,
    val dangerSoft: Color,
    val primaryFill: Color,
    val onPrimary: Color,
    val control: Color,
    val controlOn: Color,
    val scrim: Color,
    val shadow: Color,
)

object Palette {
    val Night = PonyColors(
        dark = true,
        canvas = Color(0xFF0B0A13),
        canvasGlow = Color(0xFF16132B),
        surface = Color(0xFF15141F),
        surfaceHigh = Color(0xFF1D1C2A),
        surfaceHighest = Color(0xFF262437),
        hairline = Color(0x14FFFFFF),
        hairlineStrong = Color(0x24FFFFFF),
        ink = Color(0xFFF4F2FB),
        inkMuted = Color(0xFFABA7BF),
        inkFaint = Color(0xFF8C889F),
        iris = Color(0xFFA195FF),
        irisFill = Color(0xFF6D5CF5),
        onIris = Color(0xFFFFFFFF),
        irisSoft = Color(0x297C6CFF),
        azure = Color(0xFF5EC8FF),
        rose = Color(0xFFFF7AC6),
        mane = Color(0xFFF2C66D),
        maneSoft = Color(0x24F2C66D),
        success = Color(0xFF4ADE9B),
        successSoft = Color(0x1F4ADE9B),
        warning = Color(0xFFFFB84D),
        warningSoft = Color(0x1FFFB84D),
        danger = Color(0xFFFF7A8A),
        dangerFill = Color(0xFFD63347),
        dangerSoft = Color(0x24FF5C72),
        primaryFill = Color(0xFFF4F2FB),
        onPrimary = Color(0xFF0B0A13),
        control = Color(0xFF76728E),
        controlOn = Color(0xFF7C6CFF),
        scrim = Color(0xB3050409),
        shadow = Color(0xFF000000),
    )

    val Day = PonyColors(
        dark = false,
        canvas = Color(0xFFF6F5FB),
        canvasGlow = Color(0xFFE9E4FF),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFF0EEF8),
        surfaceHighest = Color(0xFFE7E4F2),
        hairline = Color(0x140E0D18),
        hairlineStrong = Color(0x240E0D18),
        ink = Color(0xFF0E0D18),
        inkMuted = Color(0xFF4F4C63),
        inkFaint = Color(0xFF67637B),
        iris = Color(0xFF5641E0),
        irisFill = Color(0xFF5A46EA),
        onIris = Color(0xFFFFFFFF),
        irisSoft = Color(0x1F5641E0),
        azure = Color(0xFF2E9BE0),
        rose = Color(0xFFE0569F),
        mane = Color(0xFF8A5F00),
        maneSoft = Color(0x29D9A441),
        success = Color(0xFF0A7446),
        successSoft = Color(0x1A0A7446),
        warning = Color(0xFF9A5600),
        warningSoft = Color(0x1F9A5600),
        danger = Color(0xFFC0223A),
        dangerFill = Color(0xFFC92A40),
        dangerSoft = Color(0x1AC0223A),
        primaryFill = Color(0xFF0E0D18),
        onPrimary = Color(0xFFFFFFFF),
        control = Color(0xFF7A7590),
        controlOn = Color(0xFF5641E0),
        scrim = Color(0x800E0D18),
        shadow = Color(0xFF2A2350),
    )

    /** The live orb is always drawn in these, whatever the theme. */
    val OrbIris = Color(0xFF7B6CFF)
    val OrbAzure = Color(0xFF5EC8FF)
    val OrbRose = Color(0xFFFF7AC6)
    val OrbMane = Color(0xFFF6CD76)
    val OrbCore = Color(0xFFF7F5FF)
}
