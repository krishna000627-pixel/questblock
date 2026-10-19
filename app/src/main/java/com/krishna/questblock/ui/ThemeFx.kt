package com.krishna.questblock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.util.SystemEvents
import kotlin.math.roundToInt

/**
 * v239: the look engine for the style themes. A theme here changes more than colours: card and button shapes, borders,
 * decoration drawn on the cards, the font, a moving or drawn background and a strip at the top of every page.
 * Card (Glass.kt), Button (ClayStyle.kt) and Text (SvgText.kt) call into this file and change nothing while no style theme is on.
 * #svg-logo  NEW THEME RULE: add the theme here (FxNow, fxShape, fxBorder ...), in Theme.kt, in ShopCatalog.kt, in the terminal,
 * and give its emoji an SVG icon in SvgIcons.EMOJI (Self test checks that).
 */
enum class Fx { NONE, SYSTEM, NINJA, HASHIRA, MONARCH, GOTHIC, GUILD, PIXEL, HANDHELD, BLUEPRINT }

val FxNow: Fx get() = if (PosterOn || TermuxOn) Fx.NONE else when (ShopTheme) {
    "theme_system" -> Fx.SYSTEM
    "theme_ninja" -> Fx.NINJA
    "theme_hashira" -> Fx.HASHIRA
    "theme_monarch" -> Fx.MONARCH
    "theme_gothic" -> Fx.GOTHIC
    "theme_guild" -> Fx.GUILD
    "theme_pixel" -> Fx.PIXEL
    "theme_handheld" -> Fx.HANDHELD
    "theme_blueprint" -> Fx.BLUEPRINT
    else -> Fx.NONE
}
val FxOn: Boolean get() = FxNow != Fx.NONE
val SystemOn: Boolean get() = FxNow == Fx.SYSTEM

internal val SYS_SHAPE = CutCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomEnd = 12.dp, bottomStart = 0.dp)

/** Accent of the frames: the System theme turns red for a while after a Focus session is broken (the penalty look). */
@Composable
internal fun fxAccent(): Color = if (SystemOn && SystemEvents.penaltyOn) ColorDanger else ColorPrimary

internal fun fxShape(): Shape = when (FxNow) {
    Fx.SYSTEM -> SYS_SHAPE
    Fx.NINJA -> RoundedCornerShape(2.dp)
    Fx.HASHIRA -> RoundedCornerShape(22.dp)
    Fx.MONARCH -> CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
    Fx.GOTHIC -> RoundedCornerShape(0.dp)
    Fx.GUILD -> RoundedCornerShape(6.dp)
    Fx.PIXEL -> CutCornerShape(5.dp)
    Fx.HANDHELD -> RoundedCornerShape(6.dp)
    Fx.BLUEPRINT -> RoundedCornerShape(0.dp)
    Fx.NONE -> RoundedCornerShape(12.dp)
}

internal fun fxButtonShape(): Shape = when (FxNow) {
    Fx.SYSTEM -> CutCornerShape(topStart = 8.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp)
    Fx.MONARCH -> CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
    Fx.HASHIRA -> RoundedCornerShape(50)
    Fx.PIXEL -> CutCornerShape(4.dp)
    Fx.NINJA -> RoundedCornerShape(2.dp)
    Fx.GOTHIC, Fx.BLUEPRINT -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(6.dp)
}

@Composable
internal fun fxBorder(): BorderStroke? {
    val c = fxAccent()
    return when (FxNow) {
        Fx.SYSTEM -> BorderStroke(1.dp, c.copy(alpha = 0.55f))
        Fx.NINJA -> BorderStroke(1.dp, ColorText.copy(alpha = 0.55f))
        Fx.HASHIRA -> BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        Fx.MONARCH -> BorderStroke(1.dp, c.copy(alpha = 0.7f))
        Fx.GOTHIC -> BorderStroke(1.dp, ColorText.copy(alpha = 0.7f))
        Fx.GUILD -> BorderStroke(2.dp, ColorText.copy(alpha = 0.45f))
        Fx.PIXEL -> BorderStroke(2.dp, ColorText)
        Fx.HANDHELD -> BorderStroke(3.dp, ColorText)
        Fx.BLUEPRINT -> BorderStroke(1.dp, ColorText.copy(alpha = 0.55f))
        Fx.NONE -> null
    }
}

private fun outlinedButtons(): Boolean = when (FxNow) { Fx.SYSTEM, Fx.MONARCH, Fx.BLUEPRINT, Fx.HASHIRA -> true; else -> false }

@Composable
internal fun fxButtonBorder(): BorderStroke? = if (outlinedButtons()) BorderStroke(1.dp, fxAccent()) else when (FxNow) {
    Fx.PIXEL, Fx.HANDHELD -> BorderStroke(2.dp, ColorText)
    Fx.NINJA, Fx.GOTHIC, Fx.GUILD -> BorderStroke(1.dp, ColorText.copy(alpha = 0.6f))
    else -> null
}

