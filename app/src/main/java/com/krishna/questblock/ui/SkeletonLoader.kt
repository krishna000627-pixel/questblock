package com.krishna.questblock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * v281: placeholder blocks that shimmer while the lists load from the database, so a screen never flashes
 * "Nothing planned" for a split second. The shimmer is one light band that slides across, in the theme's own colours.
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "skeletonX")
    val base = ColorSurfaceHigh
    val hi = ColorMuted.copy(alpha = 0.25f)
    Box(
        modifier.height(height).clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(listOf(base, hi, base), startX = x * 400f, endX = x * 400f + 400f)),
    )
}

/** A card-shaped placeholder with a title bar and [lines] shorter lines. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, lines: Int = 2) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.55f), 16.dp)
            for (i in 0 until lines) SkeletonBox(Modifier.fillMaxWidth(if (i % 2 == 0) 0.9f else 0.65f), 12.dp)
        }
    }
}

/** False for the first [ms] milliseconds after the screen appears, then true. Used to show the skeleton only while the first load is in flight. */
@Composable
fun rememberWarm(ms: Long = 250L): Boolean {
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(ms); warm = true }
    return warm
}
