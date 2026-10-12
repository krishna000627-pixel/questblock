package com.krishna.questblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// v187 concept style. Every Card in the app (files that use the star import of material3) now resolves to these two,
// because a function in the same package wins over a star import. Same parameters as Material's Card; the only change
// is the default border: a thin glowing outline in the accent colour, so cards follow the equipped theme.

/** Thin outline in the accent colour. */
@Composable
fun glowBorder(): BorderStroke = BorderStroke(1.dp, ColorPrimary.copy(alpha = if (LightMode) 0.30f else 0.26f))

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = glowBorder(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content,
)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = glowBorder(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, elevation = elevation,
    border = border, content = content,
)

/** Six-sided badge shape (level badge, achievement badges). */
val HexShape = GenericShape { size, _ ->
    // a regular hexagon (equal sides), point up, centred in the box
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.height / 2f, size.width / 1.7320508f)
    for (k in 0 until 6) {
        val a = Math.toRadians((-90 + 60 * k).toDouble())
        val x = cx + r * Math.cos(a).toFloat()
        val y = cy + r * Math.sin(a).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The default profile picture (res/drawable-nodpi/avatar_default.jpg), filling whatever box it is put in (the box does the hexagon clipping). */
@Composable
fun AvatarFill() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.krishna.questblock.R.drawable.avatar_default),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}

/** Rounded square with a colour gradient and an emoji: the icon of a quest, item or setting row. */
@Composable
fun IconTile(emoji: String, color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.45f))))
            .border(1.dp, color.copy(alpha = 0.65f), shape),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.5f).sp) }
}

/** Row of pill tabs: the picked one is filled with the accent colour. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) ColorPrimary else ColorSurfaceHigh)
                    .border(1.dp, if (sel) ColorPrimary else ColorPrimary.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) ColorOnPrimary else ColorText,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Small stat card: emoji, big value, small label. */
@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = ColorSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ColorText, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted, maxLines = 1)
        }
    }
}

/** Thick progress bar, accent to second accent. [fraction] 0..1. */
@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(ColorSurfaceHigh)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(ColorPrimary, ColorAccent)), CircleShape),
        )
    }
}

/** Screen title with a small icon tile in front (concept style). */
@Composable
fun TitleWithIcon(emoji: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(emoji, ColorPrimary, 32.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColorText)
    }
}
