@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import android.content.Context
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
) : ViewModel() {
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
    fun allowed(): Set<String> = repo.focusAllowed()
    /** v204: the apps picked for the session are saved first, so the blocker already knows them when the session starts. */
    fun strictDefault(): Boolean = repo.strictDefault()
    fun strict(): Boolean = repo.focusStrict()
    fun start(durationMs: Long, quest: TaskEntity?, allowed: Set<String>, strict: Boolean) = viewModelScope.launch {
        repo.setFocusAllowed(allowed)
        repo.setFocusStrict(strict)
        repo.startFocus(durationMs, quest)
    }

    private val _installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed = _installed.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _installed.value = pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
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
    val installed by vm.installed.collectAsStateWithLifecycle()

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

    // background sound: off at the start of every session; plays only after you pick one during the session
    var ambChoice by remember { mutableStateOf(com.krishna.questblock.util.Sounds.ambient) }
    LaunchedEffect(running, player.focusModeUntil, ambChoice) {
        if (running) com.krishna.questblock.util.Sounds.startAmbient(player.focusModeUntil)
        else { com.krishna.questblock.util.Sounds.stopAmbient(); ambChoice = "off" }
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
                pickedCount = vm.allowed().size,
                strict = vm.strict(),
                onStop = { vm.end() },
            )
            AmbientPicker(ambChoice) { ambChoice = it; com.krishna.questblock.util.Sounds.ambient = it }
        } else {
            val breakLeft = vm.breakUntil() - now
            if (breakLeft > 0L) {
                BreakCard(leftMs = breakLeft, long = vm.breakIsLong(), onSkip = { vm.skipBreak() })
            }
            Setup(
                timerQuests = timerQuests, bonus = vm.bonus(), penalty = vm.penalty(),
                installed = installed, initialAllowed = remember { vm.allowed() }, initialStrict = remember { vm.strictDefault() },
                onStart = { dur, quest, picked, strict -> ambChoice = "off"; vm.start(dur, quest, picked, strict) },
            )
            TodayStrip(logs = logs, now = now)
            FocusStats(logs = logs, now = now)
            RecentSessions(logs = logs)
        }
    }
}

