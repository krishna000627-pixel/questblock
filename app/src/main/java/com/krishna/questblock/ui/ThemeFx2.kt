package com.krishna.questblock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.drawscope.clipPath
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
 * v261: they also get a header strip (Fx2Bar) and decoration on every card, the quest board (fx2Frame, called by Card in Glass.kt).
 * Behind the master flag themefx and the theme's own flag fx_<theme> (Flags.EXTRA). #svg-logo  New theme with effects: add it to Fx2,
 * Fx2Now, Fx2X.WORDS, Flags.EXTRA and every `when (fx)` below, plus stripLine and cardDeco (strip and cards) and the words in Fx2Bar.
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

// ── header strip and quest cards (v261) ──────────────────────────────────────────────────────────────────

private fun DrawScope.waveLine(y: Float, amp: Float, len: Float, col: Color, sw: Float, x0: Float = 0f, x1: Float = size.width) {
    val p = Path(); p.moveTo(x0, y)
    var x = x0
    while (x <= x1) { p.lineTo(x, y + Math.sin(((x - x0) / len).toDouble()).toFloat() * amp); x += 3.dp.toPx() }
    drawPath(p, col, style = Stroke(sw))
}

/** v261: what is drawn along the bottom edge of the header strip of each Fx2 theme. Static. */
private fun DrawScope.stripLine(fx: Fx2, c: Color, coin: Color, ink: Color) {
    val w = size.width; val h = size.height; val y = h - 1f
    when (fx) {
        Fx2.SUNSET -> {
            drawRect(Brush.horizontalGradient(listOf(coin.copy(alpha = 0.22f), c.copy(alpha = 0.08f), Color.Transparent)), size = size)
            drawLine(Brush.horizontalGradient(listOf(coin, c, Color.Transparent)), Offset(0f, y), Offset(w, y), 2f)
            val r = 7.dp.toPx(); val cx = w * 0.5f
            drawArc(coin, 180f, 180f, true, Offset(cx - r, h - r), Size(r * 2, r * 2))
        }
        Fx2.OCEAN -> waveLine(h - 4.dp.toPx(), 2.5.dp.toPx(), 14.dp.toPx(), c.copy(alpha = 0.7f), 1.5.dp.toPx())
        Fx2.FOREST -> {
            drawLine(c.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), 2f)
            var x = 20.dp.toPx(); var i = 0
            while (x < w) { leaf(Offset(x, y), if (i % 2 == 0) -55f else -125f, 9.dp.toPx(), 3.5.dp.toPx(), c.copy(alpha = 0.7f)); x += 40.dp.toPx(); i++ }
        }
        Fx2.NEON -> for ((wd, al) in listOf(8f to 0.10f, 4f to 0.30f, 1.5f to 1f)) drawLine(c.copy(alpha = al), Offset(0f, h - 2.dp.toPx()), Offset(w, h - 2.dp.toPx()), wd.dp.toPx())
        Fx2.SCIFI -> {
            val st = 12.dp.toPx(); var x = 0f; var i = 0
            while (x < w) { drawLine(c.copy(alpha = if (i % 3 == 0) 0.9f else 0.35f), Offset(x, y), Offset(x + 4.dp.toPx(), h - 7.dp.toPx()), 2.dp.toPx()); x += st; i++ }
            drawLine(c.copy(alpha = 0.5f), Offset(0f, y), Offset(w, y), 1f)
        }
        Fx2.AMOLED -> {
            drawLine(ink.copy(alpha = 0.25f), Offset(0f, y), Offset(w, y), 1f)
            drawCircle(c, 2.dp.toPx(), Offset(14.dp.toPx(), y))
        }
        Fx2.TERMINAL -> drawLine(c.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()), 0f))
        Fx2.PAPER -> {
            val p = Path(); p.moveTo(0f, y - 2.dp.toPx())
            var x = 0f; var i = 0
            while (x <= w) { p.lineTo(x, y - 2.dp.toPx() + (((i * 7) % 3) - 1) * 0.9.dp.toPx()); x += 5.dp.toPx(); i++ }
            drawPath(p, ink.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx()))
        }
        Fx2.LINEN -> drawLine(c.copy(alpha = 0.85f), Offset(0f, y - 2.dp.toPx()), Offset(w, y - 2.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f))
        Fx2.SAGE -> {
            var x = 4.dp.toPx()
            while (x < w) { if (Math.abs(x - w * 0.5f) > 12.dp.toPx()) drawCircle(c.copy(alpha = 0.6f), 1.2.dp.toPx(), Offset(x, y - 1.dp.toPx())); x += 8.dp.toPx() }
            val b = Offset(w * 0.5f, y - 1.dp.toPx())
            drawLine(c, b, Offset(b.x, b.y - 4.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            leaf(Offset(b.x, b.y - 3.dp.toPx()), -50f, 10.dp.toPx(), 3.5.dp.toPx(), c); leaf(Offset(b.x, b.y - 3.dp.toPx()), -130f, 10.dp.toPx(), 3.5.dp.toPx(), c)
        }
        Fx2.SKY -> {
            drawLine(c.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), 1.5.dp.toPx())
            val x = w * 0.5f; val cc = c.copy(alpha = 0.4f)
            drawCircle(cc, 3.5.dp.toPx(), Offset(x, h - 4.dp.toPx())); drawCircle(cc, 4.dp.toPx(), Offset(x + 7.dp.toPx(), h - 6.5.dp.toPx())); drawCircle(cc, 3.5.dp.toPx(), Offset(x + 14.dp.toPx(), h - 4.dp.toPx()))
        }
        Fx2.LILAC -> {
            drawLine(c.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), 1f)
            var x = 14.dp.toPx()
            while (x < w) {
                val ctr = Offset(x, h - 6.dp.toPx())
                for (k in 0 until 5) drawCircle(c.copy(alpha = 0.55f), 1.8.dp.toPx(), polar(ctr, 3.dp.toPx(), k * 72f - 90f))
                drawCircle(coin, 1.3.dp.toPx(), ctr); x += 30.dp.toPx()
            }
        }
        Fx2.CHALK -> {
            var x = 0f; var i = 0
            while (x < w) { drawLine(ink.copy(alpha = 0.5f), Offset(x, y - (i % 2)), Offset(x + 6.dp.toPx(), y), 1.6.dp.toPx(), StrokeCap.Round); x += 9.dp.toPx(); i++ }
        }
        Fx2.CLAY -> {
            drawRoundRect(c.copy(alpha = 0.25f), Offset(10.dp.toPx(), h - 7.dp.toPx()), Size(w - 20.dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(16.dp.toPx(), h - 6.dp.toPx()), Size(w * 0.3f, 1.5.dp.toPx()), CornerRadius(1.dp.toPx()))
        }
        Fx2.ROSE -> {
            drawLine(c.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), 1f)
            var x = 16.dp.toPx()
            while (x < w) { heart(Offset(x, h - 7.dp.toPx()), 2.8.dp.toPx(), c.copy(alpha = 0.7f)); x += 44.dp.toPx() }
        }
        Fx2.INDIGO -> {
            drawLine(c.copy(alpha = 0.35f), Offset(0f, y), Offset(w, y), 1f)
            for (i in 0 until 14) star4(Offset(w * (((i * 37 + 9) % 100) / 100f), h * (0.2f + 0.5f * (((i * 53 + 17) % 100) / 100f))), (1f + (i % 3) * 0.6f).dp.toPx(), coin.copy(alpha = 0.6f))
        }
        else -> Unit
    }
}

