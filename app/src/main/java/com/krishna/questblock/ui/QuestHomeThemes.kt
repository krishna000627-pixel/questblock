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
    QhTheme(
        "dungeon", "Dungeon", "stone, torches, a long way down",
        0xFF120A0A, 0xFF2B1712, 0xFFF3E4D2, 0xFFB89B86, 0xFFFFB347, 0x26FFB347, 0xB3100806, "square",
        listOf(
            QhLayer("M8,220 L8,95 Q8,38 50,38 Q92,38 92,95 L92,220", 0xFF6B4630, 0.55f, 1.4f),
            QhLayer("M20,220 L20,100 Q20,52 50,52 Q80,52 80,100 L80,220", 0xFF6B4630, 0.35f, 1f),
            QhLayer(hlines(listOf(120f, 150f, 180f)), 0xFF8A5A3C, 0.18f, 0.6f),
            QhLayer(circ(10f, 98f, 14f) + " " + circ(90f, 98f, 14f), 0xFFFF8A2B, 0.12f, 0f, 2, 1f),
            QhLayer("M10,110 Q5,98 10,84 Q15,98 10,110 Z M90,110 Q85,98 90,84 Q95,98 90,110 Z", 0xFFFFB347, 0.95f, 0f, 2, 1.6f),
            QhLayer("M0,205 L100,205 L100,220 L0,220 Z", 0xFF0C0605, 0.9f),
        ),
    ),
    QhTheme(
        "forest", "Forest", "pines, mist and fireflies",
        0xFF08170F, 0xFF1D3A28, 0xFFE6F4EA, 0xFF9CBFA7, 0xFF7BE495, 0x267BE495, 0xB3071009, "leaf",
        listOf(
            QhLayer(circ(78f, 28f, 9f), 0xFFF5F0C8, 0.85f),
            QhLayer("M0,160 Q25,135 50,155 T100,150 L100,220 L0,220 Z", 0xFF0F2A1B, 0.9f),
            QhLayer("M0,130 Q30,122 60,130 T100,126 L100,142 L0,142 Z", 0xFFFFFFFF, 0.06f, 0f, 1, 0.8f),
            QhLayer("M10,175 L16,150 L22,175 Z M24,180 L32,148 L40,180 Z M60,178 L67,152 L74,178 Z M78,182 L86,146 L94,182 Z", 0xFF0A1D13, 1f),
            QhLayer("M0,195 Q30,175 60,192 T100,188 L100,220 L0,220 Z", 0xFF06120C, 1f),
            QhLayer(dots(0.9f, 20f to 150f, 52f to 138f, 84f to 160f, 38f to 170f), 0xFFD7FF7A, 0.9f, 0f, 4, 1f, 0f),
            QhLayer(dots(0.9f, 12f to 165f, 66f to 148f, 92f to 140f, 46f to 182f), 0xFFD7FF7A, 0.9f, 0f, 4, 1f, 0.5f),
        ),
    ),
    QhTheme(
        "cyber", "Cyber Neon", "a city that never switches off",
        0xFF06041A, 0xFF1B0A3A, 0xFFEAF6FF, 0xFF8FA3C7, 0xFF00F0FF, 0x2600F0FF, 0xB3080520, "hexagon",
        listOf(
            QhLayer(circ(50f, 70f, 22f), 0xFFFF2BD6, 0.8f, 1.2f, 2, 0.8f),
            QhLayer("M28,70 L72,70 M30,78 L70,78 M33,86 L67,86", 0xFFFF2BD6, 0.6f, 0.8f),
            QhLayer("M0,170 L0,140 L8,140 L8,125 L18,125 L18,150 L28,150 L28,118 L38,118 L38,145 L52,145 L52,130 L64,130 L64,155 L76,155 L76,122 L88,122 L88,148 L100,148 L100,170 Z", 0xFF0B0828, 1f),
            QhLayer("M0,170 L0,140 L8,140 L8,125 L18,125 L18,150 L28,150 L28,118 L38,118 L38,145 L52,145 L52,130 L64,130 L64,155 L76,155 L76,122 L88,122 L88,148 L100,148 L100,170", 0xFF00F0FF, 0.5f, 0.5f),
            QhLayer(hlines(listOf(175f, 185f, 197f, 211f)), 0xFFFF2BD6, 0.4f, 0.5f),
            QhLayer("M50,170 L0,220 M50,170 L25,220 M50,170 L50,220 M50,170 L75,220 M50,170 L100,220", 0xFF00F0FF, 0.35f, 0.4f),
        ),
    ),
    QhTheme(
        "terminal", "Terminal", "green on black, nothing else",
        0xFF000000, 0xFF021208, 0xFFB8F5CE, 0xFF4E9A6C, 0xFF3DDC84, 0x1F3DDC84, 0xB3000000, "square",
        listOf(
            QhLayer(hlines((0..36).map { it * 6f }), 0xFF3DDC84, 0.10f, 0.25f),
            QhLayer("M18,70 L48,95 L18,120", 0xFF3DDC84, 0.14f, 5f),
            QhLayer("M56,122 L86,122", 0xFF3DDC84, 0.14f, 5f, 2, 1.2f),
            QhLayer("M6,16 L6,6 L16,6 M94,16 L94,6 L84,6 M6,204 L6,214 L16,214 M94,204 L94,214 L84,214", 0xFF3DDC84, 0.5f, 0.8f),
            QhLayer("M70,18 L76,18 L76,27 L70,27 Z", 0xFF3DDC84, 0.9f, 0f, 2, 2f),
        ),
    ),
    QhTheme(
        "paper", "Paper & Ink", "mountains in washed ink",
        0xFFF5F0E6, 0xFFE4D9C4, 0xFF2A2622, 0xFF6F665A, 0xFFB3261E, 0x1F2A2622, 0xCCE9DFCB, "circle",
        listOf(
            QhLayer(circ(72f, 36f, 11f), 0xFFB3261E, 0.85f),
            QhLayer("M0,120 L18,90 L32,110 L50,70 L68,108 L82,88 L100,118 L100,150 L0,150 Z", 0xFF2A2622, 0.10f),
            QhLayer("M0,150 L22,118 L40,140 L60,100 L80,138 L100,120 L100,180 L0,180 Z", 0xFF2A2622, 0.16f),
            QhLayer("M0,180 Q30,160 55,176 T100,170 L100,220 L0,220 Z", 0xFF2A2622, 0.24f),
            QhLayer("M20,60 L26,64 L32,58 M26,64 L26,70", 0xFF2A2622, 0.6f, 0.8f, 1, 1f),
            QhLayer("M44,44 L49,47 L54,42 M49,47 L49,52", 0xFF2A2622, 0.5f, 0.8f, 1, 1f, 0.3f),
            QhLayer("M0,140 Q30,134 60,140 T100,136 L100,150 L0,150 Z", 0xFFFFFFFF, 0.25f, 0f, 1, 0.6f),
        ),
    ),
    QhTheme(
        "ocean", "Ocean", "slow waves, deep blue",
        0xFF031A28, 0xFF0A5578, 0xFFEAFBFF, 0xFF8CC7DA, 0xFF4FD1E8, 0x264FD1E8, 0xB3021420, "circle",
        listOf(
            QhLayer("M30,0 L45,0 L70,220 L40,220 Z M55,0 L62,0 L100,220 L78,220 Z", 0xFFFFFFFF, 0.05f),
            QhLayer("M-20,150 Q-8,142 5,150 T30,150 T55,150 T80,150 T105,150 T130,150 L130,220 L-20,220 Z", 0xFF0E6E92, 0.5f, 0f, 1, 1f),
            QhLayer("M-20,170 Q-8,162 5,170 T30,170 T55,170 T80,170 T105,170 T130,170 L130,220 L-20,220 Z", 0xFF0A5578, 0.7f, 0f, 1, 1.2f, 0.5f),
            QhLayer("M-20,192 Q-8,185 5,192 T30,192 T55,192 T80,192 T105,192 T130,192 L130,220 L-20,220 Z", 0xFF063A56, 0.9f, 0f, 1, 0.8f, 0.2f),
            QhLayer(dots(1.2f, 20f to 120f, 34f to 100f, 70f to 90f, 82f to 130f), 0xFFFFFFFF, 0.35f, 0f, 3, 1f),
            QhLayer("M60,110 Q68,104 76,110 Q68,116 60,110 Z M76,110 L82,106 L82,114 Z", 0xFF4FD1E8, 0.6f, 0f, 1, 1.4f, 0.1f),
        ),
    ),
    QhTheme(
        "sakura", "Sakura", "petals under a pale moon",
        0xFF241020, 0xFF5A2644, 0xFFFFEFF6, 0xFFD6A5BD, 0xFFFFB7D5, 0x26FFB7D5, 0xB31A0A14, "leaf",
        listOf(
            QhLayer(circ(24f, 32f, 12f), 0xFFFFE8F1, 0.8f),
            QhLayer("M100,40 Q70,50 50,45 Q35,42 20,60", 0xFF3A1A2A, 0.9f, 2f),
            QhLayer("M55,46 Q58,60 52,70", 0xFF3A1A2A, 0.9f, 1.2f),
            QhLayer(dots(2.2f, 30f to 56f, 46f to 44f, 60f to 48f, 52f to 70f), 0xFFFFB7D5, 0.9f, 0f, 2, 0.7f),
            QhLayer(ell(30f, 100f, 2f, 1.2f) + " " + ell(70f, 120f, 2f, 1.2f) + " " + ell(52f, 150f, 2f, 1.2f), 0xFFFFB7D5, 0.7f, 0f, 3, 1f),
            QhLayer(ell(84f, 90f, 2f, 1.2f) + " " + ell(14f, 135f, 2f, 1.2f) + " " + ell(62f, 170f, 2f, 1.2f), 0xFFFFD2E6, 0.6f, 0f, 1, 1f, 0.4f),
            QhLayer("M0,200 Q40,190 100,200 L100,220 L0,220 Z", 0xFF1A0A14, 1f),
        ),
    ),
    QhTheme(
        "desert", "Desert Gold", "sun, dunes, old stone",
        0xFF3D2108, 0xFFB86B24, 0xFFFFF3DD, 0xFFE6C28E, 0xFFFFD27A, 0x33FFD27A, 0xB3301604, "diamond",
        listOf(
            QhLayer(circ(50f, 90f, 18f), 0xFFFFD27A, 0.9f, 0f, 2, 0.5f),
            QhLayer("M62,150 L78,118 L94,150 Z M80,150 L90,132 L100,150 Z", 0xFF5A3010, 0.8f),
            QhLayer("M0,150 Q30,128 60,150 T100,146 L100,220 L0,220 Z", 0xFF8A4A18, 0.85f),
            QhLayer("M0,180 Q35,160 70,182 T100,176 L100,220 L0,220 Z", 0xFF6A360F, 1f),
            QhLayer("M0,205 Q40,192 100,206 L100,220 L0,220 Z", 0xFF3F1F08, 1f),
            QhLayer("M0,140 Q25,136 50,140 T100,138", 0xFFFFE3A8, 0.25f, 0.6f, 1, 0.7f),
        ),
    ),
    QhTheme(
        "space", "Space Station", "quiet orbit, loud stars",
        0xFF02030A, 0xFF0B1030, 0xFFEAF0FF, 0xFF8E9BC9, 0xFF8AB4FF, 0x268AB4FF, 0xB3020410, "hexagon",
        listOf(
            QhLayer(dots(0.7f, *STARS_A), 0xFFFFFFFF, 0.9f, 0f, 4, 1f, 0f),
            QhLayer(dots(0.7f, *STARS_B), 0xFFFFFFFF, 0.9f, 0f, 4, 1f, 0.5f),
            QhLayer(circ(72f, 60f, 16f), 0xFF3D4F9E, 0.9f),
            QhLayer(ell(72f, 60f, 27f, 6f), 0xFF8AB4FF, 0.7f, 1.2f),
            QhLayer("M10,150 L90,150 M10,146 L10,154 M30,146 L30,154 M50,146 L50,154 M70,146 L70,154 M90,146 L90,154", 0xFF6F7FB8, 0.6f, 0.8f, 1, 0.3f),
            QhLayer("M20,138 L30,138 L30,146 L20,146 Z M70,138 L80,138 L80,146 L70,146 Z", 0xFF3D4F9E, 0.8f, 0f, 1, 0.3f),
            QhLayer("M0,210 Q50,188 100,210 L100,220 L0,220 Z", 0xFF2B5CD6, 0.5f),
        ),
    ),
    QhTheme(
        "steel", "Steel Vault", "bolted, heavy, locked",
        0xFF14171B, 0xFF2A3036, 0xFFEDEFF2, 0xFF9AA3AE, 0xFFF0B429, 0x26F0B429, 0xB30E1013, "squircle",
        listOf(
            QhLayer(circ(50f, 100f, 40f), 0xFF55606B, 0.8f, 2f),
            QhLayer(circ(50f, 100f, 30f), 0xFF55606B, 0.6f, 1f),
            QhLayer("M50,70 L50,130 M20,100 L80,100 M29,79 L71,121 M71,79 L29,121", 0xFFF0B429, 0.5f, 1.2f, 5, 0.5f),
            QhLayer(circ(50f, 100f, 6f), 0xFFF0B429, 0.7f),
            QhLayer(dots(2f, 14f to 40f, 86f to 40f, 14f to 200f, 86f to 200f), 0xFF55606B, 0.9f),
            QhLayer((0..9).joinToString(" ") { i -> "M${n(i * 10f)},215 L${n(i * 10f + 10f)},205" }, 0xFFF0B429, 0.25f, 1.2f),
        ),
    ),
)

fun qhTheme(id: String): QhTheme = QH_THEMES.firstOrNull { it.id == id } ?: QH_THEMES.first()

fun qhThemeFor(nameOrNumber: String): QhTheme? {
    val t = nameOrNumber.trim().lowercase()
    t.toIntOrNull()?.let { return QH_THEMES.getOrNull(it - 1) }
    return QH_THEMES.firstOrNull { it.id == t || it.name.lowercase() == t } ?: QH_THEMES.firstOrNull { it.name.lowercase().startsWith(t) || it.id.startsWith(t) }
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
fun QhBackground(theme: QhTheme, modifier: Modifier = Modifier.fillMaxSize()) {
    val paths = remember(theme.id) { theme.layers.map { PathParser().parsePathString(it.d).toPath() } }
    val tr = rememberInfiniteTransition(label = "qh")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "qht")
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
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
