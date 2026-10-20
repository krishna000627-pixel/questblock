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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.krishna.questblock.util.SystemEvents
import kotlinx.coroutines.delay

/**
 * v239: things only the System theme does, on top of its look (ThemeFx.kt):
 * typed [SYSTEM] messages with a haptic tick, a full-screen LEVEL UP, a red penalty frame after a broken Focus session,
 * a once-a-day DAILY QUEST popup, and quest ranks E to S on the quest cards (QuestsScreen.kt).
 */

/** Text that types itself out letter by letter, with a faint haptic tick. */
@Composable
fun TypedText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified) {
    var shown by remember(text) { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(text) {
        shown = 0
        for (i in 1..text.length) {
            shown = i
            if (i % 3 == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(32)
        }
    }
    Text(text.take(shown), modifier = modifier, color = color, fontSize = fontSize, letterSpacing = 1.sp)
}

/** Rank letter of a quest by its difficulty: Easy E, Medium C, Hard A, and S for a Hard Timer Quest. */
fun questRank(difficulty: Int, timer: Boolean): String = when {
    difficulty >= 3 && timer -> "S"
    difficulty >= 3 -> "A"
    difficulty == 2 -> "C"
    else -> "E"
}

/** Everything the System theme adds on top of the page: the level-up screen, the penalty timer and the daily quest popup. */
@Composable
fun SystemHost(strict: Boolean, onOpenQuests: () -> Unit) {
    if (ThemeExtras.on) LevelUpOverlay()
    LaunchedEffect(SystemEvents.penaltyOn) {
        if (SystemEvents.penaltyOn) { delay(90_000L); SystemEvents.penaltyOn = false }
    }
    DailyQuestPopup(strict, onOpenQuests)
}

@Composable
private fun LevelUpOverlay() {
    val start = remember { SystemEvents.levelUpStamp }
    var show by remember(SystemEvents.cutStamp) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(SystemEvents.levelUpStamp) {
        if (SystemEvents.levelUpStamp != start) {
            show = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(2200)
            show = false
        }
    }
    if (!show) return
    Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // v248: four corner brackets pop in from far out, "[ LEVEL UP ]" and "LV 4 -> 5", about 2 s, nothing moves after the pop
        val s = remember { Animatable(0f) }
        LaunchedEffect(Unit) { s.animateTo(1f, tween(2000, easing = LinearEasing)) }
        val v = s.value
        val pop = 1f + 1.2f * (1f - minOf(1f, v / 0.2f))
        val a = if (v > 0.85f) (1f - v) / 0.15f else 1f
        val c = ColorPrimary
        val lv = SystemEvents.lastLevel
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f * a)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(300.dp, 170.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }) {
                val l = 28.dp.toPx(); val w = 3.dp.toPx(); val h = w / 2
                drawLine(c, Offset(0f, h), Offset(l, h), w); drawLine(c, Offset(h, 0f), Offset(h, l), w)
                drawLine(c, Offset(size.width - l, h), Offset(size.width, h), w); drawLine(c, Offset(size.width - h, 0f), Offset(size.width - h, l), w)
                drawLine(c, Offset(0f, size.height - h), Offset(l, size.height - h), w); drawLine(c, Offset(h, size.height - l), Offset(h, size.height), w)
                drawLine(c, Offset(size.width - l, size.height - h), Offset(size.width, size.height - h), w); drawLine(c, Offset(size.width - h, size.height - l), Offset(size.width - h, size.height), w)
            }
            Column(Modifier.graphicsLayer { scaleX = pop; scaleY = pop; alpha = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("[ LEVEL UP ]", color = c, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                Spacer(Modifier.height(10.dp))
                Text("LV ${lv - 1} \u2192 $lv", color = ColorText, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun DailyQuestPopup(strict: Boolean, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(strict) {
        if (strict) return@LaunchedEffect
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        if (sp.getLong("system_daily_day", -1L) != java.time.LocalDate.now().toEpochDay()) show = true
    }
    if (!show) return
    val close = {
        show = false
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putLong("system_daily_day", java.time.LocalDate.now().toEpochDay()).apply()
    }
    AlertDialog(
        onDismissRequest = close, shape = SYS_SHAPE, containerColor = ColorBg,
        title = { Text("[ DAILY QUEST ]", color = ColorPrimary, fontWeight = FontWeight.Bold) },
        text = { TypedText("A NEW DAY HAS BEGUN.\nYOUR QUESTS AWAIT. DO NOT FALL BEHIND.", color = ColorText, fontSize = 13.sp) },
        confirmButton = { Button(onClick = { close(); onOpen() }) { Text("OPEN QUESTS") } },
        dismissButton = { Button(onClick = close) { Text("LATER") } },
    )
}
