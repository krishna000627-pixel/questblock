@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs   = repo.focusLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Session state lives in the database (Player.focusModeUntil), so it survives navigation and app kills.
    init { settle() }

    fun bonus(): Int = repo.focusBonus()
    fun breakUntil(): Long = repo.breakUntil()
    fun keepAwake(): Boolean = repo.keepScreenAwakeInFocus()
    fun breakIsLong(): Boolean = repo.breakIsLong()
    fun skipBreak() = repo.skipBreak()
    fun start(durationMs: Long, quest: TaskEntity?) = viewModelScope.launch { repo.startFocus(durationMs, quest) }
    fun end()    = viewModelScope.launch { repo.abandonFocus() }
    fun penalty(): Int = repo.focusPenalty()
    fun settle() = viewModelScope.launch { repo.settleFocus() }
}

private val DURATIONS = listOf(15, 25, 30, 45, 60, 90)

@Composable
fun FocusModeScreen(vm: FocusViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks  by vm.tasks.collectAsStateWithLifecycle()
    val logs   by vm.logs.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }

    val remaining = (player.focusModeUntil - now).coerceAtLeast(0L)
    val running   = player.focusModeUntil > now
    // Timer hit zero (or app was reopened after it expired) -> pay out once.
    LaunchedEffect(player.focusModeUntil, running) {
        if (player.focusModeUntil > 0L && !running) vm.settle()
    }

    // optional: keep the screen on while a Focus session runs and this page is open
    val view = androidx.compose.ui.platform.LocalView.current
    val awake = running && vm.keepAwake()
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    // background sound: plays while a session runs, stops when it ends or when "Off" is picked
    var ambChoice by remember { mutableStateOf(com.krishna.questblock.util.Sounds.ambient) }
    LaunchedEffect(running, player.focusModeUntil, ambChoice) {
        if (running) com.krishna.questblock.util.Sounds.startAmbient(player.focusModeUntil)
        else com.krishna.questblock.util.Sounds.stopAmbient()
    }

    val timerQuests = tasks.filter { it.isTimerQuest && it.isAvailableNow(now) }
    val activeQuest = timerQuests.firstOrNull { it.id == player.focusQuestId }
        ?: tasks.firstOrNull { it.id == player.focusQuestId && player.focusQuestId > 0 }

    Column(
        Modifier.fillMaxSize().background(ColorBg).verticalScroll(rememberScrollState()).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TitleWithIcon("\u26A1", "Focus", Modifier.fillMaxWidth())

        if (running) {
            ActiveFocus(
                remainingMs = remaining, totalMs = player.focusTotalMs,
                questTitle = activeQuest?.title,
                appCount = activeQuest?.linkedApps?.split(",")?.count { it.isNotBlank() } ?: 0,
                penalty = vm.penalty(),
                onStop = { vm.end() },
            )
            AmbientPicker(ambChoice) { ambChoice = it; com.krishna.questblock.util.Sounds.ambient = it }
        } else {
            val breakLeft = vm.breakUntil() - now
            if (breakLeft > 0L) {
                BreakCard(leftMs = breakLeft, long = vm.breakIsLong(), onSkip = { vm.skipBreak() })
            }
            Setup(timerQuests = timerQuests, bonus = vm.bonus(), onStart = { dur, quest -> vm.start(dur, quest) })
            AmbientPicker(ambChoice) { ambChoice = it; com.krishna.questblock.util.Sounds.ambient = it }
            TodayStrip(logs = logs, now = now)
            FocusStats(logs = logs, now = now)
            RecentSessions(logs = logs)
        }
    }
}

