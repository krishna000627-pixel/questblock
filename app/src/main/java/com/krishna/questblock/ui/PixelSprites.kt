package com.krishna.questblock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class PetAnimState { IDLE, HAPPY, EATING, TRAINING, THINKING, SLEEPY }

/**
 * v296: Complex articulated digital pet engine (Tamagotchi / Digimon-inspired).
 * Features horizontal patrol wander, ground shadow scaling, ear twitch, wagging tail pixels,
 * breathing chest pulse, multi-frame blink, and rich particle effects for every state. Zero emojis.
 */
@Composable
fun PixelCompanionSprite(
    companionId: String,
    stageIndex: Int,
    happy: Boolean = false,
    animState: PetAnimState = if (happy) PetAnimState.HAPPY else PetAnimState.IDLE,
    showHabitat: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val effectiveState = if (happy && animState == PetAnimState.IDLE) PetAnimState.HAPPY else animState
    val inf = rememberInfiniteTransition(label = "dp_sprite")

    val speedMs = when (effectiveState) {
        PetAnimState.TRAINING -> 280
        PetAnimState.HAPPY -> 380
        PetAnimState.EATING -> 340
        PetAnimState.THINKING -> 680
        PetAnimState.SLEEPY -> 1800
        PetAnimState.IDLE -> 950
    }

    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(speedMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "dp_phase",
    )
    val wanderPhase by inf.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dp_wander",
    )
    val blinkPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
        label = "dp_blink",
    )

    val jumpScale by animateFloatAsState(
        targetValue = when (effectiveState) {
            PetAnimState.TRAINING -> 1.14f
            PetAnimState.HAPPY -> 1.10f
            PetAnimState.EATING -> 1.05f
            else -> 1f
        },
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "dp_scale",
    )
    val tiltDeg = when (effectiveState) {
        PetAnimState.TRAINING -> sin(phase * PI * 2.0).toFloat() * 12f
        PetAnimState.HAPPY -> (phase - 0.5f) * 8f
        PetAnimState.THINKING -> -6f + phase * 12f
        else -> 0f
    }

    val bobRows = if (!AnimConfig.master) 0 else when (effectiveState) {
        PetAnimState.TRAINING -> if (phase > 0.4f) -2 else 0
        PetAnimState.HAPPY -> if (phase > 0.45f) -2 else 0
        PetAnimState.EATING -> if (phase > 0.5f) 1 else 0
        PetAnimState.SLEEPY -> 1
        else -> if (phase > 0.55f) -1 else 0
    }
    val blinking = AnimConfig.master && (effectiveState == PetAnimState.SLEEPY || blinkPhase > 0.91f)
    val earTwitch = AnimConfig.master && (blinkPhase in 0.45f..0.54f || effectiveState == PetAnimState.HAPPY)
    val tailFrame = if (!AnimConfig.master) 0 else if (phase > 0.5f) 1 else 0

    val grid = when (companionId) {
        "wolf" -> WOLF_16
        "owl" -> OWL_16
        "cat" -> CAT_16
        "dragon" -> DRAGON_16
        "frog" -> FROG_16
        else -> KITSUNE_16
    }
    val palette = paletteFor(companionId, stageIndex, effectiveState == PetAnimState.HAPPY)

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = jumpScale
            scaleY = jumpScale
            rotationZ = tiltDeg
        },
    ) {
        val cols = 18
        val rows = 18
        val px = (size.minDimension / cols).coerceAtLeast(1f)
        val wanderPx = if (showHabitat && AnimConfig.master && effectiveState != PetAnimState.SLEEPY) {
            wanderPhase * (size.width * 0.14f)
        } else 0f
        val ox = (size.width - px * 16) / 2f + wanderPx
        val oy = (size.height - px * 16) / 2f

        // Optional digital pet habitat floor
        if (showHabitat) {
            val floorY = oy + 15.2f * px
            drawRoundRect(
                color = (palette['A'] ?: ColorPrimary).copy(alpha = 0.12f),
                topLeft = Offset(size.width * 0.08f, floorY),
                size = Size(size.width * 0.84f, px * 1.4f),
                cornerRadius = CornerRadius(px, px),
            )
        }

        // Dynamic ground shadow (shrinks when pet jumps)
        val shadowScale = (1f - abs(bobRows) * 0.12f).coerceIn(0.65f, 1f)
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(ox + 3f * px + (1f - shadowScale) * 5f * px, oy + 14.5f * px),
            size = Size(10f * px * shadowScale, 1.5f * px),
        )

        // Stage 2 & 3 evolution aura
        if (stageIndex >= 1) {
            val aura = palette['A'] ?: Color(0xFFFFD54F)
            drawCircle(
                color = aura.copy(alpha = if (stageIndex == 2) 0.26f else 0.14f),
                radius = size.minDimension * (0.38f + 0.04f * phase),
                center = Offset(ox + 8f * px, oy + 8f * px),
            )
            if (stageIndex == 2) {
                drawCircle(
                    color = aura.copy(alpha = 0.65f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(ox + 8f * px, oy + 8f * px),
                    style = Stroke(width = (px * 0.55f).coerceAtLeast(1.5f)),
                )
            }
        }

        // Draw 16x16 articulated pixel body
        for (r in 0 until 16) {
            val line = grid[r]
            for (c in 0 until 16) {
                val ch = line[c]
                if (ch == '.') continue
                val effCh = if (blinking && ch == 'E') 'M' else ch
                val col = palette[effCh] ?: continue
                val earOffset = if (earTwitch && r <= 3) -1 else 0
                val drawR = (r + bobRows + earOffset).coerceIn(0, 15)
                drawRect(
                    color = col,
                    topLeft = Offset(ox + c * px, oy + drawR * px),
                    size = Size(px + 0.4f, px + 0.4f),
                )
            }
        }

        // Articulated wagging tail pixels (right flank)
        if (companionId != "frog") {
            val tailCol = palette['M'] ?: Color.Gray
            val tipCol = palette['L'] ?: Color.White
            val tx = ox + (14.2f + tailFrame * 0.9f) * px
            val ty = oy + (10.5f + bobRows - tailFrame * 1.2f) * px
            drawRect(tailCol, Offset(tx, ty), Size(px * 1.5f, px * 1.3f))
            drawRect(tipCol, Offset(tx + px * 0.8f, ty - px * 0.6f), Size(px * 1.1f, px * 1.1f))
        }

        // State particles (zero emojis)
        if (AnimConfig.master) {
            when (effectiveState) {
                PetAnimState.HAPPY -> {
                    val hc = Color(0xFFFF6B9D)
                    val hy = oy + (1.5f - 2.2f * phase) * px
                    drawRect(hc, Offset(ox + 13 * px, hy), Size(px * 1.4f, px * 1.4f))
                    drawRect(hc, Offset(ox + 1.5f * px, hy + px), Size(px * 1.1f, px * 1.1f))
                }
                PetAnimState.TRAINING -> {
                    val lc = Color(0xFFFFD54F)
                    for (k in 0 until 3) {
                        val ang = phase * PI * 2.0 + k * (PI * 2.0 / 3.0)
                        val sx = ox + 8f * px + (6.5f * cos(ang)).toFloat() * px
                        val sy = oy + 8f * px + (6.5f * sin(ang)).toFloat() * px
                        drawRect(lc, Offset(sx, sy), Size(px * 1.2f, px * 1.2f))
                    }
                }
                PetAnimState.EATING -> {
                    val fc = Color(0xFFFFA623)
                    drawRect(fc, Offset(ox + 7 * px, oy + (10.5f - 1.5f * phase) * px), Size(px * 1.3f, px * 1.3f))
                    drawRect(fc.copy(alpha = 0.7f), Offset(ox + 9 * px, oy + (11.5f - phase) * px), Size(px * 0.9f, px * 0.9f))
                }
                PetAnimState.THINKING -> {
                    val tc = Color(0xFF50B5E9)
                    drawRect(tc, Offset(ox + 13.5f * px, oy + (3f - phase) * px), Size(px, px))
                    drawRect(tc, Offset(ox + 14.5f * px, oy + (1f - phase) * px), Size(px * 1.5f, px * 1.5f))
                }
                PetAnimState.SLEEPY -> {
                    val zc = Color(0xFFB8C0D9)
                    drawRect(zc, Offset(ox + 12.5f * px, oy + (3f - 2f * phase) * px), Size(px * 1.5f, px * 0.8f))
                    drawRect(zc.copy(alpha = 0.6f), Offset(ox + 14f * px, oy + (1.5f - 2f * phase) * px), Size(px * 1.1f, px * 0.6f))
                }
                PetAnimState.IDLE -> {
                    val spCol = palette['A'] ?: Color(0xFFFFD54F)
                    val s1x = ox + (2f + 11f * phase) * px
                    val s1y = oy + (2f + 2f * sin(phase * PI * 2.0).toFloat()) * px
                    drawRect(spCol.copy(alpha = 0.85f), Offset(s1x, s1y), Size(px * 0.9f, px * 0.9f))
                }
            }
        }

        // Evolution crown / crest
        if (stageIndex >= 1) {
            val crown = Color(0xFFFFD54F)
            val topY = oy + (0 + bobRows).coerceAtLeast(0) * px
            drawRect(crown, Offset(ox + 6 * px, topY), Size(px, px))
            drawRect(crown, Offset(ox + 9 * px, topY), Size(px, px))
            if (stageIndex == 2) {
                drawRect(crown, Offset(ox + 7 * px, topY), Size(px * 2, px))
                drawRect(Color.White, Offset(ox + 7.5f * px, topY), Size(px, px))
            }
        }
    }
}

