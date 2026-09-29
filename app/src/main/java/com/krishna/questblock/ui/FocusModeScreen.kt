@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.isFocusMode
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

    private var tickJob: Job? = null
    var remainingMs by mutableLongStateOf(0L)
        private set
    var isRunning by mutableStateOf(false)
        private set

    fun startFocus(durationMs: Long, questId: Long = -1L) = viewModelScope.launch {
        repo.startFocusMode(durationMs, questId)
        remainingMs = durationMs
        isRunning = true
        tick()
    }

    fun stop() = viewModelScope.launch {
        tickJob?.cancel()
        isRunning = false
        remainingMs = 0L
        repo.endFocusMode()
    }

    private fun tick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (remainingMs > 0) {
                delay(1000L)
                remainingMs -= 1000L
                if (remainingMs <= 0) {
                    isRunning = false
                    repo.endFocusMode()
                    // Award bonus XP for completing focus session
                    break
                }
            }
        }
    }

    fun startTimerQuest(task: TaskEntity) = viewModelScope.launch { repo.startTimerQuest(task) }
    fun stopTimerQuest(task: TaskEntity)  = viewModelScope.launch { repo.stopTimerQuest(task) }
    fun completeQuest(task: TaskEntity)   = viewModelScope.launch { repo.completeTask(task) }
}

@Composable
fun FocusModeScreen(vm: FocusViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks  by vm.tasks.collectAsStateWithLifecycle()
    val pending = tasks.filter { !it.done }
    val timerQuests = pending.filter { it.isTimerQuest }

    var selectedDurationMin by remember { mutableIntStateOf(25) }
    var selectedQuest by remember { mutableStateOf<TaskEntity?>(null) }

    val DURATIONS = listOf(15, 25, 30, 45, 60, 90)

    Scaffold(containerColor = ColorBg) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Focus Mode", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = ColorText)

            if (vm.isRunning) {
                // ── Active focus session ──────────────────────────────────
                ActiveFocusCard(remainingMs = vm.remainingMs, onStop = { vm.stop() })
            } else {
                // ── Setup ─────────────────────────────────────────────────
                // Duration selector
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Session duration", style = MaterialTheme.typography.labelLarge,
                            color = ColorMuted, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DURATIONS.forEach { mins ->
                                val sel = selectedDurationMin == mins
                                Box(
                                    Modifier.weight(1f).height(40.dp)
                                        .background(if (sel) ColorPrimary.copy(0.2f) else ColorSurfaceHigh, RoundedCornerShape(8.dp))
                                        .let { if (sel) it else it },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(onClick = { selectedDurationMin = mins }, modifier = Modifier.fillMaxSize()) {
                                        Text("${mins}m", color = if (sel) ColorPrimary else ColorMuted,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Link to a timer quest
                if (timerQuests.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                        shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Link to Timer Quest (optional)", style = MaterialTheme.typography.labelLarge,
                                color = ColorMuted, fontWeight = FontWeight.SemiBold)
                            timerQuests.forEach { task ->
                                val sel = selectedQuest?.id == task.id
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (sel) ColorAccent.copy(0.15f) else ColorSurfaceHigh, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RadioButton(selected = sel, onClick = { selectedQuest = if (sel) null else task },
                                        colors = RadioButtonDefaults.colors(selectedColor = ColorAccent))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                                        val apps = task.linkedApps.split(",").filter { it.isNotBlank() }
                                        if (apps.isNotEmpty())
                                            Text("Unlocks ${apps.size} app(s)", style = MaterialTheme.typography.labelSmall, color = ColorAccent)
                                    }
                                }
                            }
                        }
                    }
                }

                // What happens info card
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("During focus session:", style = MaterialTheme.typography.labelMedium,
                            color = ColorMuted, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "🔒 All blocked apps stay locked",
                            "📚 Study apps keep earning coins",
                            if (selectedQuest != null) "🔓 Timer quest apps get unlocked" else "⏱️ Link a timer quest to unlock specific apps",
                            "⚡ 2× XP boost for this session",
                        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = ColorMuted) }
                    }
                }

                // Start button
                Button(
                    onClick = {
                        val durMs = selectedDurationMin * 60_000L
                        selectedQuest?.let { vm.startTimerQuest(it) }
                        vm.startFocus(durMs, selectedQuest?.id ?: -1L)
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start ${selectedDurationMin}min Focus", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // ── Active timer quests ───────────────────────────────────────
            val activeTimerQuests = pending.filter { it.isTimerQuest && it.timerQuestActive }
            if (activeTimerQuests.isNotEmpty()) {
                Text("Active Timer Quests", style = MaterialTheme.typography.labelLarge,
                    color = ColorMuted, fontWeight = FontWeight.SemiBold)
                activeTimerQuests.forEach { task ->
                    ActiveTimerQuestCard(task, onComplete = { vm.completeQuest(task) }, onStop = { vm.stopTimerQuest(task) })
                }
            }
        }
    }
}

@Composable
private fun ActiveFocusCard(remainingMs: Long, onStop: () -> Unit) {
    val totalSec = (remainingMs / 1000).coerceAtLeast(0)
    val mm = totalSec / 60
    val ss = totalSec % 60
    val progress = (remainingMs / 1000f).coerceIn(0f, Float.MAX_VALUE)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(0.85f, 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape  = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("🎯 Focus Session Active", style = MaterialTheme.typography.labelLarge,
                color = ColorPrimary, fontWeight = FontWeight.Bold)

            // Circular timer
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                val color = ColorPrimary
                Canvas(Modifier.size(160.dp)) {
                    drawArc(color = color.copy(0.15f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                    val sweep = if (progress > 0) (progress % (if (mm > 0) mm * 60f else 60f)) / (if (mm > 0) mm * 60f else 60f) * 360f else 0f
                    drawArc(color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:%02d".format(mm, ss), fontSize = 36.sp,
                        fontWeight = FontWeight.Black, color = ColorText)
                    Text("remaining", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }

            OutlinedButton(
                onClick = onStop,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                border  = ButtonDefaults.outlinedButtonBorder,
                shape   = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("End Focus", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActiveTimerQuestCard(task: TaskEntity, onComplete: () -> Unit, onStop: () -> Unit) {
    val started = task.timerQuestStarted ?: System.currentTimeMillis()
    val elapsed = System.currentTimeMillis() - started
    val remaining = (task.timerDurationMs - elapsed).coerceAtLeast(0)
    val mm = remaining / 60_000
    val ss = (remaining % 60_000) / 1000

    Card(colors = CardDefaults.cardColors(containerColor = ColorAccent.copy(0.1f)),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(ColorAccent.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center) {
                Text("⏱️", fontSize = 20.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, color = ColorText, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
                Text("%02d:%02d left".format(mm, ss), color = ColorAccent,
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onComplete,
                colors = ButtonDefaults.textButtonColors(contentColor = ColorPrimary)) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = ColorDanger, modifier = Modifier.size(18.dp))
            }
        }
    }
}
