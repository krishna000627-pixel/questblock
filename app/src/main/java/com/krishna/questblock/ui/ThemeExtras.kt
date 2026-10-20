package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.krishna.questblock.data.Flags
import com.krishna.questblock.util.SystemEvents
import kotlinx.coroutines.delay

/**
 * v240: what is special to each style theme, on top of its look (ThemeFx.kt). v241: each theme has its own flag fx_<theme>
 * (flags page and terminal: flags on|off fx_pixel ...) plus the master flag "themefx"; Ninja Scroll got its own flag fx_ninja in v248 (no words, only the look and effects). Words: rank names, the Undo bar text and the empty page texts.
 * Effects: System result window, Hashira mist puff, Monarch shadow army counter and burst, Gothic
 * halo and days left, Guild tilted notices and wax seal, Pixel coin pop and GAME OVER, Handheld boot screen, Blueprint APPROVED stamp and dimension lines.
 * #svg-logo  Every new theme gets its entry in WORDS and in QuestFx below.
 */
object ThemeExtras {
    /** v241: the extras of the theme in use. Off when the master flag or that theme's own flag (fx_<theme>) is off. v248: Ninja Scroll has fx_ninja too. */
    val on: Boolean get() = FxOn && Flags.get("themefx") && Flags.get("fx_" + FxNow.name.lowercase())
    var booted = false

    private val WORDS: Map<Fx, Map<String, String>> = mapOf(
        Fx.NINJA to mapOf("locked" to "Sealed"),
        Fx.SYSTEM to mapOf("locked" to "[ ACCESS DENIED ]", "done" to "[SYSTEM] QUEST CLEARED", "emptyQuests" to "NO ACTIVE QUESTS", "emptyInv" to "INVENTORY EMPTY"),
        Fx.HASHIRA to mapOf("locked" to "The mist blocks the way", "easy" to "Form 1", "medium" to "Form 2", "hard" to "Form 3", "done" to "Total concentration", "emptyQuests" to "Still as mist. No quests.", "emptyInv" to "Nothing in the mist"),
        Fx.MONARCH to mapOf("locked" to "THE GATE IS CLOSED", "easy" to "Soldier", "medium" to "Knight", "hard" to "Elite", "done" to "SHADOW EXTRACTED", "emptyQuests" to "The throne is quiet.", "emptyInv" to "No spoils yet"),
        Fx.GOTHIC to mapOf("locked" to "The gate is shut", "easy" to "Rite I", "medium" to "Rite II", "hard" to "Rite III", "done" to "So it is written", "emptyQuests" to "Rest, then rise.", "emptyInv" to "The reliquary is empty"),
        Fx.GUILD to mapOf("locked" to "Closed to adventurers", "easy" to "Copper", "medium" to "Silver", "hard" to "Gold", "done" to "Reward collected", "emptyQuests" to "The board is bare.", "emptyInv" to "The vault is empty"),
        Fx.PIXEL to mapOf("locked" to "LOCKED - INSERT COIN", "easy" to "STAGE 1", "medium" to "STAGE 2", "hard" to "STAGE 3", "done" to "STAGE CLEAR!", "emptyQuests" to "PRESS START", "emptyInv" to "NO ITEMS"),
        Fx.HANDHELD to mapOf("locked" to "NO CARTRIDGE", "easy" to "EASY", "medium" to "NORMAL", "hard" to "HARD", "done" to "SAVING...", "emptyQuests" to "NO DATA", "emptyInv" to "SLOT EMPTY"),
        Fx.BLUEPRINT to mapOf("locked" to "ACCESS RESTRICTED", "easy" to "TYPE A", "medium" to "TYPE B", "hard" to "TYPE C", "done" to "APPROVED", "emptyQuests" to "SHEET EMPTY", "emptyInv" to "STORE EMPTY"),
    )

    fun word(key: String, default: String): String = if (!on) default else WORDS[FxNow]?.get(key) ?: default
    fun diff(d: Int, default: String): String = word(listOf("easy", "medium", "hard").getOrElse(d - 1) { "easy" }, default)

