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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
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
    fun complete(task: TaskEntity) = viewModelScope.launch { repo.completeTask(task) }
}

@Composable
fun HomeScreen(onAddQuest: () -> Unit, onGoFocus: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val player       by vm.player.collectAsStateWithLifecycle()
    val tasks        by vm.tasks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()

    val now      = System.currentTimeMillis()
    val pending  = tasks.filter { !it.done }
    val overdue  = pending.filter { it.dueAt != null && it.dueAt < now }
    val upcoming = pending.filter { it.dueAt != null && it.dueAt >= now }.sortedBy { it.dueAt }
    val noDate   = pending.filter { it.dueAt == null }
    val doneToday = tasks.count { it.done }
    val recentAch = achievements.filter { it.unlockedAt != null }.sortedByDescending { it.unlockedAt }.take(3)

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Hero card ──────────────────────────────────────────────────────
        item { HeroCard(player = player, doneToday = doneToday, total = tasks.size) }

        // ── Focus mode shortcut ────────────────────────────────────────────
        item {
            FocusShortcut(
                isActive = player.isFocusMode(),
                remainingMs = (player.focusModeUntil - now).coerceAtLeast(0),
                onClick = onGoFocus,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ── Overdue banner ─────────────────────────────────────────────────
        if (overdue.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorDanger.copy(0.12f)),
                    shape  = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ColorDanger, modifier = Modifier.size(20.dp))
                        Column {
                            Text("${overdue.size} overdue quest${if (overdue.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelLarge, color = ColorDanger, fontWeight = FontWeight.Bold)
                            Text(overdue.first().title, style = MaterialTheme.typography.bodySmall, color = ColorDanger.copy(0.8f))
                        }
                    }
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
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activePowerUps) { (label, mins) ->
                        Surface(shape = RoundedCornerShape(20.dp), color = ColorPrimary.copy(0.15f)) {
                            Text("$label ${mins}m", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall, color = ColorPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Today's progress ───────────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Today's quests", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Text("$doneToday/${tasks.size} done", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
            }
        }

        // Upcoming quests (with due dates)
        if (upcoming.isNotEmpty()) {
            item { SubHeader("⏰ Upcoming") }
            items(upcoming.take(3), key = { "up_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }

        // No-date quests
        if (noDate.isNotEmpty()) {
            item { SubHeader("📋 To Do") }
            items(noDate.take(3), key = { "nd_${it.id}" }) { task ->
                HomeQuestRow(task = task, now = now, onComplete = { vm.complete(task) })
            }
        }

        if (tasks.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("⚔️", fontSize = 40.sp)
                        Text("No quests yet", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text("Add your first quest to start your journey!",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        Button(onClick = onAddQuest,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                            shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Quest", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Recent achievements ────────────────────────────────────────────
        if (recentAch.isNotEmpty()) {
            item { SubHeader("🏆 Recent Achievements") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentAch) { ach ->
                        Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                            shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp).width(100.dp),
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

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Hero card ──────────────────────────────────────────────────────────────
@Composable
private fun HeroCard(player: Player, doneToday: Int, total: Int) {
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
        modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        fontWeight = FontWeight.Black, color = ColorText)
                    Text("Level $level · ${player.xp} XP total",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    if (player.goal.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = ColorPrimary.copy(0.12f)) {
                            Text("🎯 ${player.goal}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                        }
                    }
                }
            }

            // Stats chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(Modifier.weight(1f), "🪙", "${player.coins}", "coins")
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
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ColorText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
        }
    }
}

@Composable
private fun FocusShortcut(isActive: Boolean, remainingMs: Long, onClick: () -> Unit, modifier: Modifier) {
    val mm = remainingMs / 60_000
    val ss = (remainingMs % 60_000) / 1000
    Card(colors = CardDefaults.cardColors(
        containerColor = if (isActive) ColorPrimary.copy(0.15f) else ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(
                if (isActive) ColorPrimary.copy(0.2f) else ColorSurfaceHigh, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(if (isActive) "🎯" else "⚡", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(if (isActive) "Focus Mode Active" else "Start Focus Mode",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = if (isActive) ColorPrimary else ColorText)
                Text(if (isActive) "%02d:%02d remaining".format(mm, ss) else "Block distractions · earn 2× XP",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ColorMuted)
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HomeQuestRow(task: TaskEntity, now: Long, onComplete: () -> Unit) {
    val catColor  = CatColors[task.category] ?: ColorPrimary
    val isOverdue = task.dueAt != null && task.dueAt < now
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(catColor))
            Row(Modifier.weight(1f).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = ColorText)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = catColor.copy(0.15f)) {
                            Text(task.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = catColor)
                        }
                        if (task.dueAt != null) {
                            Text(sdf.format(Date(task.dueAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) ColorDanger else ColorMuted)
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
