package com.krishna.questblock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** v281: a thin ring that fills as today's quests get done (a full ring when everything is finished). v283: its look follows the theme. */
@Composable
fun DailyRing(done: Int, total: Int, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val frac = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, tween(600, easing = FastOutSlowInEasing), label = "dailyRing")
    val look = ringLook()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = look.width.toPx()
            val gw = if (look.glow) sw * 2.6f else sw
            val inset = gw / 2
            val arcSize = Size(this.size.width - gw, this.size.height - gw)
            val cap = if (look.round) StrokeCap.Round else StrokeCap.Butt
            val dash = if (look.dashOn > 0f) PathEffect.dashPathEffect(floatArrayOf(look.dashOn.dp.toPx(), look.dashGap.dp.toPx()), 0f) else null
            drawArc(look.color.copy(alpha = look.trackAlpha), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = cap, pathEffect = dash))
            if (look.inner) {
                val gap = sw * 1.8f
                drawArc(look.color.copy(alpha = 0.35f), 0f, 360f, false, Offset(inset + gap, inset + gap), Size(arcSize.width - gap * 2, arcSize.height - gap * 2), style = Stroke(1.dp.toPx()))
            }
            if (anim > 0f) {
                if (look.glow) drawArc(look.color.copy(alpha = 0.25f), -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(gw, cap = cap))
                val e = look.end
                if (e != null) drawArc(Brush.linearGradient(listOf(look.color, e)), -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = cap, pathEffect = dash))
                else drawArc(look.color, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = cap, pathEffect = dash))
            }
        }
        if (total > 0 && done >= total) Text("\u2713", fontSize = (size.value * 0.45f).sp, color = look.color, fontWeight = FontWeight.Black)
    }
}

/** v281: replaces the "0/0 Quests" tile on Home: same size and label, with the ring instead of the check emoji. */
@Composable
fun DailyRingTile(done: Int, total: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.a11yLabel("$done of $total quests done today"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DailyRing(done, total, 22.dp)
            Text("$done/$total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ColorText, maxLines = 1)
            Text("Quests", style = MaterialTheme.typography.labelSmall, color = ColorMuted, maxLines = 1)
        }
    }
}