    fun daysLeft(): Int {
        val n = java.time.LocalDate.now()
        return java.time.temporal.ChronoUnit.DAYS.between(n, java.time.LocalDate.of(n.year, 12, 31)).toInt()
    }
}

@Composable
internal fun CenterPopup(content: @Composable () -> Unit) {
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
        },
        properties = PopupProperties(focusable = false),
        content = content,
    )
}

/** Shows each style theme's own effect after a quest, the shadow army counter, GAME OVER and the Handheld boot screen. */
@Composable
fun ThemeExtrasHost() {
    val fx = FxNow
    val ctx = LocalContext.current
    val startQ = remember { SystemEvents.questDoneStamp }
    val startP = remember { SystemEvents.penaltyStamp }
    var show by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    var gameOver by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    var boot by remember { mutableStateOf(fx == Fx.HANDHELD && !ThemeExtras.booted) }
    LaunchedEffect(fx) {
        if (fx == Fx.MONARCH) SystemEvents.armyCount = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getInt("shadow_army", 0)
    }
    LaunchedEffect(SystemEvents.questDoneStamp) {
        if (SystemEvents.questDoneStamp != startQ) {
            if (fx == Fx.MONARCH) {
                SystemEvents.armyCount = SystemEvents.armyCount + 1
                ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putInt("shadow_army", SystemEvents.armyCount).apply()
            }
            show = true
            delay(1500)
            show = false
        }
    }
    LaunchedEffect(SystemEvents.penaltyStamp) {
        if (fx == Fx.PIXEL && SystemEvents.penaltyStamp != startP) { gameOver = true; delay(1800); gameOver = false }
    }
    LevelUpHost(fx)
    if (boot) BootScreen { boot = false; ThemeExtras.booted = true }
    if (show) CenterPopup { QuestFx(fx, SystemEvents.lastCoins, SystemEvents.lastXp) }
    if (gameOver) CenterPopup {
        Text("GAME OVER", color = ColorDanger, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp)
    }
}

@Composable
private fun BootScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1600); onDone() }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(ColorBg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QUESTBOY", color = ColorText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                TypedText("LOADING...", color = ColorMuted, fontSize = 12.sp)
            }
        }
    }
}

