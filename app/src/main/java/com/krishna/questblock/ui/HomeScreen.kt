@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: GameRepository,
    private val autoVerifier: com.krishna.questblock.engine.AutoVerifier,
    private val hubStore: com.krishna.questblock.engine.HubStore,
    val prefs: com.krishna.questblock.data.Prefs,
) : ViewModel() {
    val player       = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks        = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements = repo.achievements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** +5🪙 the first time the app opens each day. */
    var loginBonus by mutableIntStateOf(0)
        private set
    init {
        viewModelScope.launch {
            repo.settleFocus()
            loginBonus = repo.claimDailyLogin()
            rentNote = runCatching { repo.chargeThemeRent() }.getOrNull()
            runCatching { repo.removeLeftoverTestCoins() }
            runCatching { autoVerifier.run() }
            hubStore.refresh()
        }
    }
    fun dismissBonus() { loginBonus = 0 }
    /** Once a day: the rent of the equipped theme (or the note that it went back to default). */
    var rentNote by mutableStateOf<String?>(null)
        private set
    fun dismissRent() { rentNote = null }
    /** Turns a suggestion into a real quest due today. */
    val subs = repo.allSubTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Ticks a routine step; when it was the last one, the routine itself is finished. */
    /** Finishes a quest; with the optional Undo switch on, the Quests snackbar offers to take it back. */
    private suspend fun finish(task: TaskEntity) {
        if (prefs.optUndo) repo.completeTaskUndoable(task)?.let { UndoBus.events.tryEmit(it) } else repo.completeTask(task)
    }
    fun tickStep(task: TaskEntity, sub: SubTask, all: List<SubTask>) = viewModelScope.launch {
        repo.completeStep(task, sub)
        if (all.all { it.done || it.id == sub.id }) finish(task)
    }
    fun skipStep(task: TaskEntity, sub: SubTask, all: List<SubTask>) = viewModelScope.launch {
        if (!repo.skipStep(task, sub)) { notice = "Skipping costs $STEP_SKIP_COST🪙 and you don't have enough"; return@launch }
        if (all.all { it.done || it.id == sub.id }) finish(task)
    }
    fun addSuggestion(q: StarterQuest, tomorrow: Boolean = false) = viewModelScope.launch {
        val dueToday = nextRunDayStart(0, System.currentTimeMillis()) - 1 + (if (tomorrow) 86_400_000L else 0L)
        repo.addTask(QuestDraft(title = q.title, difficulty = q.difficulty, category = "Study", notes = q.note, dueAt = dueToday))
    }
    /** One-line message shown as a toast (e.g. a Routine with unticked steps). */
    var notice by mutableStateOf<String?>(null)
        private set
    fun clearNotice() { notice = null }
    fun complete(task: TaskEntity) = viewModelScope.launch {
        if (task.questType == "routine" && repo.getSubTasks(task.id).any { !it.done }) {
            notice = "Tick every step first (open it in Quests)"
            return@launch
        }
        finish(task)
    }
}

