package com.krishna.questblock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** One SVG layer of a theme background, drawn on a 100 x 220 canvas that is stretched to the screen. */
class QhLayer(
    val d: String,                // SVG path data (M L Q T C A Z)
    val color: Long,              // 0xAARRGGBB
    val alpha: Float = 1f,
    val stroke: Float = 0f,       // 0 = filled shape, > 0 = outline of this width
    val anim: Int = 0,            // 0 still, 1 drift sideways, 2 pulse, 3 float up and down, 4 twinkle, 5 turn slowly
    val speed: Float = 1f,
    val phase: Float = 0f,
)

class QhTheme(
    val id: String,
    val name: String,
    val tagline: String,
    val top: Long,
    val bottom: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val tile: Long,               // icon tile colour (with alpha)
    val dock: Long,               // dock bar colour (with alpha)
    val shape: String,            // default icon shape
    val layers: List<QhLayer>,
)

/** v213: the same theme with light text and dark tiles, for walls that are a photo (phone wallpaper, own picture). */
fun qhReadable(t: QhTheme): QhTheme = QhTheme(
    t.id, t.name, t.tagline, t.top, t.bottom, 0xFFFFFFFF, 0xFFD6D6DE, t.accent, 0x40000000, 0x59000000, t.shape, t.layers,
)

private fun n(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)
private fun circ(cx: Float, cy: Float, r: Float): String =
    "M${n(cx - r)},${n(cy)} A${n(r)},${n(r)} 0 1,0 ${n(cx + r)},${n(cy)} A${n(r)},${n(r)} 0 1,0 ${n(cx - r)},${n(cy)} Z"
private fun ell(cx: Float, cy: Float, rx: Float, ry: Float): String =
    "M${n(cx - rx)},${n(cy)} A${n(rx)},${n(ry)} 0 1,0 ${n(cx + rx)},${n(cy)} A${n(rx)},${n(ry)} 0 1,0 ${n(cx - rx)},${n(cy)} Z"
private fun dots(r: Float, vararg p: Pair<Float, Float>): String = p.joinToString(" ") { circ(it.first, it.second, r) }
private fun hlines(ys: List<Float>): String = ys.joinToString(" ") { "M0,${n(it)} L100,${n(it)}" }

private val STARS_A = arrayOf(
    8f to 12f, 22f to 30f, 35f to 8f, 48f to 22f, 63f to 14f, 90f to 10f, 14f to 58f, 40f to 70f,
    88f to 45f, 55f to 95f, 10f to 100f, 30f to 120f, 80f to 110f, 95f to 130f, 66f to 125f,
)
private val STARS_B = arrayOf(
    16f to 20f, 30f to 44f, 58f to 6f, 76f to 28f, 84f to 66f, 6f to 80f, 46f to 50f, 70f to 88f,
    22f to 96f, 92f to 100f, 50f to 135f, 12f to 140f, 38f to 105f,
)

val QH_THEMES: List<QhTheme> = listOf(
    // v217: Niagara style. Pure black, no tiles, no dock; the layout is a big text list (see qhStyleOf)
    QhTheme(
        "niagara", "Niagara", "black, big text, nothing else",
        0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFF8E8E93, 0xFFFFFFFF, 0x00000000, 0x00000000, "circle",
        emptyList(),
    ),
    // v217: Smart style. Deep blue, glass tiles, categories in the drawer and above the dock
    QhTheme(
        "smart", "Smart", "sorted for you, blue and glassy",
        0xFF0E1B2B, 0xFF1D4A73, 0xFFEAF4FF, 0xFF9DB8D2, 0xFF4FC3F7, 0x2EFFFFFF, 0x66101E2E, "circle",
        listOf(
            QhLayer("M0,160 Q25,135 50,160 T100,150 L100,220 L0,220 Z", 0xFF4FC3F7, 0.10f),
            QhLayer("M0,185 Q30,165 60,185 T100,178 L100,220 L0,220 Z", 0xFF4FC3F7, 0.10f, 0f, 1, 0.4f),
        ),
    ),
    // v217: Lawn style (Pixel-like). Soft grey, date on top, floating dock, search pill at the bottom
    QhTheme(
        "lawn", "Lawn", "soft grey, date on top, search at the bottom",
        0xFF1B1C20, 0xFF2B2E37, 0xFFE8EAED, 0xFFA0A4AD, 0xFF8AB4F8, 0x26FFFFFF, 0x33FFFFFF, "squircle",
        listOf(
            QhLayer(circ(88f, 28f, 30f), 0xFF8AB4F8, 0.07f),
            QhLayer(circ(10f, 190f, 38f), 0xFF8AB4F8, 0.05f),
        ),
    ),
)