/** v248: the quest done pop, small and the same shape in every theme: the theme word (ThemeExtras.WORDS "done") plus "+XP, +coins". Ninja has no word list, so it says DONE. */
@Composable
private fun QuestFx(fx: Fx, coins: Int, xp: Int) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1300, easing = LinearEasing)) }
    val v = p.value
    val a = if (v > 0.8f) (1f - v) / 0.2f else 1f
    val pop = 1f + 0.5f * (1f - minOf(1f, v * 6f))
    val c = ColorPrimary
    val ink = ColorText
    val word = if (fx == Fx.NINJA) "DONE" else ThemeExtras.word("done", "Quest done")
    val reward = "+$xp XP  +$coins coins"
    val m = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }
    when (fx) {
        Fx.SYSTEM -> PopText(m.border(1.5.dp, c, SYS_SHAPE).background(ColorBg.copy(alpha = 0.94f), SYS_SHAPE).padding(horizontal = 16.dp, vertical = 10.dp), word, reward, c, ColorCoin)
        Fx.NINJA -> {
            val sh = RoundedCornerShape(2.dp)
            Row(m.border(2.dp, ink, sh).background(ColorBg.copy(alpha = 0.96f), sh).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HankoSeal(28.dp, c)
                Spacer(Modifier.width(12.dp))
                PopText(Modifier, word, reward, ink, c)
            }
        }
        Fx.HASHIRA -> {
            val sh = RoundedCornerShape(50)
            PopText(m.border(1.dp, c.copy(alpha = 0.7f), sh).background(ColorSurface.copy(alpha = 0.94f), sh).padding(horizontal = 24.dp, vertical = 10.dp), word, reward, ink, c)
        }
        Fx.MONARCH -> {
            val sh = CutCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 10.dp)
            PopText(m.border(1.5.dp, c, sh).background(ColorBg.copy(alpha = 0.94f), sh).padding(horizontal = 18.dp, vertical = 10.dp), word, reward, c, ink)
        }
        Fx.GOTHIC -> PopText(
            m.border(1.dp, ink.copy(alpha = 0.8f)).background(ColorBg.copy(alpha = 0.95f)).padding(3.dp).border(1.dp, ink.copy(alpha = 0.5f)).padding(horizontal = 18.dp, vertical = 8.dp),
            word, reward, ink, c)
        Fx.GUILD -> {
            val sh = RoundedCornerShape(6.dp)
            PopText(m.border(2.dp, ink.copy(alpha = 0.5f), sh).background(ColorCard, sh).padding(horizontal = 18.dp, vertical = 10.dp), word, reward, ink, c)
        }
        Fx.PIXEL -> {
            val sh = CutCornerShape(4.dp)
            val shadow = ink.copy(alpha = 0.85f)
            PopText(
                m.drawBehind { drawRect(shadow, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
                    .border(2.dp, ink, sh).background(ColorBg, sh).padding(horizontal = 16.dp, vertical = 8.dp),
                word, reward, c, ink)
        }
        Fx.HANDHELD -> PopText(m.border(3.dp, ink).background(ColorBg).padding(horizontal = 18.dp, vertical = 8.dp), word, reward, ink, ink)
        Fx.BLUEPRINT -> PopText(
            Modifier.graphicsLayer { rotationZ = -6f; scaleX = pop; scaleY = pop; alpha = a }.border(2.dp, c).padding(horizontal = 16.dp, vertical = 6.dp),
            word, reward, c, ink)
        Fx.NONE -> Unit
    }
}