private fun todayShort(): String {
    val d = java.time.LocalDate.now()
    return d.dayOfWeek.name.take(3) + " " + d.dayOfMonth + " " + d.month.name.take(3)
}

/** v261: the strip at the top of every page for the Fx2 themes (the style themes have FxBar). Keeps the page below the status bar. */
@Composable
fun Fx2Bar() {
    val fx = Fx2Now
    val c = ColorPrimary; val coin = ColorCoin; val ink = ColorText
    val date = remember { todayShort() }
    val l: String; val r: String
    when (fx) {
        Fx2.SUNSET -> { l = "GOLDEN HOUR"; r = date }
        Fx2.OCEAN -> { l = "HIGH TIDE"; r = date }
        Fx2.FOREST -> { l = "THE TRAIL"; r = "GROWING" }
        Fx2.NEON -> { l = "ON AIR"; r = "OPEN 24H" }
        Fx2.SCIFI -> { l = "SYS.ONLINE"; r = "SECTOR 01" }
        Fx2.AMOLED -> { l = "QUESTBLOCK"; r = date }
        Fx2.TERMINAL -> { l = "user@questblock:~$"; r = "bash" }
        Fx2.PAPER -> { l = "DAILY NOTES"; r = date }
        Fx2.LINEN -> { l = "STITCHED"; r = "HAND SEWN" }
        Fx2.SAGE -> { l = "SPROUTING"; r = "SLOW AND STEADY" }
        Fx2.SKY -> { l = "CLEAR SKIES"; r = date }
        Fx2.LILAC -> { l = "IN BLOOM"; r = date }
        Fx2.CHALK -> { l = "CLASS IN SESSION"; r = date }
        Fx2.CLAY -> { l = "SOFT AND SQUISHY"; r = date }
        Fx2.ROSE -> { l = "WITH LOVE"; r = date }
        Fx2.INDIGO -> { l = "NIGHT SKY"; r = "STARGAZING" }
        else -> {
            Spacer(Modifier.fillMaxWidth().background(ColorBg).statusBarsPadding())
            return
        }
    }
    val fam = fontOf(fx)
    val lc = if (fx == Fx2.NEON) lerp(c, Color.White, 0.5f) else c
    // v262: the words sit in their own row and the drawing in a band under them, so nothing is drawn over the words
    Column(Modifier.fillMaxWidth().background(ColorBg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l, color = lc, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = if (fx == Fx2.TERMINAL) 0.sp else 2.sp, fontFamily = fam)
            Spacer(Modifier.weight(1f))
            Text(r, color = ColorMuted, fontSize = 10.sp, letterSpacing = 1.sp, fontFamily = fam)
        }
        Canvas(Modifier.fillMaxWidth().height(12.dp)) { stripLine(fx, c, coin, ink) }
    }
}

