package com.krishna.questblock.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * v284: how the Daily ring (Home) and the Streak map squares look in every theme: the 9 style themes (System, Ninja Scroll, Hashira Mist,
 * Shadow Monarch, Gothic Halo, Guild Board, Pixel Arcade, Handheld, Blueprint), the 22 shop themes (Sunset to Vaporwave) and the plain default.
 * Each follows the motif of its own Focus ring and cards. Off (theme extras flag off) = the default look.
 */
class RingLook(
    val color: Color,
    val width: Dp,
    val trackAlpha: Float,
    val round: Boolean,
    val glow: Boolean = false,
    val end: Color? = null,       // gradient end colour, null = solid
    val dashOn: Float = 0f,       // dp; 0 = solid line. With a tiny value and round caps the ring becomes dots
    val dashGap: Float = 0f,      // dp
    val inner: Boolean = false,   // a second thin ring inside
)

@Composable
fun ringLook(): RingLook {
    val c = ColorPrimary; val ink = ColorText; val coin = ColorCoin; val acc = ColorAccent
    if (ThemeExtras.on) return when (FxNow) {
        Fx.SYSTEM -> RingLook(fxAccent(), 3.dp, 0.2f, false, dashOn = 5f, dashGap = 3f)   // HUD blocks
        Fx.NINJA -> RingLook(ink, 4.dp, 0.08f, true)                                      // ink brush
        Fx.HASHIRA -> RingLook(c, 3.dp, 0.15f, true, glow = true)                         // mist
        Fx.MONARCH -> RingLook(c, 3.dp, 0.2f, true, glow = true, end = acc)               // violet glow
        Fx.GOTHIC -> RingLook(c, 1.5.dp, 0.25f, false, inner = true)                      // double ring
        Fx.GUILD -> RingLook(coin, 3.5.dp, 0.25f, true, dashOn = 0.1f, dashGap = 5f)      // brass studs
        Fx.PIXEL -> RingLook(c, 3.dp, 0.2f, false, dashOn = 3f, dashGap = 3f)             // pixels
        Fx.HANDHELD -> RingLook(ink, 3.dp, 0.15f, true, dashOn = 0.1f, dashGap = 3f)      // dot matrix
        Fx.BLUEPRINT -> RingLook(c, 1.dp, 0.3f, false, dashOn = 2f, dashGap = 2f, inner = true) // drafting line
        else -> RingLook(c, 3.dp, 0.22f, true)
    }
    if (Fx2X.on) return when (Fx2Now) {
        Fx2.SUNSET -> RingLook(c, 3.dp, 0.2f, true, end = coin)
        Fx2.OCEAN -> RingLook(c, 3.dp, 0.2f, true, inner = true)
        Fx2.FOREST -> RingLook(c, 3.dp, 0.2f, true, dashOn = 0.1f, dashGap = 5f)
        Fx2.NEON -> RingLook(c, 2.5.dp, 0.15f, true, glow = true)
        Fx2.SCIFI -> RingLook(c, 2.5.dp, 0.18f, false, dashOn = 8f, dashGap = 2f)
        Fx2.AMOLED -> RingLook(c, 1.5.dp, 0.2f, false)
        Fx2.TERMINAL -> RingLook(c, 2.dp, 0.2f, false, dashOn = 2f, dashGap = 2f)
        Fx2.PAPER -> RingLook(ink, 2.dp, 0.12f, true)
        Fx2.LINEN -> RingLook(c, 2.5.dp, 0.2f, false, dashOn = 4f, dashGap = 3f)
        Fx2.SAGE -> RingLook(c, 3.dp, 0.2f, true, dashOn = 0.1f, dashGap = 5f)
        Fx2.SKY -> RingLook(c, 3.dp, 0.2f, true, end = coin)
        Fx2.LILAC -> RingLook(c, 4.dp, 0.2f, true, dashOn = 0.1f, dashGap = 4f)
        Fx2.CHALK -> RingLook(ink, 2.5.dp, 0.15f, true, dashOn = 6f, dashGap = 2f)
        Fx2.CLAY -> RingLook(c, 5.dp, 0.18f, true)
        Fx2.ROSE -> RingLook(c, 3.5.dp, 0.2f, true, dashOn = 0.1f, dashGap = 4f)
        Fx2.INDIGO -> RingLook(c, 2.5.dp, 0.2f, true, dashOn = 0.1f, dashGap = 4f, end = coin)
        Fx2.MONO -> RingLook(ink, 1.5.dp, 0.15f, false)
        Fx2.WHITESPACE -> RingLook(ink, 1.5.dp, 0.10f, false)
        Fx2.ZEN -> RingLook(ink.copy(alpha = 0.85f), 4.dp, 0.08f, true)
        Fx2.CARNIVAL -> RingLook(c, 4.dp, 0.18f, true, dashOn = 0.1f, dashGap = 4f, end = acc)
        Fx2.BAROQUE -> RingLook(coin, 3.dp, 0.2f, false, inner = true)
        Fx2.VAPORWAVE -> RingLook(c, 3.dp, 0.2f, true, end = acc)
        else -> RingLook(c, 3.dp, 0.22f, true)
    }
    return RingLook(c, 3.dp, 0.22f, true)
}