@Composable
private fun PopText(modifier: Modifier, word: String, reward: String, wordColor: Color, rewardColor: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word, color = wordColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
        Text(reward, color = rewardColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** v244: a full-screen level up for every style theme except System (own screen in SystemStyle.kt). v248: Ninja has one too (red seal stamp). Behind that theme's fx_<theme> flag, like the rest. */
@Composable
private fun LevelUpHost(fx: Fx) {
    if (fx == Fx.NONE || fx == Fx.SYSTEM) return
    val start = remember { SystemEvents.levelUpStamp }
    var show by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    LaunchedEffect(SystemEvents.levelUpStamp) {
        if (SystemEvents.levelUpStamp != start) { show = true; delay(2400); show = false }
    }
    if (!show) return
    Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) { LevelUpFx(fx) }
}

@Composable
private fun LevelUpFx(fx: Fx) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val v = p.value
    val a = if (v > 0.85f) (1f - v) / 0.15f else 1f
    val pop = 1f + 0.8f * (1f - minOf(1f, v * 5f))
    val c = ColorPrimary
    val dark = fx == Fx.MONARCH || fx == Fx.HASHIRA || fx == Fx.GOTHIC || fx == Fx.PIXEL
    val veil = if (dark) Color.Black.copy(alpha = 0.8f * a) else ColorBg.copy(alpha = 0.94f * a)
    Box(Modifier.fillMaxSize().background(veil), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (fx) {
                Fx.MONARCH -> for (k in 0..2) drawCircle(c.copy(alpha = 0.6f * a * (1f - k * 0.25f)), 30.dp.toPx() + (140.dp.toPx() + k * 40.dp.toPx()) * v, center, style = Stroke(3.dp.toPx()))
                Fx.GOTHIC -> {
                    drawCircle(c.copy(alpha = a), 70.dp.toPx() + 20.dp.toPx() * v, Offset2(center.x, center.y - 50.dp.toPx()), style = Stroke(3.dp.toPx()))
                    drawCircle(c.copy(alpha = 0.5f * a), 50.dp.toPx() + 14.dp.toPx() * v, Offset2(center.x, center.y - 50.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
                Fx.HASHIRA -> for (k in 0..2) {
                    val r = size.minDimension * (0.3f + 0.5f * v) * (1f - 0.18f * k)
                    val o = Offset2(center.x + (k - 1) * 70f * v, center.y - k * 30f * v)
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * a), Color.Transparent), o, r), r, o)
                }
                Fx.PIXEL -> {
                    val cols = listOf(c, ColorCoin, ColorAccent, Color.White)
                    for (i in 0 until 24) {
                        val x = size.width * (((i * 37 + 11) % 100) / 100f)
                        val y0 = -(((i * 53) % 40) / 100f) * size.height
                        val y = y0 + v * (1.3f + (i % 4) * 0.2f) * size.height
                        if (y >= 0f && y <= size.height) drawRect(cols[i % 4].copy(alpha = a), Offset2(x, y), androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()))
                    }
                }
                else -> Unit
            }
        }
        when (fx) {
            Fx.MONARCH -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }) {
                Text("ARISE", color = c, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                Text("YOU GREW STRONGER", color = Color.White, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Fx.HASHIRA -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = a; translationY = (1f - minOf(1f, v / 0.35f)) * 80f }) {
                Text("LEVEL UP", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light, letterSpacing = 6.sp)
                Text("Next form unlocked", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Fx.GOTHIC -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 120.dp).graphicsLayer { alpha = a }) {
                Text("ASCENDED", color = Color.White, fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                Text("So it is written", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = FontFamily.Serif, modifier = Modifier.padding(top = 8.dp))
            }
            Fx.GUILD -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }) {
                Box(
                    Modifier.size(150.dp).background(Color(0xFF9B1C1C), CircleShape).border(4.dp, Color(0xFF6E1010), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Canvas(Modifier.size(80.dp)) { star(center, 38.dp.toPx(), 15.dp.toPx(), Color(0xFFFFE0D0)) } }
                Text("RANK UP", color = ColorText, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, letterSpacing = 3.sp, modifier = Modifier.padding(top = 16.dp))
            }
            Fx.PIXEL -> Text(
                "LEVEL UP!", color = c, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 3.sp,
                modifier = Modifier.graphicsLayer { alpha = a * (if ((v * 12).toInt() % 2 == 0) 1f else 0.35f); translationY = -40f * v },
            )
            Fx.HANDHELD -> Box(Modifier.graphicsLayer { alpha = a }.border(4.dp, ColorText).background(ColorBg).padding(horizontal = 26.dp, vertical = 16.dp)) {
                Text("LEVEL UP", color = ColorText, fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    modifier = Modifier.graphicsLayer { alpha = if ((v * 10).toInt() % 2 == 0) 1f else 0f })
            }
            Fx.BLUEPRINT -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { rotationZ = -10f; scaleX = pop; scaleY = pop; alpha = a }) {
                Box(Modifier.border(3.dp, c).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("LEVEL UP", color = c, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                }
                Text("REVISION ACCEPTED", color = c, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Fx.NINJA -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { alpha = a }) {
                val st = 1f + 1.4f * (1f - minOf(1f, v / 0.22f))   // the seal comes down big and lands at full size
                Box(Modifier.graphicsLayer { scaleX = st; scaleY = st; alpha = minOf(1f, v / 0.12f) }) { HankoSeal(150.dp, c) }
                Text("Level ${SystemEvents.lastLevel}", color = ColorText, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    modifier = Modifier.padding(top = 20.dp).graphicsLayer { alpha = ((v - 0.25f) / 0.15f).coerceIn(0f, 1f) })
            }
            else -> Unit
        }
    }
}

/** v248: filled five point star around [ctr] (the Guild wax seal). */
private fun DrawScope.star(ctr: Offset, outer: Float, inner: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val ang = Math.toRadians((-90 + i * 36).toDouble())
        val x = ctr.x + r * Math.cos(ang).toFloat(); val y = ctr.y + r * Math.sin(ang).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun Offset2(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