/** v261: what is drawn over every card (the quest board) for each Fx2 theme. Small and static; the card's shape, colours and border stay. */
private fun DrawScope.cardDeco(fx: Fx2, c: Color, coin: Color, ink: Color) {
    val w = size.width; val h = size.height; val m = 12.dp.toPx()
    when (fx) {
        Fx2.SUNSET -> {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, coin.copy(alpha = 0.10f)), startY = h * 0.5f, endY = h), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
            val r = 7.dp.toPx()
            drawArc(coin.copy(alpha = 0.8f), 180f, 180f, true, Offset(w - 24.dp.toPx() - r, h - r), Size(r * 2, r * 2))
        }
        Fx2.OCEAN -> {
            drawCircle(c.copy(alpha = 0.40f), 4.dp.toPx(), Offset(w - m, m + 2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            drawCircle(c.copy(alpha = 0.30f), 2.5.dp.toPx(), Offset(w - m - 10.dp.toPx(), m + 9.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawCircle(c.copy(alpha = 0.22f), 1.5.dp.toPx(), Offset(w - m - 4.dp.toPx(), m + 15.dp.toPx()), style = Stroke(1.dp.toPx()))
        }
        Fx2.FOREST -> {
            leaf(Offset(w - m, 14.dp.toPx()), 150f, 12.dp.toPx(), 4.dp.toPx(), c.copy(alpha = 0.45f))
            leaf(Offset(w - m, 14.dp.toPx()), 190f, 12.dp.toPx(), 4.dp.toPx(), c.copy(alpha = 0.35f))
            drawLine(c.copy(alpha = 0.3f), Offset(m, h - 6.dp.toPx()), Offset(m + 44.dp.toPx(), h - 6.dp.toPx()), 1.5.dp.toPx())
            leaf(Offset(m + 22.dp.toPx(), h - 6.dp.toPx()), -60f, 8.dp.toPx(), 3.dp.toPx(), c.copy(alpha = 0.4f))
        }
        Fx2.NEON -> for ((wd, al) in listOf(5f to 0.10f, 2.5f to 0.35f, 1f to 0.9f)) {
            drawLine(c.copy(alpha = al), Offset(m, 0.5f), Offset(w - m, 0.5f), wd.dp.toPx())
            drawLine(c.copy(alpha = al), Offset(m, h - 0.5f), Offset(w - m, h - 0.5f), wd.dp.toPx())
        }
        Fx2.SCIFI -> {
            val l = 10.dp.toPx(); val sw = 1.5.dp.toPx()
            drawLine(c, Offset(0f, 0f), Offset(l, 0f), sw); drawLine(c, Offset(0f, 0f), Offset(0f, l), sw)
            drawLine(c, Offset(w, h), Offset(w - l, h), sw); drawLine(c, Offset(w, h), Offset(w, h - l), sw)
        }
        Fx2.AMOLED -> drawCircle(c.copy(alpha = 0.8f), 2.dp.toPx(), Offset(w - m, m))
        Fx2.TERMINAL -> {
            drawRect(c.copy(alpha = 0.5f), Offset(0f, 0f), Size(2.dp.toPx(), h))
            drawRect(c.copy(alpha = 0.7f), Offset(w - m - 6.dp.toPx(), h - m - 10.dp.toPx()), Size(6.dp.toPx(), 10.dp.toPx()))
        }
        Fx2.PAPER -> {
            val tape = Path().apply {
                moveTo(w / 2 - 20.dp.toPx(), 2.dp.toPx()); lineTo(w / 2 + 18.dp.toPx(), 0f)
                lineTo(w / 2 + 20.dp.toPx(), 9.dp.toPx()); lineTo(w / 2 - 18.dp.toPx(), 11.dp.toPx()); close()
            }
            drawPath(tape, coin.copy(alpha = 0.45f))
            val fold = Path().apply { moveTo(w, h - 14.dp.toPx()); lineTo(w - 14.dp.toPx(), h); lineTo(w, h); close() }
            drawPath(fold, ink.copy(alpha = 0.12f))
        }
        Fx2.LINEN -> drawRoundRect(c.copy(alpha = 0.45f), Offset(5.dp.toPx(), 5.dp.toPx()), Size(w - 10.dp.toPx(), h - 10.dp.toPx()), CornerRadius(6.dp.toPx()),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)))
        Fx2.SAGE -> {
            val b = Offset(w - m, 24.dp.toPx())
            drawLine(c.copy(alpha = 0.7f), b, Offset(b.x, b.y - 12.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            leaf(Offset(b.x, b.y - 11.dp.toPx()), 205f, 12.dp.toPx(), 4.5.dp.toPx(), c.copy(alpha = 0.7f))
            leaf(Offset(b.x, b.y - 11.dp.toPx()), 335f, 12.dp.toPx(), 4.5.dp.toPx(), c.copy(alpha = 0.7f))
        }
        Fx2.SKY -> {
            val cc = c.copy(alpha = 0.22f); val x = w - m - 20.dp.toPx()
            drawCircle(cc, 5.dp.toPx(), Offset(x, 14.dp.toPx())); drawCircle(cc, 6.dp.toPx(), Offset(x + 8.dp.toPx(), 11.dp.toPx())); drawCircle(cc, 4.5.dp.toPx(), Offset(x + 16.dp.toPx(), 14.dp.toPx()))
        }
        Fx2.LILAC -> {
            val ctr = Offset(w - m - 6.dp.toPx(), m + 5.dp.toPx())
            for (k in 0 until 5) drawCircle(c.copy(alpha = 0.4f), 3.dp.toPx(), polar(ctr, 4.dp.toPx(), k * 72f - 90f))
            drawCircle(coin.copy(alpha = 0.8f), 2.dp.toPx(), ctr)
        }
        Fx2.CHALK -> {
            var x = m; var i = 0
            while (x < w * 0.4f) { drawLine(ink.copy(alpha = 0.35f), Offset(x, h - 5.dp.toPx() - (i % 2)), Offset(x + 6.dp.toPx(), h - 5.dp.toPx()), 1.6.dp.toPx(), StrokeCap.Round); x += 9.dp.toPx(); i++ }
            for (k in 0 until 6) drawCircle(ink.copy(alpha = 0.10f), (0.8f + (k % 3) * 0.5f).dp.toPx(), Offset(w - m - k * 7.dp.toPx(), m + (k * 5 % 12).dp.toPx()))
        }
        Fx2.CLAY -> drawLine(Color.White.copy(alpha = 0.22f), Offset(16.dp.toPx(), 5.dp.toPx()), Offset(w * 0.35f, 5.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        Fx2.ROSE -> heart(Offset(w - m - 4.dp.toPx(), m + 3.dp.toPx()), 4.dp.toPx(), c.copy(alpha = 0.45f))
        Fx2.INDIGO -> {
            star4(Offset(w - m - 4.dp.toPx(), m + 2.dp.toPx()), 4.dp.toPx(), coin.copy(alpha = 0.7f))
            star4(Offset(w - m - 16.dp.toPx(), m + 8.dp.toPx()), 2.5.dp.toPx(), coin.copy(alpha = 0.5f))
            star4(Offset(w - m - 10.dp.toPx(), m + 16.dp.toPx()), 2.dp.toPx(), c.copy(alpha = 0.5f))
        }
        else -> Unit
    }
}

/** v262: card shape of each Fx2 theme (the quest box), like the style themes have. */
internal fun fx2Shape(): Shape = when (Fx2Now) {
    Fx2.SUNSET -> RoundedCornerShape(20.dp)
    Fx2.OCEAN -> RoundedCornerShape(16.dp)
    Fx2.FOREST -> RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Fx2.NEON -> RoundedCornerShape(10.dp)
    Fx2.SCIFI -> CutCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
    Fx2.AMOLED -> RoundedCornerShape(6.dp)
    Fx2.TERMINAL -> RectangleShape
    Fx2.PAPER -> RoundedCornerShape(2.dp)
    Fx2.LINEN -> RoundedCornerShape(10.dp)
    Fx2.SAGE -> RoundedCornerShape(20.dp)
    Fx2.SKY -> RoundedCornerShape(22.dp)
    Fx2.LILAC -> RoundedCornerShape(22.dp)
    Fx2.CHALK -> RoundedCornerShape(3.dp)
    Fx2.CLAY -> RoundedCornerShape(22.dp)
    Fx2.ROSE -> RoundedCornerShape(22.dp)
    Fx2.INDIGO -> RoundedCornerShape(12.dp)
    Fx2.NONE -> RoundedCornerShape(12.dp)
}

/** v262: card outline of each Fx2 theme. */
@Composable
internal fun fx2Border(): BorderStroke? {
    val c = ColorPrimary; val ink = ColorText
    return when (Fx2Now) {
        Fx2.SUNSET -> BorderStroke(1.dp, c.copy(alpha = 0.35f))
        Fx2.OCEAN -> BorderStroke(1.dp, c.copy(alpha = 0.45f))
        Fx2.FOREST -> BorderStroke(1.5.dp, c.copy(alpha = 0.5f))
        Fx2.NEON -> BorderStroke(1.5.dp, c.copy(alpha = 0.9f))
        Fx2.SCIFI -> BorderStroke(1.dp, c.copy(alpha = 0.7f))
        Fx2.AMOLED -> BorderStroke(1.dp, ink.copy(alpha = 0.22f))
        Fx2.TERMINAL -> BorderStroke(1.dp, c.copy(alpha = 0.8f))
        Fx2.PAPER -> BorderStroke(1.5.dp, ink.copy(alpha = 0.5f))
        Fx2.LINEN -> BorderStroke(1.dp, c.copy(alpha = 0.25f))
        Fx2.SAGE -> BorderStroke(1.dp, c.copy(alpha = 0.45f))
        Fx2.SKY -> BorderStroke(1.dp, c.copy(alpha = 0.4f))
        Fx2.LILAC -> BorderStroke(1.dp, c.copy(alpha = 0.45f))
        Fx2.CHALK -> BorderStroke(1.5.dp, ink.copy(alpha = 0.6f))
        Fx2.CLAY -> null
        Fx2.ROSE -> BorderStroke(1.dp, c.copy(alpha = 0.5f))
        Fx2.INDIGO -> BorderStroke(1.dp, c.copy(alpha = 0.5f))
        Fx2.NONE -> null
    }
}

/** v261: the quest board look of the Fx2 themes: every Card (Glass.kt) gets its shadow or glow and its decoration while the theme's effects are on. v262: plus a shape and outline (fx2Shape, fx2Border). */
@Composable
internal fun Modifier.fx2Frame(): Modifier {
    if (!Fx2X.on) return this
    val fx = Fx2Now; val c = ColorPrimary; val coin = ColorCoin; val ink = ColorText
    val shape = fx2Shape()
    val base: Modifier = when (fx) {
        Fx2.SUNSET -> this.shadow(6.dp, shape, ambientColor = coin.copy(alpha = 0.35f), spotColor = coin.copy(alpha = 0.35f))
        Fx2.OCEAN -> this.shadow(4.dp, shape, ambientColor = c.copy(alpha = 0.3f), spotColor = c.copy(alpha = 0.3f))
        Fx2.NEON -> this.shadow(9.dp, shape, ambientColor = c.copy(alpha = 0.8f), spotColor = c.copy(alpha = 0.8f))
        Fx2.SCIFI -> this.shadow(5.dp, shape, ambientColor = c.copy(alpha = 0.45f), spotColor = c.copy(alpha = 0.45f))
        Fx2.PAPER -> this.shadow(3.dp, shape, ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
        Fx2.SKY, Fx2.LILAC -> this.shadow(4.dp, shape, ambientColor = c.copy(alpha = 0.25f), spotColor = c.copy(alpha = 0.25f))
        Fx2.CLAY -> this.shadow(8.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
        Fx2.ROSE -> this.shadow(5.dp, shape, ambientColor = c.copy(alpha = 0.3f), spotColor = c.copy(alpha = 0.3f))
        Fx2.INDIGO -> this.shadow(6.dp, shape, ambientColor = c.copy(alpha = 0.3f), spotColor = c.copy(alpha = 0.3f))
        else -> this
    }
    return base.drawWithContent { drawContent(); cardDeco(fx, c, coin, ink) }
}

/** v262: button shape of each Fx2 theme. */
internal fun fx2ButtonShape(): Shape = when (Fx2Now) {
    Fx2.SUNSET, Fx2.SKY, Fx2.LILAC, Fx2.ROSE, Fx2.SAGE, Fx2.CLAY, Fx2.AMOLED -> RoundedCornerShape(50)
    Fx2.OCEAN -> RoundedCornerShape(12.dp)
    Fx2.FOREST -> RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 4.dp)
    Fx2.NEON -> RoundedCornerShape(8.dp)
    Fx2.SCIFI -> CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
    Fx2.TERMINAL -> RectangleShape
    Fx2.PAPER -> RoundedCornerShape(2.dp)
    Fx2.LINEN -> RoundedCornerShape(8.dp)
    Fx2.CHALK -> RoundedCornerShape(3.dp)
    Fx2.INDIGO -> RoundedCornerShape(10.dp)
    Fx2.NONE -> RoundedCornerShape(6.dp)
}

private fun outlinedFx2(): Boolean = when (Fx2Now) { Fx2.NEON, Fx2.SCIFI, Fx2.AMOLED, Fx2.TERMINAL, Fx2.CHALK -> true; else -> false }

/** v262: see-through outlined buttons for Neon, Sci-Fi, AMOLED, Terminal and Chalk, the normal solid ones for the rest. */
@Composable
internal fun fx2ButtonColors(base: ButtonColors): ButtonColors {
    if (!outlinedFx2()) return base
    val a = if (Fx2Now == Fx2.CHALK) ColorText else ColorPrimary
    return ButtonDefaults.buttonColors(
        containerColor = a.copy(alpha = 0.14f), contentColor = a,
        disabledContainerColor = ColorMuted.copy(alpha = 0.10f), disabledContentColor = ColorMuted.copy(alpha = 0.6f),
    )
}

@Composable
internal fun fx2ButtonBorder(): BorderStroke? {
    val c = ColorPrimary; val ink = ColorText
    return when (Fx2Now) {
        Fx2.NEON -> BorderStroke(1.5.dp, c)
        Fx2.SCIFI, Fx2.TERMINAL, Fx2.AMOLED -> BorderStroke(1.dp, c.copy(alpha = 0.8f))
        Fx2.CHALK -> BorderStroke(1.5.dp, ink.copy(alpha = 0.7f))
        Fx2.PAPER -> BorderStroke(1.dp, ink.copy(alpha = 0.6f))
        Fx2.INDIGO -> BorderStroke(1.dp, c.copy(alpha = 0.6f))
        else -> null
    }
}

// ── page background ──

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
            // v262: a static background for the rest too
            Fx2.SUNSET -> {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, ColorCoin.copy(alpha = 0.08f)), startY = size.height * 0.55f, endY = size.height), Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.45f))
                drawCircle(ColorCoin.copy(alpha = 0.07f), 90.dp.toPx(), Offset(size.width * 0.5f, size.height))
            }
            Fx2.OCEAN -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.10f)), startY = size.height * 0.4f, endY = size.height), Offset(0f, size.height * 0.4f), Size(size.width, size.height * 0.6f))
            Fx2.FOREST -> for (i in 0 until 14) leaf(Offset(size.width * (((i * 37 + 11) % 100) / 100f), size.height * (((i * 53 + 7) % 100) / 100f)), ((i * 47) % 360).toFloat(), 22.dp.toPx(), 8.dp.toPx(), c.copy(alpha = 0.07f))
            Fx2.NEON -> {
                drawRect(Brush.horizontalGradient(listOf(c.copy(alpha = 0.10f), Color.Transparent), 0f, 40.dp.toPx()), Offset(0f, 0f), Size(40.dp.toPx(), size.height))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.copy(alpha = 0.10f)), size.width - 40.dp.toPx(), size.width), Offset(size.width - 40.dp.toPx(), 0f), Size(40.dp.toPx(), size.height))
            }
            Fx2.LINEN -> {
                val st = 6.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(c.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f); x += st }
                var y = 0f
                while (y < size.height) { drawLine(c.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f); y += st }
            }
            Fx2.SAGE -> {
                val st = 22.dp.toPx()
                var y = st / 2
                while (y < size.height) { var x = st / 2; while (x < size.width) { drawCircle(c.copy(alpha = 0.08f), 1.dp.toPx(), Offset(x, y)); x += st }; y += st }
            }
            Fx2.SKY -> for (i in 0 until 3) {
                val cx = size.width * (0.2f + 0.3f * i); val cy = size.height * (0.18f + 0.3f * i)
                val cc = c.copy(alpha = 0.06f)
                drawCircle(cc, 22.dp.toPx(), Offset(cx, cy)); drawCircle(cc, 28.dp.toPx(), Offset(cx + 30.dp.toPx(), cy - 8.dp.toPx())); drawCircle(cc, 22.dp.toPx(), Offset(cx + 60.dp.toPx(), cy))
            }
            Fx2.LILAC -> for (i in 0 until 8) {
                val ctr = Offset(size.width * (((i * 41 + 13) % 100) / 100f), size.height * (((i * 59 + 31) % 100) / 100f))
                for (k in 0 until 5) drawCircle(c.copy(alpha = 0.07f), 5.dp.toPx(), polar(ctr, 7.dp.toPx(), k * 72f - 90f))
            }
            Fx2.CLAY -> {
                drawCircle(c.copy(alpha = 0.06f), 110.dp.toPx(), Offset(size.width * 0.1f, size.height * 0.2f))
                drawCircle(c.copy(alpha = 0.05f), 90.dp.toPx(), Offset(size.width * 0.9f, size.height * 0.55f))
                drawCircle(c.copy(alpha = 0.06f), 120.dp.toPx(), Offset(size.width * 0.3f, size.height * 0.95f))
            }
            Fx2.ROSE -> for (i in 0 until 10) heart(Offset(size.width * (((i * 43 + 9) % 100) / 100f), size.height * (((i * 61 + 23) % 100) / 100f)), (6 + i % 3 * 2).dp.toPx(), c.copy(alpha = 0.07f))
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
                    // v263: a clear track and progress arc, 24 petals around the outside (lit as time passes), a flower at the end of the arc
                    arc(c.copy(alpha = 0.16f), 360f, 7.dp.toPx())
                    arc(c, sweep, 7.dp.toPx(), StrokeCap.Round)
                    for (i in 0 until 24) {
                        val a = -90f + i * 15f
                        leaf(polar(ctr, r + 10.dp.toPx(), a), a, (if (i % 2 == 0) 20 else 14).dp.toPx(), 5.dp.toPx(), if (i / 24f <= f) c.copy(alpha = 0.8f) else c.copy(alpha = 0.2f))
                    }
                    val e = polar(ctr, r, end)
                    for (k in 0 until 5) drawCircle(c, 4.dp.toPx(), polar(e, 6.dp.toPx(), k * 72f - 90f))
                    drawCircle(coin, 3.dp.toPx(), e)
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
        Fx2.SAGE -> 30.dp
        Fx2.FOREST, Fx2.LINEN, Fx2.LILAC, Fx2.SKY, Fx2.ROSE, Fx2.INDIGO, Fx2.CHALK, Fx2.PAPER -> 24.dp
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
                // v264: a clean pill of water: the fill is clipped to the pill, its leading edge is a gentle wave, with two light ripples and a few bubbles inside
                val half = 8.dp.toPx()
                val track = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, mid - half, w, mid + half, CornerRadius(half))) }
                drawPath(track, c.copy(alpha = 0.14f))
                if (fw > 2f) clipPath(track) {
                    val edge = Path().apply {
                        moveTo(0f, mid - half); lineTo(fw, mid - half)
                        var y = mid - half
                        while (y < mid + half) { y += 2.dp.toPx(); lineTo(fw + Math.sin(((y - mid) / 5.dp.toPx()).toDouble()).toFloat() * 2.5.dp.toPx(), minOf(y, mid + half)) }
                        lineTo(0f, mid + half); close()
                    }
                    drawPath(edge, Brush.horizontalGradient(listOf(lerp(c, Color.White, 0.25f), c), 0f, maxOf(fw, 1f)))
                    waveLine(mid - 2.dp.toPx(), 1.5.dp.toPx(), 10.dp.toPx(), Color.White.copy(alpha = 0.30f), 1.dp.toPx(), 0f, fw)
                    waveLine(mid + 3.dp.toPx(), 1.5.dp.toPx(), 12.dp.toPx(), Color.White.copy(alpha = 0.18f), 1.dp.toPx(), 0f, fw)
                    for (k in 0..2) {
                        val bx = fw * (0.25f + 0.25f * k)
                        if (bx < fw - 8.dp.toPx()) drawCircle(Color.White.copy(alpha = 0.55f), 1.8.dp.toPx(), Offset(bx, mid + (k % 2 * 4 - 2).dp.toPx()), style = Stroke(1.dp.toPx()))
                    }
                }
            }
            Fx2.SAGE -> {
                // v264: a soft pill that fills up, three seeds on it that open into leaves once passed, and a sprout growing from the end of the fill
                val bh = 12.dp.toPx(); val y = hh - 4.dp.toPx() - bh
                drawRoundRect(c.copy(alpha = 0.16f), Offset(0f, y), Size(w, bh), CornerRadius(bh / 2))
                if (f > 0f) drawRoundRect(Brush.horizontalGradient(listOf(lerp(c, Color.White, 0.2f), c), 0f, maxOf(fw, bh)), Offset(0f, y), Size(maxOf(fw, bh), bh), CornerRadius(bh / 2))
                for (k in 1..3) {
                    val sx = w * k / 4f
                    if (f >= k / 4f) leaf(Offset(sx, y + bh / 2), -90f, 9.dp.toPx(), 3.5.dp.toPx(), lerp(c, Color.White, 0.55f))
                    else drawCircle(c.copy(alpha = 0.45f), 1.8.dp.toPx(), Offset(sx, y + bh / 2))
                }
                val ex = fw.coerceIn(bh, w - 8.dp.toPx())
                val base = Offset(ex, y + 1.dp.toPx())
                drawLine(c, base, Offset(base.x, base.y - 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                leaf(Offset(base.x, base.y - 5.dp.toPx()), -40f, 11.dp.toPx(), 4.dp.toPx(), c)
                leaf(Offset(base.x, base.y - 5.dp.toPx()), -140f, 11.dp.toPx(), 4.dp.toPx(), c)
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
    var pop by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    var lvl by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    LaunchedEffect(SystemEvents.questDoneStamp) {
        if (SystemEvents.questDoneStamp != startQ) { pop = true; delay(1500); pop = false }
    }
    LaunchedEffect(SystemEvents.levelUpStamp) {
        if (SystemEvents.levelUpStamp != startL) { lvl = true; delay(2400); lvl = false }
    }
    if (pop) CenterPopup { Fx2QuestPop(fx, SystemEvents.lastCoins, SystemEvents.lastXp) }
    if (lvl) Dialog(onDismissRequest = { lvl = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) { Fx2LevelUp(fx) }
}