// ── Setup: big dial -> start ──────────────────────────────────────────────────
@Composable
private fun Setup(timerQuests: List<TaskEntity>, bonus: Int, onStart: (Long, TaskEntity?) -> Unit) {
    var minutes by remember { mutableIntStateOf(25) }
    var quest by remember { mutableStateOf<TaskEntity?>(null) }
    // quest may be deleted/completed while this screen is open
    val selected = quest?.let { q -> timerQuests.firstOrNull { it.id == q.id } }
    val questMs = selected?.timerDurationMs?.takeIf { it > 0 } ?: 60 * 60_000L
    val durationMs = if (selected != null) questMs else minutes * 60_000L
    val shownMin = (durationMs / 60_000L).toInt()
    val frac = (shownMin / 120f).coerceIn(0.02f, 1f)

    // Dial
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (selected == null) RoundStep("\u2212", enabled = minutes > 5) { minutes = (minutes - 5).coerceAtLeast(5) }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(ColorPrimary.copy(0.15f), -90f, 360f, false, style = stroke)
                drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(ColorPrimary, ColorAccent, ColorPrimary)), -90f, 360f * frac, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$shownMin", fontSize = 56.sp, fontWeight = FontWeight.Black, color = ColorText)
                Text("minutes", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            }
        }
        if (selected == null) RoundStep("+", enabled = minutes < 120) { minutes = (minutes + 5).coerceAtMost(120) }
    }

    if (selected == null) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PickChip("\uD83E\uDDE0 Deep 60", minutes == 60, ColorPrimary) { minutes = 60 } }
            item { PickChip("\uD83D\uDCDA Study 45", minutes == 45, ColorPrimary) { minutes = 45 } }
            item { PickChip("\uD83D\uDCD6 Reading 30", minutes == 30, ColorPrimary) { minutes = 30 } }
            item { PickChip("\u270F\uFE0F Custom (use + / \u2212)", minutes !in listOf(30, 45, 60), ColorPrimary) { } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DURATIONS.forEach { m ->
                val sel = minutes == m
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) ColorPrimary else ColorSurfaceHigh)
                        .clickable { minutes = m },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${m}m", maxLines = 1, color = if (sel) ColorOnPrimary else ColorMuted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    } else {
        val apps = selected.linkedApps.split(",").count { it.isNotBlank() }
        Text("\u23F1\uFE0F Quest timer" + if (apps > 0) " \u00b7 unlocks $apps app${if (apps > 1) "s" else ""}" else "",
            style = MaterialTheme.typography.bodyMedium, color = ColorAccent, fontWeight = FontWeight.SemiBold)
    }

    // What to focus on
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Focus on", style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PickChip("\u26A1 Quick focus", quest == null, ColorPrimary) { quest = null } }
            items(timerQuests, key = { it.id }) { t ->
                PickChip("\u23F1\uFE0F ${t.title}", selected?.id == t.id, ColorAccent) { quest = t }
            }
        }
    }

    val reward = bonus + (selected?.let { baseReward(it.difficulty) } ?: 0)
    Text(
        "Blocked apps stay locked \u00b7 finish for +$reward\uD83E\uDE99" + if (selected != null) " and the quest completes" else "",
        style = MaterialTheme.typography.bodySmall, color = ColorMuted,
    )

    Button(
        onClick = { onStart(durationMs, selected) },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
        shape = RoundedCornerShape(50),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Start ${shownMin} min focus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** Background sound for the session: Off, Rain, Brown, Pink or White noise. */
@Composable
private fun AmbientPicker(choice: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("\uD83D\uDD0A Background sound", style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.krishna.questblock.util.Sounds.AMBIENTS) { (key, label) ->
                PickChip(label, choice == key, ColorPrimary) { onPick(key) }
            }
        }
    }
}

@Composable
private fun RoundStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (enabled) ColorSurfaceHigh else ColorSurfaceHigh.copy(0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) ColorText else ColorMuted)
    }
}

// ── Today strip + recent sessions ────────────────────────────────────────────
@Composable
private fun TodayStrip(logs: List<FocusLog>, now: Long) {
    val dayStart = startOfToday(now)
    val today = logs.filter { it.startedAt >= dayStart }
    val minutes = today.sumOf { it.minutes }
    val sessions = today.count { it.completed }
    val best = logs.filter { it.completed }.maxOfOrNull { it.minutes } ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StripCell("Today", "$minutes min", Modifier.weight(1f))
        StripCell("Sessions", "$sessions", Modifier.weight(1f))
        StripCell("Best", "$best min", Modifier.weight(1f))
    }
}