/** A Streak map square: the colour at each level and the shape. */
class CellLook(val base: Color, val shape: Shape) {
    fun fill(n: Int): Color = when {
        n <= 0 -> ColorMuted.copy(alpha = 0.12f)
        n == 1 -> base.copy(alpha = 0.35f)
        n == 2 -> base.copy(alpha = 0.6f)
        else -> base.copy(alpha = 0.9f)
    }
}

@Composable
fun cellLook(): CellLook {
    val c = ColorPrimary; val ink = ColorText; val coin = ColorCoin
    val round = CircleShape
    if (ThemeExtras.on) return when (FxNow) {
        Fx.SYSTEM -> CellLook(fxAccent(), CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp))
        Fx.NINJA -> CellLook(ink, RoundedCornerShape(1.dp))
        Fx.HASHIRA -> CellLook(c, round)
        Fx.MONARCH -> CellLook(c, RoundedCornerShape(4.dp))
        Fx.GOTHIC -> CellLook(c, CutCornerShape(5.dp))
        Fx.GUILD -> CellLook(coin, RoundedCornerShape(3.dp))
        Fx.PIXEL -> CellLook(c, RectangleShape)
        Fx.HANDHELD -> CellLook(ink, RectangleShape)
        Fx.BLUEPRINT -> CellLook(c, RectangleShape)
        else -> CellLook(c, RoundedCornerShape(4.dp))
    }
    if (Fx2X.on) return when (Fx2Now) {
        Fx2.SUNSET -> CellLook(coin, round)
        Fx2.OCEAN -> CellLook(c, RoundedCornerShape(6.dp))
        Fx2.FOREST -> CellLook(c, RoundedCornerShape(topStart = 7.dp, topEnd = 1.dp, bottomEnd = 7.dp, bottomStart = 1.dp))
        Fx2.NEON -> CellLook(c, RoundedCornerShape(4.dp))
        Fx2.SCIFI -> CellLook(c, CutCornerShape(topEnd = 4.dp, bottomStart = 4.dp))
        Fx2.AMOLED -> CellLook(c, round)
        Fx2.TERMINAL -> CellLook(c, RectangleShape)
        Fx2.PAPER -> CellLook(ink, RoundedCornerShape(2.dp))
        Fx2.LINEN -> CellLook(c, RoundedCornerShape(3.dp))
        Fx2.SAGE -> CellLook(c, round)
        Fx2.SKY -> CellLook(c, round)
        Fx2.LILAC -> CellLook(c, round)
        Fx2.CHALK -> CellLook(ink, RoundedCornerShape(2.dp))
        Fx2.CLAY -> CellLook(c, RoundedCornerShape(8.dp))
        Fx2.ROSE -> CellLook(c, round)
        Fx2.INDIGO -> CellLook(coin, round)
        Fx2.MONO -> CellLook(ink, RectangleShape)
        Fx2.WHITESPACE -> CellLook(ink, RectangleShape)
        Fx2.ZEN -> CellLook(ink, round)
        Fx2.CARNIVAL -> CellLook(c, RoundedCornerShape(2.dp))
        Fx2.BAROQUE -> CellLook(coin, CutCornerShape(4.dp))
        Fx2.VAPORWAVE -> CellLook(c, RectangleShape)
        else -> CellLook(c, RoundedCornerShape(4.dp))
    }
    return CellLook(c, RoundedCornerShape(4.dp))
}
