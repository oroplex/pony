package app.pony.companion.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Pony's stroke icon set, generated from Lucide (ISC license, see
 * assets/licenses/LICENSE-Lucide.txt) by android/tools/lucide-icons.mjs.
 * Tint follows the caller. Do not edit by hand.
 */
object PonyIcons {
    const val STROKE = 1.75f

    /** Lucide `house` */
    val Home: ImageVector by lazy {
        icon(
            "Home",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    /** Lucide `rotate-ccw-clock` */
    val History: ImageVector by lazy {
        icon(
            "History",
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
            "M12 7v5l4 2",
        )
    }

    /** Lucide `settings-2` */
    val Settings: ImageVector by lazy {
        icon(
            "Settings",
            "M14 17H5",
            "M19 7h-9",
            "M14,17a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
            "M4,7a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        )
    }

    /** Lucide `mic` */
    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M12,2h0a3,3 0 0 1 3,3v7a3,3 0 0 1 -3,3h0a3,3 0 0 1 -3,-3v-7a3,3 0 0 1 3,-3z",
        )
    }

    /** Lucide `mic-off` */
    val MicOff: ImageVector by lazy {
        icon(
            "MicOff",
            "M12 19v3",
            "M15 9.34V5a3 3 0 0 0-5.68-1.33",
            "M16.95 16.95A7 7 0 0 1 5 12v-2",
            "M18.89 13.23A7 7 0 0 0 19 12v-2",
            "m2 2 20 20",
            "M9 9v3a3 3 0 0 0 5.12 2.12",
        )
    }

    /** Lucide `arrow-up` */
    val Send: ImageVector by lazy {
        icon(
            "Send",
            "m5 12 7-7 7 7",
            "M12 19V5",
        )
    }

    /** Lucide `square` */
    val Stop: ImageVector by lazy {
        icon(
            "Stop",
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
        )
    }

    /** Lucide `x` */
    val Close: ImageVector by lazy {
        icon(
            "Close",
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }

    /** Lucide `check` */
    val Check: ImageVector by lazy {
        icon(
            "Check",
            "M20 6 9 17l-5-5",
        )
    }

    /** Lucide `circle-check` */
    val CheckCircle: ImageVector by lazy {
        icon(
            "CheckCircle",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
            "m16 9-5.5 5.5L8 12",
        )
    }

    /** Lucide `chevron-right` */
    val ChevronRight: ImageVector by lazy {
        icon(
            "ChevronRight",
            "m9 18 6-6-6-6",
        )
    }

    /** Lucide `chevron-down` */
    val ChevronDown: ImageVector by lazy {
        icon(
            "ChevronDown",
            "m6 9 6 6 6-6",
        )
    }

    /** Lucide `arrow-left` */
    val Back: ImageVector by lazy {
        icon(
            "Back",
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }

    /** Lucide `sparkles` */
    val Sparkles: ImageVector by lazy {
        icon(
            "Sparkles",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2,20a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        )
    }

    /** Lucide `shield-check` */
    val Shield: ImageVector by lazy {
        icon(
            "Shield",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4",
        )
    }

    /** Lucide `lock` */
    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            "M5,11h14a2,2 0 0 1 2,2v7a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-7a2,2 0 0 1 2,-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    /** Lucide `eye-off` */
    val EyeOff: ImageVector by lazy {
        icon(
            "EyeOff",
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    /** Lucide `eye` */
    val Eye: ImageVector by lazy {
        icon(
            "Eye",
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        )
    }

    /** Lucide `zap` */
    val Bolt: ImageVector by lazy {
        icon(
            "Bolt",
            "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
        )
    }

    /** Lucide `battery-charging` */
    val Battery: ImageVector by lazy {
        icon(
            "Battery",
            "m11 7-3 5h4l-3 5",
            "M14.856 6H16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.935",
            "M22 14v-4",
            "M5.14 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.936",
        )
    }

    /** Lucide `bell` */
    val Bell: ImageVector by lazy {
        icon(
            "Bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }

    /** Lucide `phone` */
    val Phone: ImageVector by lazy {
        icon(
            "Phone",
            "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384",
        )
    }

    /** Lucide `wifi` */
    val Wifi: ImageVector by lazy {
        icon(
            "Wifi",
            "M12 20h.01",
            "M2 8.82a15 15 0 0 1 20 0",
            "M5 12.859a10 10 0 0 1 14 0",
            "M8.5 16.429a5 5 0 0 1 7 0",
        )
    }

    /** Lucide `wifi-off` */
    val WifiOff: ImageVector by lazy {
        icon(
            "WifiOff",
            "M12 20h.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
            "m2 2 20 20",
        )
    }

    /** Lucide `cloud` */
    val Cloud: ImageVector by lazy {
        icon(
            "Cloud",
            "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z",
        )
    }

    /** Lucide `link-2` */
    val Link: ImageVector by lazy {
        icon(
            "Link",
            "M9 17H7A5 5 0 0 1 7 7h2",
            "M15 7h2a5 5 0 1 1 0 10h-2",
            "M8,12L16,12",
        )
    }

    /** Lucide `qr-code` */
    val Qr: ImageVector by lazy {
        icon(
            "Qr",
            "M4,3h3a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1z",
            "M17,3h3a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1z",
            "M4,16h3a1,1 0 0 1 1,1v3a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-3a1,1 0 0 1 1,-1z",
            "M21 16h-3a2 2 0 0 0-2 2v3",
            "M21 21v.01",
            "M12 7v3a2 2 0 0 1-2 2H7",
            "M3 12h.01",
            "M12 3h.01",
            "M12 16v.01",
            "M16 12h1",
            "M21 12v.01",
            "M12 21v-1",
        )
    }

    /** Lucide `scan-line` */
    val Scan: ImageVector by lazy {
        icon(
            "Scan",
            "M3 7V5a2 2 0 0 1 2-2h2",
            "M17 3h2a2 2 0 0 1 2 2v2",
            "M21 17v2a2 2 0 0 1-2 2h-2",
            "M7 21H5a2 2 0 0 1-2-2v-2",
            "M7 12h10",
        )
    }

    /** Lucide `keyboard` */
    val Keyboard: ImageVector by lazy {
        icon(
            "Keyboard",
            "M10 8h.01",
            "M12 12h.01",
            "M14 8h.01",
            "M16 12h.01",
            "M18 8h.01",
            "M6 8h.01",
            "M7 16h10",
            "M8 12h.01",
            "M4,4h16a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-16a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z",
        )
    }

    /** Lucide `bot` */
    val Bot: ImageVector by lazy {
        icon(
            "Bot",
            "M12 8V4H8",
            "M6,8h12a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2h-12a2,2 0 0 1 -2,-2v-8a2,2 0 0 1 2,-2z",
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2",
        )
    }

    /** Lucide `brain` */
    val Brain: ImageVector by lazy {
        icon(
            "Brain",
            "M12 18V5",
            "M15 13a4.17 4.17 0 0 1-3-4 4.17 4.17 0 0 1-3 4",
            "M17.598 6.5A3 3 0 1 0 12 5a3 3 0 1 0-5.598 1.5",
            "M17.997 5.125a4 4 0 0 1 2.526 5.77",
            "M18 18a4 4 0 0 0 2-7.464",
            "M19.967 17.483A4 4 0 1 1 12 18a4 4 0 1 1-7.967-.517",
            "M6 18a4 4 0 0 1-2-7.464",
            "M6.003 5.125a4 4 0 0 0-2.526 5.77",
        )
    }

    /** Lucide `calculator` */
    val Calculator: ImageVector by lazy {
        icon(
            "Calculator",
            "M6,2h12a2,2 0 0 1 2,2v16a2,2 0 0 1 -2,2h-12a2,2 0 0 1 -2,-2v-16a2,2 0 0 1 2,-2z",
            "M8,6L16,6",
            "M16,14L16,18",
            "M16 10h.01",
            "M12 10h.01",
            "M8 10h.01",
            "M12 14h.01",
            "M8 14h.01",
            "M12 18h.01",
            "M8 18h.01",
        )
    }

    /** Lucide `timer` */
    val Timer: ImageVector by lazy {
        icon(
            "Timer",
            "M10,2L14,2",
            "M12,14L15,11",
            "M4,14a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
        )
    }

    /** Lucide `message-square` */
    val Message: ImageVector by lazy {
        icon(
            "Message",
            "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
        )
    }

    /** Lucide `camera` */
    val Camera: ImageVector by lazy {
        icon(
            "Camera",
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            "M9,13a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        )
    }

    /** Lucide `trash` */
    val Trash: ImageVector by lazy {
        icon(
            "Trash",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    /** Lucide `info` */
    val Info: ImageVector by lazy {
        icon(
            "Info",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
            "M12 16v-4",
            "M12 8h.01",
        )
    }

    /** Lucide `triangle-alert` */
    val Alert: ImageVector by lazy {
        icon(
            "Alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    /** Lucide `circle-alert` */
    val AlertCircle: ImageVector by lazy {
        icon(
            "AlertCircle",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
            "M12,8L12,12",
            "M12,16L12.01,16",
        )
    }

    /** Lucide `refresh-cw` */
    val Refresh: ImageVector by lazy {
        icon(
            "Refresh",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    /** Lucide `undo-2` */
    val Undo: ImageVector by lazy {
        icon(
            "Undo",
            "M9 14 4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5v0a5.5 5.5 0 0 1-5.5 5.5H11",
        )
    }

    /** Lucide `pencil` */
    val Pencil: ImageVector by lazy {
        icon(
            "Pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    /** Lucide `download` */
    val Download: ImageVector by lazy {
        icon(
            "Download",
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5",
        )
    }

    /** Lucide `external-link` */
    val External: ImageVector by lazy {
        icon(
            "External",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }

    /** Lucide `copy` */
    val Copy: ImageVector by lazy {
        icon(
            "Copy",
            "M10,8h10a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-10a2,2 0 0 1 2,-2z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    /** Lucide `moon` */
    val Moon: ImageVector by lazy {
        icon(
            "Moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    /** Lucide `sun` */
    val Sun: ImageVector by lazy {
        icon(
            "Sun",
            "M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    /** Lucide `smartphone` */
    val Phone2: ImageVector by lazy {
        icon(
            "Phone2",
            "M7,2h10a2,2 0 0 1 2,2v16a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-16a2,2 0 0 1 2,-2z",
            "M12 18h.01",
        )
    }

    /** Lucide `pointer` */
    val Tap: ImageVector by lazy {
        icon(
            "Tap",
            "M22 14a8 8 0 0 1-8 8",
            "M18 11v-1a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
            "M14 10V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1",
            "M10 9.5V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v10",
            "M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
        )
    }

    /** Lucide `type` */
    val Type: ImageVector by lazy {
        icon(
            "Type",
            "M12 4v16",
            "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
            "M9 20h6",
        )
    }

    /** Lucide `move` */
    val Swipe: ImageVector by lazy {
        icon(
            "Swipe",
            "M12 2v20",
            "m15 19-3 3-3-3",
            "m19 9 3 3-3 3",
            "M2 12h20",
            "m5 9-3 3 3 3",
            "m9 5 3-3 3 3",
        )
    }

    /** Lucide `volume-2` */
    val Speak: ImageVector by lazy {
        icon(
            "Speak",
            "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
            "M16 9a5 5 0 0 1 0 6",
            "M19.364 18.364a9 9 0 0 0 0-12.728",
        )
    }

    /** Lucide `message-circle-question-mark` */
    val Ask: ImageVector by lazy {
        icon(
            "Ask",
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
            "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3",
            "M12 17h.01",
        )
    }

    /** Lucide `pause` */
    val Pause: ImageVector by lazy {
        icon(
            "Pause",
            "M15,3h3a1,1 0 0 1 1,1v16a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-16a1,1 0 0 1 1,-1z",
            "M6,3h3a1,1 0 0 1 1,1v16a1,1 0 0 1 -1,1h-3a1,1 0 0 1 -1,-1v-16a1,1 0 0 1 1,-1z",
        )
    }

    /** Lucide `play` */
    val Play: ImageVector by lazy {
        icon(
            "Play",
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }

    /** Lucide `key-round` */
    val Key: ImageVector by lazy {
        icon(
            "Key",
            "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z",
            "M16,7.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
        )
    }

    /** Lucide `globe` */
    val Globe: ImageVector by lazy {
        icon(
            "Globe",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20",
        )
    }

    /** Lucide `server` */
    val Server: ImageVector by lazy {
        icon(
            "Server",
            "M4,2h16a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-16a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M4,14h16a2,2 0 0 1 2,2v4a2,2 0 0 1 -2,2h-16a2,2 0 0 1 -2,-2v-4a2,2 0 0 1 2,-2z",
            "M6,6L6.01,6",
            "M6,18L6.01,18",
        )
    }

    /** Lucide `radio` */
    val Radio: ImageVector by lazy {
        icon(
            "Radio",
            "M16.247 7.761a6 6 0 0 1 0 8.478",
            "M19.075 4.933a10 10 0 0 1 0 14.134",
            "M4.925 19.067a10 10 0 0 1 0-14.134",
            "M7.753 16.239a6 6 0 0 1 0-8.478",
            "M10,12a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        )
    }

    /** Lucide `activity` */
    val Activity: ImageVector by lazy {
        icon(
            "Activity",
            "M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2",
        )
    }

    /** Lucide `sliders-horizontal` */
    val Sliders: ImageVector by lazy {
        icon(
            "Sliders",
            "M10 5H3",
            "M12 19H3",
            "M14 3v4",
            "M16 17v4",
            "M21 12h-9",
            "M21 19h-5",
            "M21 5h-7",
            "M8 10v4",
            "M8 12H3",
        )
    }

    /** Lucide `palette` */
    val Palette: ImageVector by lazy {
        icon(
            "Palette",
            "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
            "M13,6.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
            "M17,10.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
            "M6,12.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
            "M8,7.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
        )
    }

    /** Lucide `wand-sparkles` */
    val Wand: ImageVector by lazy {
        icon(
            "Wand",
            "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72",
            "m14 7 3 3",
            "M5 6v4",
            "M19 14v4",
            "M10 2v2",
            "M7 8H3",
            "M21 16h-4",
            "M11 3H9",
        )
    }

    /** Lucide `image` */
    val Image: ImageVector by lazy {
        icon(
            "Image",
            "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
            "M7,9a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    /** Lucide `car` */
    val Car: ImageVector by lazy {
        icon(
            "Car",
            "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.5 2.8A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
            "M9 17h6",
            "M5,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
            "M15,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        )
    }

    /** Lucide `calendar` */
    val Calendar: ImageVector by lazy {
        icon(
            "Calendar",
            "M8 2v4",
            "M16 2v4",
            "M5,4h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2z",
            "M3 10h18",
        )
    }

    /** Lucide `music` */
    val Music: ImageVector by lazy {
        icon(
            "Music",
            "M9 18V5l12-2v13",
            "M3,18a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
            "M15,16a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        )
    }

    /** Lucide `mail` */
    val Mail: ImageVector by lazy {
        icon(
            "Mail",
            "M4,4h16a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-16a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z",
            "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7",
        )
    }

    /** Lucide `plane` */
    val Plane: ImageVector by lazy {
        icon(
            "Plane",
            "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z",
        )
    }

    /** Lucide `map-pin` */
    val MapPin: ImageVector by lazy {
        icon(
            "MapPin",
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            "M9,10a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        )
    }

    /** Lucide `alarm-clock` */
    val AlarmClock: ImageVector by lazy {
        icon(
            "AlarmClock",
            "M4,13a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
            "M12 9v4l2 2",
            "M5 3 2 6",
            "m22 6-3-3",
            "M6.38 18.7 4 21",
            "M17.64 18.67 20 21",
        )
    }

    /** Lucide `shopping-bag` */
    val ShoppingBag: ImageVector by lazy {
        icon(
            "ShoppingBag",
            "M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z",
            "M3 6h18",
            "M16 10a4 4 0 0 1-8 0",
        )
    }

    /** Lucide `bed-double` */
    val Bed: ImageVector by lazy {
        icon(
            "Bed",
            "M2 20v-8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8",
            "M4 10V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v4",
            "M2 18h20",
            "M12 10v-4",
        )
    }

    /** Lucide `utensils` */
    val Utensils: ImageVector by lazy {
        icon(
            "Utensils",
            "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2",
            "M7 2v20",
            "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7",
        )
    }

    /** Lucide `plus` */
    val Plus: ImageVector by lazy {
        icon(
            "Plus",
            "M5 12h14",
            "M12 5v14",
        )
    }

    /** Lucide `unplug` */
    val Unplug: ImageVector by lazy {
        icon(
            "Unplug",
            "m19 5 3-3",
            "m2 22 3-3",
            "M6.3 20.3a2.4 2.4 0 0 0 3.4 0L12 18l-6-6-2.3 2.3a2.4 2.4 0 0 0 0 3.4Z",
            "M7.5 13.5 10 11",
            "M10.5 16.5 13 14",
            "m12 6 6 6 2.3-2.3a2.4 2.4 0 0 0 0-3.4l-2.6-2.6a2.4 2.4 0 0 0-3.4 0Z",
        )
    }

    /** Lucide `clock` */
    val Clock: ImageVector by lazy {
        icon(
            "Clock",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
            "M12 6v6l4 2",
        )
    }

    /** Lucide `layers` */
    val Layers: ImageVector by lazy {
        icon(
            "Layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
            "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
            "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
        )
    }

    /** Lucide `fingerprint-pattern` */
    val Fingerprint: ImageVector by lazy {
        icon(
            "Fingerprint",
            "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
            "M14 13.12c0 2.38 0 6.38-1 8.88",
            "M17.29 21.02c.12-.6.43-2.3.5-3.02",
            "M2 12a10 10 0 0 1 18-6",
            "M2 16h.01",
            "M21.8 16c.2-2 .131-5.354 0-6",
            "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
            "M8.65 22c.21-.66.45-1.32.57-2",
            "M9 6.8a6 6 0 0 1 9 5.2v2",
        )
    }

    /** Lucide `hand` */
    val Hand: ImageVector by lazy {
        icon(
            "Hand",
            "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
            "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2",
            "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8",
            "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
        )
    }

    /** Lucide `search` */
    val Search: ImageVector by lazy {
        icon(
            "Search",
            "m21 21-4.34-4.34",
            "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
        )
    }

    /** Lucide `arrow-right` */
    val ArrowRight: ImageVector by lazy {
        icon(
            "ArrowRight",
            "M5 12h14",
            "m12 5 7 7-7 7",
        )
    }

    /** Lucide `rocket` */
    val Rocket: ImageVector by lazy {
        icon(
            "Rocket",
            "M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5",
            "M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09",
            "M9 12a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.4 22.4 0 0 1-4 2z",
            "M9 12H4s.55-3.03 2-4c1.62-1.08 5 .05 5 .05",
        )
    }

    /** Lucide `moon-star` */
    val Moon2: ImageVector by lazy {
        icon(
            "Moon2",
            "M18 5h4",
            "M20 3v4",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    /** Lucide `person-standing` */
    val Accessibility: ImageVector by lazy {
        icon(
            "Accessibility",
            "M11,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
            "m9 20 3-6 3 6",
            "m6 8 6 2 6-2",
            "M12 10v4",
        )
    }

    /** Lucide `audio-lines` */
    val Waves: ImageVector by lazy {
        icon(
            "Waves",
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3",
        )
    }

    /** Lucide `command` */
    val Command: ImageVector by lazy {
        icon(
            "Command",
            "M15 6v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3",
        )
    }

    /** Lucide `terminal` */
    val Terminal: ImageVector by lazy {
        icon(
            "Terminal",
            "M12 19h8",
            "m4 17 6-6-6-6",
        )
    }

    /** Lucide `package` */
    val Package: ImageVector by lazy {
        icon(
            "Package",
            "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
            "M12 22V12",
            "M3.29,7L12,12L20.71,7",
            "m7.5 4.27 9 5.15",
        )
    }

    /** Lucide `circle` */
    val Circle: ImageVector by lazy {
        icon(
            "Circle",
            "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        )
    }

    /** Lucide `screen-share` */
    val ScreenShare: ImageVector by lazy {
        icon(
            "ScreenShare",
            "M13 3H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3",
            "M8 21h8",
            "M12 17v4",
            "m17 8 5-5",
            "M17 3h5v5",
        )
    }

    /** Lucide `phone-off` */
    val PhoneOff: ImageVector by lazy {
        icon(
            "PhoneOff",
            "M10.1 13.9a14 14 0 0 0 3.732 2.668 1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2 18 18 0 0 1-12.728-5.272",
            "M22 2 2 22",
            "M4.76 13.582A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 .244.473",
        )
    }

    /** Lucide `monitor-smartphone` */
    val Monitor: ImageVector by lazy {
        icon(
            "Monitor",
            "M18 8V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h8",
            "M10 19v-3.96 3.15",
            "M7 19h5",
            "M18,12h2a2,2 0 0 1 2,2v6a2,2 0 0 1 -2,2h-2a2,2 0 0 1 -2,-2v-6a2,2 0 0 1 2,-2z",
        )
    }

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = "Pony.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (path in paths) {
            builder.addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
