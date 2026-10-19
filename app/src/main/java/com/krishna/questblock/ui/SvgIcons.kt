package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em

/** v224: emoji are drawn as SVG icons (24 x 24 grid, stroke 2) in every Text of the normal pages. Switch: Settings > Icons, terminal: icons. */
class SvgIcon(val paths: List<String>, val fill: Long = 0L)

var SvgIconsOn by mutableStateOf(true)

/** v226: icon names switched off one by one (flags > SVG icons > sub page). Their emoji stay as text. */
var SvgIconsOff by mutableStateOf<Set<String>>(emptySet())

object SvgIconsMode {
    private const val KEY = "svg_icons"
    private const val OFF_KEY = "svg_icons_off"
    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        SvgIconsOn = sp.getBoolean(KEY, true)
        SvgIconsOff = (sp.getString(OFF_KEY, "") ?: "").split(',').filter { it.isNotEmpty() }.toSet()
    }
    private fun saveOff(ctx: Context, s: Set<String>) {
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putString(OFF_KEY, s.joinToString(",")).apply()
        SvgIconsOff = s
    }
    fun setIcon(ctx: Context, name: String, on: Boolean) = saveOff(ctx, if (on) SvgIconsOff - name else SvgIconsOff + name)
    fun setAll(ctx: Context, on: Boolean) = saveOff(ctx, if (on) emptySet() else SvgIcons.EMOJI_ICONS)
    fun set(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        SvgIconsOn = on
    }
}

class SvgTextResult(val text: AnnotatedString, val names: Set<String>)

