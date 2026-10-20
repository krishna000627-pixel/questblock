package com.krishna.questblock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.krishna.questblock.data.Flags
import com.krishna.questblock.util.SystemEvents
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * v254: effects for the shop themes that have no style engine (Sunset, Ocean, Forest, Neon, Sci-Fi, AMOLED, Terminal, Paper, Linen,
 * Sage, Sky, Lilac, Chalk, Clay, Rose, Indigo). The look of their cards, buttons and fonts is untouched; they get the same five things
 * as the style themes: Focus ring, XP level bar, level up, quest done pop and lock screens, each with its own motif, plus a page
 * background for a few. Everything is static; the only motion is the one-shot level up, quest done and lock emblem pop.
 * Behind the master flag themefx and the theme's own flag fx_<theme> (Flags.EXTRA). #svg-logo  New theme with effects: add it to Fx2,
 * Fx2Now, Fx2X.WORDS, Flags.EXTRA and every `when (fx)` below.
 */
enum class Fx2 { NONE, SUNSET, OCEAN, FOREST, NEON, SCIFI, AMOLED, TERMINAL, PAPER, LINEN, SAGE, SKY, LILAC, CHALK, CLAY, ROSE, INDIGO }

val Fx2Now: Fx2 get() = if (PosterOn || TermuxOn) Fx2.NONE else when (ShopTheme) {
    "theme_sunset" -> Fx2.SUNSET
    "theme_ocean" -> Fx2.OCEAN
    "theme_forest" -> Fx2.FOREST
    "theme_neon" -> Fx2.NEON
    "theme_scifi" -> Fx2.SCIFI
    "theme_amoled" -> Fx2.AMOLED
    "theme_terminal" -> Fx2.TERMINAL
    "theme_paper" -> Fx2.PAPER
    "theme_linen" -> Fx2.LINEN
    "theme_sage" -> Fx2.SAGE
    "theme_sky" -> Fx2.SKY
    "theme_lilac" -> Fx2.LILAC
    "theme_chalk" -> Fx2.CHALK
    "theme_clay" -> Fx2.CLAY
    "theme_rose" -> Fx2.ROSE
    "theme_indigo" -> Fx2.INDIGO
    else -> Fx2.NONE
}

object Fx2X {
    /** The effects of the theme in use are on: master flag themefx and the theme's own fx_<theme>. */
    val on: Boolean get() = Fx2Now != Fx2.NONE && Flags.get("themefx") && Flags.get("fx_" + Fx2Now.name.lowercase())

    private val WORDS: Map<Fx2, Map<String, String>> = mapOf(
        Fx2.SUNSET to mapOf("locked" to "The sun has set", "done" to "\u2600 Golden hour", "level" to "NEW DAY", "ring" to "until sunset"),
        Fx2.OCEAN to mapOf("locked" to "Too deep to reach", "done" to "Tide is in", "level" to "DEEPER", "ring" to "until low tide"),
        Fx2.FOREST to mapOf("locked" to "The path is overgrown", "done" to "Grown", "level" to "GROWTH", "ring" to "growing"),
        Fx2.NEON to mapOf("locked" to "OFFLINE", "done" to "NICE", "level" to "LEVEL UP", "ring" to "ON AIR"),
        Fx2.SCIFI to mapOf("locked" to "ACCESS LOCKED", "done" to "SYNC COMPLETE", "level" to "UPGRADE COMPLETE", "ring" to "T-MINUS"),
        Fx2.AMOLED to mapOf("locked" to "Locked", "done" to "Done", "level" to "Level up", "ring" to "left"),
        Fx2.TERMINAL to mapOf("locked" to "permission denied", "done" to "exit 0", "level" to "level_up", "ring" to "# remaining"),
        Fx2.PAPER to mapOf("locked" to "Page closed", "done" to "Noted", "level" to "NEW CHAPTER", "ring" to "left to write"),
        Fx2.LINEN to mapOf("locked" to "Stitched shut", "done" to "Stitched", "level" to "WELL DONE", "ring" to "left to sew"),
        Fx2.SAGE to mapOf("locked" to "Resting", "done" to "Sprouted", "level" to "BLOOM", "ring" to "to grow"),
        Fx2.SKY to mapOf("locked" to "Cloud cover", "done" to "Clear skies", "level" to "SOAR", "ring" to "in flight"),
        Fx2.LILAC to mapOf("locked" to "Petals closed", "done" to "In bloom", "level" to "BLOOM", "ring" to "to bloom"),
        Fx2.CHALK to mapOf("locked" to "Class dismissed", "done" to "Marked", "level" to "A+", "ring" to "left in class"),
        Fx2.CLAY to mapOf("locked" to "Squished shut", "done" to "Squish", "level" to "BOING!", "ring" to "left"),
        Fx2.ROSE to mapOf("locked" to "Heart closed", "done" to "\u2665 Lovely", "level" to "LOVELY", "ring" to "with love"),
        Fx2.INDIGO to mapOf("locked" to "The night is closed", "done" to "\u2726 Shooting star", "level" to "STARGAZER", "ring" to "until dawn"),
    )

    fun word(key: String, default: String): String = if (!on) default else WORDS[Fx2Now]?.get(key) ?: default
}

/** Either effects family (style themes or these) is on. */
object AnyFx { val on: Boolean get() = ThemeExtras.on || Fx2X.on }

// ── helpers ───────────────────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.leaf(base: Offset, deg: Float, len: Float, wid: Float, col: Color) {
    val a = Math.toRadians(deg.toDouble()); val dx = Math.cos(a).toFloat(); val dy = Math.sin(a).toFloat()
    val tip = Offset(base.x + dx * len, base.y + dy * len)
    val mx = base.x + dx * len * 0.5f; val my = base.y + dy * len * 0.5f
    val px = -dy * wid; val py = dx * wid
    val p = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(mx + px, my + py, mx + px, my + py, tip.x, tip.y)
        cubicTo(mx - px, my - py, mx - px, my - py, base.x, base.y)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.heart(at: Offset, s: Float, col: Color) {
    val p = Path().apply {
        moveTo(at.x, at.y + s * 0.9f)
        cubicTo(at.x - s * 1.4f, at.y - s * 0.1f, at.x - s * 0.7f, at.y - s * 1.2f, at.x, at.y - s * 0.4f)
        cubicTo(at.x + s * 0.7f, at.y - s * 1.2f, at.x + s * 1.4f, at.y - s * 0.1f, at.x, at.y + s * 0.9f)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.star4(at: Offset, s: Float, col: Color) {
    drawLine(col, Offset(at.x - s, at.y), Offset(at.x + s, at.y), 1.5.dp.toPx(), StrokeCap.Round)
    drawLine(col, Offset(at.x, at.y - s), Offset(at.x, at.y + s), 1.5.dp.toPx(), StrokeCap.Round)
    drawCircle(col, s * 0.3f, at)
}

private fun hexPath(ctr: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val q = polar(ctr, r, -90f + i * 60f)
        if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
    }
    close()
}

private fun DrawScope.padlock(c: Color) {
    val w = size.width; val h = size.height
    drawCircle(ColorBg, w / 2, center)
    drawArc(c, 180f, 180f, false, topLeft = Offset(w * 0.30f, h * 0.18f), size = Size(w * 0.40f, h * 0.42f), style = Stroke(2.5.dp.toPx()))
    drawRoundRect(c, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.40f), CornerRadius(2.dp.toPx()))
    drawCircle(ColorBg, w * 0.05f, Offset(w * 0.5f, h * 0.62f))
}