private fun paletteFor(id: String, stage: Int, happy: Boolean): Map<Char, Color> {
    val eye = if (happy) Color(0xFFFF6B9D) else Color(0xFF1A1423)
    return when (id) {
        "wolf" -> mapOf('O' to Color(0xFF161122), 'M' to if (stage == 2) Color(0xFF8B6BFF) else Color(0xFF5D647B), 'L' to Color(0xFFB8C0D9), 'W' to Color(0xFFEAF0FF), 'E' to if (happy) Color(0xFFFF85B3) else Color(0xFFB388FF), 'A' to Color(0xFF9C6BFF))
        "owl" -> mapOf('O' to Color(0xFF1A1423), 'M' to if (stage == 2) Color(0xFF4A86C5) else Color(0xFF7B563E), 'L' to Color(0xFFE8D5B7), 'W' to Color(0xFFFFC857), 'E' to eye, 'A' to Color(0xFF4A86C5))
        "cat" -> mapOf('O' to Color(0xFF0A101E), 'M' to if (stage == 2) Color(0xFF1C2B4B) else Color(0xFF28324A), 'L' to Color(0xFF00E5FF), 'W' to Color(0xFFE0FBFF), 'E' to if (happy) Color(0xFFFF66C4) else Color(0xFF00E5FF), 'A' to Color(0xFF00E5FF))
        "dragon" -> mapOf('O' to Color(0xFF0E2416), 'M' to if (stage == 2) Color(0xFF2ECC71) else Color(0xFF4EA864), 'L' to Color(0xFFFFD166), 'W' to Color(0xFFDFF7E2), 'E' to eye, 'A' to Color(0xFF6BCB77))
        "frog" -> mapOf('O' to Color(0xFF172819), 'M' to if (stage == 2) Color(0xFF58B970) else Color(0xFF6B9E6E), 'L' to Color(0xFFD8F3DC), 'W' to Color(0xFFFF9EBB), 'E' to eye, 'A' to Color(0xFF6B8F71))
        else -> mapOf('O' to Color(0xFF2A140E), 'M' to if (stage == 2) Color(0xFFFF6B35) else Color(0xFFFF8243), 'L' to Color(0xFFFFE6C7), 'W' to Color(0xFFFFFFFF), 'E' to eye, 'A' to Color(0xFFFF7A59))
    }
}

