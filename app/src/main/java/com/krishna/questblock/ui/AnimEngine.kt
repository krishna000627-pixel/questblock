package com.krishna.questblock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.Flags
import com.krishna.questblock.util.Sounds
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * v292: Central animation & sensory engine.
 * Every effect is gated by `anim_master` and its own granular toggle in `Flags`.
 */
object AnimConfig {
    val master: Boolean get() = Flags.get("anim_master")
    val transitions: Boolean get() = master && Flags.get("anim_transitions")
    val stagger: Boolean get() = master && Flags.get("anim_stagger")
    val press: Boolean get() = master && Flags.get("anim_press")
    val counters: Boolean get() = master && Flags.get("anim_counters")
    val xpShimmer: Boolean get() = master && Flags.get("anim_xp_shimmer")
    val holdFx: Boolean get() = master && Flags.get("anim_hold_fx")
    val ambientBg: Boolean get() = master && Flags.get("anim_ambient_bg")
    val combo: Boolean get() = master && Flags.get("anim_combo")
    val emptyStates: Boolean get() = master && Flags.get("anim_empty_states")
}

/** Springy press-scale physics + optional UI tap sound. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    playSound: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && AnimConfig.press && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bounce_click",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
            onClick = {
                if (playSound) Sounds.uiTap()
                onClick()
            },
        )
}

/** Staggered slide + fade entrance for list cards and sections. */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    if (!AnimConfig.stagger) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        val delayMs = (index.coerceAtMost(8) * 32L)
        if (delayMs > 0) delay(delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        )
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22f
    }
}

/** Rolling count-up text for Coins, XP, and Streaks. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = ColorText,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = if (AnimConfig.counters) tween(520, easing = FastOutSlowInEasing) else tween(0),
        label = "counter",
    )
    Text(
        text = "$prefix$shown$suffix",
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
    )
}

/** Traveling light & spark sweep across XP bars. */
@Composable
fun XpShimmerOverlay(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (!AnimConfig.xpShimmer || fraction <= 0.02f) return
    val inf = rememberInfiniteTransition(label = "xp_shimmer")
    val sweep by inf.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "xp_sweep",
    )
    Canvas(modifier = modifier) {
        val fillW = (size.width * fraction.coerceIn(0f, 1f))
        if (fillW <= 2f) return@Canvas
        val bandW = (fillW * 0.35f).coerceAtLeast(18f)
        val cx = sweep * fillW
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), color.copy(alpha = 0.65f), Color.Transparent),
                startX = cx - bandW,
                endX = cx + bandW,
            ),
            size = androidx.compose.ui.geometry.Size(fillW, size.height),
        )
    }
}

/** Floating combo badge when multiple quests are cleared back-to-back. */
@Composable
fun ComboHost() {
    if (!AnimConfig.combo) return
    var comboCount by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Sounds.comboEvents.collect { count ->
            comboCount = count
            visible = true
            delay(1900L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && comboCount >= 2,
        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(240)) + scaleOut(tween(200), targetScale = 0.85f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ColorPrimary.copy(alpha = 0.92f), ColorCoin.copy(alpha = 0.92f))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("\u26A1", fontSize = 16.sp)
                Text(
                    text = "${comboCount}x QUEST COMBO!",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Radial charge glow & escalating micro-pulse around the Hold-to-Unlock control. */
@Composable
fun HoldUnlockHalo(
    progress: Float,
    isHolding: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (!AnimConfig.holdFx || (!isHolding && progress <= 0.01f)) return
    val p = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.42f
        val ringR = baseR * (0.95f + 0.14f * p)
        // Outer radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.28f * p), Color.Transparent),
                center = Offset(cx, cy),
                radius = ringR * 1.45f,
            ),
            radius = ringR * 1.45f,
            center = Offset(cx, cy),
        )
        // Progress arc
        drawArc(
            color = accent.copy(alpha = 0.25f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx()),
        )
        drawArc(
            color = accent,
            startAngle = -90f,
            sweepAngle = 360f * p,
            useCenter = false,
            style = Stroke(width = 5.dp.toPx()),
        )
        // Orbiting sparks as charge builds
        if (p > 0.15f) {
            val sparks = 6
            for (i in 0 until sparks) {
                val ang = Math.toRadians((-90.0 + p * 360.0 + i * (360.0 / sparks)))
                val sx = cx + (ringR * cos(ang)).toFloat()
                val sy = cy + (ringR * sin(ang)).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f * p),
                    radius = (2.5f + 2.5f * p).dp.toPx(),
                    center = Offset(sx, sy),
                )
            }
        }
    }
}

/** Interactive Animation & Sound Studio card for Flags > Theme effects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundAndAnimStudioCard() {
    var demoCounter by remember { mutableIntStateOf(120) }
    var demoXp by remember { mutableIntStateOf(64) }
    var comboStep by remember { mutableIntStateOf(2) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Animation & Sound Studio", fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Tap any chip below to test the new theme-matched sounds & motion", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                AnimatedCounter(
                    value = demoCounter,
                    prefix = "\uD83E\uDE99 ",
                    color = ColorCoin,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Live XP Shimmer demo bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ColorSurfaceHigh),
            ) {
                val frac = (demoXp / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(10.dp)
                        .background(ColorPrimary),
                )
                XpShimmerOverlay(fraction = frac, color = ColorPrimary, modifier = Modifier.fillMaxWidth().height(10.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val buttons: List<Pair<String, () -> Unit>> = listOf(
                    "Tap" to { Sounds.uiTap() },
                    "Tab" to { Sounds.uiTab() },
                    "Toggle" to { Sounds.uiToggle(true) },
                    "Open" to { Sounds.uiOpen() },
                    "Hold" to { Sounds.holdTick(0.75f) },
                    "Unlock" to { Sounds.unlock() },
                    "Denied" to { Sounds.denied() },
                    "Undo" to { Sounds.undo() },
                    "Coin +" to { demoCounter += 15; demoXp = (demoXp + 18) % 100; Sounds.coin() },
                    "Combo ${comboStep}x" to { Sounds.combo(comboStep); Sounds.comboEvents.tryEmit(comboStep); comboStep = if (comboStep >= 5) 2 else comboStep + 1 },
                    "Level Up" to { Sounds.levelUp() },
                    "Streak" to { Sounds.streak() },
                    "Shop Buy" to { Sounds.shopBuy() },
                    "Pet Happy" to { Sounds.petHappy() },
                    "Pet Evolve" to { Sounds.petEvolve() },
                    "Focus Start" to { Sounds.focusStart() },
                    "Drawer" to { Sounds.drawerOpen() },
                )
                buttons.forEach { (label, action) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ColorSurface)
                            .border(1.dp, ColorDivider, RoundedCornerShape(10.dp))
                            .bounceClick(playSound = false, onClick = action)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = ColorText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