@Composable
private fun StripCell(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(ColorCard).border(1.dp, ColorPrimary.copy(0.22f), RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
    }
}

@Composable
private fun RecentSessions(logs: List<FocusLog>) {
    if (logs.isEmpty()) {
        Text("Your finished sessions will show up here.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        return
    }
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Recent sessions", style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
        logs.take(5).forEach { l ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorCard).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (l.completed) "\u2705" else "\u23F9\uFE0F", fontSize = 18.sp)
                Column(Modifier.weight(1f)) {
                    Text(l.questTitle.ifBlank { "Quick focus" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ColorText)
                    Text(sdf.format(java.util.Date(l.startedAt)), style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                Text("${l.minutes} min", style = MaterialTheme.typography.labelLarge,
                    color = if (l.completed) ColorPrimary else ColorMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PickChip(text: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(0.2f) else ColorSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 200.dp),
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) color else ColorMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActiveFocus(remainingMs: Long, totalMs: Long, questTitle: String?, appCount: Int, penalty: Int, onStop: () -> Unit) {
    val totalSec = (remainingMs / 1000).coerceAtLeast(0)
    val frac = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Spacer(Modifier.height(24.dp))
    Text(if (questTitle != null) "\uD83C\uDFAF $questTitle" else "\uD83C\uDFAF Focus session",
        style = MaterialTheme.typography.titleMedium, color = ColorPrimary, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(ColorPrimary.copy(0.15f), -90f, 360f, false, style = stroke)
            drawArc(ColorPrimary, -90f, 360f * frac, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%02d:%02d".format(totalSec / 60, totalSec % 60), fontSize = 60.sp,
                fontWeight = FontWeight.Black, color = ColorText)
            Text("remaining", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
        }
    }

    Text(
        "Blocked apps stay locked" + if (appCount > 0) " \u00b7 $appCount linked app${if (appCount > 1) "s" else ""} unlocked" else "",
        style = MaterialTheme.typography.bodyMedium, color = ColorMuted,
    )

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onStop,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
        shape = RoundedCornerShape(50),
    ) {
        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (penalty > 0) "Give up (no reward, \u2212$penalty\uD83E\uDE99 after 1 min)" else "Give up (no reward)", fontWeight = FontWeight.SemiBold)
    }
}

// ── Focus streak, score and break nudge ──────────────────────────────────────
@Composable
private fun FocusStats(logs: List<FocusLog>, now: Long) {
    val todayNo = dayNumber(now)
    val doneDays = logs.filter { it.completed }.map { dayNumber(it.startedAt) }.toSet()
    var streak = 0
    var d = if (todayNo in doneDays) todayNo else todayNo - 1
    while (d in doneDays) { streak++; d-- }
    val recent = logs.filter { it.startedAt >= now - 14 * 86_400_000L }
    val score = if (recent.isEmpty()) 0 else {
        val rate = recent.count { it.completed }.toDouble() / recent.size
        val mins = recent.filter { it.completed }.sumOf { it.minutes }
        (rate * 60 + minOf(40.0, mins * 40.0 / 300.0)).toInt().coerceIn(0, 100)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StripCell("Focus streak", "$streak d", Modifier.weight(1f))
        StripCell("Focus score", "$score", Modifier.weight(1f))
        StripCell("Focus XP", "+1 / 5 min", Modifier.weight(1f))
    }
}

@Composable
private fun BreakCard(leftMs: Long, long: Boolean, onSkip: () -> Unit) {
    val sec = (leftMs / 1000).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ColorAccent.copy(alpha = 0.14f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("\u2615", fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(if (long) "Long break" else "Short break",
                fontWeight = FontWeight.Bold, color = ColorAccent)
            Text("%02d:%02d left \u00b7 stretch, drink water, no phone".format(sec / 60, sec % 60),
                style = MaterialTheme.typography.bodySmall, color = ColorText)
        }
        TextButton(onClick = onSkip) { Text("Skip", color = ColorMuted, fontWeight = FontWeight.Bold) }
    }
}
