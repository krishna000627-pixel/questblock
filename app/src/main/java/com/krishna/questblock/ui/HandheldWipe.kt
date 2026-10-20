package com.krishna.questblock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * v273: Handheld page wipe. When you change page in the Handheld theme (behind its flag fx_handheld and themefx) ten dark bars close over the page
 * from the left and open to the right, about a third of a second, like a console screen change. One shot, nothing loops, taps still go through.
 */
@Composable
internal fun Modifier.handheldWipe(route: String?): Modifier {
    val on = FxNow == Fx.HANDHELD && ThemeExtras.on
    val prog = remember { Animatable(1f) }
    var last by remember { mutableStateOf(route) }
    LaunchedEffect(route) {
        if (route != last) {
            last = route
            if (on) { com.krishna.questblock.util.Haptics.tick(); prog.snapTo(0f); prog.animateTo(1f, tween(340, easing = LinearEasing)) }
        }
    }
    val ink = ColorText
    return this.drawWithContent {
        drawContent()
        val p = prog.value
        if (on && p > 0f && p < 1f) {
            val n = 10
            val bh = size.height / n
            for (i in 0 until n) {
                val t = (p * 1.3f - i * 0.03f).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) continue
                val left = if (t < 0.5f) 0f else size.width * ((t - 0.5f) * 2f)
                val right = if (t < 0.5f) size.width * (t * 2f) else size.width
                drawRect(ink, Offset(left, i * bh), Size(right - left, bh + 1f))
            }
        }
    }
}