/** v214: a saved own theme as a normal theme. */
fun qhFromCustom(c: com.krishna.questblock.data.QhCustomTheme): QhTheme = QhTheme(
    c.id, c.name, "my theme", c.top, c.bottom,
    if (c.dark) 0xFFF2F2F7 else 0xFF1B1B22, if (c.dark) 0xFFB5B5C2 else 0xFF5A5A66, c.accent,
    if (c.dark) 0x33FFFFFF else 0x22000000, if (c.dark) 0x66000000 else 0x66FFFFFF, c.shape,
    if (c.wall.isBlank()) emptyList() else listOf(QhLayer(c.wall, c.accent, 0.35f, 0f)),
)

fun qhTheme(id: String): QhTheme =
    QH_THEMES.firstOrNull { it.id == id }
        ?: com.krishna.questblock.data.QhStore.customCache.firstOrNull { it.id == id }?.let { qhFromCustom(it) }
        ?: QH_THEMES.first()

/** v214: colours from the phone (Android 12+) as one extra theme. */
fun qhMaterialTheme(ctx: android.content.Context): QhTheme? {
    if (android.os.Build.VERSION.SDK_INT < 31) return null
    val cs = androidx.compose.material3.dynamicDarkColorScheme(ctx)
    fun c(x: Color): Long = x.toArgb().toLong() and 0xFFFFFFFFL
    return QhTheme(
        "material", "Material You", "colours from your phone", c(cs.surface), c(cs.surfaceVariant), c(cs.onSurface), c(cs.onSurfaceVariant), c(cs.primary),
        (c(cs.primaryContainer) and 0xFFFFFFL) or 0x99000000L, (c(cs.surfaceContainer) and 0xFFFFFFL) or 0xB3000000L, "squircle", emptyList(),
    )
}

/** v214: every theme the person can pick (built in, own, Material You). */
fun qhAllThemes(ctx: android.content.Context): List<QhTheme> =
    QH_THEMES + com.krishna.questblock.data.QhStore.customCache.map { qhFromCustom(it) } + listOfNotNull(qhMaterialTheme(ctx))

/** v214: the theme in use right now: the night theme between the night hours, else the chosen one. */
fun qhThemeForState(ctx: android.content.Context, st: com.krishna.questblock.data.QhState): QhTheme {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val night = st.nightTheme.isNotBlank() && (if (st.nightFrom == st.nightTo) false else if (st.nightFrom < st.nightTo) h in st.nightFrom until st.nightTo else (h >= st.nightFrom || h < st.nightTo))
    val id = if (night) st.nightTheme else st.theme
    return if (id == "material") qhMaterialTheme(ctx) ?: qhTheme(st.theme) else qhTheme(id)
}

fun qhThemeFor(nameOrNumber: String): QhTheme? {
    val t = nameOrNumber.trim().lowercase()
    t.toIntOrNull()?.let { return QH_THEMES.getOrNull(it - 1) }
    val all = QH_THEMES + com.krishna.questblock.data.QhStore.customCache.map { qhFromCustom(it) }
    return all.firstOrNull { it.id == t || it.name.lowercase() == t } ?: all.firstOrNull { it.name.lowercase().startsWith(t) || it.id.startsWith(t) }
}

val QH_SHAPES: List<String> = listOf("circle", "squircle", "square", "leaf", "hexagon", "diamond")