private fun fontOf(fx: Fx2): FontFamily = when (fx) {
    Fx2.TERMINAL, Fx2.SCIFI -> FontFamily.Monospace
    Fx2.PAPER, Fx2.ROSE -> FontFamily.Serif
    Fx2.CHALK -> FontFamily.Cursive
    else -> FontFamily.Default
}

// ── page background ──────────────────────────────────────────────────────────────────────────────────────

/** v254: a static page background for Sci-Fi (grid), Terminal (scan lines), Indigo (stars), Chalk (dust) and Paper (specks). */
@Composable
internal fun Modifier.fx2Background(): Modifier {
    val fx = Fx2Now
    if (!Fx2X.on) return this
    val c = ColorPrimary
    val ink = ColorText
    return drawBehind {
        when (fx) {
            Fx2.SCIFI -> {
                val st = 28.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(c.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), 1f); x += st }
                var y = 0f
                while (y < size.height) { drawLine(c.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f); y += st }
            }
            Fx2.TERMINAL -> {
                var y = 0f
                while (y < size.height) { drawLine(c.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += 4.dp.toPx() }
            }
            Fx2.INDIGO -> for (i in 0 until 40) {
                drawCircle(c.copy(alpha = 0.25f), (1f + (i % 3) * 0.5f).dp.toPx(), Offset(size.width * (((i * 37) % 100) / 100f), size.height * (((i * 53 + 17) % 100) / 100f)))
            }
            Fx2.CHALK -> for (i in 0 until 60) {
                drawCircle(ink.copy(alpha = 0.06f), (0.8f + (i % 3) * 0.6f).dp.toPx(), Offset(size.width * (((i * 41 + 7) % 100) / 100f), size.height * (((i * 59 + 23) % 100) / 100f)))
            }
            Fx2.PAPER -> for (i in 0 until 50) {
                drawCircle(ink.copy(alpha = 0.05f), (0.8f + (i % 2) * 0.7f).dp.toPx(), Offset(size.width * (((i * 43 + 5) % 100) / 100f), size.height * (((i * 61 + 29) % 100) / 100f)))
            }
            else -> Unit
        }
    }
}

// ── Focus ring ───────────────────────────────────────────────────────────────────────────────────────────