private val KITSUNE_16 = listOf("................","..OO........OO..",".OMMO......OMMO.",".OMLMO....OMLMO.",".OMMMOOOOOOMMMO.","..OMMMMMMMMMMO..","..OMMWMMMMWMMO..","..OMMEMMMMEMMO..","..OMLLLLLLLLMO..","...OLLLOOLLLO...","...OMMMMMMMMO...","..OMMLLLLLLMMO..","..OMMLLLLLLMMO..","...OOMMMMMMOO...","....OO....OO....","................")
private val WOLF_16 = listOf("................","..OO........OO..",".OMMO......OMMO.",".OMMMO....OMMMO.",".OMMMMOOOOMMMMO.","..OMMMMMMMMMMO..","..OMMEMMMMEMMO..","..OMMWMMMMWMMO..","..OMLLLLLLLLMO..","...OLLLOOLLLO...","...OMMMMMMMMO...","..OMMMLLLLMMMO..","..OMMMLLLLMMMO..","...OOMMMMMMOO...","....OO....OO....","................")
private val OWL_16 = listOf("................","...OO......OO...","..OMMO....OMMO..","..OMMMOOOOMMMO..","..OMMMMMMMMMMO..",".OMMWWMMMMWWMMO.",".OMWEWMMMMWEWMO.",".OMMWWMOOOWWMMO.","..OMMMMWWMMMMO..","..OMMLLLLLLMMO..","..OMLMLMLMLMLO..","..OMMLLLLLLMMO..","...OMMMMMMMMO...","...OOWO..OWOO...","................","................")
private val CAT_16 = listOf("................","..OO........OO..",".OLLO......OLLO.",".OMMMO....OMMMO.",".OMMMMOOOOMMMMO.","..OMMMMMMMMMMO..","..OMLEMMMMELMO..","..OMMMMMMMMMMO..","..OMMMWOOOWMMO..","...OMMMMMMMMO...","...OMMLLLLMMO...","..OMMMLLLLMMMO..","...OMMMMMMMMO...","....OO....OO....","................","................")
private val DRAGON_16 = listOf("................","..LL........LL..","..OMMO....OMMO..","..OMMMOOOOMMMO..",".OOMMMMMMMMMMOO.",".OMMWEMMMMEWMMO.","..OMMMMMMMMMMO..","..OMLLLLLLLLMO..","...OMMMMMMMMO...",".OMMMMWWWWMMMMO.",".OMMMMWWWWMMMMO.","..OMMMWWWWMMMO..","...OOMMMMMMOO...","....OO....OO....","................","................")
private val FROG_16 = listOf("................","...OOO....OOO...","..OWEMO..OMEWO..","..OMMMOOOOOMMO..",".OMMMMMMMMMMMMO.",".OMWMMMMMMMMWMO.",".OMMMMMMMMMMMMO.","..OMLLLLLLLLMO..","..OMLLLLLLLLMO..","...OMMMMMMMMO...","..OMMLLLLLLMMO..","..OOMMMMMMMMOO..","...OO......OO...","................","................","................")
