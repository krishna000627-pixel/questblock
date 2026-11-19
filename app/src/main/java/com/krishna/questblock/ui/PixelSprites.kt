package com.krishna.questblock.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * v292: High-quality 16x16 pixel-art companion sprites rendered via integer-aligned Canvas blocks.
 * Each companion has 3 evolution stages (stageIndex 0..2) with idle bobbing and happy pet reactions.
 */
@Composable
fun PixelCompanionSprite(
    companionId: String,
    stageIndex: Int,
    happy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val inf = rememberInfiniteTransition(label = "px_sprite")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "px_phase",
    )
    val bobRows = if (AnimConfig.master && phase > 0.5f) -1 else 0
    val blinkPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "px_blink",
    )
    val blinking = AnimConfig.master && blinkPhase > 0.92f

    val grid = when (companionId) {
        "wolf" -> WOLF_16
        "owl" -> OWL_16
        "cat" -> CAT_16
        "dragon" -> DRAGON_16
        "frog" -> FROG_16
        else -> KITSUNE_16
    }
    val palette = paletteFor(companionId, stageIndex, happy)

    Canvas(modifier = modifier) {
        val cols = 16
        val rows = 16
        val px = (size.minDimension / cols).coerceAtLeast(1f)
        val ox = (size.width - px * cols) / 2f
        val oy = (size.height - px * rows) / 2f

        // Stage 2 & 3 evolution aura behind the pixel sprite
        if (stageIndex >= 1) {
            val aura = palette['A'] ?: Color(0xFFFFD54F)
            drawCircle(
                color = aura.copy(alpha = if (stageIndex == 2) 0.24f else 0.14f),
                radius = size.minDimension * (0.42f + 0.04f * phase),
                center = Offset(size.width / 2f, size.height / 2f),
            )
            if (stageIndex == 2) {
                drawCircle(
                    color = aura.copy(alpha = 0.65f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = (px * 0.6f).coerceAtLeast(1.5f)),
                )
            }
        }

        // Draw 16x16 pixel matrix
        for (r in 0 until rows) {
            val line = grid[r]
            for (c in 0 until cols) {
                val ch = line[c]
                if (ch == '.') continue
                val effCh = if (blinking && ch == 'E') 'M' else ch
                val col = palette[effCh] ?: continue
                val drawR = (r + bobRows).coerceIn(0, rows - 1)
                drawRect(
                    color = col,
                    topLeft = Offset(ox + c * px, oy + drawR * px),
                    size = Size(px + 0.4f, px + 0.4f),
                )
            }
        }

        // Orbiting pixel sparkles in any theme
        if (AnimConfig.master) {
            val spCol = if (happy) Color(0xFFFF6B9D) else (palette['A'] ?: Color(0xFFFFD54F))
            val s1x = ox + (2f + 11f * phase) * px
            val s1y = oy + (2f + 2f * kotlin.math.sin(phase * Math.PI * 2.0).toFloat()) * px
            drawRect(spCol.copy(alpha = 0.85f), Offset(s1x, s1y), Size(px * 0.9f, px * 0.9f))
            if (happy || stageIndex >= 1) {
                val s2x = ox + (13f - 10f * phase) * px
                val s2y = oy + (12f - 3f * phase) * px
                drawRect(Color.White.copy(alpha = 0.85f), Offset(s2x, s2y), Size(px * 0.85f, px * 0.85f))
            }
        }

        // Evolution crown / crest pixels on Stage 2 & 3
        if (stageIndex >= 1) {
            val crown = Color(0xFFFFD54F)
            val topY = oy + (0 + bobRows).coerceAtLeast(0) * px
            drawRect(crown, Offset(ox + 6 * px, topY), Size(px, px))
            drawRect(crown, Offset(ox + 9 * px, topY), Size(px, px))
            if (stageIndex == 2) {
                drawRect(crown, Offset(ox + 7 * px, topY), Size(px * 2, px))
                drawRect(Color(0xFFFFFFFF), Offset(ox + 7.5f * px, topY), Size(px, px))
            }
        }
    }
}