// ── Setup: big dial -> start ──────────────────────────────────────────────────
@Composable
private fun Setup(
    timerQuests: List<TaskEntity>, bonus: Int, penalty: Int,
    installed: List<InstalledApp>, initialAllowed: Set<String>, initialStrict: Boolean,
    onStart: (Long, TaskEntity?, Set<String>, Boolean) -> Unit,
) {
    var strict by remember { mutableStateOf(initialStrict) }
    var minutes by remember { mutableIntStateOf(25) }
    var allowed by remember { mutableStateOf(initialAllowed) }
    var picking by remember { mutableStateOf(false) }
    // 0 = nothing asked, 1 = first "start?", 2 = second "commit?"
    var confirm by remember { mutableIntStateOf(0) }
    fun labelOf(pkg: String) = installed.find { it.pkg == pkg }?.label ?: pkg
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

    // v204: the apps this session needs; everything else is locked while it runs
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Apps I need this session", style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
        Text("Everything else is locked and Do Not Disturb is on. Phone, home screen and QuestBlock always work.",
            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allowed.toList(), key = { it }) { pkg ->
                PickChip("${labelOf(pkg)}  \u2715", true, ColorAccent) { allowed = allowed - pkg }
            }
            item { PickChip(if (allowed.isEmpty()) "+ Pick apps" else "+ Add / change", false, ColorPrimary) { picking = true } }
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Strict mode", style = MaterialTheme.typography.labelLarge, color = ColorText, fontWeight = FontWeight.SemiBold)
            Text("Can't give up. QuestBlock only shows this timer and its Settings pages stay blocked until it ends.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        Switch(checked = strict, onCheckedChange = { strict = it },
            colors = SwitchDefaults.colors(checkedTrackColor = ColorPrimary))
    }

    val reward = bonus + (selected?.let { baseReward(it.difficulty) } ?: 0)
    Text(
        "Everything but your picked apps is locked \u00b7 finish for +$reward\uD83E\uDE99" + if (selected != null) " and the quest completes" else "",
        style = MaterialTheme.typography.bodySmall, color = ColorMuted,
    )

    if (picking) {
        FocusAppPicker(installed = installed, selected = allowed,
            onDismiss = { picking = false }, onConfirm = { allowed = it; picking = false })
    }
    // v204: asked twice, so a session is never started by mistake
    if (confirm == 1) {
        val names = if (allowed.isEmpty()) "no apps picked" else allowed.map { labelOf(it) }.sorted().joinToString(", ")
        AlertDialog(
            onDismissRequest = { confirm = 0 },
            modifier = Modifier.grayWindow(),
            containerColor = ColorSurface,
            title = { Text("Start $shownMin min focus?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = { Text("Locked for $shownMin min: every app except $names. Do Not Disturb turns on." + if (strict) " Strict mode: you can't give up." else "", color = ColorMuted) },
            confirmButton = { TextButton(onClick = { confirm = 2 }) { Text("Yes, continue", color = ColorPrimary, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirm = 0 }) { Text("Cancel", color = ColorMuted) } },
        )
    }
    if (confirm == 2) {
        AlertDialog(
            onDismissRequest = { confirm = 0 },
            modifier = Modifier.grayWindow(),
            containerColor = ColorSurface,
            title = { Text("Commit for $shownMin minutes?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = {
                Text("Last check. The apps can't be changed once it starts." +
                    if (strict) " Strict mode is on: no giving up, and turning accessibility off breaks the session (penalty, no reward)." else if (penalty > 0) " Giving up after the first minute costs $penalty coins and gives no reward." else " Giving up gives no reward.",
                    color = ColorMuted)
            },
            confirmButton = {
                TextButton(onClick = { confirm = 0; onStart(durationMs, selected, allowed, strict) }) {
                    Text("Commit and start", color = ColorPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = 0 }) { Text("Go back", color = ColorMuted) } },
        )
    }

    Button(
        onClick = { confirm = 1 },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
        shape = RoundedCornerShape(50),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Start ${shownMin} min focus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** v204: pick the apps this Focus session needs (writing: WhatsApp, Telegram, a music app). */
@Composable
private fun FocusAppPicker(installed: List<InstalledApp>, selected: Set<String>, onDismiss: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var sel by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.grayWindow(),
        containerColor = ColorSurface,
        title = { Text("Apps for this session", color = ColorText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(350.dp)) {
                items(installed, key = { it.pkg }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (app.pkg in sel) sel - app.pkg else sel + app.pkg }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = app.pkg in sel, onCheckedChange = { checked ->
                            sel = if (checked) sel + app.pkg else sel - app.pkg
                        }, colors = CheckboxDefaults.colors(checkedColor = ColorPrimary))
                        Text(app.label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = ColorDivider, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(sel) }) { Text("Done", color = ColorPrimary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
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
private fun ActiveFocus(remainingMs: Long, totalMs: Long, questTitle: String?, appCount: Int, penalty: Int, pickedCount: Int, strict: Boolean, onStop: () -> Unit) {
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
        "Everything else is locked \u00b7 $pickedCount app${if (pickedCount != 1) "s" else ""} picked" +
            if (appCount > 0) " \u00b7 $appCount linked app${if (appCount > 1) "s" else ""} unlocked" else "",
        style = MaterialTheme.typography.bodyMedium, color = ColorMuted,
    )

    Spacer(Modifier.height(8.dp))
    if (strict) {
        Text("\uD83D\uDD12 Strict mode: no giving up until the timer ends", style = MaterialTheme.typography.bodyMedium,
            color = ColorWarning, fontWeight = FontWeight.SemiBold)
    } else OutlinedButton(
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