/** v254: the Focus ring of the running session for the Fx2 themes (called through FxFocusRing). [frac] 1 = full time left. Static. */
@Composable
internal fun Fx2FocusRing(frac: Float, left: String) {
    val fx = Fx2Now
    val c = ColorPrimary
    val ink = ColorText
    val coin = ColorCoin
    val f = frac.coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = center
            val full = size.minDimension / 2
            val sweep = 360f * f
            val end = -90f + sweep
            val r = full - 34.dp.toPx()
            val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
            fun arc(col: Color, sw: Float, w: Float, cap: StrokeCap = StrokeCap.Butt, rr: Float = r, pe: PathEffect? = null) =
                drawArc(col, -90f, sw, false, topLeft = Offset(ctr.x - rr, ctr.y - rr), size = Size(rr * 2, rr * 2), style = Stroke(w, cap = cap, pathEffect = pe))
            when (fx) {
                Fx2.SUNSET -> {
                    arc(c.copy(alpha = 0.15f), 360f, 14.dp.toPx())
                    arc(c, sweep, 14.dp.toPx(), StrokeCap.Round)
                    for (i in 0 until 24) {
                        val a = -90f + i * 15f
                        val len = if (i % 3 == 0) 14.dp.toPx() else 8.dp.toPx()
                        drawLine(if (i / 24f <= f) coin else coin.copy(alpha = 0.25f), polar(ctr, r + 18.dp.toPx(), a), polar(ctr, r + 18.dp.toPx() + len, a), 2.5.dp.toPx(), StrokeCap.Round)
                    }
                    val e = polar(ctr, r, end)
                    drawCircle(coin.copy(alpha = 0.3f), 17.dp.toPx(), e); drawCircle(coin, 9.dp.toPx(), e)
                }
                Fx2.OCEAN -> {
                    arc(c.copy(alpha = 0.12f), 360f, 10.dp.toPx())
                    for (k in 1..3) drawCircle(c.copy(alpha = 0.22f - k * 0.05f), r - k * 14.dp.toPx() - 6.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx()))
                    arc(c, sweep, 10.dp.toPx(), StrokeCap.Round)
                    val e = polar(ctr, r, end)
                    drawCircle(c, 7.dp.toPx(), e, style = Stroke(2.dp.toPx()))
                    drawCircle(c.copy(alpha = 0.6f), 3.dp.toPx(), polar(ctr, r - 14.dp.toPx(), end - 6f))
                    drawCircle(c.copy(alpha = 0.4f), 2.dp.toPx(), polar(ctr, r - 24.dp.toPx(), end - 3f))
                }
                Fx2.FOREST -> {
                    arc(c.copy(alpha = 0.14f), 360f, 8.dp.toPx())
                    arc(c, sweep, 8.dp.toPx(), StrokeCap.Round)
                    for (i in 0 until 12) {
                        val a = -90f + i * 30f
                        leaf(polar(ctr, r + 6.dp.toPx(), a), a + (if (i % 2 == 0) 25f else -25f), 20.dp.toPx(), 6.dp.toPx(), if (i * 30f <= sweep) c.copy(alpha = 0.9f) else c.copy(alpha = 0.18f))
                    }
                }
                Fx2.NEON -> {
                    arc(c.copy(alpha = 0.18f), 360f, 2.dp.toPx())
                    arc(c.copy(alpha = 0.10f), sweep, 26.dp.toPx(), StrokeCap.Round)
                    arc(c.copy(alpha = 0.20f), sweep, 18.dp.toPx(), StrokeCap.Round)
                    arc(c.copy(alpha = 0.40f), sweep, 11.dp.toPx(), StrokeCap.Round)
                    arc(c, sweep, 6.dp.toPx(), StrokeCap.Round)
                    arc(lerp(c, Color.White, 0.75f), sweep, 2.dp.toPx(), StrokeCap.Round)
                }
                Fx2.SCIFI -> {
                    for (i in 0 until 36) {
                        val lit = (i + 1) / 36f <= f + 0.001f
                        drawArc(if (lit) c else c.copy(alpha = 0.18f), -90f + i * 10f + 1f, 8f, false, topLeft = tl, size = sz, style = Stroke(12.dp.toPx()))
                    }
                    drawCircle(c.copy(alpha = 0.35f), r - 20.dp.toPx(), ctr, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)))
                    for (k in 0 until 4) drawCircle(c, 3.dp.toPx(), polar(ctr, r - 20.dp.toPx(), k * 90f))
                }
                Fx2.AMOLED -> {
                    arc(ink.copy(alpha = 0.18f), 360f, 1.5.dp.toPx())
                    arc(c, sweep, 3.dp.toPx(), StrokeCap.Round)
                    drawCircle(ink, 4.dp.toPx(), polar(ctr, r, end))
                }
                Fx2.TERMINAL -> {
                    for (i in 0 until 60) {
                        val a = -90f + i * 6f
                        drawLine(if ((i + 1) / 60f <= f + 0.001f) c else c.copy(alpha = 0.2f), polar(ctr, r - 8.dp.toPx(), a), polar(ctr, r + 8.dp.toPx(), a), 2.dp.toPx())
                    }
                    val e = polar(ctr, r + 16.dp.toPx(), end)
                    drawRect(c, Offset(e.x - 5.dp.toPx(), e.y - 8.dp.toPx()), Size(10.dp.toPx(), 16.dp.toPx()))
                }
                Fx2.PAPER -> {
                    arc(ink.copy(alpha = 0.10f), 360f, 2.dp.toPx(), pe = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f))
                    arc(ink.copy(alpha = 0.8f), sweep, 3.dp.toPx(), StrokeCap.Round)
                    arc(ink.copy(alpha = 0.45f), sweep * 0.98f, 2.dp.toPx(), StrokeCap.Round, rr = r + 3.dp.toPx())
                    arc(ink.copy(alpha = 0.30f), sweep * 0.90f, 1.5.dp.toPx(), StrokeCap.Round, rr = r - 3.dp.toPx())
                    drawCircle(ink, 3.5.dp.toPx(), polar(ctr, r, end))
                }
                Fx2.LINEN -> {
                    val d = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), 0f)
                    arc(c.copy(alpha = 0.2f), 360f, 4.dp.toPx(), pe = d)
                    arc(c, sweep, 4.dp.toPx(), StrokeCap.Round, pe = d)
                    val eye = polar(ctr, r + 22.dp.toPx(), end + 8f)
                    drawLine(c, polar(ctr, r - 4.dp.toPx(), end), eye, 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(c, 4.dp.toPx(), eye, style = Stroke(1.5.dp.toPx()))
                }
                Fx2.SAGE -> {
                    val d = PathEffect.dashPathEffect(floatArrayOf(0.1f, 11.dp.toPx()), 0f)
                    arc(c.copy(alpha = 0.2f), 360f, 5.dp.toPx(), StrokeCap.Round, pe = d)
                    arc(c, sweep, 5.dp.toPx(), StrokeCap.Round, pe = d)
                    val e = polar(ctr, r, end)
                    leaf(e, end - 40f, 22.dp.toPx(), 7.dp.toPx(), c); leaf(e, end + 40f, 22.dp.toPx(), 7.dp.toPx(), c)
                }
                Fx2.SKY -> {
                    arc(c.copy(alpha = 0.15f), 360f, 8.dp.toPx())
                    arc(c, sweep, 8.dp.toPx(), StrokeCap.Round)
                    val e = polar(ctr, r, end)
                    drawCircle(coin.copy(alpha = 0.3f), 16.dp.toPx(), e); drawCircle(coin, 9.dp.toPx(), e)
                    val cc = Offset(ctr.x + 40.dp.toPx(), ctr.y + 62.dp.toPx()); val cl = c.copy(alpha = 0.14f)
                    drawCircle(cl, 12.dp.toPx(), cc)
                    drawCircle(cl, 16.dp.toPx(), Offset(cc.x + 16.dp.toPx(), cc.y - 6.dp.toPx()))
                    drawCircle(cl, 11.dp.toPx(), Offset(cc.x + 32.dp.toPx(), cc.y))
                }
                Fx2.LILAC -> {
                    for (i in 0 until 12) {
                        val a = -90f + i * 30f
                        leaf(polar(ctr, r - 6.dp.toPx(), a), a, 30.dp.toPx(), 9.dp.toPx(), if (i / 12f <= f) c.copy(alpha = 0.85f) else c.copy(alpha = 0.15f))
                    }
                    arc(c.copy(alpha = 0.5f), sweep, 2.dp.toPx(), rr = r - 12.dp.toPx())
                }
                Fx2.CHALK -> {
                    arc(ink.copy(alpha = 0.12f), 360f, 2.dp.toPx(), pe = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f))
                    arc(ink.copy(alpha = 0.75f), sweep, 4.dp.toPx(), StrokeCap.Round)
                    arc(ink.copy(alpha = 0.40f), sweep * 0.96f, 2.dp.toPx(), StrokeCap.Round, rr = r + 3.dp.toPx())
                    arc(ink.copy(alpha = 0.30f), sweep * 0.92f, 2.dp.toPx(), StrokeCap.Round, rr = r - 3.dp.toPx())
                    for (i in 0 until 12) {
                        val a = -90f + i * 30f
                        drawLine(ink.copy(alpha = if (i / 12f <= f) 0.7f else 0.25f), polar(ctr, r + 14.dp.toPx(), a), polar(ctr, r + 22.dp.toPx(), a), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                Fx2.CLAY -> {
                    arc(c.copy(alpha = 0.14f), 360f, 26.dp.toPx())
                    drawArc(Color.Black.copy(alpha = 0.10f), -90f, sweep, false, topLeft = Offset(tl.x, tl.y + 4.dp.toPx()), size = sz, style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
                    arc(c, sweep, 22.dp.toPx(), StrokeCap.Round)
                    arc(Color.White.copy(alpha = 0.35f), sweep * 0.9f, 5.dp.toPx(), StrokeCap.Round, rr = r - 5.dp.toPx())
                    val e = polar(ctr, r, end)
                    drawCircle(Color.White.copy(alpha = 0.5f), 5.dp.toPx(), Offset(e.x - 3.dp.toPx(), e.y - 4.dp.toPx()))
                }
                Fx2.ROSE -> {
                    arc(c.copy(alpha = 0.15f), 360f, 8.dp.toPx())
                    arc(c, sweep, 8.dp.toPx(), StrokeCap.Round)
                    for (i in 0 until 12) drawCircle(c.copy(alpha = if (i / 12f <= f) 0.8f else 0.2f), 3.dp.toPx(), polar(ctr, r + 22.dp.toPx(), -90f + i * 30f))
                    heart(polar(ctr, r, end), 11.dp.toPx(), c)
                }
                Fx2.INDIGO -> {
                    arc(c.copy(alpha = 0.18f), 360f, 3.dp.toPx(), pe = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx()), 0f))
                    arc(c, sweep, 4.dp.toPx(), StrokeCap.Round)
                    for (i in 0 until 14) {
                        val rr = r + (if (i % 2 == 0) 22 else -20).dp.toPx()
                        star4(polar(ctr, rr, -90f + i * 360f / 14f), (if (i % 3 == 0) 6 else 4).dp.toPx(), if (i / 14f <= f) coin else c.copy(alpha = 0.25f))
                    }
                    val e = polar(ctr, r, end)
                    drawCircle(coin, 11.dp.toPx(), e)
                    drawCircle(ColorBg, 9.dp.toPx(), Offset(e.x + 5.dp.toPx(), e.y - 4.dp.toPx()))
                }
                else -> Unit
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val txt = if (fx == Fx2.TERMINAL) "> $left" else left
            val fs = when (fx) {
                Fx2.TERMINAL, Fx2.SCIFI -> 40.sp
                Fx2.PAPER, Fx2.ROSE, Fx2.CHALK -> 50.sp
                else -> 54.sp
            }
            Text(txt, fontSize = fs, fontWeight = if (fx == Fx2.AMOLED) FontWeight.ExtraLight else FontWeight.Black, fontFamily = fontOf(fx), color = ink)
            Text(Fx2X.word("ring", "remaining"), color = ColorMuted, fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = fontOf(fx))
        }
    }
}