/** Outlined see-through buttons for the dark glow themes, the normal solid ones for the rest. */
@Composable
internal fun fxButtonColors(c: ButtonColors): ButtonColors = if (outlinedButtons()) ButtonDefaults.buttonColors(
    containerColor = fxAccent().copy(alpha = 0.16f), contentColor = fxAccent(),
    disabledContainerColor = ColorMuted.copy(alpha = 0.10f), disabledContentColor = ColorMuted.copy(alpha = 0.6f),
) else c

/** The hard pixel shadow under Pixel Arcade buttons. */
@Composable
internal fun Modifier.fxButtonFx(): Modifier {
    if (FxNow != Fx.PIXEL) return this
    val ink = ColorText
    return drawBehind { drawRect(ink.copy(alpha = 0.85f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
}

internal val FxFont: FontFamily? get() = when (FxNow) {
    Fx.SYSTEM, Fx.PIXEL, Fx.HANDHELD, Fx.BLUEPRINT -> FontFamily.Monospace
    Fx.NINJA, Fx.GOTHIC, Fx.GUILD -> FontFamily.Serif
    else -> null
}
internal val FxSpacing: TextUnit get() = if (FxFont == FontFamily.Monospace) 0.3.sp else TextUnit.Unspecified

/** Shadow / glow behind a card plus the decoration drawn over it. The System theme also sweeps a slow scan line over every card. */
@Composable
internal fun Modifier.fxFrame(): Modifier {
    val fx = FxNow
    val c = fxAccent()
    val ink = ColorText
    val shape = fxShape()
    val extras = ThemeExtras.on
    val tilt = remember { ((0..100).random() - 50) / 60f }
    val scan = if (fx == Fx.SYSTEM) rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "scanY") else null
    val base: Modifier = when (fx) {
        Fx.SYSTEM -> this.shadow(5.dp, shape, ambientColor = c.copy(alpha = 0.55f), spotColor = c.copy(alpha = 0.55f))
        Fx.MONARCH -> this.shadow(10.dp, shape, ambientColor = c.copy(alpha = 0.8f), spotColor = c.copy(alpha = 0.8f))
        Fx.HASHIRA -> this.shadow(8.dp, shape, ambientColor = c.copy(alpha = 0.35f), spotColor = c.copy(alpha = 0.35f))
        Fx.GUILD -> this.graphicsLayer { if (extras) rotationZ = tilt }.shadow(3.dp, shape, ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
        Fx.PIXEL -> this.drawBehind { drawRect(ink.copy(alpha = 0.85f), topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = size) }
        else -> this
    }
    return base.drawWithContent {
        drawContent()
        when (fx) {
            Fx.SYSTEM -> {
                brackets(c, 12.dp.toPx(), 2.dp.toPx(), topEnd = true, bottomStart = true)
                val y = (scan?.value ?: 0f) * size.height
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.10f), Color.Transparent), startY = y - 28f, endY = y + 28f),
                    topLeft = Offset(0f, y - 28f), size = Size(size.width, 56f),
                )
            }
            Fx.HASHIRA -> drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset.Zero, Offset(size.width, size.height)))
            Fx.MONARCH -> drawLine(c, Offset(0f, 1f), Offset(size.width, 1f), 2f)
            Fx.GOTHIC -> drawRect(ink.copy(alpha = 0.5f), Offset(4.dp.toPx(), 4.dp.toPx()), Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = Stroke(1f))
            Fx.GUILD -> {
                val brass = Color(0xFFB8862B); val r = 2.5.dp.toPx(); val m = 7.dp.toPx()
                drawCircle(brass, r, Offset(m, m)); drawCircle(brass, r, Offset(size.width - m, m))
                drawCircle(brass, r, Offset(m, size.height - m)); drawCircle(brass, r, Offset(size.width - m, size.height - m))
            }
            Fx.HANDHELD -> drawRect(ink.copy(alpha = 0.35f), Offset(6.dp.toPx(), 6.dp.toPx()), Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), style = Stroke(1.5f))
            Fx.BLUEPRINT -> {
                brackets(c, 8.dp.toPx(), 1.5.dp.toPx(), topEnd = true, bottomStart = true, topStart = true, bottomEnd = true)
                if (extras) { // dimension line along the bottom edge
                    val y = size.height - 5.dp.toPx(); val x0 = 14.dp.toPx(); val x1 = size.width - 14.dp.toPx(); val tk = 3.dp.toPx()
                    drawLine(c.copy(alpha = 0.5f), Offset(x0, y), Offset(x1, y), 1f)
                    drawLine(c.copy(alpha = 0.5f), Offset(x0, y - tk), Offset(x0, y + tk), 1f)
                    drawLine(c.copy(alpha = 0.5f), Offset(x1, y - tk), Offset(x1, y + tk), 1f)
                }
            }
            else -> Unit
        }
    }
}

