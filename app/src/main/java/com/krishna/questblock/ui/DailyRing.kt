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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** v281: a thin ring that fills as today's quests get done (a full ring when everything is finished). */
@Composable
fun DailyRing(done: Int, total: Int, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val frac = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, tween(600, easing = FastOutSlowInEasing), label = "dailyRing")
    val track = ColorMuted.copy(alpha = 0.22f)
    val fill = ColorPrimary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = 3.dp.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            if (anim > 0f) drawArc(fill, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        if (total > 0 && done >= total) Text("\u2713", fontSize = (size.value * 0.45f).sp, color = fill, fontWeight = FontWeight.Black)
    }
}

/** v281: replaces the "0/0 Quests" tile on Home: same size and label, with the ring instead of the check emoji. */
@Composable
fun DailyRingTile(done: Int, total: Int, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
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