private fun paletteFor(id: String, stage: Int, happy: Boolean): Map<Char, Color> {
    val eye = if (happy) Color(0xFFFF6B9D) else Color(0xFF1A1423)
    return when (id) {
        "wolf" -> mapOf(
            'O' to Color(0xFF161122),
            'M' to if (stage == 2) Color(0xFF8B6BFF) else Color(0xFF5D647B),
            'L' to Color(0xFFB8C0D9),
            'W' to Color(0xFFEAF0FF),
            'E' to if (happy) Color(0xFFFF85B3) else Color(0xFFB388FF),
            'A' to Color(0xFF9C6BFF),
        )
        "owl" -> mapOf(
            'O' to Color(0xFF1A1423),
            'M' to if (stage == 2) Color(0xFF4A86C5) else Color(0xFF7B563E),
            'L' to Color(0xFFE8D5B7),
            'W' to Color(0xFFFFC857),
            'E' to eye,
            'A' to Color(0xFF4A86C5),
        )
        "cat" -> mapOf(
            'O' to Color(0xFF0A101E),
            'M' to if (stage == 2) Color(0xFF1C2B4B) else Color(0xFF28324A),
            'L' to Color(0xFF00E5FF),
            'W' to Color(0xFFE0FBFF),
            'E' to if (happy) Color(0xFFFF66C4) else Color(0xFF00E5FF),
            'A' to Color(0xFF00E5FF),
        )
        "dragon" -> mapOf(
            'O' to Color(0xFF0E2416),
            'M' to if (stage == 2) Color(0xFF2ECC71) else Color(0xFF4EA864),
            'L' to Color(0xFFFFD166),
            'W' to Color(0xFFDFF7E2),
            'E' to eye,
            'A' to Color(0xFF6BCB77),
        )
        "frog" -> mapOf(
            'O' to Color(0xFF172819),
            'M' to if (stage == 2) Color(0xFF58B970) else Color(0xFF6B9E6E),
            'L' to Color(0xFFD8F3DC),
            'W' to Color(0xFFFF9EBB),
            'E' to eye,
            'A' to Color(0xFF6B8F71),
        )
        else -> mapOf( // kitsune
            'O' to Color(0xFF2A140E),
            'M' to if (stage == 2) Color(0xFFFF6B35) else Color(0xFFFF8243),
            'L' to Color(0xFFFFE6C7),
            'W' to Color(0xFFFFFFFF),
            'E' to eye,
            'A' to Color(0xFFFF7A59),
        )
    }
}

private val KITSUNE_16 = listOf(
    "................",
    "..OO........OO..",
    ".OMMO......OMMO.",
    ".OMLMO....OMLMO.",
    ".OMMMOOOOOOMMMO.",
    "..OMMMMMMMMMMO..",
    "..OMMWMMMMWMMO..",
    "..OMMEMMMMEMMO..",
    "..OMLLLLLLLLMO..",
    "...OLLLOOLLLO...",
    "...OMMMMMMMMO...",
    "..OMMLLLLLLMMO..",
    "..OMMLLLLLLMMO..",
    "...OOMMMMMMOO...",
    "....OO....OO....",
    "................",
)

private val WOLF_16 = listOf(
    "................",
    "..OO........OO..",
    ".OMMO......OMMO.",
    ".OMMMO....OMMMO.",
    ".OMMMMOOOOMMMMO.",
    "..OMMMMMMMMMMO..",
    "..OMMEMMMMEMMO..",
    "..OMMWMMMMWMMO..",
    "..OMLLLLLLLLMO..",
    "...OLLLOOLLLO...",
    "...OMMMMMMMMO...",
    "..OMMMLLLLMMMO..",
    "..OMMMLLLLMMMO..",
    "...OOMMMMMMOO...",
    "....OO....OO....",
    "................",
)

private val OWL_16 = listOf(
    "................",
    "...OO......OO...",
    "..OMMO....OMMO..",
    "..OMMMOOOOMMMO..",
    "..OMMMMMMMMMMO..",
    ".OMMWWMMMMWWMMO.",
    ".OMWEWMMMMWEWMO.",
    ".OMMWWMOOOWWMMO.",
    "..OMMMMWWMMMMO..",
    "..OMMLLLLLLMMO..",
    "..OMLMLMLMLMLO..",
    "..OMMLLLLLLMMO..",
    "...OMMMMMMMMO...",
    "...OOWO..OWOO...",
    "................",
    "................",
)

private val CAT_16 = listOf(
    "................",
    "..OO........OO..",
    ".OLLO......OLLO.",
    ".OMMMO....OMMMO.",
    ".OMMMMOOOOMMMMO.",
    "..OMMMMMMMMMMO..",
    "..OMLEMMMMELMO..",
    "..OMMMMMMMMMMO..",
    "..OMMMWOOOWMMO..",
    "...OMMMMMMMMO...",
    "...OMMLLLLMMO...",
    "..OMMMLLLLMMMO..",
    "...OMMMMMMMMO...",
    "....OO....OO....",
    "................",
    "................",
)

private val DRAGON_16 = listOf(
    "................",
    "..LL........LL..",
    "..OMMO....OMMO..",
    "..OMMMOOOOMMMO..",
    ".OOMMMMMMMMMMOO.",
    ".OMMWEMMMMEWMMO.",
    "..OMMMMMMMMMMO..",
    "..OMLLLLLLLLMO..",
    "...OMMMMMMMMO...",
    "..OMMMWWWWMMMO..",
    ".OMMMMWWWWMMMMO.",
    "..OMMMWWWWMMMO..",
    "...OOMMMMMMOO...",
    "....OO....OO....",
    "................",
    "................",
)

private val FROG_16 = listOf(
    "................",
    "...OOO....OOO...",
    "..OWEMO..OMEWO..",
    "..OMMMOOOOOMMO..",
    ".OMMMMMMMMMMMMO.",
    ".OMWMMMMMMMMWMO.",
    ".OMMMMMMMMMMMMO.",
    "..OMLLLLLLLLMO..",
    "..OMLLLLLLLLMO..",
    "...OMMMMMMMMO...",
    "..OMMLLLLLLMMO..",
    "..OOMMMMMMMMOO..",
    "...OO......OO...",
    "................",
    "................",
    "................",
)