private fun DrawScope.brackets(c: Color, l: Float, w: Float, topEnd: Boolean = false, bottomStart: Boolean = false, topStart: Boolean = false, bottomEnd: Boolean = false) {
    val h = w / 2
    if (topEnd) { drawLine(c, Offset(size.width - l, h), Offset(size.width, h), w); drawLine(c, Offset(size.width - h, 0f), Offset(size.width - h, l), w) }
    if (bottomStart) { drawLine(c, Offset(0f, size.height - h), Offset(l, size.height - h), w); drawLine(c, Offset(h, size.height - l), Offset(h, size.height), w) }
    if (topStart) { drawLine(c, Offset(0f, h), Offset(l, h), w); drawLine(c, Offset(h, 0f), Offset(h, l), w) }
    if (bottomEnd) { drawLine(c, Offset(size.width - l, size.height - h), Offset(size.width, size.height - h), w); drawLine(c, Offset(size.width - h, size.height - l), Offset(size.width - h, size.height), w) }
}

private fun DrawScope.grid(c: Color, step: Float, majorEvery: Int, majorAlpha: Float) {
    var i = 0; var x = 0f
    while (x < size.width) { drawLine(if (i % majorEvery == 0) c.copy(alpha = majorAlpha) else c, Offset(x, 0f), Offset(x, size.height), 1f); x += step; i++ }
    i = 0; var y = 0f
    while (y < size.height) { drawLine(if (i % majorEvery == 0) c.copy(alpha = majorAlpha) else c, Offset(0f, y), Offset(size.width, y), 1f); y += step; i++ }
}

/** The page background drawing: HUD grid (System), blue grid (Blueprint), LCD dots (Handheld), drifting mist (Hashira), rising shadow motes (Monarch). */
@Composable
internal fun Modifier.fxBackground(): Modifier {
    val fx = FxNow
    if (fx == Fx.NONE) return this
    val c = ColorPrimary
    val ink = ColorText
    val t = if (fx == Fx.HASHIRA || fx == Fx.MONARCH) rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (fx == Fx.HASHIRA) 20000 else 9000, easing = LinearEasing)), label = "bgT") else null
    return drawBehind {
        when (fx) {
            Fx.SYSTEM -> grid(c.copy(alpha = 0.06f), 28.dp.toPx(), 1000, 0.06f)
            Fx.BLUEPRINT -> grid(ink.copy(alpha = 0.07f), 20.dp.toPx(), 5, 0.15f)
            Fx.HANDHELD -> {
                val pts = ArrayList<Offset>()
                val step = 8.dp.toPx()
                var y = step / 2
                while (y < size.height) { var x = step / 2; while (x < size.width) { pts.add(Offset(x, y)); x += step }; y += step }
                drawPoints(pts, PointMode.Points, ink.copy(alpha = 0.10f), 1.5f)
            }
            Fx.HASHIRA -> {
                val p = t?.value ?: 0f
                val r1 = size.minDimension * 0.7f; val c1 = Offset(size.width * (0.2f + 0.6f * p), size.height * 0.30f)
                val r2 = size.minDimension * 0.8f; val c2 = Offset(size.width * (0.8f - 0.6f * p), size.height * 0.75f)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.18f), Color.Transparent), c1, r1), r1, c1)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.12f), Color.Transparent), c2, r2), r2, c2)
            }
            Fx.MONARCH -> {
                val p = t?.value ?: 0f
                for (i in 0 until 14) {
                    val x = size.width * (((i * 37) % 100) / 100f)
                    val y = size.height * (1f - ((p + i / 14f) % 1f))
                    drawCircle(c.copy(alpha = 0.35f), 1.5.dp.toPx(), Offset(x, y))
                }
            }
            else -> Unit
        }
    }
}

