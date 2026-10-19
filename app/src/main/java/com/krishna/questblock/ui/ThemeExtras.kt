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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * v240: what is special to each style theme, on top of its look (ThemeFx.kt). All of it is behind the flag "themefx"
 * (flags page and terminal: flags on|off themefx). Words: rank names, the Undo bar text and the empty page texts.
 * Effects: System result window, Ninja hanko stamp, Hashira mist puff, Monarch shadow army counter and burst, Gothic
 * halo and days left, Guild tilted notices and wax seal, Pixel coin pop and GAME OVER, Handheld boot screen, Blueprint APPROVED stamp and dimension lines.
 * #svg-logo  Every new theme gets its entry in WORDS and in QuestFx below.
 */
object ThemeExtras {
    val on: Boolean get() = FxOn && Flags.get("themefx")
    var booted = false

    private val WORDS: Map<Fx, Map<String, String>> = mapOf(
        Fx.SYSTEM to mapOf("done" to "[SYSTEM] QUEST CLEARED", "emptyQuests" to "NO ACTIVE QUESTS", "emptyInv" to "INVENTORY EMPTY"),
        Fx.NINJA to mapOf("easy" to "Genin", "medium" to "Chunin", "hard" to "Jonin", "done" to "Scroll sealed", "emptyQuests" to "No missions, shinobi.", "emptyInv" to "Your pouch is empty"),
        Fx.HASHIRA to mapOf("easy" to "Form 1", "medium" to "Form 2", "hard" to "Form 3", "done" to "Total concentration", "emptyQuests" to "Still as mist. No quests.", "emptyInv" to "Nothing in the mist"),
        Fx.MONARCH to mapOf("easy" to "Soldier", "medium" to "Knight", "hard" to "Elite", "done" to "SHADOW EXTRACTED", "emptyQuests" to "The throne is quiet.", "emptyInv" to "No spoils yet"),
        Fx.GOTHIC to mapOf("easy" to "Rite I", "medium" to "Rite II", "hard" to "Rite III", "done" to "So it is written", "emptyQuests" to "Rest, then rise.", "emptyInv" to "The reliquary is empty"),
        Fx.GUILD to mapOf("easy" to "Copper", "medium" to "Silver", "hard" to "Gold", "done" to "Reward collected", "emptyQuests" to "The board is bare.", "emptyInv" to "The vault is empty"),
        Fx.PIXEL to mapOf("easy" to "STAGE 1", "medium" to "STAGE 2", "hard" to "STAGE 3", "done" to "STAGE CLEAR!", "emptyQuests" to "PRESS START", "emptyInv" to "NO ITEMS"),
        Fx.HANDHELD to mapOf("easy" to "EASY", "medium" to "NORMAL", "hard" to "HARD", "done" to "SAVING...", "emptyQuests" to "NO DATA", "emptyInv" to "SLOT EMPTY"),
        Fx.BLUEPRINT to mapOf("easy" to "TYPE A", "medium" to "TYPE B", "hard" to "TYPE C", "done" to "APPROVED", "emptyQuests" to "SHEET EMPTY", "emptyInv" to "STORE EMPTY"),
    )

    fun word(key: String, default: String): String = if (!on) default else WORDS[FxNow]?.get(key) ?: default
    fun diff(d: Int, default: String): String = word(listOf("easy", "medium", "hard").getOrElse(d - 1) { "easy" }, default)

    fun daysLeft(): Int {
        val n = java.time.LocalDate.now()
        return java.time.temporal.ChronoUnit.DAYS.between(n, java.time.LocalDate.of(n.year, 12, 31)).toInt()
    }
}

@Composable
private fun CenterPopup(content: @Composable () -> Unit) {
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
    var show by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
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

@Composable
private fun QuestFx(fx: Fx, coins: Int, xp: Int) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1300, easing = LinearEasing)) }
    val v = p.value
    val a = if (v > 0.8f) (1f - v) / 0.2f else 1f
    val pop = 1f + 1.2f * (1f - minOf(1f, v * 6f))
    val c = ColorPrimary
    when (fx) {
        Fx.SYSTEM -> Column(
            Modifier.graphicsLayer { alpha = a }.border(2.dp, c, SYS_SHAPE).background(ColorBg.copy(alpha = 0.94f), SYS_SHAPE).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("[ QUEST CLEARED ]", color = c, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
            Text("REWARD ACQUIRED", color = ColorText, fontSize = 12.sp)
            Text("+$coins COINS   +$xp XP", color = ColorCoin, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Fx.NINJA -> Box(
            Modifier.graphicsLayer { scaleX = pop; scaleY = pop; rotationZ = -8f; alpha = a }
                .border(4.dp, c, RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("DONE", color = c, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif) }
        Fx.HASHIRA -> Canvas(Modifier.size(300.dp)) {
            val ctr = center
            for (k in 0..2) {
                val r = size.minDimension / 2 * (0.25f + 0.75f * v) * (1f - 0.2f * k)
                val o = Offset2(ctr.x + (k - 1) * 40f * v, ctr.y - k * 20f * v)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.45f * a), Color.Transparent), o, r), r, o)
            }
        }
        Fx.MONARCH -> Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(c.copy(alpha = a), 20.dp.toPx() + 120.dp.toPx() * v, center, style = Stroke(3.dp.toPx())) }
            Text("+1 SHADOW", color = c, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.graphicsLayer { alpha = a })
        }
        Fx.GOTHIC -> Canvas(Modifier.size(300.dp)) {
            drawCircle(c.copy(alpha = a), 30.dp.toPx() + 90.dp.toPx() * v, center, style = Stroke(3.dp.toPx()))
            drawCircle(c.copy(alpha = 0.5f * a), 21.dp.toPx() + 63.dp.toPx() * v, center, style = Stroke(1.5.dp.toPx()))
        }
        Fx.GUILD -> Box(
            Modifier.size(88.dp).graphicsLayer { scaleX = 1f + 0.8f * (1f - minOf(1f, v * 5f)); scaleY = 1f + 0.8f * (1f - minOf(1f, v * 5f)); alpha = a }
                .background(Color(0xFF9B1C1C), CircleShape).border(3.dp, Color(0xFF6E1010), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("SEALED", color = Color(0xFFFFE0D0), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Serif) }
        Fx.PIXEL -> Text(
            "+$coins", color = c, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.graphicsLayer { translationY = -120f * v; alpha = a },
        )
        Fx.HANDHELD -> Box(Modifier.graphicsLayer { alpha = a }.border(3.dp, ColorText).background(ColorBg).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("SAVING...", color = ColorText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Fx.BLUEPRINT -> Box(
            Modifier.graphicsLayer { rotationZ = -12f; scaleX = pop; scaleY = pop; alpha = a }.border(3.dp, c).padding(horizontal = 16.dp, vertical = 6.dp),
        ) { Text("APPROVED", color = c, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp) }
        Fx.NONE -> Unit
    }
}

private fun Offset2(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
