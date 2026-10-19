package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** v232: Material icon (last part of its name) -> icon of our own set (SvgIcons.kt). */
private val MATERIAL_TO_SVG: Map<String, String> = mapOf(
    "Home" to "home",
    "Star" to "star",
    "Bolt" to "bolt",
    "Lock" to "lock",
    "Person" to "person",
    "ArrowBack" to "arrow_left",
    "Add" to "plus",
    "Remove" to "minus",
    "Delete" to "trash",
    "Close" to "x",
    "Edit" to "pencil",
    "CheckCircle" to "check",
    "Check" to "tick",
    "PlayArrow" to "play",
    "MenuBook" to "openbook",
    "Tune" to "sliders",
    "Stop" to "stop",
    "Search" to "search",
    "Schedule" to "clock",
    "DateRange" to "calendar",
    "ContentCopy" to "copy",
    "CheckBoxOutlineBlank" to "checkbox",
    "CheckBox" to "checkbox_check",
    "Apps" to "apps",
)

/**
 * v232: this Icon shadows material3's Icon(imageVector) in the ui package. The filled Material icons the app uses
 * (back arrow, add, delete, close, the bottom bar ...) are drawn as the thin outline icons of the SVG set instead; any other icon
 * goes straight to material3.
 */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val name = MATERIAL_TO_SVG[imageVector.name.substringAfterLast('.')]
    if (name == null) {
        androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
    } else {
        Box(modifier.then(Modifier.size(24.dp))) { SvgGlyph(name, if (tint == Color.Unspecified) LocalContentColor.current else tint) }
    }
}
