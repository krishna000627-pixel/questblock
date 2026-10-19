package com.krishna.questblock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.util.SystemEvents

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
            Fx.NINJA -> drawRoundRect(c.copy(alpha = 0.9f), Offset(size.width - 22.dp.toPx(), 6.dp.toPx()), Size(14.dp.toPx(), 14.dp.toPx()), CornerRadius(2.dp.toPx()))
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
            // v241: no strip (Ninja, Hashira): still keep the page below the status bar
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
