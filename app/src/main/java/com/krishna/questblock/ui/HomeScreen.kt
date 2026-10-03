@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
class HomeViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
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
        }
    }
    fun dismissBonus() { loginBonus = 0 }
    /** Turns a suggestion into a real quest due today. */
    fun addSuggestion(q: StarterQuest) = viewModelScope.launch {
        val dueToday = nextRunDayStart(0, System.currentTimeMillis()) - 1
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
        repo.completeTask(task)
    }
}

@Composable
fun HomeScreen(onAddQuest: () -> Unit, onGoFocus: () -> Unit, onOpenShop: () -> Unit, onOpenStats: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val player       by vm.player.collectAsStateWithLifecycle()
    val tasks        by vm.tasks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()

    val ctx = LocalContext.current
    LaunchedEffect(vm.notice) {
        vm.notice?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.clearNotice() }
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
    val recentAch    = achievements.filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }.take(3)

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Hero card ──────────────────────────────────────────────────────
        item { HeroCard(player = player, doneToday = doneToday, total = totalToday, onCoinsClick = onOpenShop) }

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

        // ── Today's quests (right under the hero) ──────────────────────────
        item {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today's quests", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (overdueCount > 0) Text("⚠ $overdueCount overdue", style = MaterialTheme.typography.labelMedium, color = ColorDanger)
                    if (totalToday > 0) Text("$doneToday/$totalToday done", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                }
            }
        }
        if (todayList.isEmpty() && tasks.isEmpty()) {
            item { SuggestedStart(onAdd = { vm.addSuggestion(it) }, onAddOwn = onAddQuest) }
        } else if (todayList.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (tasks.isEmpty()) "⚔️" else "🎉", fontSize = 22.sp)
                        Text(if (tasks.isEmpty()) "No quests yet" else "All clear for today",
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
        } else {
            items(todayList.take(6), key = { "td_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
            if (todayList.size > 6) item {
                Text("+${todayList.size - 6} more in Quests", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            }
        }

        // ── Next: dated quests, then undated ───────────────────────────────
        if (comingUp.isNotEmpty()) {
            item { SubHeader("⏰ Coming up") }
            items(comingUp.take(3), key = { "up_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }
        if (noDate.isNotEmpty()) {
            item { SubHeader("📋 No date") }
            items(noDate.take(3), key = { "nd_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }

        // ── Slim shortcuts: Shop + Stats ───────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlimPill("\uD83D\uDED2 Shop", false, onOpenShop, Modifier.weight(1f))
                SlimPill("\uD83D\uDCCA Stats", false, onOpenStats, Modifier.weight(1f))
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

private fun TaskEntity.isForToday(endOfToday: Long): Boolean =
    (dueAt != null && dueAt <= endOfToday) ||
        (isRecurring && (dueAt == null || questType == "daily" || questType == "weekly" || questType == "challenge"))

// ── Hero card ──────────────────────────────────────────────────────────────
@Composable
private fun HeroCard(player: Player, doneToday: Int, total: Int, onCoinsClick: () -> Unit) {
    val level = player.level()
    val xp    = player.xpIntoLevel()
    val frameColor = when (player.equippedFrame) {
        "frame_fire"   -> androidx.compose.ui.graphics.Color(0xFFFF6B35)
        "frame_ice"    -> androidx.compose.ui.graphics.Color(0xFF64B5F6)
        "frame_gold"   -> ColorCoin
        "frame_galaxy" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        else           -> ColorPrimary
    }

    // Animated XP bar
    val xpAnim by animateFloatAsState(targetValue = xp / 100f,
        animationSpec = tween(800, easing = EaseOutCubic), label = "xp")

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Avatar
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).background(
                        Brush.radialGradient(listOf(frameColor.copy(0.5f), frameColor.copy(0.05f))), CircleShape))
                    Box(Modifier.size(54.dp).clip(CircleShape).background(frameColor.copy(0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("$level", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = frameColor)
                    }
                    // Badge
                    val badgeEmoji = when (player.equippedBadge) {
                        "badge_star" -> "⭐"; "badge_crown" -> "👑"
                        "badge_sword" -> "⚔️"; "badge_dragon" -> "🐉"; else -> ""
                    }
                    if (badgeEmoji.isNotBlank()) {
                        Box(Modifier.align(Alignment.BottomEnd).size(22.dp)
                            .background(ColorCard, CircleShape), contentAlignment = Alignment.Center) {
                            Text(badgeEmoji, fontSize = 12.sp)
                        }
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(player.name.ifBlank { "Hero" }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black, color = ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Level $level · ${player.xp} XP total",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    if (player.goal.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = ColorPrimary.copy(0.12f)) {
                            Text("🎯 ${player.goal}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, color = ColorPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Stats chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onCoinsClick() }, "🪙", "${player.coins}", "coins · shop")
                StatPill(Modifier.weight(1f), "🔥", "${player.streak}d", "streak")
                StatPill(Modifier.weight(1f), "✅", "$doneToday/$total", "done")
            }

            // XP bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("XP to Level ${level + 1}", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    Text("$xp / 100", style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ColorSurfaceHigh)) {
                    Box(Modifier.fillMaxWidth(xpAnim).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(ColorPrimary, ColorAccent)), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun StatPill(modifier: Modifier, emoji: String, value: String, label: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = ColorSurfaceHigh) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(emoji, fontSize = 16.sp)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ColorText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
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
private fun SuggestedStart(onAdd: (StarterQuest) -> Unit, onAddOwn: () -> Unit) {
    var seed by remember { mutableIntStateOf(0) }
    val picks = remember(seed) { STARTER_QUESTS.shuffled().take(3) }
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Suggested to start", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Class 12 Boards + JEE \u00b7 basics first. Tap + to add one.",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                TextButton(onClick = { seed++ }) { Text("\uD83D\uDD00 Shuffle", fontWeight = FontWeight.Bold) }
            }
            picks.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ColorSurfaceHigh).padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(q.emoji, fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(q.title, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(q.note, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    IconButton(onClick = { onAdd(q) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add quest", tint = ColorPrimary)
                    }
                }
            }
            TextButton(onClick = onAddOwn) { Text("+ Write my own quest", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HomeQuestRow(task: TaskEntity, now: Long, onComplete: () -> Unit) {
    val catColor  = CatColors[task.category] ?: ColorPrimary
    val isOverdue = task.dueAt != null && task.dueAt < now
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val tf  = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(catColor))
            Row(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = ColorText,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(questTypeInfo(task.questType).emoji, fontSize = 12.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.15f)) {
                            Text(task.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = catColor)
                        }
                        if (task.dueAt != null) {
                            Text((if (task.isRecurring) tf else sdf).format(Date(task.dueAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) ColorDanger else ColorMuted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                IconButton(onClick = onComplete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                        tint = ColorPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