/** The strip at the top of every page. Ninja Scroll and Hashira Mist have no strip, only the status bar space (v241). */
@Composable
fun FxBar() {
    val fx = FxNow
    val c = fxAccent()
    val l: String; val r: String
    when (fx) {
        Fx.SYSTEM -> { l = "[ SYSTEM ]"; r = if (SystemEvents.penaltyOn) "PENALTY ZONE \u00b7 FOCUS BROKEN" else "PLAYER STATUS \u00b7 ONLINE" }
        Fx.GUILD -> { l = "GUILD BOARD"; r = "QUESTS OPEN" }
        Fx.PIXEL -> { l = "PLAYER 1"; r = "INSERT COIN" }
        Fx.BLUEPRINT -> { l = "SHEET 01"; r = "QUESTBLOCK \u00b7 REV A" }
        Fx.MONARCH -> { l = "ARISE"; r = if (ThemeExtras.on) "SHADOW ARMY: ${SystemEvents.armyCount}" else "SHADOW ARMY" }
        Fx.HANDHELD -> { l = "QUESTBOY"; r = "BATT \u25AE\u25AE\u25AE" }
        Fx.GOTHIC -> { l = "MEMENTO"; r = if (ThemeExtras.on) "${ThemeExtras.daysLeft()} DAYS LEFT" else "VITA BREVIS" }
        else -> {
            // v241: no strip (Ninja, Hashira): still keep the page below the status bar.
            // v249: Ninja's hanko seal no longer takes a header row of its own; it sits in front of the page title (TitleIcon).
            Spacer(Modifier.fillMaxWidth().background(ColorBg).statusBarsPadding())
            return
        }
    }
    Row(
        Modifier.fillMaxWidth().background(ColorBg).statusBarsPadding().height(24.dp)
            .drawBehind { drawLine(c.copy(alpha = 0.5f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(l, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        if (fx == Fx.SYSTEM) TypedText(r, color = ColorMuted, fontSize = 10.sp)
        else Text(r, color = ColorMuted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
internal fun rememberHankoPaint(): android.graphics.Paint = remember {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER; color = 0xFFFFFCF3.toInt()
    }
}

/** v248: one hanko seal (red disc, thin inner ring, the kanji) of diameter [d] px around [ctr]. Shared by FxBar, the Ninja XP bar and the Ninja level up. */
internal fun DrawScope.drawHanko(ctr: Offset, d: Float, c: Color, paint: android.graphics.Paint, tilt: Float = 8f) {
    rotate(tilt, ctr) {
        drawCircle(c.copy(alpha = 0.92f), d / 2, ctr)
        drawCircle(Color(0xFFFFFCF3).copy(alpha = 0.9f), d / 2 - d * 0.083f, ctr, style = Stroke(d * 0.042f))
        paint.textSize = d * 0.56f
        drawContext.canvas.nativeCanvas.drawText("\u5FCD", ctr.x, ctr.y + paint.textSize * 0.35f, paint)
    }
}

@Composable
internal fun HankoSeal(dia: Dp, c: Color, tilt: Float = 8f) {
    val paint = rememberHankoPaint()
    Canvas(Modifier.size(dia)) { drawHanko(center, this.size.minDimension, c, paint, tilt) }
}

// ── v248: themed Focus ring and XP bar (static looks, only the allowed motion) ───────────────────────────

/** Point on a circle, [deg] 0 = right, -90 = top, clockwise. */
private fun polar(ctr: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(ctr.x + r * Math.cos(a).toFloat(), ctr.y + r * Math.sin(a).toFloat())
}

private fun DrawScope.diamond(at: Offset, d: Float, c: Color) {
    val p = Path().apply { moveTo(at.x, at.y - d); lineTo(at.x + d, at.y); lineTo(at.x, at.y + d); lineTo(at.x - d, at.y); close() }
    drawPath(p, c)
}

private fun DrawScope.dash(on: Float, off: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off), 0f)

/**
 * v248: the Focus ring of the running session in the look of the style theme (only called while ThemeExtras.on, so behind fx_<theme>).
 * [frac] 1 = full time left, [left] is the time text (fmtLeft). Allowed motion: Hashira breathing glow, Monarch rising motes, Pixel last pixel blink.
 */
@Composable
internal fun FxFocusRing(frac: Float, left: String) {
    val fx = FxNow
    val c = ColorPrimary
    val ink = ColorText
    val muted = ColorMuted
    val breath = if (fx == Fx.HASHIRA) rememberInfiniteTransition(label = "breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathT") else null
    val rise = if (fx == Fx.MONARCH) rememberInfiniteTransition(label = "rise").animateFloat(
        0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "riseT") else null
    val blink = if (fx == Fx.PIXEL) rememberInfiniteTransition(label = "pxblink").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "pxblinkT") else null
    val f = frac.coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = center
            val full = size.minDimension / 2
            val sweep = 360f * f
            when (fx) {
                Fx.SYSTEM -> {
                    val r = full - 22.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    drawArc(c.copy(alpha = 0.45f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(3.dp.toPx(), pathEffect = dash(10.dp.toPx(), 8.dp.toPx())))
                    drawArc(c, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(7.dp.toPx()))
                    for (i in 0 until 12) {
                        val a = -90f + i * 30f
                        drawLine(c.copy(alpha = 0.8f), polar(ctr, r + 12.dp.toPx(), a), polar(ctr, r + 20.dp.toPx(), a), 2.dp.toPx())
                    }
                }
                Fx.NINJA -> {
                    val r = full - 26.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    drawArc(ink.copy(alpha = 0.08f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(24.dp.toPx()))
                    drawArc(ink, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(22.dp.toPx(), cap = StrokeCap.Round))
                    val rs = r - 4.dp.toPx()
                    drawArc(ColorBg.copy(alpha = 0.30f), -90f, sweep, false, topLeft = Offset(ctr.x - rs, ctr.y - rs), size = Size(rs * 2, rs * 2), style = Stroke(2.5.dp.toPx()))
                    val e = polar(ctr, r, -90f + sweep)
                    drawCircle(c, 11.dp.toPx(), e)
                    drawCircle(Color(0xFFFFFCF3).copy(alpha = 0.85f), 8.dp.toPx(), e, style = Stroke(1.dp.toPx()))
                }
                Fx.HASHIRA -> {
                    val r = full - 26.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    val s = 1f + 0.07f * (breath?.value ?: 0f)
                    val gr = r + 16.dp.toPx()
                    scale(s, ctr) { drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.40f), Color.Transparent), ctr, gr), gr, ctr) }
                    drawArc(c.copy(alpha = 0.15f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(10.dp.toPx()))
                    drawArc(c, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(Color.White.copy(alpha = 0.30f), r - 18.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                }
                Fx.MONARCH -> {
                    val r = full - 30.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    drawCircle(c.copy(alpha = 0.7f), r + 18.dp.toPx(), ctr, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash(0.1f, 11.dp.toPx())))
                    drawArc(c.copy(alpha = 0.10f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(22.dp.toPx()))
                    drawArc(c.copy(alpha = 0.22f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(12.dp.toPx()))
                    drawArc(c.copy(alpha = 0.30f), -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(c, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    val xs = floatArrayOf(-62f, 28f, -20f, 60f, 4f)
                    val p0 = rise?.value ?: 0f
                    for (i in 0 until 5) {
                        val ph = (p0 + i / 5f) % 1f
                        val y = ctr.y + r * 0.75f - ph * r * 1.5f
                        drawCircle(c.copy(alpha = (Math.sin(ph * Math.PI).toFloat() * 0.9f).coerceIn(0f, 1f)), 2.5.dp.toPx(), Offset(ctr.x + xs[i].dp.toPx(), y))
                    }
                }
                Fx.GOTHIC -> {
                    val r = full - 20.dp.toPx()
                    drawCircle(ink.copy(alpha = 0.7f), r, ctr, style = Stroke(1.dp.toPx()))
                    drawCircle(ink.copy(alpha = 0.7f), r - 8.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                    val ra = r - 4.dp.toPx()
                    drawArc(ink, -90f, sweep, false, topLeft = Offset(ctr.x - ra, ctr.y - ra), size = Size(ra * 2, ra * 2), style = Stroke(2.dp.toPx()))
                    diamond(polar(ctr, ra, -90f + sweep), 7.dp.toPx(), c)
                }
                Fx.GUILD -> {
                    val big = full - 4.dp.toPx()
                    drawCircle(ColorCard, big, ctr)
                    drawCircle(ink.copy(alpha = 0.5f), big, ctr, style = Stroke(2.dp.toPx()))
                    for (i in 0 until 12) drawCircle(c, 3.dp.toPx(), polar(ctr, big - 9.dp.toPx(), -90f + i * 30f))
                    val r = big - 36.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    drawArc(ink.copy(alpha = 0.12f), -90f, 360f, false, topLeft = tl, size = sz, style = Stroke(14.dp.toPx()))
                    drawArc(c, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                }
                Fx.PIXEL -> {
                    val r = full - 18.dp.toPx(); val n = 24; val side = 20.dp.toPx()
                    val lit = (f * n).roundToInt().coerceIn(0, n)
                    for (i in 0 until n) {
                        val p = polar(ctr, r, -90f + i * 360f / n)
                        val on = i < lit
                        val dim = on && i == lit - 1 && (blink?.value ?: 0f) > 0.5f
                        val col = if (!on) muted.copy(alpha = 0.25f) else if (dim) c.copy(alpha = 0.15f) else c
                        drawRect(col, Offset(p.x - side / 2, p.y - side / 2), Size(side, side))
                    }
                }
                Fx.HANDHELD -> {
                    val r = full - 14.dp.toPx(); val n = 36
                    val lit = (f * n).roundToInt().coerceIn(0, n)
                    for (i in 0 until n) {
                        val on = i < lit
                        drawCircle(if (on) ink else ink.copy(alpha = 0.22f), if (on) 5.dp.toPx() else 2.dp.toPx(), polar(ctr, r, -90f + i * 360f / n))
                    }
                }
                Fx.BLUEPRINT -> {
                    val r = full - 26.dp.toPx()
                    val tl = Offset(ctr.x - r, ctr.y - r); val sz = Size(r * 2, r * 2)
                    drawCircle(ink.copy(alpha = 0.45f), r, ctr, style = Stroke(1.5.dp.toPx(), pathEffect = dash(8.dp.toPx(), 5.dp.toPx())))
                    drawArc(c, -90f, sweep, false, topLeft = tl, size = sz, style = Stroke(4.dp.toPx(), pathEffect = dash(12.dp.toPx(), 6.dp.toPx())))
                    for (i in 0 until 36) {
                        val a = -90f + i * 10f
                        val len = if (i % 9 == 0) 20.dp.toPx() else 14.dp.toPx()
                        drawLine(ink.copy(alpha = 0.7f), polar(ctr, r + 8.dp.toPx(), a), polar(ctr, r + len, a), 1.dp.toPx())
                    }
                    drawLine(ink.copy(alpha = 0.25f), Offset(ctr.x - r, ctr.y), Offset(ctr.x + r, ctr.y), 1.dp.toPx())
                    drawLine(ink.copy(alpha = 0.25f), Offset(ctr.x, ctr.y - r), Offset(ctr.x, ctr.y + r), 1.dp.toPx())
                }
                else -> Unit
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val txt = if (fx == Fx.SYSTEM) "[ $left ]" else left
            val fs = when (fx) {
                Fx.SYSTEM -> if (txt.length > 9) 22.sp else 30.sp
                Fx.PIXEL, Fx.HANDHELD, Fx.BLUEPRINT -> 44.sp
                Fx.NINJA, Fx.GUILD, Fx.GOTHIC -> 54.sp
                else -> 56.sp
            }
            Text(txt, fontSize = fs, fontWeight = FontWeight.Black, color = ink)
            Text(if (fx == Fx.BLUEPRINT) "\u00D8 92" else "remaining", color = muted, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

/**
 * v248: the XP level bar of the Home hero card in the look of the style theme (only called while ThemeExtras.on, so behind fx_<theme>).
 * [fraction] is the animated XP into the level (0..1, so the bar fills when XP is gained), [xp] the XP into the level out of 100. No moving lines.
 */
@Composable
internal fun FxXpBar(fraction: Float, xp: Int) {
    val fx = FxNow
    val f = fraction.coerceIn(0f, 1f)
    val c = ColorPrimary
    val ink = ColorText
    val paint = rememberHankoPaint()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (fx == Fx.SYSTEM) Text("[ XP ] $xp/100", color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (fx == Fx.BLUEPRINT) Text("FIG. 2 TYPE B", color = ColorMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        val mod = when (fx) {
            Fx.SYSTEM -> Modifier.fillMaxWidth().height(12.dp)
            Fx.NINJA -> Modifier.fillMaxWidth().height(26.dp)
            Fx.HASHIRA -> Modifier.fillMaxWidth().height(10.dp)
            Fx.MONARCH, Fx.GOTHIC, Fx.GUILD -> Modifier.fillMaxWidth().height(18.dp)
            Fx.PIXEL -> Modifier.fillMaxWidth().height(16.dp)
            Fx.HANDHELD -> Modifier.fillMaxWidth().height(20.dp).border(3.dp, ink).padding(3.dp)
            Fx.BLUEPRINT -> Modifier.fillMaxWidth().height(16.dp)
            else -> Modifier.fillMaxWidth().height(10.dp)
        }
        Canvas(mod) {
            val w = size.width; val h = size.height; val mid = h / 2
            when (fx) {
                Fx.SYSTEM -> {
                    val n = 20; val gap = 3.dp.toPx(); val bw = (w - gap * (n - 1)) / n
                    val lit = (f * n).roundToInt()
                    for (i in 0 until n) {
                        val x = i * (bw + gap)
                        if (i < lit) drawRect(c, Offset(x, 0f), Size(bw, h))
                        else drawRect(c.copy(alpha = 0.6f), Offset(x + 0.5f, 0.5f), Size(bw - 1f, h - 1f), style = Stroke(1.dp.toPx()))
                    }
                }
                Fx.NINJA -> {
                    val sealR = 11.dp.toPx()
                    val endX = (f * w).coerceIn(sealR, w - sealR)
                    val th = 6.dp.toPx()
                    drawRoundRect(ink.copy(alpha = 0.10f), Offset(0f, mid - 4.dp.toPx()), Size(w, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                    val taper = maxOf(0f, endX - 28.dp.toPx())
                    val p = Path().apply { moveTo(0f, mid - th); lineTo(taper, mid - th); lineTo(endX, mid); lineTo(taper, mid + th); lineTo(0f, mid + th); close() }
                    drawPath(p, ink)
                    if (taper > 8.dp.toPx()) drawLine(ColorBg.copy(alpha = 0.25f), Offset(4.dp.toPx(), mid - 1.dp.toPx()), Offset(taper, mid - 1.dp.toPx()), 1.5.dp.toPx())
                    drawHanko(Offset(endX, mid), sealR * 2, c, paint)
                }
                Fx.HASHIRA -> {
                    drawRoundRect(c.copy(alpha = 0.18f), Offset.Zero, Size(w, h), CornerRadius(h / 2))
                    if (f > 0f) drawRoundRect(c, Offset.Zero, Size(maxOf(f * w, h), h), CornerRadius(h / 2))
                }
                Fx.MONARCH -> {
                    val bh = 8.dp.toPx(); val y = mid - bh / 2
                    drawRoundRect(c.copy(alpha = 0.15f), Offset(0f, y), Size(w, bh), CornerRadius(bh / 2))
                    if (f > 0f) {
                        val fw = maxOf(f * w, bh)
                        drawRoundRect(c.copy(alpha = 0.18f), Offset(0f, y - 5.dp.toPx()), Size(fw, bh + 10.dp.toPx()), CornerRadius(bh / 2 + 5.dp.toPx()))
                        drawRoundRect(c.copy(alpha = 0.30f), Offset(0f, y - 2.dp.toPx()), Size(fw, bh + 4.dp.toPx()), CornerRadius(bh / 2 + 2.dp.toPx()))
                        drawRoundRect(c, Offset(0f, y), Size(fw, bh), CornerRadius(bh / 2))
                    }
                }
                Fx.GOTHIC -> {
                    val d = 6.dp.toPx()
                    val endX = (f * w).coerceIn(d, w - d)
                    drawLine(ink.copy(alpha = 0.45f), Offset(0f, mid - 3.dp.toPx()), Offset(w, mid - 3.dp.toPx()), 1.dp.toPx())
                    drawLine(ink.copy(alpha = 0.45f), Offset(0f, mid + 3.dp.toPx()), Offset(w, mid + 3.dp.toPx()), 1.dp.toPx())
                    drawLine(ink, Offset(0f, mid), Offset(endX, mid), 2.dp.toPx())
                    diamond(Offset(endX, mid), d, c)
                }
                Fx.GUILD -> {
                    val cr = CornerRadius(4.dp.toPx())
                    drawRoundRect(ink.copy(alpha = 0.20f), Offset.Zero, Size(w, h), cr)
                    drawRoundRect(ink.copy(alpha = 0.6f), Offset.Zero, Size(w, h), cr, style = Stroke(1.5.dp.toPx()))
                    val fw = f * w - 4.dp.toPx()
                    if (fw > 0f) drawRoundRect(c, Offset(2.dp.toPx(), 2.dp.toPx()), Size(fw, h - 4.dp.toPx()), CornerRadius(3.dp.toPx()))
                    for (i in 1..9) { val x = w * i / 10f; drawLine(ink.copy(alpha = 0.35f), Offset(x, 3.dp.toPx()), Offset(x, h - 3.dp.toPx()), 1.dp.toPx()) }
                    drawCircle(ColorCoin, 3.dp.toPx(), Offset(7.dp.toPx(), mid))
                    drawCircle(ColorCoin, 3.dp.toPx(), Offset(w - 7.dp.toPx(), mid))
                }
                Fx.PIXEL -> {
                    val n = 10; val gap = 3.dp.toPx(); val sh = 2.dp.toPx()
                    val bw = (w - sh - gap * (n - 1)) / n; val bh = h - sh
                    val lit = (f * n).toInt()
                    for (i in 0 until n) {
                        val x = i * (bw + gap)
                        if (i < lit) {
                            drawRect(ink.copy(alpha = 0.85f), Offset(x + sh, sh), Size(bw, bh))
                            drawRect(c, Offset(x, 0f), Size(bw, bh))
                        } else drawRect(ColorMuted.copy(alpha = 0.25f), Offset(x, 0f), Size(bw, bh))
                    }
                }
                Fx.HANDHELD -> {
                    val n = 24; val gap = 2.dp.toPx(); val sw = (w - gap * (n - 1)) / n
                    val lit = (f * n).roundToInt()
                    for (i in 0 until n) drawRect(if (i < lit) ink else ink.copy(alpha = 0.12f), Offset(i * (sw + gap), 0f), Size(sw, h))
                }
                Fx.BLUEPRINT -> {
                    val endX = f * w
                    drawLine(ink.copy(alpha = 0.5f), Offset(0f, mid), Offset(w, mid), 1.dp.toPx())
                    drawLine(ink, Offset(0.75.dp.toPx(), 0f), Offset(0.75.dp.toPx(), h), 1.5.dp.toPx())
                    drawLine(ink, Offset(w - 0.75.dp.toPx(), 0f), Offset(w - 0.75.dp.toPx(), h), 1.5.dp.toPx())
                    if (f > 0f) {
                        drawLine(c, Offset(0f, mid), Offset(endX, mid), 3.dp.toPx())
                        drawLine(c, Offset(endX, mid - 5.dp.toPx()), Offset(endX, mid + 5.dp.toPx()), 2.dp.toPx())
                    }
                }
                else -> Unit
            }
        }
    }
}

// ── v253: lock screens (BlockScreen, Fun limit, Focus lock, Study first) in the look of the style theme ──────────

/** v253: the theme word under the title of a lock page ("[ ACCESS DENIED ]", "Sealed", ...). Empty (nothing drawn) when the theme's extras are off. */
@Composable
internal fun FxLockWord() {
    val w = ThemeExtras.word("locked", "")
    if (w.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Text(w, color = fxAccent(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
}

/** v253: [content] (the app icon or emoji of a lock page) inside a static themed emblem with a padlock badge (Ninja: red hanko seal). Only motion: a one-shot pop in (0.45 s). Call only while ThemeExtras.on. */
@Composable
internal fun FxLockEmblem(content: @Composable () -> Unit) {
    val fx = FxNow
    val c = ColorPrimary
    val ink = ColorText
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
                Fx.SYSTEM -> {
                    drawCircle(c.copy(alpha = 0.45f), full - 6.dp.toPx(), ctr, style = Stroke(2.dp.toPx(), pathEffect = dash(8.dp.toPx(), 6.dp.toPx())))
                    val l = 20.dp.toPx(); val w = 3.dp.toPx(); val h = w / 2; val sw = size.width; val sh = size.height
                    drawLine(c, Offset(0f, h), Offset(l, h), w); drawLine(c, Offset(h, 0f), Offset(h, l), w)
                    drawLine(c, Offset(sw - l, h), Offset(sw, h), w); drawLine(c, Offset(sw - h, 0f), Offset(sw - h, l), w)
                    drawLine(c, Offset(0f, sh - h), Offset(l, sh - h), w); drawLine(c, Offset(h, sh - l), Offset(h, sh), w)
                    drawLine(c, Offset(sw - l, sh - h), Offset(sw, sh - h), w); drawLine(c, Offset(sw - h, sh - l), Offset(sw - h, sh), w)
                }
                Fx.NINJA -> {
                    val r = full - 8.dp.toPx()
                    drawArc(ink, -60f, 300f, false, topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
                Fx.HASHIRA -> {
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.40f), Color.Transparent), ctr, full), full, ctr)
                    drawCircle(Color.White.copy(alpha = 0.30f), full - 10.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                }
                Fx.MONARCH -> {
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), ctr, full), full, ctr)
                    drawCircle(c.copy(alpha = 0.7f), full - 6.dp.toPx(), ctr, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash(0.1f, 9.dp.toPx())))
                }
                Fx.GOTHIC -> {
                    drawCircle(ink.copy(alpha = 0.7f), full - 4.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                    drawCircle(ink.copy(alpha = 0.7f), full - 10.dp.toPx(), ctr, style = Stroke(1.dp.toPx()))
                    diamond(Offset(ctr.x, ctr.y - full + 4.dp.toPx()), 5.dp.toPx(), c)
                }
                Fx.GUILD -> {
                    drawCircle(ColorCard, full - 2.dp.toPx(), ctr)
                    drawCircle(ink.copy(alpha = 0.5f), full - 2.dp.toPx(), ctr, style = Stroke(2.dp.toPx()))
                    for (i in 0 until 12) drawCircle(c, 2.5.dp.toPx(), polar(ctr, full - 9.dp.toPx(), -90f + i * 30f))
                }
                Fx.PIXEL -> {
                    val side = 10.dp.toPx()
                    for (i in 0 until 16) { val q = polar(ctr, full - 8.dp.toPx(), -90f + i * 22.5f); drawRect(c, Offset(q.x - side / 2, q.y - side / 2), Size(side, side)) }
                }
                Fx.HANDHELD -> {
                    for (i in 0 until 24) drawCircle(ink, 3.dp.toPx(), polar(ctr, full - 6.dp.toPx(), -90f + i * 15f))
                }
                Fx.BLUEPRINT -> {
                    drawCircle(ink.copy(alpha = 0.45f), full - 8.dp.toPx(), ctr, style = Stroke(1.5.dp.toPx(), pathEffect = dash(6.dp.toPx(), 4.dp.toPx())))
                    for (i in 0 until 24) drawLine(ink.copy(alpha = 0.7f), polar(ctr, full - 3.dp.toPx(), -90f + i * 15f), polar(ctr, full - 8.dp.toPx(), -90f + i * 15f), 1.dp.toPx())
                    drawLine(ink.copy(alpha = 0.25f), Offset(0f, ctr.y), Offset(size.width, ctr.y), 1.dp.toPx())
                    drawLine(ink.copy(alpha = 0.25f), Offset(ctr.x, 0f), Offset(ctr.x, size.height), 1.dp.toPx())
                }
                else -> Unit
            }
        }
        content()
        if (fx == Fx.NINJA) {
            Box(Modifier.align(Alignment.BottomEnd)) { HankoSeal(36.dp, c) }
        } else {
            Canvas(Modifier.size(28.dp).align(Alignment.BottomEnd)) {
                val w = size.width; val h = size.height
                drawCircle(ColorBg, w / 2, center)
                drawArc(c, 180f, 180f, false, topLeft = Offset(w * 0.30f, h * 0.18f), size = Size(w * 0.40f, h * 0.42f), style = Stroke(2.5.dp.toPx()))
                drawRoundRect(c, Offset(w * 0.22f, h * 0.42f), Size(w * 0.56f, h * 0.40f), CornerRadius(2.dp.toPx()))
                drawCircle(ColorBg, w * 0.05f, Offset(w * 0.5f, h * 0.62f))
            }
        }
    }
}
