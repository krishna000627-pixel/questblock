package com.krishna.questblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v236: the System shop theme (theme_system), the "main character system window" look. Not only colours:
 * cards and buttons get cut corners, a thin glowing frame and bright corner brackets (Card in Glass.kt, Button in
 * ClayStyle.kt), all text is monospace (SvgText.kt), the pages sit on a faint HUD grid with a [ SYSTEM ] strip on top,
 * and the Undo bar after a quest reads [SYSTEM] QUEST CLEARED (MainScreen.kt). Off in the terminal and over a poster.
 */
val SystemOn: Boolean get() = ShopTheme == "theme_system" && !PosterOn && !TermuxOn

internal val SYS_SHAPE = CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)
internal val SYS_BUTTON = CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)

/** Soft blue glow under the window plus bright L-shaped brackets on its two square corners. */
@Composable
internal fun Modifier.systemWindow(): Modifier {
    val c = ColorPrimary
    return this
        .shadow(5.dp, SYS_SHAPE, ambientColor = c.copy(alpha = 0.55f), spotColor = c.copy(alpha = 0.55f))
        .drawWithContent {
            drawContent()
            val l = 12.dp.toPx(); val w = 2.dp.toPx(); val h = w / 2
            drawLine(c, Offset(size.width - l, h), Offset(size.width, h), w)
            drawLine(c, Offset(size.width - h, 0f), Offset(size.width - h, l), w)
            drawLine(c, Offset(0f, size.height - h), Offset(l, size.height - h), w)
            drawLine(c, Offset(h, size.height - l), Offset(h, size.height), w)
        }
}

@Composable
internal fun systemBorder(): BorderStroke = BorderStroke(1.dp, ColorPrimary.copy(alpha = 0.55f))

/** Outlined, translucent System button. Every button takes the accent colour while the theme is on. */
@Composable
internal fun systemButtonColors(c: ButtonColors): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = ColorPrimary.copy(alpha = 0.16f), contentColor = ColorPrimary,
    disabledContainerColor = ColorMuted.copy(alpha = 0.10f), disabledContentColor = ColorMuted.copy(alpha = 0.6f),
)

/** A faint square grid behind the pages, like a holographic screen. */
@Composable
internal fun Modifier.systemGrid(): Modifier {
    if (!SystemOn) return this
    val c = ColorPrimary.copy(alpha = 0.06f)
    return drawBehind {
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    }
}

/** The strip at the top of every page: [ SYSTEM ] on the left, status on the right. */
@Composable
fun SystemBar() {
    val c = ColorPrimary
    Row(
        Modifier.fillMaxWidth().background(ColorBg).statusBarsPadding().height(24.dp)
            .drawBehind { drawLine(c.copy(alpha = 0.5f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("[ SYSTEM ]", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text("PLAYER STATUS \u00b7 ONLINE", color = ColorMuted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}
