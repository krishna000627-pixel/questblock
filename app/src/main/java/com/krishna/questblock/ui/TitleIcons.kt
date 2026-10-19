package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** v221: thin outline icons in front of the page titles. v228: drawn from the one SVG set (SvgIcons.TITLES maps a title to an icon name). */
@Composable
fun TitleIcon(title: String, size: Dp = 26.dp, tint: Color = ColorPrimary, endPad: Dp = 0.dp) {
    val name = SvgIcons.TITLES[title] ?: return
    Box(Modifier.padding(end = endPad).size(size)) { SvgGlyph(name, tint) }
}
