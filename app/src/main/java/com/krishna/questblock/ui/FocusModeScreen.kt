@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var remainingMs  by mutableLongStateOf(0L);  private set
    var totalMs      by mutableLongStateOf(0L);  private set
    var isRunning    by mutableStateOf(false);   private set
    private var tickJob: Job? = null

    fun start(durationMs: Long, linkedQuest: TaskEntity? = null) = viewModelScope.launch {
        totalMs = durationMs
        remainingMs = durationMs
        isRunning = true
        repo.startGlobalFocus(durationMs)
        linkedQuest?.let { repo.startFocusQuest(it) }
        tick()
    }

    fun stop(linkedQuest: TaskEntity? = null) = viewModelScope.launch {
        tickJob?.cancel()
        isRunning = false
        remainingMs = 0L
        repo.endGlobalFocus()
        linkedQuest?.let { repo.stopFocusQuest(it) }
    }

    private fun tick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (remainingMs > 0) {
                delay(1000L)
                remainingMs = (remainingMs - 1000L).coerceAtLeast(0L)
            }
            if (isRunning) {
                isRunning = false
                repo.endGlobalFocus()
            }
        }
    }

    fun completeQuest(task: TaskEntity) = viewModelScope.launch { repo.completeTask(task) }
    fun stopFocusQuest(task: TaskEntity) = viewModelScope.launch { repo.stopFocusQuest(task) }
}

private val DURATIONS = listOf(15 to "15m", 25 to "25m", 30 to "30m", 45 to "45m", 60 to "1h", 90 to "90m")

@Composable
fun FocusModeScreen(vm: FocusViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks  by vm.tasks.collectAsStateWithLifecycle()

    val pending      = tasks.filter { !it.done }
    val focusQuests  = pending.filter { it.isFocusQuest }
    val activeQuests = pending.filter { it.isFocusQuest && it.focusActive }

    var selectedMins  by remember { mutableIntStateOf(25) }
    var linkedQuest   by remember { mutableStateOf<TaskEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Focus Mode", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        if (vm.isRunning) {
            // ── Active session ─────────────────────────────────────────────
            item {
                ActiveTimerCard(
                    remainingMs = vm.remainingMs,
                    totalMs     = vm.totalMs,
                    onStop      = { vm.stop(linkedQuest) },
                )
            }
            if (activeQuests.isNotEmpty()) {
                item {
                    Text("Linked quests", style = MaterialTheme.typography.labelMedium,
                        color = ColorMuted, fontWeight = FontWeight.SemiBold)
                }
                items(activeQuests, key = { it.id }) { task ->
                    ActiveQuestRow(task = task, now = System.currentTimeMillis(),
                        onComplete = { vm.completeQuest(task) },
                        onStop     = { vm.stopFocusQuest(task) })
                }
            }
        } else {
            // ── Setup ──────────────────────────────────────────────────────
            item {
                // Duration picker
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Session duration", style = MaterialTheme.typography.labelMedium,
                            color = ColorMuted, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DURATIONS.forEach { (mins, label) ->
                                val sel = selectedMins == mins
                                Box(
                                    Modifier.weight(1f)
                                        .background(if (sel) ColorPrimary.copy(0.2f) else ColorSurfaceHigh, RoundedCornerShape(8.dp))
                                        .clickable { selectedMins = mins }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sel) ColorPrimary else ColorMuted,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // Link a focus quest
            if (focusQuests.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                        shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Link a quest (optional)", style = MaterialTheme.typography.labelMedium,
                                color = ColorMuted, fontWeight = FontWeight.SemiBold)
                            Text("Linked quest apps unlock during this session",
                                style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            focusQuests.forEach { task ->
                                val sel = linkedQuest?.id == task.id
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (sel) ColorAccent.copy(0.15f) else ColorSurfaceHigh, RoundedCornerShape(10.dp))
                                        .clickable { linkedQuest = if (sel) null else task }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RadioButton(selected = sel, onClick = { linkedQuest = if (sel) null else task },
                                        colors = RadioButtonDefaults.colors(selectedColor = ColorAccent))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, color = ColorText,
                                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        val apps = task.linkedApps.split(",").filter { it.isNotBlank() }
                                        if (apps.isNotEmpty())
                                            Text("🔓 Unlocks ${apps.size} app(s)",
                                                style = MaterialTheme.typography.labelSmall, color = ColorAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Info card
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("During this session:", style = MaterialTheme.typography.labelSmall,
                            color = ColorMuted, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "🔒 All blocked apps stay locked",
                            "📚 Study apps keep earning 🪙",
                            if (linkedQuest != null) "🔓 ${linkedQuest!!.title} apps unlocked" else "⚡ 2× XP on all quest completions",
                            "🎁 +15🪙 bonus when session ends",
                        ).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                        }
                    }
                }
            }

            // Start button
            item {
                Button(
                    onClick = { vm.start(selectedMins * 60_000L, linkedQuest) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start ${selectedMins}min Focus", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ActiveTimerCard(remainingMs: Long, totalMs: Long, onStop: () -> Unit) {
    val sec  = (remainingMs / 1000).coerceAtLeast(0)
    val mm   = sec / 60
    val ss   = sec % 60
    val frac = if (totalMs > 0) remainingMs.toFloat() / totalMs else 0f

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("🎯 Focus Active", style = MaterialTheme.typography.labelLarge,
                color = ColorPrimary, fontWeight = FontWeight.Bold)

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Canvas(Modifier.size(150.dp)) {
                    drawArc(color = ColorPrimary.copy(0.12f), startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(color = ColorPrimary, startAngle = -90f, sweepAngle = frac * 360f,
                        useCenter = false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(mm, ss), fontSize = 34.sp,
                        fontWeight = FontWeight.Black, color = ColorText)
                    Text("remaining", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }

            OutlinedButton(onClick = onStop, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger)) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("End Session", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActiveQuestRow(task: TaskEntity, now: Long, onComplete: () -> Unit, onStop: () -> Unit) {
    val started = task.focusStarted ?: now
    val elapsed = now - started
    val rem = (task.focusDurationMs - elapsed).coerceAtLeast(0L)
    val mm = rem / 60_000; val ss = (rem % 60_000) / 1000

    Card(colors = CardDefaults.cardColors(containerColor = ColorAccent.copy(0.1f)),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(ColorAccent.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center) { Text("⏱️", fontSize = 18.sp) }
            Column(Modifier.weight(1f)) {
                Text(task.title, color = ColorText, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall)
                Text("%02d:%02d left".format(mm, ss), color = ColorAccent,
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onComplete,
                colors = ButtonDefaults.textButtonColors(contentColor = ColorPrimary)) {
                Text("Done", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Stop, null, tint = ColorDanger, modifier = Modifier.size(16.dp))
            }
        }
    }
}
