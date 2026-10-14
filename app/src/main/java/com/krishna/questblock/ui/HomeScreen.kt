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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
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

    val todayFrac by animateFloatAsState(
        targetValue = if (shownTotal > 0) shownDone.toFloat() / shownTotal else 0f,
        animationSpec = tween(800, easing = EaseOutCubic), label = "today",
    )

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Jelly Pop header: avatar bubble, greeting, coin bubble, big headline ──
        item { HomeHeader(player, hideCoins = vm.prefs.optHideCoins, onCoinsClick = onOpenShop) }

        // ── Momentum card: XP ring, level, today's progress, stat bubbles ──
        item {
            MomentumCard(
                player = player, doneToday = shownDone, total = shownTotal, todayFrac = todayFrac,
                hideCoins = vm.prefs.optHideCoins, onCoinsClick = onOpenShop, onAddQuest = { vm.addSuggestion(it) },
            )
        }

        // ── Quick bubbles (every page of the app is one tap away) ──
        item {
            QuickBubbles(listOf(
                QuickBubble("\uD83D\uDED2", "Shop", ColorCoin, onOpenShop),
                QuickBubble("\uD83D\uDCCA", "Stats", Color(0xFF6CB6FF), onOpenStats),
                QuickBubble("\uD83E\uDDED", "Hub", Color(0xFFC39CFF), onOpenHub),
                QuickBubble("\uD83D\uDCDA", "Chapters", ColorPrimary, onOpenChapters),
                QuickBubble("\uD83D\uDCC5", "Attendance", Color(0xFFFF9E6C), onOpenAttendance),
                QuickBubble("⚡", "Focus", ColorAccent, onGoFocus),
            ))
        }

        // ── Daily login bonus / theme rent ──
        if (vm.loginBonus > 0) {
            item {
                Box(Modifier.fillMaxWidth().clip(PillShape).background(ColorCoin.copy(0.16f)).clickable { vm.dismissBonus() }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("🎁 Daily login: +${vm.loginBonus}🪙", style = MaterialTheme.typography.labelLarge, color = ColorCoin, fontWeight = FontWeight.Black)
                }
            }
        }
        vm.rentNote?.let { note ->
            item {
                Box(Modifier.fillMaxWidth().clip(PillShape).background(ColorSurfaceHigh).clickable { vm.dismissRent() }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(note, style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Today's quests ──
        item {
            BubbleSection("Today's quests", trailing = if (shownTotal > 0) "$shownDone/$shownTotal done" else null)
        }
        if (overdueCount > 0) item {
            BubbleChip("⚠ $overdueCount overdue", ColorDanger)
        }
        if (routineDay) {
            routineToday.forEach { task ->
                val steps = (stepsByTask[task.id] ?: emptyList()).sortedBy { it.id }
                item(key = "rh_${task.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📋 ${task.title} · ${steps.count { it.done }}/${steps.size}", style = MaterialTheme.typography.labelLarge,
                            color = ColorText, fontWeight = FontWeight.Black)
                        GlowBar(if (steps.isEmpty()) 0f else steps.count { it.done }.toFloat() / steps.size, height = 12.dp)
                    }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(if (doneToday > 0) "\uD83C\uDF89" else "\uD83D\uDCDD", ColorPrimary, 56.dp)
                        Text(if (doneToday > 0) "All done for today" else "Nothing planned for today",
                            style = MaterialTheme.typography.titleSmall, color = ColorText,
                            fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Button(onClick = onAddQuest, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Black)
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
                BubbleChip("+${todayList.size - 6} more in Quests", ColorMuted)
            }
        }

        // v116: with 5 or fewer active quests the suggestions stay visible (the empty / routine cases above already show them)
        if (!routineDay && todayList.isNotEmpty() && pending.size <= 5) {
            item { SuggestedQuests(onAdd = { vm.addSuggestion(it) }, title = "\uD83D\uDCA1 Suggested for today") }
        }

        // ── Next: dated quests, then undated ──
        if (!routineDay && comingUp.isNotEmpty()) {
            item { BubbleSection("⏰ Coming up") }
            items(comingUp.take(3), key = { "up_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }
        if (!routineDay && noDate.isNotEmpty()) {
            item { BubbleSection("📋 No date") }
            items(noDate.take(3), key = { "nd_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }

        // ── Active power-ups ──
        val activePowerUps = buildList {
            if (now < player.xpBoostUntil)       add("⚡ XP Boost" to ((player.xpBoostUntil - now) / 60_000))
            if (now < player.coinMultiplierUntil) add("🧲 Coins ×1.5" to ((player.coinMultiplierUntil - now) / 60_000))
            if (now < player.streakShieldUntil)   add("🛡️ Shield" to ((player.streakShieldUntil - now) / 60_000))
        }
        if (activePowerUps.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activePowerUps) { (label, mins) -> BubbleChip("$label ${mins}m", ColorPrimary) }
                }
            }
        }

        // ── Recent achievements ──
        if (recentAch.isNotEmpty()) {
            item { BubbleSection("🏆 Recent achievements") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentAch) { ach ->
                        Card {
                            Column(Modifier.padding(14.dp).width(100.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ArtIcon(Art.ach(ach.id), ach.emoji, 52.dp)
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

// ── Header ─────────────────────────────────────────────────────────────────
private fun frameColorOf(player: Player): Color = when (player.equippedFrame) {
    "frame_fire"   -> Color(0xFFFF6B35)
    "frame_ice"    -> Color(0xFF64B5F6)
    "frame_gold"   -> ColorCoin
    "frame_galaxy" -> Color(0xFF9C27B0)
    else           -> ColorPrimary
}

@Composable
private fun HomeHeader(player: Player, hideCoins: Boolean, onCoinsClick: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val fc = frameColorOf(player)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).border(2.5.dp, fc, CircleShape), contentAlignment = Alignment.Center) { AvatarFill() }
            Column(Modifier.weight(1f)) {
                Text(greet, style = MaterialTheme.typography.labelLarge, color = ColorMuted)
                Text(player.name.ifBlank { "Hero" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                    color = ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.clip(PillShape).background(ColorCoin.copy(alpha = 0.18f)).clickable { onCoinsClick() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🪙", fontSize = 16.sp)
                Text(if (hideCoins) "???" else "${player.coins}", color = ColorCoin, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            }
        }
        Text(
            buildAnnotatedString {
                append("Let's build\n")
                withStyle(SpanStyle(color = ColorPrimary)) { append("momentum") }
                append(" today")
            },
            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = ColorText, lineHeight = 44.sp,
        )
    }
}

// ── Momentum card ──────────────────────────────────────────────────────────
@Composable
private fun MomentumCard(player: Player, doneToday: Int, total: Int, todayFrac: Float, hideCoins: Boolean, onCoinsClick: () -> Unit, onAddQuest: ((StarterQuest) -> Unit)? = null) {
    val level = player.level()
    val xp    = player.xpIntoLevel()
    val fc    = frameColorOf(player)
    val xpAnim by animateFloatAsState(targetValue = xp / 100f,
        animationSpec = tween(800, easing = EaseOutCubic), label = "xp")
    val pct = if (total > 0) (doneToday * 100 / total) else 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    BubbleRing(xpAnim, 96.dp, 9.dp, fc) {
                        Box(Modifier.size(70.dp).clip(CircleShape), contentAlignment = Alignment.Center) { AvatarFill() }
                    }
                    val badgeEmoji = when (com.krishna.questblock.data.shownBadgeOf(player)) {
                        "badge_beginner" -> "\uD83C\uDF31"; "badge_star" -> "\u2B50"; "badge_crown" -> "\uD83D\uDC51"
                        "badge_sword" -> "\u2694\uFE0F"; "badge_dragon" -> "\uD83D\uDC09"; else -> ""
                    }
                    if (badgeEmoji.isNotBlank()) {
                        Box(Modifier.align(Alignment.BottomEnd).size(28.dp).background(ColorSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                            Text(badgeEmoji, fontSize = 15.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lv. $level", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = ColorText)
                    BubbleChip("$xp / 100 XP", ColorPrimary)
                    Text("${player.xp} XP total", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    if (player.goal.isNotBlank()) {
                        Text("\uD83C\uDFAF ${player.goal}", style = MaterialTheme.typography.labelMedium, color = ColorPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // today's progress: big percentage and a fat jelly bar
            Column(Modifier.fillMaxWidth().clip(BubbleShapeSmall).background(ColorSurfaceHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Today's progress", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
                        Text("$doneToday of $total quests", style = MaterialTheme.typography.bodyMedium, color = ColorText, fontWeight = FontWeight.SemiBold)
                    }
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = ColorPrimary)
                }
                GlowBar(todayFrac, height = 16.dp)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("\uD83E\uDE99", if (hideCoins) "???" else "${player.coins}", "Coins", Modifier.weight(1f).clickable { onCoinsClick() })
                StatTile("\uD83D\uDD25", "${player.streak}d", "Streak", Modifier.weight(1f))
                StatTile("✨", "${player.xp}", "Total XP", Modifier.weight(1f))
            }

            CultivationStrip(player.xp, onAddQuest = onAddQuest)
        }
    }
}

// ── Quick bubbles ──────────────────────────────────────────────────────────
private class QuickBubble(val emoji: String, val label: String, val color: Color, val onClick: () -> Unit)

@Composable
private fun QuickBubbles(list: List<QuickBubble>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(list) { b ->
            Column(Modifier.width(72.dp).clip(BubbleShapeSmall).clickable { b.onClick() }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconTile(b.emoji, b.color, 62.dp)
                Text(b.label, style = MaterialTheme.typography.labelMedium, color = ColorText, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CheckBubble(onClick = { if (!sub.done) onTick() }, done = sub.done, size = 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sub.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = if (sub.done) ColorMuted else ColorText,
                        textDecoration = if (sub.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BubbleChip(when (pr) { 1 -> "High"; 2 -> "Med"; else -> "Low" }, prColor)
                        BubbleChip("+$coins🪙", ColorCoin)
                    }
                }
            }
            if (!sub.done) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (mins != null) {
                    if (endAt > 0L) {
                        val left = ((endAt - nowMs).coerceAtLeast(0L)) / 1000L
                        BubbleChip("⏱ %d:%02d".format(left / 60, left % 60), ColorPrimary)
                        TextButton(onClick = { endAt = 0L; prefs.setStepTimerEnd(sub.id, 0L) }) { Text("Cancel", color = ColorMuted) }
                    } else TextButton(onClick = {
                        endAt = System.currentTimeMillis() + mins * 60_000L; prefs.setStepTimerEnd(sub.id, endAt)
                    }) { Text("▶ Start $mins min", fontWeight = FontWeight.Bold) }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(questTypeInfo(task.questType).emoji, catColor, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, color = ColorText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BubbleChip(task.category, catColor)
                    if (task.dueAt != null) {
                        BubbleChip((if (task.isRecurring) tf else sdf).format(Date(task.dueAt)), if (isOverdue) ColorDanger else ColorMuted)
                    }
                    BubbleChip("\uD83E\uDE99 ${baseReward(task.difficulty)}", ColorCoin)
                }
            }
            CheckBubble(onClick = onComplete, size = 42.dp)
        }
    }
}