fun qhShape(id: String): Shape = when (id) {
    "circle" -> CircleShape
    "squircle" -> RoundedCornerShape(32)
    "square" -> RoundedCornerShape(10)
    "leaf" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 12, bottomEndPercent = 50, bottomStartPercent = 12)
    "hexagon" -> GenericShape { s, _ ->
        moveTo(s.width * 0.5f, 0f); lineTo(s.width, s.height * 0.25f); lineTo(s.width, s.height * 0.75f)
        lineTo(s.width * 0.5f, s.height); lineTo(0f, s.height * 0.75f); lineTo(0f, s.height * 0.25f); close()
    }
    "diamond" -> GenericShape { s, _ ->
        moveTo(s.width * 0.5f, 0f); lineTo(s.width, s.height * 0.5f); lineTo(s.width * 0.5f, s.height); lineTo(0f, s.height * 0.5f); close()
    }
    else -> RoundedCornerShape(32)
}

fun qhColor(v: Long): Color = Color(v)

/** The theme background: a gradient with the SVG layers on top, a few of them slowly moving. */
@Composable
fun QhBackground(theme: QhTheme, modifier: Modifier = Modifier.fillMaxSize(), fill: Boolean = true, animate: Boolean = true) {
    val paths = remember(theme.id, theme.layers.size, theme.layers.firstOrNull()?.d) {
        theme.layers.map { l -> runCatching { PathParser().parsePathString(l.d).toPath() }.getOrDefault(Path()) }
    }
    val t: Float = if (animate) {
        val tr = rememberInfiniteTransition(label = "qh")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "qht").value
    } else 0f
    Canvas(modifier) {
        if (fill) drawRect(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
        val sx = size.width / 100f
        val sy = size.height / 220f
        theme.layers.forEachIndexed { i, l ->
            val ph = (t * l.speed + l.phase) % 1f
            var alpha = l.alpha
            var dx = 0f
            var dy = 0f
            var rot = 0f
            when (l.anim) {
                1 -> dx = sin(ph * 2f * PI.toFloat()) * 4f
                2 -> alpha *= 0.55f + 0.45f * (0.5f + 0.5f * sin(ph * 2f * PI.toFloat()))
                3 -> dy = sin(ph * 2f * PI.toFloat()) * 3f
                4 -> alpha *= 0.25f + 0.75f * abs(sin(ph * PI.toFloat()))
                5 -> rot = ph * 360f
            }
            withTransform({
                scale(sx, sy, Offset.Zero)
                translate(dx, dy)
                if (rot != 0f) rotate(rot, Offset(50f, 100f))
            }) {
                drawPath(
                    paths[i], Color(l.color), alpha,
                    if (l.stroke > 0f) Stroke(l.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else Fill,
                )
            }
        }
    }
}

// ── small SVG icons for the launcher's own buttons (24 x 24) ──
object QhSvg {
    const val APPS = "M4,4 L9,4 L9,9 L4,9 Z M10,4 L14,4 L14,9 L10,9 Z M15,4 L20,4 L20,9 L15,9 Z " +
        "M4,10 L9,10 L9,14 L4,14 Z M10,10 L14,10 L14,14 L10,14 Z M15,10 L20,10 L20,14 L15,14 Z " +
        "M4,15 L9,15 L9,20 L4,20 Z M10,15 L14,15 L14,20 L10,20 Z M15,15 L20,15 L20,20 L15,20 Z"
    const val SEARCH = "M10,4 A6,6 0 1,0 10,16 A6,6 0 1,0 10,4 Z M15,15 L20,20"
    const val HANDLE = "M7,12 L17,12"
    const val MORE = "M12,4 A1.6,1.6 0 1,0 12,7.2 A1.6,1.6 0 1,0 12,4 Z M12,10.4 A1.6,1.6 0 1,0 12,13.6 A1.6,1.6 0 1,0 12,10.4 Z M12,16.8 A1.6,1.6 0 1,0 12,20 A1.6,1.6 0 1,0 12,16.8 Z"
    const val LOCK = "M7,11 L17,11 L17,20 L7,20 Z M9,11 L9,8 A3,3 0 0,1 15,8 L15,11"
    const val FOLDER = "M3,7 L9,7 L11,9 L21,9 L21,19 L3,19 Z"
}

/** Draws one of the [QhSvg] paths. [filled] true = solid shapes, false = outline. */
@Composable
fun QhSvgIcon(d: String, color: Color, size: Dp = 24.dp, filled: Boolean = false, modifier: Modifier = Modifier) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(path, color, 1f, if (filled) Fill else Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
