package com.krishna.questblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** v221: thin outline icons (24 x 24 grid, stroke 2) in front of the page titles. Drawn from SVG path data, so they follow the theme colour. */
private val TITLE_ICON_PATHS: Map<String, List<String>> = mapOf(
    "QuestBlock" to listOf("M14.5 17.5L3 6V3h3l11.5 11.5", "M13 19l6-6", "M16 16l4 4", "M19 21l2-2"),
    "Quests" to listOf("M3.5 5.5l1.5 1.5 2.5-2.5", "M3.5 11.5l1.5 1.5 2.5-2.5", "M3.5 17.5l1.5 1.5 2.5-2.5", "M11 6h9", "M11 12h9", "M11 18h9"),
    "Focus" to listOf("M13 3v7h6l-8 11v-7H5l8-11z"),
    "Vault" to listOf("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4"),
    "Settings" to listOf("M4 8h6", "M14 8h6", "M12 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M4 16h2", "M10 16h10", "M8 14a2 2 0 1 0 0 4a2 2 0 1 0 0-4z"),
    "Badges" to listOf("M12 3a6 6 0 1 0 0 12a6 6 0 1 0 0-12z", "M9 14.5L8 21l4-2 4 2-1-6.5"),
    "Rules" to listOf("M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M14 3v5h5", "M9 13h6", "M9 17h6"),
    "Chapters" to listOf("M5 4h3v16H5z", "M10 4h3v16h-3z", "M15.5 5.5l2.8-.8 3.2 14.6-2.8.8z"),
    "Attendance" to listOf("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M16 3v4", "M8 3v4", "M4 11h16", "M9 16l2 2 4-4"),
    "Stats" to listOf("M4 20h16", "M6 20v-6", "M11 20V6", "M16 20v-9"),
    "About me" to listOf("M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M5 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2"),
    "App categories" to listOf("M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"),
    "Debug log" to listOf("M9 9a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z", "M12 9v9", "M9 12H5", "M15 12h4", "M9 16l-3 2", "M15 16l3 2", "M10 6L8 4", "M14 6l2-2"),
    "Flags" to listOf("M5 21V4", "M5 4h13l-3 4 3 4H5"),
    "Homework" to listOf("M6 4h11a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6z", "M6 4v16", "M10 9h5", "M10 13h5"),
    "Quest board" to listOf("M8 4h10v14a2 2 0 0 1-2 2H6a2 2 0 0 0 2-2z", "M8 4a2 2 0 0 0-2 2v1h2", "M11 9h4", "M11 13h4"),
    "Shop" to listOf("M6 8h12l1 12H5z", "M9 8V7a3 3 0 0 1 6 0v1"),
    "Marks" to listOf("M4 20l1-4L16 5l3 3L8 19z", "M14 7l3 3"),
    "Reliability" to listOf("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z", "M9 12l2 2 4-4"),
    "Self test" to listOf("M4 6l1.5 1.5L8 5", "M4 13l1.5 1.5L8 12", "M11 6h9", "M11 13h9", "M4 19h4", "M11 19h6"),
    "App usage" to listOf("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M12 8v4l3 2"),
    "Version info" to listOf("M10 7h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z", "M6 17V6a1 1 0 0 1 1-1h9"),
    "Weekly review" to listOf("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M16 3v4", "M8 3v4", "M4 11h16", "M8 15h8"),
    "Experiments" to listOf("M10 3h4", "M11 3v6L5 19a1.5 1.5 0 0 0 1.3 2h11.4a1.5 1.5 0 0 0 1.3-2l-6-10V3", "M8 15h8"),
)

/** The icon for a page title, or nothing when that title has none. */
@Composable
fun TitleIcon(title: String, size: Dp = 26.dp, tint: Color = ColorPrimary, endPad: Dp = 0.dp) {
    val d = TITLE_ICON_PATHS[title] ?: return
    val paths = remember(title) { d.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.padding(end = endPad).size(size)) {
        val k = this.size.width / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach { drawPath(it, tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}