object SvgIcons {
    val ICONS: Map<String, SvgIcon> = mapOf(
    "coin" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9.5 9.5h4a1.5 1.5 0 0 1 0 3h-3a1.5 1.5 0 0 0 0 3h4", "M12 7.5v2", "M12 15v1.5")),
    "bolt" to SvgIcon(listOf("M13 3v7h6l-8 11v-7H5l8-11z")),
    "check" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M8 12l3 3 5-6")),
    "target" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 11.5a.5.5 0 1 0 0 1a.5.5 0 1 0 0-1z")),
    "flame" to SvgIcon(listOf("M12 3c1 3 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-5 1-9z")),
    "shield" to SvgIcon(listOf("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z")),
    "clipboard" to SvgIcon(listOf("M9 4h6v3H9z", "M7 5.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6.5a1 1 0 0 0-1-1h-1", "M8.5 12h7", "M8.5 16h7")),
    "gamepad" to SvgIcon(listOf("M7 8h10a4 4 0 0 1 4 4v1a3 3 0 0 1-5.2 2L14.5 14h-5l-1.3 1A3 3 0 0 1 3 13v-1a4 4 0 0 1 4-4z", "M8 10.5v3", "M6.5 12h3", "M16 11.5h.01", "M18 13h.01")),
    "headphones" to SvgIcon(listOf("M4 15v-3a8 8 0 0 1 16 0v3", "M4 15h3v5H5a1 1 0 0 1-1-1z", "M20 15h-3v5h2a1 1 0 0 0 1-1z")),
    "book" to SvgIcon(listOf("M5 4h11a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z", "M5 18a2 2 0 0 1 2-2h11")),
    "books" to SvgIcon(listOf("M5 4h3v16H5z", "M10 4h3v16h-3z", "M15.5 5.5l2.8-.8 3.2 14.6-2.8.8z")),
    "doc" to SvgIcon(listOf("M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M14 3v5h5", "M9 13h6", "M9 17h6")),
    "scroll" to SvgIcon(listOf("M8 4h10v14a2 2 0 0 1-2 2H6a2 2 0 0 0 2-2z", "M8 4a2 2 0 0 0-2 2v1h2", "M11 9h4", "M11 13h4")),
    "warning" to SvgIcon(listOf("M12 4L3 20h18z", "M12 10v4", "M12 17h.01")),
    "trophy" to SvgIcon(listOf("M8 4h8v6a4 4 0 0 1-8 0z", "M8 6H5v1a3 3 0 0 0 3 3", "M16 6h3v1a3 3 0 0 1-3 3", "M12 14v4", "M8 20h8")),
    "lock" to SvgIcon(listOf("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4")),
    "bulb" to SvgIcon(listOf("M9 18h6", "M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")),
    "gift" to SvgIcon(listOf("M4 10h16v10H4z", "M3 7h18v3H3z", "M12 7v13", "M12 7C10 3 6 4 7 7z", "M12 7c2-4 6-3 5 0z")),
    "laptop" to SvgIcon(listOf("M5 6h14v10H5z", "M2.5 19h19")),
    "galaxy" to SvgIcon(listOf("M3 12c0-3 4-5 9-5s9 2 9 5-4 5-9 5-9-2-9-5z", "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z", "M6 4h.01", "M19 19h.01", "M18 4h.01")),
    "gear" to SvgIcon(listOf("M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 3v3", "M12 18v3", "M3 12h3", "M18 12h3", "M5.6 5.6l2.1 2.1", "M16.3 16.3l2.1 2.1", "M5.6 18.4l2.1-2.1", "M16.3 7.7l2.1-2.1")),
    "swords" to SvgIcon(listOf("M14.5 17.5L3 6V3h3l11.5 11.5", "M13 19l6-6", "M16 16l4 4", "M19 21l2-2", "M9.5 17.5L21 6V3h-3L6.5 14.5", "M11 19l-6-6", "M8 16l-4 4", "M5 21l-2-2")),
    "sword" to SvgIcon(listOf("M14.5 17.5L3 6V3h3l11.5 11.5", "M13 19l6-6", "M16 16l4 4", "M19 21l2-2")),
    "dragon" to SvgIcon(listOf("M4 15c0-5 4-9 9-9h5l-2 3 2 1-3 1c0 4-2 7-6 7H6z", "M15 10h.01")),
    "palette" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18c1.5 0 2-1 1.5-2.2-.5-1.3.3-2.3 1.6-2.3H17a4 4 0 0 0 4-4c0-5-4-9-9-9z", "M7.5 11h.01", "M10 7h.01", "M14.5 7h.01")),
    "magnet" to SvgIcon(listOf("M5 4v8a7 7 0 0 0 14 0V4h-4v8a3 3 0 0 1-6 0V4z", "M5 8h4", "M15 8h4")),
    "hourglass" to SvgIcon(listOf("M6 3h12", "M6 21h12", "M7 3v3a5 5 0 0 0 2 4l1 2-1 2a5 5 0 0 0-2 4v3", "M17 3v3a5 5 0 0 1-2 4l-1 2 1 2a5 5 0 0 1 2 4v3")),
    "flask" to SvgIcon(listOf("M10 3h4", "M11 3v6L5 19a1.5 1.5 0 0 0 1.3 2h11.4a1.5 1.5 0 0 0 1.3-2l-6-10V3", "M8 15h8")),
    "star" to SvgIcon(listOf("M12 3l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 16.8 6.6 19.7l1.1-6.1L3.2 9.4l6.1-.8z")),
    "alarm" to SvgIcon(listOf("M12 6a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M12 9v4l2 2", "M5 4L3 6", "M19 4l2 2")),
    "stopwatch" to SvgIcon(listOf("M12 7a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M12 10v4l2 1", "M10 3h4", "M12 3v4")),
    "calendar" to SvgIcon(listOf("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M16 3v4", "M8 3v4", "M4 11h16")),
    "cross" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 9l6 6", "M15 9l-6 6")),
    "phone" to SvgIcon(listOf("M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M11 18h2")),
    "crown" to SvgIcon(listOf("M4 18l-1-10 5 4 4-7 4 7 5-4-1 10z")),
    "bars" to SvgIcon(listOf("M4 20h16", "M6 20v-6", "M11 20V6", "M16 20v-9")),
    "trend" to SvgIcon(listOf("M3 20h18", "M5 15l4-4 3 3 6-7", "M14 7h4v4")),
    "medal" to SvgIcon(listOf("M12 12a5 5 0 1 0 0 10a5 5 0 1 0 0-10z", "M8.5 13L6 3h4l2 5", "M15.5 13L18 3h-4l-2 5")),
    "bell" to SvgIcon(listOf("M6 17V11a6 6 0 0 1 12 0v6l2 2H4z", "M10 21h4")),
    "bug" to SvgIcon(listOf("M9 9a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z", "M12 9v9", "M9 12H5", "M15 12h4", "M9 16l-3 2", "M15 16l3 2", "M10 6L8 4", "M14 6l2-2")),
    "sparkles" to SvgIcon(listOf("M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z", "M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z")),
    "person" to SvgIcon(listOf("M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M5 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2")),
    "image" to SvgIcon(listOf("M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M9 9h.01")),
    "pause" to SvgIcon(listOf("M8 5v14", "M16 5v14")),
    "sunrise" to SvgIcon(listOf("M3 18h18", "M7 18a5 5 0 0 1 10 0", "M12 7v3", "M5 10l1.5 1.5", "M19 10l-1.5 1.5")),
    "moon" to SvgIcon(listOf("M20 14A8 8 0 0 1 10 4a8 8 0 1 0 10 10z")),
    "battery" to SvgIcon(listOf("M3 8h15v8H3z", "M18 11h3v2h-3z", "M6 11v2")),
    "rocket" to SvgIcon(listOf("M12 3c4 2 6 6 5 11l-3 3h-4l-3-3c-1-5 1-9 5-11z", "M12 9a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z", "M9 17l-2 4", "M15 17l2 4")),
    "ufo" to SvgIcon(listOf("M4 14c0-2 3-3 8-3s8 1 8 3-3 3-8 3-8-1-8-3z", "M8 11a4 4 0 0 1 8 0", "M7 20l1-3", "M17 20l-1-3", "M12 20v-3")),
    "pin" to SvgIcon(listOf("M9 4h6l-1 6 3 3H7l3-3z", "M12 13v8")),
    "repeat" to SvgIcon(listOf("M4 11V9a3 3 0 0 1 3-3h11", "M15 3l3 3-3 3", "M20 13v2a3 3 0 0 1-3 3H6", "M9 21l-3-3 3-3")),
    "zzz" to SvgIcon(listOf("M4 5h6L4 12h6", "M13 12h5l-5 6h5")),
    "pencil" to SvgIcon(listOf("M4 20l1-4L16 5l3 3L8 19z", "M14 7l3 3")),
    "wind" to SvgIcon(listOf("M3 8h11a3 3 0 1 0-3-3", "M3 12h16a3 3 0 1 1-3 3", "M3 16h8")),
    "tools" to SvgIcon(listOf("M14 6a4 4 0 0 0 5 5l-9 9a2 2 0 0 1-3-3z", "M14 6a4 4 0 0 1 5 5")),
    "scale" to SvgIcon(listOf("M12 4v16", "M6 20h12", "M4 8h16", "M5 8l-3 6a3 3 0 0 0 6 0z", "M19 8l-3 6a3 3 0 0 0 6 0z")),
    "wizard" to SvgIcon(listOf("M12 2l5 14H7z", "M4 19h16", "M10 11h4")),
    "camera" to SvgIcon(listOf("M4 8h3l2-3h6l2 3h3v11H4z", "M12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z")),
    "telescope" to SvgIcon(listOf("M3 13l14-6 2 5L5 18z", "M10 17l-2 4", "M13 15l3 6")),
    "face" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 10h.01", "M15 10h.01", "M9 15h6")),
    "cake" to SvgIcon(listOf("M4 12h16v8H4z", "M4 16c2 2 3-2 5 0s3-2 5 0 3-2 6 0", "M8 12V9", "M12 12V8", "M16 12V9")),
    "bag" to SvgIcon(listOf("M6 8h12l1 12H5z", "M9 8V7a3 3 0 0 1 6 0v1")),
    "receipt" to SvgIcon(listOf("M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6", "M9 12h6")),
    "dot_red" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFFE5484DL),
    "dot_yellow" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFFF5C518L),
    "dot_green" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFF30A46CL),
    "dot_black" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFF1B1B1FL),
    "dot_white" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFFFFFFFFL),
    "signal" to SvgIcon(listOf("M5 18a10 10 0 0 1 0-12", "M19 18a10 10 0 0 0 0-12", "M8 15a5 5 0 0 1 0-6", "M16 15a5 5 0 0 0 0-6", "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z")),
    "flag" to SvgIcon(listOf("M5 21V4", "M5 4h13l-3 4 3 4H5")),
    "link" to SvgIcon(listOf("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")),
    "moneybag" to SvgIcon(listOf("M9 3h6l-1.5 3h-3z", "M8 8h8c3 3 4 7 4 9a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4c0-2 1-6 4-9z", "M12 11v7")),
    "save" to SvgIcon(listOf("M5 4h11l3 3v13H5z", "M8 4v5h7V4", "M8 20v-6h8v6")),
    "tv" to SvgIcon(listOf("M4 7h16v11H4z", "M9 3l3 4 3-4", "M8 21h8")),
    "globe" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M3 12h18", "M12 3c3 3 3 15 0 18", "M12 3c-3 3-3 15 0 18")),
    "robot" to SvgIcon(listOf("M6 8h12v10H6z", "M12 4v4", "M9 12h.01", "M15 12h.01", "M9 15.5h6")),
    "access" to SvgIcon(listOf("M10 4a1 1 0 1 0 0 .01", "M10 8v6h5l3 5", "M7 12a5 5 0 1 0 7 6")),
    "window" to SvgIcon(listOf("M4 4h16v16H4z", "M12 4v16", "M4 12h16")),
    "film" to SvgIcon(listOf("M4 9h16v11H4z", "M4 9l2-5h3L7 9", "M10 9l2-5h3l-2 5", "M16 9l2-5h2l-2 5")),
    "ghost" to SvgIcon(listOf("M6 20V11a6 6 0 0 1 12 0v9l-2-2-2 2-2-2-2 2-2-2z", "M10 11h.01", "M14 11h.01")),
    "temple" to SvgIcon(listOf("M3 9l9-5 9 5z", "M5 9v9", "M9 9v9", "M15 9v9", "M19 9v9", "M3 21h18")),
    "crystal" to SvgIcon(listOf("M12 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M7 21h10", "M8.5 9a4 4 0 0 1 3-2.5")),
    "trident" to SvgIcon(listOf("M12 21V8", "M6 4v5a6 6 0 0 0 12 0V4", "M12 3v5")),
    "system" to SvgIcon(listOf("M12 2l10 10-10 10L2 12z", "M12 8l4 4-4 4-4-4z")),
    "shuriken" to SvgIcon(listOf("M12 2l2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z", "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z")),
    "arch" to SvgIcon(listOf("M5 21V10a7 7 0 0 1 14 0v11", "M9 21v-8a3 3 0 0 1 6 0v8", "M3 21h18")),
    "castle" to SvgIcon(listOf("M4 21V7h3v2h2V7h2v2h2V7h2v2h2V7h3v14z", "M10 21v-5a2 2 0 0 1 4 0v5")),
    "invader" to SvgIcon(listOf("M8 4v2", "M16 4v2", "M6 8h12v6h-2v3h-2v-3h-4v3H8v-3H6z", "M9 10h1", "M14 10h1")),
    "joystick" to SvgIcon(listOf("M12 4a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M12 10v6", "M5 20h14v-4H5z")),
    "gem" to SvgIcon(listOf("M6 4h12l3 5-9 12L3 9z", "M3 9h18", "M9 4l3 5 3-5")),
    "volcano" to SvgIcon(listOf("M3 20l6-10h6l6 10z", "M9 10l-1-4", "M15 10l1-5", "M12 7v3")),
    "hero" to SvgIcon(listOf("M12 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M6 21v-5a6 6 0 0 1 12 0v5", "M12 12v3", "M10.5 13.5h3")),
    "feet" to SvgIcon(listOf("M8 4a2 3 0 1 0 0 6a2 3 0 1 0 0-6z", "M7 13h3v3a1.5 1.5 0 0 1-3 0z", "M16 10a2 3 0 1 0 0 6a2 3 0 1 0 0-6z", "M15 19h3v1a1.5 1.5 0 0 1-3 0z")),
    "map" to SvgIcon(listOf("M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z", "M9 4v14", "M15 6v14")),
    "bandage" to SvgIcon(listOf("M4 14L14 4a3 3 0 0 1 6 6L10 20a3 3 0 0 1-6-6z", "M9 9l6 6")),
    "cube" to SvgIcon(listOf("M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M12 12l8-4.5", "M12 12v9", "M12 12L4 7.5")),
    "snow" to SvgIcon(listOf("M12 3v18", "M4 7.5l16 9", "M4 16.5l16-9", "M9 4l3 2 3-2", "M9 20l3-2 3 2")),
    "wave" to SvgIcon(listOf("M3 9c3-3 6 3 9 0s6 3 9 0", "M3 15c3-3 6 3 9 0s6 3 9 0")),
    "tree" to SvgIcon(listOf("M12 3l6 8h-3l4 6H5l4-6H6z", "M12 17v4")),
    "heart_purple" to SvgIcon(listOf("M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z"), 0xFF8B5CF6L),
    "heart_black" to SvgIcon(listOf("M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z"), 0xFF1B1B1FL),
    "heart_grey" to SvgIcon(listOf("M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z"), 0xFF8A8F98L),
    "flower" to SvgIcon(listOf("M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M12 8c-2-3-1-5 0-5s2 2 0 5z", "M12 16c-2 3-1 5 0 5s2-2 0-5z", "M8 12c-3 2-5 1-5 0s2-2 5 0z", "M16 12c3-2 5-1 5 0s-2 2-5 0z")),
    "leaf" to SvgIcon(listOf("M5 19C5 9 11 4 20 4c0 9-5 15-15 15z", "M5 19l8-8")),
    "sun" to SvgIcon(listOf("M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2v2", "M12 20v2", "M2 12h2", "M20 12h2", "M5 5l1.5 1.5", "M17.5 17.5L19 19", "M5 19l1.5-1.5", "M17.5 6.5L19 5")),
    "fog" to SvgIcon(listOf("M4 9h16", "M3 13h14", "M6 17h14")),
    "feather" to SvgIcon(listOf("M20 4C10 4 5 9 5 15v3", "M5 18l4-4", "M20 4c0 8-4 12-10 12")),
    "butterfly" to SvgIcon(listOf("M12 7v12", "M12 7c-3-5-9-4-8 2 1 3 5 3 8 0z", "M12 7c3-5 9-4 8 2-1 3-5 3-8 0z", "M12 11c-3 6-8 7-7 3 .5-2 3-3 7-3z", "M12 11c3 6 8 7 7 3-.5-2-3-3-7-3z")),
    "fox" to SvgIcon(listOf("M4 4l4 3h8l4-3v9a8 8 0 0 1-16 0z", "M9 12h.01", "M15 12h.01", "M10 16h4")),
    "mail" to SvgIcon(listOf("M3 6h18v12H3z", "M3 7l9 6 9-6")),
    "triangle" to SvgIcon(listOf("M4 20V4l16 16z", "M4 12h4", "M4 16h4")),
    "ruler" to SvgIcon(listOf("M3 9h18v6H3z", "M7 9v3", "M11 9v4", "M15 9v3")),
    "divide" to SvgIcon(listOf("M5 12h14", "M12 6h.01", "M12 18h.01")),
    "infinity" to SvgIcon(listOf("M8 8a4 4 0 1 0 0 8c4 0 4-8 8-8a4 4 0 1 1 0 8c-4 0-4-8-8-8z")),
    "runner" to SvgIcon(listOf("M14 4a1.5 1.5 0 1 0 0 .01", "M8 20l3-5 3 2 1 4", "M11 15l-1-5 5-1 3 3", "M10 10l-3 2")),
    "speaker" to SvgIcon(listOf("M4 9h4l5-4v14l-5-4H4z", "M16 9a4 4 0 0 1 0 6", "M18.5 6.5a8 8 0 0 1 0 11")),
    "brain" to SvgIcon(listOf("M12 5v14", "M12 5a3 3 0 0 0-5-1 3 3 0 0 0-3 3 3 3 0 0 0-1 4 3 3 0 0 0 2 4 3 3 0 0 0 4 2 3 3 0 0 0 3-1", "M12 5a3 3 0 0 1 5-1 3 3 0 0 1 3 3 3 3 0 0 1 1 4 3 3 0 0 1-2 4 3 3 0 0 1-4 2 3 3 0 0 1-3-1")),
    "openbook" to SvgIcon(listOf("M3 5h6a3 3 0 0 1 3 3v12a2 2 0 0 0-2-2H3z", "M21 5h-6a3 3 0 0 0-3 3v12a2 2 0 0 1 2-2h7z")),
    "school" to SvgIcon(listOf("M3 21V10l9-6 9 6v11z", "M9 21v-6h6v6", "M12 8h.01")),
    "palm" to SvgIcon(listOf("M12 21V11", "M12 11c-1-4-5-5-8-4 3-1 6 0 8 4z", "M12 11c1-4 5-5 8-4-3-1-6 0-8 4z", "M12 11c-2-1-4 1-5 4 2-2 4-2 5-4z", "M12 11c2-1 4 1 5 4-2-2-4-2-5-4z")),
    "coffee" to SvgIcon(listOf("M5 8h11v7a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z", "M16 10h2a2 2 0 0 1 0 4h-2", "M8 3v2", "M12 3v2")),
    "stop" to SvgIcon(listOf("M6 6h12v12H6z")),
    "shuffle" to SvgIcon(listOf("M3 7h4l10 10h4", "M3 17h4l3-3", "M14 10l3-3h4", "M18 4l3 3-3 3", "M18 14l3 3-3 3")),
    "cart" to SvgIcon(listOf("M3 4h2l2.5 11h10l2-8H6", "M9 20h.01", "M17 20h.01")),
    "compass" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M15.5 8.5l-2 5-5 2 2-5z")),
    "atom" to SvgIcon(listOf("M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z", "M2.5 12c0-2.5 4-4.5 9.5-4.5s9.5 2 9.5 4.5-4 4.5-9.5 4.5S2.5 14.5 2.5 12z", "M12 2.5c2.5 0 4.5 4 4.5 9.5s-2 9.5-4.5 9.5-4.5-4-4.5-9.5S9.500 2.5 12 2.5z")),
    "puzzle" to SvgIcon(listOf("M10 4a2 2 0 1 1 4 0v2h4v4h-2a2 2 0 1 0 0 4h2v4h-4v-2a2 2 0 1 0-4 0v2H6v-4h2a2 2 0 1 0 0-4H6V6h4z")),
    "party" to SvgIcon(listOf("M4 20l4-12 8 8z", "M14 4h.01", "M18 8h.01", "M20 13h.01", "M15 8c1-2 2-2 3-2")),
    "sprout" to SvgIcon(listOf("M12 21v-9", "M12 12c0-4-3-6-7-6 0 4 3 6 7 6z", "M12 14c0-3 2-5 6-5 0 3-2 5-6 5z")),
    "memo" to SvgIcon(listOf("M6 3h9l4 4v14H6z", "M15 3v4h4", "M9 12h6", "M9 16h4")),
    "rain" to SvgIcon(listOf("M7 15a4 4 0 0 1-.5-8A5.5 5.5 0 0 1 17 8a3.5 3.5 0 0 1 .5 7z", "M8 18l-1 3", "M12 18l-1 3", "M16 18l-1 3")),
    "dot_brown" to SvgIcon(listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z"), 0xFF8B5E3CL),
    "face_sad" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 10h.01", "M15 10h.01", "M9 16c1-1.5 5-1.5 6 0")),
    "face_meh" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 10h.01", "M15 10h.01", "M9 15c2-1 4 1 6 0")),
    "face_smile" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 10h.01", "M15 10h.01", "M9 14c1 1.5 5 1.5 6 0")),
    "face_happy" to SvgIcon(listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M9 10h.01", "M15 10h.01", "M8 13h8a4 4 0 0 1-8 0z")),
    "meditate" to SvgIcon(listOf("M12 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M12 9v4", "M5 18c3 0 4-3 7-3s4 3 7 3", "M7 18c0 2 3 3 5 3s5-1 5-3")),
    "checklist" to SvgIcon(listOf("M3.5 5.5l1.5 1.5 2.5-2.5", "M3.5 11.5l1.5 1.5 2.5-2.5", "M3.5 17.5l1.5 1.5 2.5-2.5", "M11 6h9", "M11 12h9", "M11 18h9")),
    "sliders" to SvgIcon(listOf("M4 8h6", "M14 8h6", "M12 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M4 16h2", "M10 16h10", "M8 14a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")),
    "rosette" to SvgIcon(listOf("M12 3a6 6 0 1 0 0 12a6 6 0 1 0 0-12z", "M9 14.5L8 21l4-2 4 2-1-6.5")),
    "calendar_check" to SvgIcon(listOf("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M16 3v4", "M8 3v4", "M4 11h16", "M9 16l2 2 4-4")),
    "apps" to SvgIcon(listOf("M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0z")),
    "notebook" to SvgIcon(listOf("M6 4h11a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6z", "M6 4v16", "M10 9h5", "M10 13h5")),
    "shield_check" to SvgIcon(listOf("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z", "M9 12l2 2 4-4")),
    "selftest" to SvgIcon(listOf("M4 6l1.5 1.5L8 5", "M4 13l1.5 1.5L8 12", "M11 6h9", "M11 13h9", "M4 19h4", "M11 19h6")),
    "clock" to SvgIcon(listOf("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M12 8v4l3 2")),
    "versions" to SvgIcon(listOf("M10 7h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z", "M6 17V6a1 1 0 0 1 1-1h9")),
    "calendar_week" to SvgIcon(listOf("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M16 3v4", "M8 3v4", "M4 11h16", "M8 15h8")),
    "home" to SvgIcon(listOf("M3 11l9-8 9 8", "M5 10v10h14V10", "M10 20v-6h4v6")),
    "arrow_left" to SvgIcon(listOf("M19 12H5", "M12 5l-7 7 7 7")),
    "plus" to SvgIcon(listOf("M12 5v14", "M5 12h14")),
    "minus" to SvgIcon(listOf("M5 12h14")),
    "trash" to SvgIcon(listOf("M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3")),
    "x" to SvgIcon(listOf("M6 6l12 12", "M18 6L6 18")),
    "play" to SvgIcon(listOf("M7 4l13 8-13 8z")),
    "search" to SvgIcon(listOf("M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-4-4")),
    "copy" to SvgIcon(listOf("M8 8h12v12H8z", "M4 16V4h12")),
    "checkbox" to SvgIcon(listOf("M5 5h14v14H5z")),
    "checkbox_check" to SvgIcon(listOf("M5 5h14v14H5z", "M8.5 12l2.5 2.5 4.5-5")),
    "tick" to SvgIcon(listOf("M5 12l5 5 9-10")),
    )

    /** emoji (without the variation selector) -> icon name */
    val EMOJI: Map<String, String> = mapOf(
    "\uD83E\uDE99" to "coin",
    "\u26A1" to "bolt",
    "\uD83D\uDCAA" to "bolt",
    "\u2705" to "check",
    "\uD83C\uDFAF" to "target",
    "\uD83D\uDD25" to "flame",
    "\uD83D\uDEE1" to "shield",
    "\uD83D\uDCCB" to "clipboard",
    "\uD83C\uDFAE" to "gamepad",
    "\uD83C\uDFA7" to "headphones",
    "\uD83D\uDCDA" to "books",
    "\uD83D\uDCD3" to "book",
    "\uD83D\uDCD7" to "book",
    "\uD83D\uDCC3" to "doc",
    "\uD83D\uDCC4" to "doc",
    "\uD83D\uDCDC" to "scroll",
    "\u26A0" to "warning",
    "\uD83C\uDFC6" to "trophy",
    "\uD83D\uDD12" to "lock",
    "\uD83D\uDCA1" to "bulb",
    "\uD83C\uDF81" to "gift",
    "\uD83D\uDCBB" to "laptop",
    "\uD83D\uDDA5" to "laptop",
    "\uD83C\uDF0C" to "galaxy",
    "\u2699" to "gear",
    "\u2694" to "swords",
    "\uD83D\uDDE1" to "sword",
    "\uD83D\uDC09" to "dragon",
    "\uD83D\uDC32" to "dragon",
    "\uD83C\uDFA8" to "palette",
    "\uD83E\uDD77" to "shuriken",
    "\uD83D\uDCA8" to "wind",
    "\u26EA" to "arch",
    "\uD83C\uDFF0" to "castle",
    "\uD83D\uDC7E" to "invader",
    "\uD83D\uDD79" to "joystick",
    "\uD83D\uDCA0" to "system",
    "\uD83E\uDDF2" to "magnet",
    "\u23F3" to "hourglass",
    "\u231B" to "hourglass",
    "\uD83E\uDDEA" to "flask",
    "\u2697" to "flask",
    "\u2B50" to "star",
    "\uD83C\uDF1F" to "star",
    "\uD83D\uDCAF" to "star",
    "\u23F0" to "alarm",
    "\u23F1" to "stopwatch",
    "\uD83D\uDCC5" to "calendar",
    "\u274C" to "cross",
    "\uD83D\uDCF1" to "phone",
    "\uD83D\uDC51" to "crown",
    "\uD83D\uDCCA" to "bars",
    "\uD83D\uDCC8" to "trend",
    "\uD83C\uDFC5" to "medal",
    "\uD83E\uDD47" to "medal",
    "\uD83E\uDD48" to "medal",
    "\uD83E\uDD49" to "medal",
    "\uD83D\uDD14" to "bell",
    "\uD83D\uDC1E" to "bug",
    "\uD83E\uDD82" to "bug",
    "\u2728" to "sparkles",
    "\uD83D\uDC64" to "person",
    "\uD83D\uDC68" to "person",
    "\uD83E\uDDB8" to "person",
    "\uD83D\uDDBC" to "image",
    "\u23F8" to "pause",
    "\uD83C\uDF05" to "sunrise",
    "\uD83C\uDF19" to "moon",
    "\uD83C\uDF11" to "moon",
    "\uD83D\uDD0B" to "battery",
    "\uD83D\uDE80" to "rocket",
    "\uD83D\uDEF8" to "ufo",
    "\uD83D\uDCCC" to "pin",
    "\uD83D\uDD01" to "repeat",
    "\uD83D\uDCA4" to "zzz",
    "\u270D" to "pencil",
    "\u270F" to "pencil",
    "\uD83C\uDF2C" to "wind",
    "\uD83D\uDEE0" to "tools",
    "\u2696" to "scale",
    "\uD83E\uDDD9" to "wizard",
    "\uD83D\uDCF8" to "camera",
    "\uD83D\uDD2D" to "telescope",
    "\uD83D\uDE10" to "face",
    "\uD83C\uDF82" to "cake",
    "\uD83D\uDECD" to "bag",
    "\uD83E\uDDFE" to "receipt",
    "\uD83D\uDD34" to "dot_red",
    "\uD83D\uDFE1" to "dot_yellow",
    "\uD83D\uDFE2" to "dot_green",
    "\u26AB" to "dot_black",
    "\u26AA" to "dot_white",
    "\uD83D\uDCE1" to "signal",
    "\uD83C\uDFC1" to "flag",
    "\uD83D\uDD17" to "link",
    "\uD83D\uDCB0" to "moneybag",
    "\uD83D\uDCB8" to "moneybag",
    "\uD83D\uDCBE" to "save",
    "\uD83D\uDCFA" to "tv",
    "\uD83C\uDF10" to "globe",
    "\uD83E\uDD16" to "robot",
    "\u267F" to "access",
    "\uD83E\uDE9F" to "window",
    "\uD83C\uDFAC" to "film",
    "\uD83D\uDC79" to "ghost",
    "\uD83D\uDC7B" to "ghost",
    "\uD83D\uDC19" to "ghost",
    "\uD83C\uDFDB" to "temple",
    "\uD83D\uDD2E" to "crystal",
    "\uD83D\uDD31" to "trident",
    "\uD83D\uDC8E" to "gem",
    "\uD83C\uDF0B" to "volcano",
    "\uD83D\uDC63" to "feet",
    "\uD83D\uDDFA" to "map",
    "\uD83E\uDE79" to "bandage",
    "\uD83E\uDDCA" to "cube",
    "\u2744" to "snow",
    "\uD83C\uDF0A" to "wave",
    "\uD83C\uDF32" to "tree",
    "\uD83D\uDC9C" to "heart_purple",
    "\uD83D\uDDA4" to "heart_black",
    "\uD83E\uDE76" to "heart_grey",
    "\uD83E\uDEBB" to "flower",
    "\uD83C\uDF38" to "flower",
    "\uD83C\uDF3F" to "leaf",
    "\uD83C\uDF24" to "sun",
    "\uD83C\uDF2B" to "fog",
    "\uD83E\uDEB6" to "feather",
    "\uD83D\uDD4A" to "feather",
    "\uD83E\uDD8B" to "butterfly",
    "\uD83E\uDD8A" to "fox",
    "\u2709" to "mail",
    "\uD83D\uDCD0" to "triangle",
    "\uD83D\uDCCF" to "ruler",
    "\u2797" to "divide",
    "\u267E" to "infinity",
    "\uD83C\uDFC3" to "runner",
    "\uD83D\uDD0A" to "speaker",
    "\u2600" to "sun",
    "\uD83E\uDDD8" to "meditate",
    "\uD83E\uDDE0" to "brain",
    "\uD83D\uDCD6" to "openbook",
    "\uD83C\uDFEB" to "school",
    "\uD83C\uDF34" to "palm",
    "\u2615" to "coffee",
    "\u23F9" to "stop",
    "\uD83D\uDD00" to "shuffle",
    "\uD83D\uDED2" to "cart",
    "\uD83E\uDDED" to "compass",
    "\u269B" to "atom",
    "\uD83E\uDDE9" to "puzzle",
    "\uD83C\uDF89" to "party",
    "\uD83C\uDF31" to "sprout",
    "\uD83D\uDCDD" to "memo",
    "\uD83C\uDF27" to "rain",
    "\uD83D\uDFE4" to "dot_brown",
    "\uD83D\uDE1E" to "face_sad",
    "\uD83D\uDE15" to "face_meh",
    "\uD83D\uDE42" to "face_smile",
    "\uD83D\uDE04" to "face_happy",
    )

    /** Icons that stand for an emoji (the rest are page-title icons). */
    val EMOJI_ICONS: Set<String> = EMOJI.values.toSet()

    /** Page title -> icon name (v228: TitleIcon draws from the same set). */
    val TITLES: Map<String, String> = mapOf(
        "QuestBlock" to "sword",
        "Quests" to "checklist",
        "Focus" to "bolt",
        "Vault" to "lock",
        "Settings" to "sliders",
        "Badges" to "rosette",
        "Rules" to "doc",
        "Chapters" to "books",
        "Attendance" to "calendar_check",
        "Stats" to "bars",
        "About me" to "person",
        "App categories" to "apps",
        "Debug log" to "bug",
        "Flags" to "flag",
        "Homework" to "notebook",
        "Quest board" to "scroll",
        "Shop" to "bag",
        "Marks" to "pencil",
        "Reliability" to "shield_check",
        "Self test" to "selftest",
        "App usage" to "clock",
        "Version info" to "versions",
        "Weekly review" to "calendar_week",
        "Experiments" to "flask",
    )

    private val BY_CODEPOINT: Map<Int, List<String>> = EMOJI.keys.groupBy { it.codePointAt(0) }

    /** The text with every known emoji swapped for an inline icon, or null when it has none. */
    fun annotate(s: String, off: Set<String> = emptySet()): SvgTextResult? {
        var any = false
        for (ch in s) if (ch.code >= 0x2190) { any = true; break }
        if (!any) return null
        val names = LinkedHashSet<String>()
        val b = buildAnnotatedString {
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val len = Character.charCount(cp)
                val name = BY_CODEPOINT[cp]?.firstOrNull { s.startsWith(it, i) }?.let { EMOJI[it] }?.takeIf { it !in off }
                if (name != null) {
                    var end = i + len
                    if (end < s.length && s[end] == '\uFE0F') end++
                    // v229: a joined emoji (man + laptop) is one picture, so the parts after the joiner are skipped
                    while (end + 1 < s.length && s[end] == '\u200D') {
                        var e2 = end + 1 + Character.charCount(s.codePointAt(end + 1))
                        if (e2 < s.length && s[e2] == '\uFE0F') e2++
                        end = e2
                    }
                    appendInlineContent(name, "?")
                    names.add(name)
                    i = end
                } else {
                    append(s.substring(i, i + len))
                    i += len
                }
            }
        }
        return if (names.isEmpty()) null else SvgTextResult(b, names)
    }

    fun inline(names: Set<String>, tint: Color): Map<String, InlineTextContent> =
        names.associateWith { n ->
            InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) { SvgGlyph(n, tint) }
        }
}

/** One icon, filling the space it is given. The coin is gold, the dots and hearts keep their own colour. */
@Composable
fun SvgGlyph(name: String, tint: Color, modifier: Modifier = Modifier) {
    val icon = SvgIcons.ICONS[name] ?: return
    val paths = remember(name) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    val line = if (name == "coin") ColorCoin else if (icon.fill != 0L && name != "dot_white") Color(icon.fill) else tint
    Canvas(modifier.fillMaxSize()) {
        val k = this.size.width / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach { p ->
                if (icon.fill != 0L) drawPath(p, Color(icon.fill))
                drawPath(p, line, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
