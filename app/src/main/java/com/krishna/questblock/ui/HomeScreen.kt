@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.baseReward
import com.krishna.questblock.data.level
import com.krishna.questblock.data.xpIntoLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun complete(task: TaskEntity) = viewModelScope.launch { repo.completeTask(task) }
}

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks  by vm.tasks.collectAsStateWithLifecycle()

    val today   = tasks.filter { !it.done }
    val doneToday = tasks.count { it.done }

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Hero card ──────────────────────────────────────────────────────
        item {
            HeroCard(player = player)
        }

        // ── Active power-ups (if any) ──────────────────────────────────────
        val now = System.currentTimeMillis()
        val activePowerUps = buildList {
            if (now < player.xpBoostUntil)       add("⚡ XP Boost")
            if (now < player.coinMultiplierUntil) add("🧲 Coin Magnet")
            if (now < player.streakShieldUntil)   add("🛡️ Streak Shield")
        }
        if (activePowerUps.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activePowerUps.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = ColorPrimary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                label,
                                modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style      = MaterialTheme.typography.labelSmall,
                                color      = ColorPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // ── Today's quests header ──────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Today's quests", style = MaterialTheme.typography.titleMedium,
                    color = ColorText, fontWeight = FontWeight.SemiBold)
                Text("$doneToday / ${tasks.size} done",
                    style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
            }
        }

        // ── Quest list ─────────────────────────────────────────────────────
        if (today.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape  = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (tasks.isEmpty()) {
                            Text("No quests yet", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = ColorText)
                            Text("Add your first quest to start earning coins.",
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                            Button(
                                onClick = { showSheet = true },
                                colors  = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                                shape   = RoundedCornerShape(20.dp),
                            ) { Text("Add a quest", fontWeight = FontWeight.Bold) }
                        } else {
                            Text("🎉 All done for today!", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = ColorPrimary)
                            Text("Amazing streak! Come back tomorrow.",
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                    }
                }
            }
        } else {
            items(today.take(5), key = { it.id }) { task ->
                HomeQuestRow(task = task, onComplete = { vm.complete(task) })
            }
            if (today.size > 5) {
                item {
                    Text("+${today.size - 5} more in Quests tab",
                        style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }
        }
    }

    // Add quest quick-sheet (from empty state button)
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState       = sheetState,
            containerColor   = ColorSurface,
        ) {
            // Reuse QuestsScreen's AddQuestSheet via the shared ViewModel
            val qVm: QuestsViewModel = hiltViewModel()
            AddQuestSheetContent(onAdd = { title, diff ->
                qVm.add(title, diff)
                scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
            })
        }
    }
}

// ── Hero card ──────────────────────────────────────────────────────────────
@Composable
private fun HeroCard(player: Player) {
    val level = player.level()
    val xp    = player.xpIntoLevel()

    // Frame color based on equipped frame
    val frameColor = when (player.equippedFrame) {
        "frame_fire"   -> androidx.compose.ui.graphics.Color(0xFFFF6B35)
        "frame_ice"    -> androidx.compose.ui.graphics.Color(0xFF64B5F6)
        "frame_gold"   -> ColorCoin
        "frame_galaxy" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        else           -> ColorPrimary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape  = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Avatar circle with equipped frame
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(56.dp)
                            .background(Brush.radialGradient(listOf(frameColor.copy(0.4f), frameColor.copy(0.1f))), CircleShape)
                    )
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(frameColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$level", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = frameColor)
                    }
                }

                Column(Modifier.weight(1f)) {
                    val displayName = player.name.ifBlank { "Hero" }
                    Text("Level $level · $displayName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🪙 ${player.coins}", style = MaterialTheme.typography.labelMedium, color = ColorCoin)
                        Text("🔥 ${player.streak}d", style = MaterialTheme.typography.labelMedium, color = ColorDanger)
                        Text("⚡ $xp/100", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                    }
                    // Goal badge if set
                    if (player.goal.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ColorPrimary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                "🎯 ${player.goal}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorPrimary,
                            )
                        }
                    }
                    // Equipped badge
                    if (player.equippedBadge.isNotBlank()) {
                        val badgeEmoji = when (player.equippedBadge) {
                            "badge_star"   -> "⭐"
                            "badge_crown"  -> "👑"
                            "badge_sword"  -> "⚔️"
                            "badge_dragon" -> "🐉"
                            else           -> ""
                        }
                        if (badgeEmoji.isNotBlank()) Text(badgeEmoji, fontSize = 18.sp)
                    }
                }
            }

            // XP bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress          = { xp / 100f },
                    modifier          = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color             = ColorPrimary,
                    trackColor        = ColorSurfaceHigh,
                )
                Text("$xp / 100 XP to level ${level + 1}",
                    style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
        }
    }
}

// ── Quest row on Home ──────────────────────────────────────────────────────
private val DIFF_COLORS_H = listOf(ColorPrimary, ColorCoin, ColorDanger)
private val DIFF_NAMES_H  = listOf("Easy", "Medium", "Hard")

@Composable
private fun HomeQuestRow(task: TaskEntity, onComplete: () -> Unit) {
    val color = DIFF_COLORS_H.getOrElse(task.difficulty - 1) { ColorPrimary }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape  = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Box(
                Modifier.size(36.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onComplete, modifier = Modifier.size(36.dp)) {
                    Box(Modifier.size(14.dp).background(color.copy(0.5f), CircleShape))
                }
            }

            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, color = ColorText)
                Text("${DIFF_NAMES_H.getOrElse(task.difficulty - 1) { "Easy" }} · +${baseReward(task.difficulty)} 🪙",
                    style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
        }
    }
}

// ── Shared Add-quest sheet content ─────────────────────────────────────────
@Composable
fun AddQuestSheetContent(onAdd: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(1) }
    val DIFF_NAMES  = listOf("Easy", "Medium", "Hard")
    val DIFF_COLORS = listOf(ColorPrimary, ColorCoin, ColorDanger)

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("New Quest", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = ColorText)

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            placeholder = { Text("What do you need to do?", color = ColorMuted) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ColorPrimary, unfocusedBorderColor = ColorSurfaceHigh,
                cursorColor = ColorPrimary, focusedTextColor = ColorText, unfocusedTextColor = ColorText,
            ),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { d ->
                val c = DIFF_COLORS[d - 1]
                FilterChip(
                    selected = difficulty == d, onClick = { difficulty = d },
                    label    = { Text("${DIFF_NAMES[d - 1]} +${baseReward(d)}🪙") },
                    modifier = Modifier.weight(1f),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = c.copy(alpha = 0.2f), selectedLabelColor = c,
                        containerColor = ColorSurfaceHigh, labelColor = ColorMuted,
                    ),
                )
            }
        }

        Button(
            onClick  = { if (title.isNotBlank()) onAdd(title.trim(), difficulty) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            shape    = RoundedCornerShape(12.dp),
        ) { Text("Add Quest", fontWeight = FontWeight.Bold) }
    }
}