@Composable
fun HomeScreen(onAddQuest: () -> Unit, onGoFocus: () -> Unit, onOpenShop: () -> Unit, onOpenStats: () -> Unit, onOpenHub: () -> Unit, onOpenChapters: () -> Unit = {}, onOpenAttendance: () -> Unit = {}, vm: HomeViewModel = hiltViewModel()) {
    val player       by vm.player.collectAsStateWithLifecycle()
    val tasks        by vm.tasks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    LaunchedEffect(vm.notice) {
        vm.notice?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearNotice() }
    }
    // optional: ask before spending coins to skip a routine step
    var askSkip by remember { mutableStateOf<Triple<TaskEntity, SubTask, List<SubTask>>?>(null) }
    askSkip?.let { (t, sub, all) ->
        AlertDialog(
            onDismissRequest = { askSkip = null },
            modifier = Modifier.grayWindow(),
            containerColor = ColorSurface,
            title = { Text("Skip this step?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = { Text("${sub.title}\nCosts $STEP_SKIP_COST🪙, no reward.", color = ColorMuted) },
            confirmButton = { TextButton(onClick = { vm.skipStep(t, sub, all); askSkip = null }) { Text("Skip", color = ColorDanger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { askSkip = null }) { Text("Cancel", color = ColorMuted) } },
        )
    }

    val now      = System.currentTimeMillis()
    val dayStart = startOfToday(now)
    val dayEnd   = nextRunDayStart(0, now) - 1          // last millisecond of today
    val pending  = tasks.filter { it.isAvailableNow(now) }

    // "For today" = due today / overdue, or a recurring quest (daily, weekly, challenge, repeating routine/focus).
    // Order: priority first (High → Low), then due time, then quest type order.
    val todayList = pending.filter { it.isForToday(dayEnd) }.sortedWith(
        compareBy<TaskEntity> { it.priority }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenBy { t -> QUEST_TYPES.indexOfFirst { it.id == t.questType } })
    val rest     = pending.filter { !it.isForToday(dayEnd) }
    val comingUp = rest.filter { it.dueAt != null }.sortedWith(compareBy<TaskEntity> { it.dueAt ?: Long.MAX_VALUE }.thenBy { it.priority })
    val noDate   = rest.filter { it.dueAt == null }.sortedBy { it.priority }

    val overdueCount = todayList.count { it.dueAt != null && it.dueAt < now }
    val doneToday    = tasks.count { it.done && (it.completedAt ?: 0L) >= dayStart }
    val totalToday   = doneToday + todayList.size
    // Routine day: only the routine's steps count as today's quests (each step is one quest)
    val subs by vm.subs.collectAsStateWithLifecycle()
    val routineToday = todayList.filter { it.questType == "routine" }
    val routineDay = routineToday.isNotEmpty()
    val stepsByTask = subs.groupBy { it.taskId }
    val stepTotal = routineToday.sumOf { stepsByTask[it.id]?.size ?: 0 }
    val stepDone = routineToday.sumOf { t -> stepsByTask[t.id]?.count { it.done } ?: 0 }
    val shownDone = if (routineDay) stepDone else doneToday
    val shownTotal = if (routineDay) stepTotal else totalToday

    val recentAch    = achievements.filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }.take(3)

    val healthCtx = LocalContext.current
    var problems by remember { mutableStateOf(listOf<com.krishna.questblock.util.Health.Problem>()) }
    LaunchedEffect(Unit) {
        while (true) { problems = com.krishna.questblock.util.Health.problems(healthCtx, vm.prefs); delay(5000L) }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Protection warning (Accessibility / Usage access / battery) ─────
        if (problems.isNotEmpty()) item { HealthBanner(problems.first(), problems.size) }

        // ── Hero card ──────────────────────────────────────────────────────
        item { HomeHeader(player.name) }
        item { HeroCard(player = player, doneToday = shownDone, total = shownTotal, hideCoins = vm.prefs.optHideCoins, onCoinsClick = onOpenShop, onAddQuest = { vm.addSuggestion(it) }) }

        // ── Daily login bonus ──────────────────────────────────────────────
        if (vm.loginBonus > 0) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = ColorCoin.copy(0.12f),
                    modifier = Modifier.fillMaxWidth().clickable { vm.dismissBonus() }) {
                    Text("🎁 Daily login: +${vm.loginBonus}🪙", modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelLarge, color = ColorCoin, fontWeight = FontWeight.Bold)
                }
            }
        }

        vm.rentNote?.let { note ->
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = ColorSurfaceHigh,
                    modifier = Modifier.fillMaxWidth().clickable { vm.dismissRent() }) {
                    Text(note, modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Today's quests (right under the hero) ──────────────────────────
        item {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today's quests", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (overdueCount > 0) Text("⚠ $overdueCount overdue", style = MaterialTheme.typography.labelMedium, color = ColorDanger)
                    if (shownTotal > 0) Text("$shownDone/$shownTotal done", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                }
            }
        }
        if (routineDay) {
            routineToday.forEach { task ->
                val steps = (stepsByTask[task.id] ?: emptyList()).sortedBy { it.id }
                item(key = "rh_${task.id}") {
                    Text("📋 ${task.title} · ${steps.count { it.done }}/${steps.size}", style = MaterialTheme.typography.labelLarge,
                        color = ColorMuted, fontWeight = FontWeight.SemiBold)
                }
                steps.forEachIndexed { i, sub ->
                    item(key = "rs_${sub.id}") {
                        HomeStepRow(task, sub, i, steps.size, vm.prefs, onTick = { vm.tickStep(task, sub, steps) }, onSkip = { if (vm.prefs.optConfirm) askSkip = Triple(task, sub, steps) else vm.skipStep(task, sub, steps) })
                    }
                }
            }
            item { Text("Routine day: other quests wait until tomorrow", style = MaterialTheme.typography.labelSmall, color = ColorMuted) }
            item { SuggestedQuests(onAdd = { vm.addSuggestion(it, tomorrow = true) }, title = "\uD83D\uDCA1 Suggested (added for tomorrow)") }
        } else if (todayList.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (doneToday > 0) "\uD83C\uDF89" else "\uD83D\uDCDD", fontSize = 22.sp)
                        Text(if (doneToday > 0) "All done for today" else "Nothing planned for today",
                            style = MaterialTheme.typography.bodyMedium, color = ColorText,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAddQuest) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { SuggestedQuests(onAdd = { vm.addSuggestion(it) }, title = "\uD83D\uDCA1 Suggested for today") }
        } else {
            items(todayList.take(6), key = { "td_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
            if (todayList.size > 6) item {
                Text("+${todayList.size - 6} more in Quests", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            }
        }

        // v116: with 5 or fewer active quests the suggestions stay visible (the empty / routine cases above already show them)
        if (!routineDay && todayList.isNotEmpty() && pending.size <= 5) {
            item { SuggestedQuests(onAdd = { vm.addSuggestion(it) }, title = "\uD83D\uDCA1 Suggested for today") }
        }

        // ── Next: dated quests, then undated ───────────────────────────────
        if (!routineDay && comingUp.isNotEmpty()) {
            item { SubHeader("⏰ Coming up") }
            items(comingUp.take(3), key = { "up_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }
        if (!routineDay && noDate.isNotEmpty()) {
            item { SubHeader("📋 No date") }
            items(noDate.take(3), key = { "nd_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }

        // ── Slim shortcuts: Shop + Stats ───────────────────────────────────
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlimPill("\uD83D\uDED2 Shop", false, onOpenShop, Modifier.weight(1f))
                    SlimPill("\uD83D\uDCCA Stats", false, onOpenStats, Modifier.weight(1f))
                    SlimPill("\uD83E\uDDED Hub", false, onOpenHub, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlimPill("\uD83D\uDCDA Chapters", true, onOpenChapters, Modifier.weight(1f))
                    SlimPill("\uD83D\uDCC5 Attendance", true, onOpenAttendance, Modifier.weight(1f))
                }
            }
        }

        // ── Active power-ups ───────────────────────────────────────────────
        val activePowerUps = buildList {
            if (now < player.xpBoostUntil)       add("⚡ XP Boost" to ((player.xpBoostUntil - now) / 60_000))
            if (now < player.coinMultiplierUntil) add("🧲 Coins ×1.5" to ((player.coinMultiplierUntil - now) / 60_000))
            if (now < player.streakShieldUntil)   add("🛡️ Shield" to ((player.streakShieldUntil - now) / 60_000))
        }
        if (activePowerUps.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activePowerUps) { (label, mins) ->
                        Surface(shape = RoundedCornerShape(20.dp), color = ColorPrimary.copy(0.15f)) {
                            Text("$label ${mins}m", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall, color = ColorPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Recent achievements ────────────────────────────────────────────
        if (recentAch.isNotEmpty()) {
            item { SubHeader("🏆 Recent Achievements") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentAch) { ach ->
                        Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                            shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(12.dp).width(96.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ach.emoji, fontSize = 28.sp)
                                Text(ach.name, style = MaterialTheme.typography.labelSmall,
                                    color = ColorText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun TaskEntity.isForToday(endOfToday: Long): Boolean =
    (dueAt != null && dueAt <= endOfToday) ||
        // v116: a one-time quest added today with no due date shows under Today's quests (not in No date)
        (questType == "onetime" && !isRecurring && dueAt == null && createdAt >= startOfToday(endOfToday)) ||
        (isRecurring && (dueAt == null || questType == "daily" || questType == "weekly" || questType == "challenge"))

// ── Hero card ──────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(name: String) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 12 -> "Good Morning,"; hour < 17 -> "Good Afternoon,"; else -> "Good Evening," }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile("\u2694\uFE0F", ColorPrimary, 34.dp)
            Text("QuestBlock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColorText)
        }
        Spacer(Modifier.height(6.dp))
        Text(greet, style = MaterialTheme.typography.bodyMedium, color = ColorMuted)
        Text(name.ifBlank { "Hero" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = ColorText,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Keep going, legends build habits.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
    }
}

// ?? Hero card ??????????????????????????????????????????????????????????????
@Composable
private fun HeroCard(player: Player, doneToday: Int, total: Int, hideCoins: Boolean = false, onCoinsClick: () -> Unit, onAddQuest: ((StarterQuest) -> Unit)? = null) {
    val level = player.level()
    val xp    = player.xpIntoLevel()
    val frameColor = when (player.equippedFrame) {
        "frame_fire"   -> androidx.compose.ui.graphics.Color(0xFFFF6B35)
        "frame_ice"    -> androidx.compose.ui.graphics.Color(0xFF64B5F6)
        "frame_gold"   -> ColorCoin
        "frame_galaxy" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        else           -> ColorPrimary
    }
    val xpAnim by animateFloatAsState(targetValue = xp / 100f,
        animationSpec = tween(800, easing = EaseOutCubic), label = "xp")

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Level badge (hexagon)
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(68.dp).clip(HexShape)
                        .background(Brush.verticalGradient(listOf(frameColor.copy(0.55f), frameColor.copy(0.12f))))
                        .border(2.dp, frameColor, HexShape),
                        contentAlignment = Alignment.Center) {
                        Text("$level", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = ColorText)
                    }
                    val badgeEmoji = when (com.krishna.questblock.data.shownBadgeOf(player)) {
                        "badge_beginner" -> "\uD83C\uDF31"; "badge_star" -> "\u2B50"; "badge_crown" -> "\uD83D\uDC51"
                        "badge_sword" -> "\u2694\uFE0F"; "badge_dragon" -> "\uD83D\uDC09"; else -> ""
                    }
                    if (badgeEmoji.isNotBlank()) {
                        Box(Modifier.align(Alignment.BottomEnd).size(24.dp)
                            .background(ColorCard, CircleShape), contentAlignment = Alignment.Center) {
                            Text(badgeEmoji, fontSize = 13.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text("Lv. $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColorText)
                        Text("$xp / 100 XP", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    }
                    GlowBar(xpAnim, height = 10.dp)
                    Text("${player.xp} XP total", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    if (player.goal.isNotBlank()) {
                        Text("\uD83C\uDFAF ${player.goal}", style = MaterialTheme.typography.labelSmall, color = ColorPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("\uD83E\uDE99", if (hideCoins) "???" else "${player.coins}", "Coins", Modifier.weight(1f).clickable { onCoinsClick() })
                StatTile("\uD83D\uDD25", "${player.streak}d", "Streak", Modifier.weight(1f))
                StatTile("\u2705", "$doneToday/$total", "Quests", Modifier.weight(1f))
            }

            CultivationStrip(player.xp, onAddQuest = onAddQuest)
        }
    }
}

@Composable
private fun FocusShortcut(isActive: Boolean, remainingMs: Long, onClick: () -> Unit, modifier: Modifier) {
    val mm = remainingMs / 60_000
    val ss = (remainingMs % 60_000) / 1000
    ShortcutTile(
        emoji = if (isActive) "🎯" else "⚡",
        title = if (isActive) "Focus active" else "Focus mode",
        sub = if (isActive) "%02d:%02d left".format(mm, ss) else "Start a session",
        highlighted = isActive, onClick = onClick, modifier = modifier,
    )
}

@Composable
private fun ShortcutTile(emoji: String, title: String, sub: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = if (highlighted) ColorPrimary.copy(0.15f) else ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable { onClick() }) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = if (highlighted) ColorPrimary else ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HealthBanner(p: com.krishna.questblock.util.Health.Problem, count: Int) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ColorDanger.copy(alpha = 0.14f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("\u26A0\uFE0F", fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(p.title, fontWeight = FontWeight.Bold, color = ColorDanger)
            Text(p.text + if (count > 1) " (+${count - 1} more)" else "", style = MaterialTheme.typography.bodySmall, color = ColorText)
        }
        TextButton(onClick = {
            runCatching { ctx.startActivity(android.content.Intent(p.action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Fix", fontWeight = FontWeight.Bold, color = ColorDanger) }
    }
}

@Composable
private fun SlimPill(text: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) ColorPrimary.copy(0.15f) else ColorSurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (highlighted) ColorPrimary else ColorText)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
}

/** Priority is automatic: the routine's first third of steps is High, the middle Medium, the last third Low. */
private fun stepPriority(i: Int, n: Int, own: Int): Int = when {
    n <= 1 -> own
    i * 3 < n -> 1
    i * 3 < n * 2 -> 2
    else -> 3
}

@Composable
private fun HomeStepRow(task: TaskEntity, sub: SubTask, i: Int, n: Int, prefs: Prefs, onTick: () -> Unit, onSkip: () -> Unit) {
    val pr = stepPriority(i, n, task.priority)
    val prColor = when (pr) { 1 -> ColorDanger; 2 -> ColorWarning; else -> ColorMuted }
    val coins = maxOf(1, (baseReward(task.difficulty) + n - 1) / n)
    // a step like "Study 25 min" gets a timer; it ticks itself when the time is up
    val mins = remember(sub.title) { Regex("(\\d{1,3})\\s*min", RegexOption.IGNORE_CASE).find(sub.title)?.groupValues?.get(1)?.toIntOrNull() }
    var endAt by remember(sub.id) { mutableLongStateOf(prefs.stepTimerEnd(sub.id)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(endAt, sub.done) {
        while (endAt > 0L && !sub.done) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= endAt) { prefs.setStepTimerEnd(sub.id, 0L); endAt = 0L; tick(); break }
            delay(1000L)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sub.done, onCheckedChange = { if (!sub.done) onTick() })
                Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (sub.done) ColorMuted else ColorText,
                    textDecoration = if (sub.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                Text(when (pr) { 1 -> "High"; 2 -> "Med"; else -> "Low" }, style = MaterialTheme.typography.labelSmall, color = prColor)
                Spacer(Modifier.width(8.dp))
                Text("+$coins🪙", style = MaterialTheme.typography.labelSmall, color = ColorCoin, fontWeight = FontWeight.Bold)
            }
            if (!sub.done) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mins != null) {
                    if (endAt > 0L) {
                        val left = ((endAt - nowMs).coerceAtLeast(0L)) / 1000L
                        Text("⏱ %d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.labelMedium, color = ColorPrimary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { endAt = 0L; prefs.setStepTimerEnd(sub.id, 0L) }) { Text("Cancel", color = ColorMuted) }
                    } else TextButton(onClick = {
                        endAt = System.currentTimeMillis() + mins * 60_000L; prefs.setStepTimerEnd(sub.id, endAt)
                    }) { Text("▶ Start $mins min") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSkip) { Text("Skip −$STEP_SKIP_COST🪙", color = ColorMuted, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun HomeQuestRow(task: TaskEntity, now: Long, onComplete: () -> Unit) {
    val catColor  = CatColors[task.category] ?: ColorPrimary
    val isOverdue = task.dueAt != null && task.dueAt < now
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val tf  = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(questTypeInfo(task.questType).emoji, catColor, 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = ColorText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(task.category, style = MaterialTheme.typography.labelSmall, color = catColor, fontWeight = FontWeight.Bold)
                    if (task.dueAt != null) {
                        Text((if (task.isRecurring) tf else sdf).format(Date(task.dueAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) ColorDanger else ColorMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("\uD83E\uDE99 ${baseReward(task.difficulty)}", style = MaterialTheme.typography.labelSmall,
                        color = ColorCoin, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onComplete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                    tint = ColorPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}
