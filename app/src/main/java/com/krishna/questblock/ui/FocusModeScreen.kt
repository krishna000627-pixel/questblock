@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Session state lives in the database (Player.focusModeUntil), so it survives navigation and app kills.
    init { settle() }

    fun start(durationMs: Long, quest: TaskEntity?) = viewModelScope.launch { repo.startFocus(durationMs, quest) }
    fun end()    = viewModelScope.launch { repo.finishFocus(completed = false) }
    fun settle() = viewModelScope.launch { repo.settleFocus() }
}

private val DURATIONS = listOf(15, 25, 30, 45, 60, 90)

@Composable
fun FocusModeScreen(vm: FocusViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks  by vm.tasks.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }

    val remaining = (player.focusModeUntil - now).coerceAtLeast(0L)
    val running   = player.focusModeUntil > now
    // Timer hit zero (or app was reopened after it expired) -> pay out once.
    LaunchedEffect(player.focusModeUntil, running) {
        if (player.focusModeUntil > 0L && !running) vm.settle()
    }

    val timerQuests = tasks.filter { !it.done && it.isTimerQuest }
    val activeQuest = timerQuests.firstOrNull { it.id == player.focusQuestId }
        ?: tasks.firstOrNull { it.id == player.focusQuestId && player.focusQuestId > 0 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = ColorText)

        if (running) {
            ActiveFocusCard(
                remainingMs = remaining, totalMs = player.focusTotalMs,
                questTitle = activeQuest?.title,
                appCount = activeQuest?.linkedApps?.split(",")?.count { it.isNotBlank() } ?: 0,
                onStop = { vm.end() },
            )
        } else {
            SetupCard(timerQuests = timerQuests, onStart = { dur, quest -> vm.start(dur, quest) })
        }
    }
}

// One card, one flow: pick what you're focusing on -> (duration) -> start.
@Composable
private fun SetupCard(timerQuests: List<TaskEntity>, onStart: (Long, TaskEntity?) -> Unit) {
    var minutes by remember { mutableIntStateOf(25) }
    var quest by remember { mutableStateOf<TaskEntity?>(null) }
    // quest may be deleted/completed while this screen is open
    val selected = quest?.let { q -> timerQuests.firstOrNull { it.id == q.id } }
    val questMs = selected?.timerDurationMs?.takeIf { it > 0 } ?: 60 * 60_000L
    val durationMs = if (selected != null) questMs else minutes * 60_000L
    val shownMin = (durationMs / 60_000L).toInt()

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Focus on", style = MaterialTheme.typography.labelLarge, color = ColorMuted,
                fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { PickChip("⚡ Quick focus", quest == null, ColorPrimary) { quest = null } }
                items(timerQuests, key = { it.id }) { t ->
                    PickChip("⏱️ ${t.title}", selected?.id == t.id, ColorAccent) { quest = t }
                }
            }

            if (selected == null) {
                Text("Duration", style = MaterialTheme.typography.labelLarge, color = ColorMuted,
                    fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DURATIONS.forEach { m ->
                        val sel = minutes == m
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) ColorPrimary.copy(0.2f) else ColorSurfaceHigh)
                                .clickable { minutes = m },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${m}m", maxLines = 1, color = if (sel) ColorPrimary else ColorMuted,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                val apps = selected.linkedApps.split(",").count { it.isNotBlank() }
                Text(
                    "⏱️ ${shownMin}m" + if (apps > 0) " · unlocks $apps app${if (apps > 1) "s" else ""}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = ColorAccent, fontWeight = FontWeight.SemiBold,
                )
            }

            val reward = FOCUS_BONUS_COINS + (selected?.let { baseReward(it.difficulty) } ?: 0)
            Text(
                "Blocked apps stay locked · finish for +$reward🪙" +
                    if (selected != null) " and the quest completes" else "",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            )

            Button(
                onClick = { onStart(durationMs, selected) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start ${shownMin}min Focus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
private fun ActiveFocusCard(remainingMs: Long, totalMs: Long, questTitle: String?, appCount: Int, onStop: () -> Unit) {
    val totalSec = (remainingMs / 1000).coerceAtLeast(0)
    val frac = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (questTitle != null) "🎯 $questTitle" else "🎯 Focus session",
                style = MaterialTheme.typography.titleSmall, color = ColorPrimary, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(ColorPrimary.copy(0.15f), -90f, 360f, false, style = stroke)
                    drawArc(ColorPrimary, -90f, 360f * frac, false, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(totalSec / 60, totalSec % 60), fontSize = 38.sp,
                        fontWeight = FontWeight.Black, color = ColorText)
                    Text("remaining", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }

            Text(
                "Blocked apps stay locked" + if (appCount > 0) " · $appCount linked app${if (appCount > 1) "s" else ""} unlocked" else "",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            )

            OutlinedButton(
                onClick = onStop,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("End early (no reward)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