// ── XP level bar ─────────────────────────────────────────────────────────────────────────────────────────

/** v254: the XP level bar of the Home hero card for the Fx2 themes (called through FxXpBar). [fraction] is the animated XP into the level, [xp] out of 100. */
@Composable
internal fun Fx2XpBar(fraction: Float, xp: Int) {
    val fx = Fx2Now
    val f = fraction.coerceIn(0f, 1f)
    val c = ColorPrimary
    val ink = ColorText
    val coin = ColorCoin
    if (fx == Fx2.TERMINAL) {
        val n = 20; val k = (f * n).roundToInt()
        Text("[" + "#".repeat(k) + ".".repeat(n - k) + "] $xp%", color = c, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        return
    }
    val h = when (fx) {
        Fx2.CLAY -> 22.dp
        Fx2.FOREST, Fx2.SAGE, Fx2.LINEN, Fx2.LILAC, Fx2.SKY, Fx2.ROSE, Fx2.INDIGO, Fx2.CHALK, Fx2.PAPER -> 24.dp
        else -> 20.dp
    }
    Canvas(Modifier.fillMaxWidth().height(h)) {
        val w = size.width; val hh = size.height; val mid = hh / 2
        val fw = f * w
        when (fx) {
            Fx2.SUNSET -> {
                val bh = 12.dp.toPx()
                drawRoundRect(c.copy(alpha = 0.15f), Offset(0f, mid - bh / 2), Size(w, bh), CornerRadius(bh / 2))
                if (f > 0f) drawRoundRect(Brush.horizontalGradient(listOf(coin, c)), Offset(0f, mid - bh / 2), Size(maxOf(fw, bh), bh), CornerRadius(bh / 2))
                val e = Offset(fw.coerceIn(8.dp.toPx(), w - 8.dp.toPx()), mid)
                drawCircle(coin.copy(alpha = 0.3f), 9.dp.toPx(), e); drawCircle(coin, 6.dp.toPx(), e)
            }
            Fx2.OCEAN -> {
                val half = 7.dp.toPx()
                drawRoundRect(c.copy(alpha = 0.15f), Offset(0f, mid - half), Size(w, half * 2), CornerRadius(half))
                if (fw > 2f) {
                    val top = mid - half
                    val path = Path().apply {
                        moveTo(0f, mid + half); lineTo(0f, top)
                        var x = 0f
                        while (x < fw) { x += 2.dp.toPx(); val xx = minOf(x, fw); lineTo(xx, top + Math.sin(xx / 14.dp.toPx() * 2 * Math.PI).toFloat() * 2.dp.toPx()) }
                        lineTo(fw, mid + half); close()
                    }
                    drawPath(path, c)
                    for (i in 0..2) {
                        val bx = fw * (0.2f + 0.3f * i)
                        if (bx < fw - 4.dp.toPx()) drawCircle(Color.White.copy(alpha = 0.5f), 2.dp.toPx(), Offset(bx, mid + (i % 2 * 3 - 1) * 2.dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                }
            }
            Fx2.FOREST -> {
                drawLine(c.copy(alpha = 0.2f), Offset(0f, mid), Offset(w, mid), 3.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(0f, mid), Offset(fw, mid), 3.dp.toPx(), StrokeCap.Round)
                var x = 12.dp.toPx(); var k = 0
                while (x < fw - 6.dp.toPx()) { leaf(Offset(x, mid), if (k % 2 == 0) -50f else 50f, 11.dp.toPx(), 4.dp.toPx(), c); x += 26.dp.toPx(); k++ }
                drawCircle(c, 5.dp.toPx(), Offset(fw.coerceIn(5.dp.toPx(), w - 5.dp.toPx()), mid))
            }
            Fx2.NEON -> {
                val cap = StrokeCap.Round; val a = Offset(4.dp.toPx(), mid); val b = Offset(maxOf(fw, 4.dp.toPx()), mid)
                drawLine(c.copy(alpha = 0.15f), a, Offset(w - 4.dp.toPx(), mid), 6.dp.toPx(), cap)
                if (f > 0f) {
                    drawLine(c.copy(alpha = 0.12f), a, b, 16.dp.toPx(), cap)
                    drawLine(c.copy(alpha = 0.28f), a, b, 11.dp.toPx(), cap)
                    drawLine(c, a, b, 5.dp.toPx(), cap)
                    drawLine(lerp(c, Color.White, 0.75f), a, b, 1.5.dp.toPx(), cap)
                }
            }
            Fx2.SCIFI -> {
                val n = 16; val g = 3.dp.toPx(); val sk = 4.dp.toPx(); val bw = (w - sk - g * (n - 1)) / n
                val lit = (f * n).roundToInt()
                for (i in 0 until n) {
                    val x0 = i * (bw + g)
                    val p = Path().apply { moveTo(x0 + sk, 0f); lineTo(x0 + bw + sk, 0f); lineTo(x0 + bw, hh); lineTo(x0, hh); close() }
                    drawPath(p, if (i < lit) c else c.copy(alpha = 0.15f))
                }
            }
            Fx2.AMOLED -> {
                drawLine(ink.copy(alpha = 0.25f), Offset(0f, mid), Offset(w, mid), 1.dp.toPx())
                drawLine(c, Offset(0f, mid), Offset(fw, mid), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(ink, 4.dp.toPx(), Offset(fw.coerceIn(4.dp.toPx(), w - 4.dp.toPx()), mid))
            }
            Fx2.PAPER -> {
                drawLine(ink.copy(alpha = 0.3f), Offset(0f, mid), Offset(w, mid), 1.5.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()), 0f))
                val path = Path().apply {
                    moveTo(0f, mid)
                    var x = 0f
                    while (x < fw) { x += 5.dp.toPx(); lineTo(minOf(x, fw), mid + Math.sin(x / 9.dp.toPx().toDouble()).toFloat() * 1.8.dp.toPx()) }
                }
                if (fw > 1f) drawPath(path, ink, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(ink, 3.dp.toPx(), Offset(fw.coerceIn(3.dp.toPx(), w - 3.dp.toPx()), mid))
            }
            Fx2.LINEN -> {
                val d = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f)
                drawLine(c.copy(alpha = 0.25f), Offset(0f, mid + 3.dp.toPx()), Offset(w, mid + 3.dp.toPx()), 3.dp.toPx(), StrokeCap.Round, d)
                drawLine(c, Offset(0f, mid + 3.dp.toPx()), Offset(fw, mid + 3.dp.toPx()), 4.dp.toPx(), StrokeCap.Round, d)
                val e = Offset(fw.coerceIn(2.dp.toPx(), w - 10.dp.toPx()), mid + 3.dp.toPx())
                drawLine(c, e, Offset(e.x + 8.dp.toPx(), e.y - 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(c, 2.5.dp.toPx(), Offset(e.x + 8.dp.toPx(), e.y - 9.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            Fx2.SAGE -> {
                val d = PathEffect.dashPathEffect(floatArrayOf(0.1f, 9.dp.toPx()), 0f)
                drawLine(c.copy(alpha = 0.2f), Offset(3.dp.toPx(), mid + 3.dp.toPx()), Offset(w, mid + 3.dp.toPx()), 6.dp.toPx(), StrokeCap.Round, d)
                drawLine(c, Offset(3.dp.toPx(), mid + 3.dp.toPx()), Offset(maxOf(fw, 3.dp.toPx()), mid + 3.dp.toPx()), 6.dp.toPx(), StrokeCap.Round, d)
                val e = Offset(fw.coerceIn(8.dp.toPx(), w - 14.dp.toPx()), mid + 3.dp.toPx())
                leaf(e, -50f, 13.dp.toPx(), 5.dp.toPx(), c); leaf(e, -130f, 13.dp.toPx(), 5.dp.toPx(), c)
            }
            Fx2.SKY -> {
                val bh = 10.dp.toPx()
                drawRoundRect(c.copy(alpha = 0.15f), Offset(0f, mid - bh / 2), Size(w, bh), CornerRadius(bh / 2))
                if (f < 0.7f) { val cx0 = w * 0.75f; drawCircle(c.copy(alpha = 0.2f), 6.dp.toPx(), Offset(cx0, mid)); drawCircle(c.copy(alpha = 0.2f), 8.dp.toPx(), Offset(cx0 + 9.dp.toPx(), mid - 3.dp.toPx())) }
                if (f > 0f) drawRoundRect(c, Offset(0f, mid - bh / 2), Size(maxOf(fw, bh), bh), CornerRadius(bh / 2))
                val e = Offset(fw.coerceIn(8.dp.toPx(), w - 8.dp.toPx()), mid)
                drawCircle(coin.copy(alpha = 0.3f), 9.dp.toPx(), e); drawCircle(coin, 6.dp.toPx(), e)
            }
            Fx2.LILAC -> {
                val n = 14; val lit = (f * n).roundToInt()
                for (i in 0 until n) {
                    val x = 8.dp.toPx() + i * (w - 16.dp.toPx()) / (n - 1)
                    drawCircle(if (i < lit) c else c.copy(alpha = 0.2f), 5.dp.toPx(), Offset(x, mid))
                    if (i < lit) drawCircle(coin, 1.8.dp.toPx(), Offset(x, mid))
                }
            }
            Fx2.CHALK -> {
                for (i in 0..10) drawLine(ink.copy(alpha = 0.3f), Offset(w * i / 10f, mid - 8.dp.toPx()), Offset(w * i / 10f, mid + 8.dp.toPx()), 1.dp.toPx())
                drawLine(ink.copy(alpha = 0.8f), Offset(0f, mid), Offset(fw, mid), 3.dp.toPx(), StrokeCap.Round)
                drawLine(ink.copy(alpha = 0.4f), Offset(0f, mid - 2.dp.toPx()), Offset(fw * 0.95f, mid - 2.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(ink.copy(alpha = 0.3f), Offset(0f, mid + 2.dp.toPx()), Offset(fw * 0.9f, mid + 2.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawCircle(ink.copy(alpha = 0.3f), 5.dp.toPx(), Offset(fw.coerceIn(5.dp.toPx(), w - 5.dp.toPx()), mid))
            }
            Fx2.CLAY -> {
                val bh = 16.dp.toPx(); val y = mid - bh / 2
                drawRoundRect(c.copy(alpha = 0.15f), Offset(0f, y), Size(w, bh), CornerRadius(bh / 2))
                if (f > 0f) {
                    val cw = maxOf(fw, bh)
                    drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(0f, y + 3.dp.toPx()), Size(cw, bh), CornerRadius(bh / 2))
                    drawRoundRect(c, Offset(0f, y), Size(cw, bh), CornerRadius(bh / 2))
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(bh / 2, y + 3.dp.toPx()), Size(maxOf(cw - bh, 0f), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
            }
            Fx2.ROSE -> {
                val bh = 6.dp.toPx()
                drawRoundRect(c.copy(alpha = 0.2f), Offset(0f, mid - bh / 2), Size(w, bh), CornerRadius(bh / 2))
                if (f > 0f) drawRoundRect(c, Offset(0f, mid - bh / 2), Size(maxOf(fw, bh), bh), CornerRadius(bh / 2))
                heart(Offset(fw.coerceIn(9.dp.toPx(), w - 9.dp.toPx()), mid - 1.dp.toPx()), 8.dp.toPx(), c)
            }
            Fx2.INDIGO -> {
                drawLine(c.copy(alpha = 0.25f), Offset(0f, mid), Offset(w, mid), 2.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 7.dp.toPx()), 0f))
                drawLine(c, Offset(0f, mid), Offset(fw, mid), 2.dp.toPx(), StrokeCap.Round)
                var x = 20.dp.toPx()
                while (x < fw - 8.dp.toPx()) { star4(Offset(x, mid), 4.dp.toPx(), coin); x += 36.dp.toPx() }
                star4(Offset(fw.coerceIn(8.dp.toPx(), w - 8.dp.toPx()), mid), 8.dp.toPx(), coin)
            }
            else -> Unit
        }
    }
}

// ── lock screens ─────────────────────────────────────────────────────────────────────────────────────────

/** v254: [content] (app icon or emoji of a lock page) inside a static emblem of the Fx2 theme with a padlock badge (called through FxLockEmblem). One-shot pop in only. */
@Composable
internal fun Fx2LockEmblem(content: @Composable () -> Unit) {
    val fx = Fx2Now
    val c = ColorPrimary
    val ink = ColorText
    val coin = ColorCoin
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(450)) }
    Box(
        Modifier.size(132.dp).graphicsLayer { val sc = 1.2f - 0.2f * p.value; scaleX = sc; scaleY = sc; alpha = p.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = center
            val full = size.minDimension / 2
            when (fx) {
                Fx2.SUNSET -> {
                    drawCircle(coin.copy(alpha = 0.25f), full - 16.dp.toPx(), ctr)
                    for (i in 0 until 16) drawLine(coin, polar(ctr, full - 12.dp.toPx(), i * 22.5f), polar(ctr, full - 3.dp.toPx(), i * 22.5f), 3.dp.toPx(), StrokeCap.Round)
                }
                Fx2.OCEAN -> for (k in 0..2) drawCircle(c.copy(alpha = 0.5f - k * 0.15f), full - 4.dp.toPx() - k * 10.dp.toPx(), ctr, style = Stroke(2.dp.toPx()))
                Fx2.FOREST -> {
                    for (k in 0..2) drawCircle(c.copy(alpha = 0.5f - k * 0.15f), full - 4.dp.toPx() - k * 10.dp.toPx(), ctr, style = Stroke(2.dp.toPx()))
                    leaf(polar(ctr, full - 6.dp.toPx(), -40f), -40f, 18.dp.toPx(), 6.dp.toPx(), c); leaf(polar(ctr, full - 6.dp.toPx(), -75f), -75f, 18.dp.toPx(), 6.dp.toPx(), c)
                }
                Fx2.NEON -> {
                    val r = full - 8.dp.toPx()
                    drawCircle(c.copy(alpha = 0.12f), r, ctr, style = Stroke(14.dp.toPx())); drawCircle(c.copy(alpha = 0.25f), r, ctr, style = Stroke(9.dp.toPx()))
                    drawCircle(c, r, ctr, style = Stroke(4.dp.toPx())); drawCircle(lerp(c, Color.White, 0.75f), r, ctr, style = Stroke(1.5.dp.toPx()))
                }
                Fx2.SCIFI -> {
                    drawPath(hexPath(ctr, full - 4.dp.toPx()), c, style = Stroke(2.dp.toPx()))
                    drawPath(hexPath(ctr, full - 14.dp.toPx()), c.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
                    for (i in 0 until 6) drawCircle(c, 3.dp.toPx(), polar(ctr, full - 4.dp.toPx(), -90f + i * 60f))
                }
                Fx2.AMOLED -> {
                    drawCircle(c, full - 4.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx()))
                    for (k in 0 until 4) drawCircle(ink, 3.dp.toPx(), polar(ctr, full - 4.dp.toPx(), k * 90f))
                }
                Fx2.TERMINAL -> {
                    drawRect(c, Offset(4.dp.toPx(), 4.dp.toPx()), Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = Stroke(2.dp.toPx()))
                    for (k in 0..2) drawCircle(c, 2.dp.toPx(), Offset(14.dp.toPx() + k * 8.dp.toPx(), 13.dp.toPx()))
                    drawRect(c, Offset(10.dp.toPx(), size.height - 20.dp.toPx()), Size(8.dp.toPx(), 12.dp.toPx()))
                }
                Fx2.PAPER -> {
                    drawCircle(ink.copy(alpha = 0.7f), full - 4.dp.toPx(), ctr, style = Stroke(2.dp.toPx()))
                    drawCircle(ink.copy(alpha = 0.5f), full - 11.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                    drawCircle(ink.copy(alpha = 0.4f), full - 7.5.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()), 0f)))
                }
                Fx2.LINEN -> {
                    drawCircle(c, full - 6.dp.toPx(), ctr, style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()), 0f)))
                    for (k in 0 until 4) {
                        val q = polar(ctr, full - 6.dp.toPx(), 45f + k * 90f)
                        drawLine(c, Offset(q.x - 4.dp.toPx(), q.y - 4.dp.toPx()), Offset(q.x + 4.dp.toPx(), q.y + 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(c, Offset(q.x - 4.dp.toPx(), q.y + 4.dp.toPx()), Offset(q.x + 4.dp.toPx(), q.y - 4.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                Fx2.SAGE -> for (i in 0 until 8) { val a = -90f + i * 45f; leaf(polar(ctr, full - 34.dp.toPx(), a), a, 26.dp.toPx(), 7.dp.toPx(), c.copy(alpha = 0.8f)) }
                Fx2.SKY -> {
                    drawCircle(c.copy(alpha = 0.4f), full - 4.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx()))
                    drawCircle(coin, 8.dp.toPx(), polar(ctr, full - 8.dp.toPx(), -45f))
                    val cl = c.copy(alpha = 0.25f); val y = ctr.y + full - 14.dp.toPx()
                    drawCircle(cl, 10.dp.toPx(), Offset(ctr.x - 20.dp.toPx(), y)); drawCircle(cl, 14.dp.toPx(), Offset(ctr.x - 4.dp.toPx(), y - 5.dp.toPx())); drawCircle(cl, 10.dp.toPx(), Offset(ctr.x + 14.dp.toPx(), y))
                }
                Fx2.LILAC -> for (i in 0 until 8) { val a = -90f + i * 45f; leaf(polar(ctr, full - 34.dp.toPx(), a), a, 28.dp.toPx(), 10.dp.toPx(), c.copy(alpha = 0.75f)) }
                Fx2.CHALK -> {
                    drawArc(ink.copy(alpha = 0.7f), -90f, 340f, false, topLeft = Offset(ctr.x - (full - 6.dp.toPx()), ctr.y - (full - 6.dp.toPx())), size = Size((full - 6.dp.toPx()) * 2, (full - 6.dp.toPx()) * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(ink.copy(alpha = 0.35f), -70f, 300f, false, topLeft = Offset(ctr.x - (full - 9.dp.toPx()), ctr.y - (full - 9.dp.toPx())), size = Size((full - 9.dp.toPx()) * 2, (full - 9.dp.toPx()) * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                Fx2.CLAY -> {
                    drawCircle(Color.Black.copy(alpha = 0.08f), full - 4.dp.toPx(), Offset(ctr.x, ctr.y + 4.dp.toPx()))
                    drawCircle(c.copy(alpha = 0.28f), full - 4.dp.toPx(), ctr)
                    drawArc(Color.White.copy(alpha = 0.45f), 200f, 70f, false, topLeft = Offset(ctr.x - (full - 14.dp.toPx()), ctr.y - (full - 14.dp.toPx())), size = Size((full - 14.dp.toPx()) * 2, (full - 14.dp.toPx()) * 2), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Fx2.ROSE -> for (i in 0 until 8) heart(polar(ctr, full - 12.dp.toPx(), -90f + i * 45f), 7.dp.toPx(), c.copy(alpha = 0.8f))
                Fx2.INDIGO -> {
                    for (i in 0 until 10) star4(polar(ctr, full - 8.dp.toPx(), -90f + i * 36f), (if (i % 2 == 0) 5 else 3).dp.toPx(), if (i % 2 == 0) coin else c.copy(alpha = 0.5f))
                    val m = Offset(ctr.x, ctr.y - full + 18.dp.toPx())
                    drawCircle(coin, 10.dp.toPx(), m); drawCircle(ColorBg, 8.dp.toPx(), Offset(m.x + 4.dp.toPx(), m.y - 3.dp.toPx()))
                }
                else -> Unit
            }
        }
        content()
        Canvas(Modifier.size(28.dp).align(Alignment.BottomEnd)) { padlock(c) }
    }
}

// ── quest done pop and level up ──────────────────────────────────────────────────────────────────────────

private class PopLook(val shape: Shape, val bw: Dp, val bc: Color, val bg: Color, val wc: Color, val rc: Color, val rot: Float = 0f, val dashed: Boolean = false, val mono: Boolean = false)

/** v254: the small quest done pop for the Fx2 themes: the theme word plus "+XP, +coins". */
@Composable
private fun Fx2QuestPop(fx: Fx2, coins: Int, xp: Int) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1300, easing = LinearEasing)) }
    val v = p.value
    val a = if (v > 0.8f) (1f - v) / 0.2f else 1f
    val pop = 1f + 0.5f * (1f - minOf(1f, v * 6f))
    val c = ColorPrimary; val ink = ColorText; val bg = ColorBg.copy(alpha = 0.95f); val card = ColorCard
    val look = when (fx) {
        Fx2.SUNSET -> PopLook(RoundedCornerShape(20.dp), 1.dp, c.copy(alpha = 0.5f), c.copy(alpha = 0.18f), ink, c)
        Fx2.OCEAN -> PopLook(RoundedCornerShape(14.dp), 1.dp, c, ColorSurface, ink, c)
        Fx2.FOREST -> PopLook(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 4.dp), 1.5.dp, c, bg, ink, c)
        Fx2.NEON -> PopLook(RoundedCornerShape(10.dp), 2.dp, c, bg, c, ink)
        Fx2.SCIFI -> PopLook(CutCornerShape(8.dp), 1.5.dp, c, bg, c, ink)
        Fx2.AMOLED -> PopLook(RoundedCornerShape(50), 1.dp, ink.copy(alpha = 0.3f), bg, ink, ColorMuted)
        Fx2.TERMINAL -> PopLook(RectangleShape, 1.5.dp, c, bg, c, ink, mono = true)
        Fx2.PAPER -> PopLook(RoundedCornerShape(2.dp), 2.dp, ink.copy(alpha = 0.6f), card, ink, c, rot = -2f)
        Fx2.LINEN -> PopLook(RoundedCornerShape(8.dp), 0.dp, c, card, ink, c, dashed = true)
        Fx2.SAGE -> PopLook(RoundedCornerShape(24.dp), 1.dp, c.copy(alpha = 0.6f), bg, ink, c)
        Fx2.SKY -> PopLook(RoundedCornerShape(30.dp), 0.dp, c, c.copy(alpha = 0.15f), ink, c)
        Fx2.LILAC -> PopLook(RoundedCornerShape(24.dp), 1.dp, c, card, ink, c)
        Fx2.CHALK -> PopLook(RectangleShape, 2.dp, ink.copy(alpha = 0.7f), card, ink, ColorMuted, rot = 1.5f)
        Fx2.CLAY -> PopLook(RoundedCornerShape(24.dp), 0.dp, c, c.copy(alpha = 0.2f), ink, c)
        Fx2.ROSE -> PopLook(RoundedCornerShape(50), 1.dp, c, c.copy(alpha = 0.18f), ink, c)
        Fx2.INDIGO -> PopLook(RoundedCornerShape(12.dp), 1.dp, c, ColorSurface, ink, c)
        Fx2.NONE -> return
    }
    val word = Fx2X.word("done", "Quest done")
    val fam = if (look.mono) FontFamily.Monospace else fontOf(fx)
    var m = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a; rotationZ = look.rot }
    if (look.dashed) m = m.drawBehind { drawRoundRect(look.bc, Offset.Zero, size, CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f))) }
    if (fx == Fx2.CLAY) m = m.drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(0f, 4.dp.toPx()), size, CornerRadius(24.dp.toPx())) }
    if (look.bw > 0.dp) m = m.border(look.bw, look.bc, look.shape)
    Column(m.background(look.bg, look.shape).padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (fx == Fx2.TERMINAL) "> $word" else word, color = look.wc, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, fontFamily = fam)
        Text("+$xp XP  +$coins coins", color = look.rc, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = fam)
    }
}

/** v254: full-screen level up (about 2 s) for the Fx2 themes, each with its own motif and word. */
@Composable
private fun Fx2LevelUp(fx: Fx2) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val v = p.value
    val a = if (v > 0.85f) (1f - v) / 0.15f else 1f
    val pop = 1f + 0.8f * (1f - minOf(1f, v * 5f))
    val rise = (1f - minOf(1f, v / 0.35f)) * 80f
    val c = ColorPrimary; val ink = ColorText; val coin = ColorCoin; val acc = ColorAccent
    val lv = SystemEvents.lastLevel
    val word = Fx2X.word("level", "LEVEL UP")
    val fam = fontOf(fx)
    Box(Modifier.fillMaxSize().background(ColorBg.copy(alpha = 0.94f * a)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = center
            when (fx) {
                Fx2.SUNSET -> {
                    val sc = Offset(ctr.x, ctr.y - 130.dp.toPx() + 70.dp.toPx() * (1f - minOf(1f, v * 2.2f)))
                    drawCircle(Brush.radialGradient(listOf(coin.copy(alpha = 0.5f * a), Color.Transparent), sc, 150.dp.toPx()), 150.dp.toPx(), sc)
                    drawCircle(coin.copy(alpha = a), 48.dp.toPx(), sc)
                    for (i in 0 until 16) drawLine(coin.copy(alpha = a), polar(sc, 60.dp.toPx(), i * 22.5f), polar(sc, 60.dp.toPx() + 16.dp.toPx() * minOf(1f, v * 3f), i * 22.5f), 3.dp.toPx(), StrokeCap.Round)
                }
                Fx2.OCEAN -> for (k in 0..2) drawCircle(c.copy(alpha = 0.6f * a * (1f - k * 0.25f)), 30.dp.toPx() + (140.dp.toPx() + k * 40.dp.toPx()) * v, ctr, style = Stroke(3.dp.toPx()))
                Fx2.FOREST -> for (i in 0 until 18) {
                    val x = size.width * (((i * 37 + 11) % 100) / 100f) + Math.sin((v * 6f + i).toDouble()).toFloat() * 12.dp.toPx()
                    val y = -(((i * 53) % 40) / 100f) * size.height + v * (1.2f + (i % 3) * 0.2f) * size.height
                    if (y >= 0f && y <= size.height) leaf(Offset(x, y), 60f + (i * 40 % 100), 18.dp.toPx(), 6.dp.toPx(), (if (i % 2 == 0) c else coin).copy(alpha = 0.85f * a))
                }
                Fx2.NEON -> {
                    val rw = 300.dp.toPx(); val rh = 130.dp.toPx(); val tl = Offset(ctr.x - rw / 2, ctr.y - rh / 2)
                    for ((wd, al) in listOf(14f to 0.10f, 9f to 0.20f, 5f to 0.45f, 2.5f to 1f)) drawRoundRect(c.copy(alpha = al * a), tl, Size(rw, rh), CornerRadius(18.dp.toPx()), style = Stroke(wd.dp.toPx()))
                }
                Fx2.SCIFI -> for (k in 0..2) drawPath(hexPath(ctr, (30.dp.toPx() + 130.dp.toPx() * v) + k * 30.dp.toPx()), c.copy(alpha = 0.7f * a * (1f - k * 0.25f)), style = Stroke(2.dp.toPx()))
                Fx2.AMOLED -> drawCircle(ink.copy(alpha = 0.6f * a), 20.dp.toPx() + 150.dp.toPx() * v, ctr, style = Stroke(1.5.dp.toPx()))
                Fx2.LINEN -> drawCircle(c.copy(alpha = a), 100.dp.toPx() + 14.dp.toPx() * v, ctr, style = Stroke(4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), 0f)))
                Fx2.SAGE -> for (i in 0 until 8) { val ang = -90f + i * 45f; leaf(polar(ctr, 20.dp.toPx() + 50.dp.toPx() * v, ang), ang, (30.dp.toPx() + 90.dp.toPx() * v), 12.dp.toPx(), c.copy(alpha = 0.7f * a)) }
                Fx2.SKY -> {
                    val sc = Offset(ctr.x, ctr.y - 120.dp.toPx())
                    drawCircle(coin.copy(alpha = a), 34.dp.toPx(), sc)
                    for (i in 0 until 16) drawLine(coin.copy(alpha = a), polar(sc, 44.dp.toPx(), i * 22.5f), polar(sc, 44.dp.toPx() + 18.dp.toPx() * minOf(1f, v * 3f), i * 22.5f), 3.dp.toPx(), StrokeCap.Round)
                }
                Fx2.LILAC -> for (i in 0 until 12) { val ang = -90f + i * 30f; leaf(polar(ctr, 10.dp.toPx(), ang), ang, 30.dp.toPx() + 110.dp.toPx() * v, 14.dp.toPx(), c.copy(alpha = 0.6f * a)) }
                Fx2.CHALK -> drawLine(ink.copy(alpha = a), Offset(ctr.x - 90.dp.toPx(), ctr.y + 56.dp.toPx()), Offset(ctr.x - 90.dp.toPx() + 180.dp.toPx() * minOf(1f, v / 0.4f), ctr.y + 56.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
                Fx2.CLAY -> {
                    val s = 1f + 0.25f * Math.sin(minOf(1f, v * 3f) * Math.PI).toFloat()
                    drawCircle(c.copy(alpha = 0.55f * a), 70.dp.toPx() * s, Offset(ctr.x, ctr.y - 10.dp.toPx()))
                    drawCircle(coin.copy(alpha = 0.55f * a), 44.dp.toPx() * s, Offset(ctr.x - 90.dp.toPx(), ctr.y - 50.dp.toPx()))
                    drawCircle(acc.copy(alpha = 0.55f * a), 36.dp.toPx() * s, Offset(ctr.x + 90.dp.toPx(), ctr.y - 40.dp.toPx()))
                }
                Fx2.ROSE -> for (i in 0 until 10) {
                    val x = size.width * (((i * 37 + 11) % 100) / 100f)
                    val y = size.height * (1.05f - v * (0.9f + (i % 3) * 0.15f)) + ((i * 29) % 40).dp.toPx()
                    heart(Offset(x, y), (8 + i % 3 * 3).dp.toPx(), c.copy(alpha = 0.8f * a))
                }
                Fx2.INDIGO -> {
                    for (i in 0 until 24) {
                        val tw = 0.4f + 0.6f * Math.pow(Math.sin((v * 12f + i).toDouble()), 2.0).toFloat()
                        star4(Offset(size.width * (((i * 37 + 11) % 100) / 100f), size.height * (((i * 53 + 17) % 100) / 100f)), (3 + i % 3 * 2).dp.toPx(), coin.copy(alpha = tw * a))
                    }
                    val m = Offset(ctr.x, ctr.y - 120.dp.toPx())
                    drawCircle(coin.copy(alpha = a), 36.dp.toPx(), m); drawCircle(ColorBg.copy(alpha = a), 30.dp.toPx(), Offset(m.x + 14.dp.toPx(), m.y - 10.dp.toPx()))
                }
                else -> Unit
            }
        }
        val sub = "LV ${lv - 1} \u2192 $lv"
        when (fx) {
            Fx2.OCEAN, Fx2.SKY, Fx2.SUNSET -> Column(Modifier.graphicsLayer { alpha = a; translationY = rise }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word, color = c, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Text(sub, color = ink, fontSize = 14.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Fx2.NEON -> {
                val fl = if (v < 0.3f && (v * 20).toInt() % 3 == 1) 0.25f else 1f
                Column(Modifier.graphicsLayer { alpha = a * fl }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word, color = lerp(c, Color.White, 0.5f), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    Text(sub, color = c, fontSize = 14.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Fx2.TERMINAL -> {
                val line = "> level_up --to $lv"
                Column(Modifier.graphicsLayer { alpha = a }, horizontalAlignment = Alignment.Start) {
                    Text(line.take((line.length * minOf(1f, v / 0.45f)).toInt()) + "_", color = c, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    if (v > 0.5f) Text(sub, color = ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Fx2.PAPER -> Column(
                Modifier.graphicsLayer { rotationZ = -6f; scaleX = pop; scaleY = pop; alpha = a }.border(3.dp, ink.copy(alpha = 0.8f)).padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("CHAPTER $lv", color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 3.sp)
                Text("COMPLETE", color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 4.sp)
            }
            Fx2.CHALK -> Column(Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word, color = ink, fontSize = 72.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Cursive)
                Text(sub, color = ColorMuted, fontSize = 14.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 18.dp))
            }
            else -> Column(Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word, color = c, fontSize = if (fx == Fx2.SCIFI) 30.sp else 40.sp, fontWeight = if (fx == Fx2.AMOLED) FontWeight.Light else FontWeight.ExtraBold, letterSpacing = 4.sp, fontFamily = fam)
                Text(sub, color = ink, fontSize = 14.sp, letterSpacing = 2.sp, fontFamily = fam, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** v254: shows the quest done pop and the level up of the Fx2 themes (the style themes use ThemeExtrasHost). Call only while Fx2X.on. */
@Composable
fun Fx2Host() {
    val fx = Fx2Now
    val startQ = remember { SystemEvents.questDoneStamp }
    val startL = remember { SystemEvents.levelUpStamp }
    var pop by remember { mutableStateOf(false) }
    var lvl by remember { mutableStateOf(false) }
    LaunchedEffect(SystemEvents.questDoneStamp) {
        if (SystemEvents.questDoneStamp != startQ) { pop = true; delay(1500); pop = false }
    }
    LaunchedEffect(SystemEvents.levelUpStamp) {
        if (SystemEvents.levelUpStamp != startL) { lvl = true; delay(2400); lvl = false }
    }
    if (pop) CenterPopup { Fx2QuestPop(fx, SystemEvents.lastCoins, SystemEvents.lastXp) }
    if (lvl) Dialog(onDismissRequest = { lvl = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) { Fx2LevelUp(fx) }
}
