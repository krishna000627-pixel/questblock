@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.SwipeToDismissBoxValue.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class QuestsViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    fun add(title: String, difficulty: Int) = viewModelScope.launch { repo.addTask(title, difficulty) }
    fun complete(task: TaskEntity) = viewModelScope.launch { repo.completeTask(task) }
    fun delete(task: TaskEntity)   = viewModelScope.launch { repo.deleteTask(task) }
    fun startFocus(task: TaskEntity) = viewModelScope.launch { repo.startFocusQuest(task) }
    fun stopFocus(task: TaskEntity)  = viewModelScope.launch { repo.stopFocusQuest(task) }
}

private val DIFF_COLORS = listOf(ColorPrimary, ColorCoin, ColorDanger)
private val DIFF_NAMES  = listOf("Easy", "Medium", "Hard")
private val FILTER_TABS = listOf("All", "Active", "Done", "Overdue")

@Composable
fun QuestsScreen(onAddQuest: () -> Unit, vm: QuestsViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val now   = System.currentTimeMillis()

    var filterTab by remember { mutableStateOf("All") }
    var filterCat by remember { mutableStateOf("All") }

    val filtered = tasks.filter { task ->
        val tabOk = when (filterTab) {
            "Active"  -> !task.done
            "Done"    -> task.done
            "Overdue" -> !task.done && task.dueAt != null && task.dueAt < now
            else      -> true
        }
        val catOk = filterCat == "All" || task.category == filterCat
        tabOk && catOk
    }
    val pending  = filtered.filter { !it.done }
    val done     = filtered.filter { it.done }

    Scaffold(
        containerColor = ColorBg,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddQuest, containerColor = ColorPrimary,
                contentColor = ColorOnPrimary, shape = CircleShape) {
                Icon(Icons.Default.Add, "Add quest")
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 80.dp)) {
            // Header
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Quests", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text("${tasks.count { it.done }}/${tasks.size} done",
                            style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                    }
                    // Filter tabs
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTER_TABS) { tab ->
                            val sel = filterTab == tab
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (sel) ColorPrimary.copy(0.2f) else ColorSurface,
                                modifier = Modifier.clickable { filterTab = tab },
                            ) {
                                Text(tab, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = if (sel) ColorPrimary else ColorMuted,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    // Category filter
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            val sel = filterCat == "All"
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = if (sel) ColorMuted.copy(0.2f) else Color.Transparent,
                                modifier = Modifier.border(1.dp, if (sel) ColorMuted else ColorDivider, RoundedCornerShape(16.dp))
                                    .clickable { filterCat = "All" }) {
                                Text("All", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    color = if (sel) ColorText else ColorMuted,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        items(listOf("Study","Work","Health","Personal","Custom")) { cat ->
                            val color = CatColors[cat] ?: ColorPrimary
                            val sel = filterCat == cat
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = if (sel) color.copy(0.2f) else Color.Transparent,
                                modifier = Modifier.border(1.dp, if (sel) color else ColorDivider, RoundedCornerShape(16.dp))
                                    .clickable { filterCat = cat }) {
                                Text(cat, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    color = if (sel) color else ColorMuted,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Empty state
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🗺️", fontSize = 48.sp)
                            Text("No quests", style = MaterialTheme.typography.titleMedium, color = ColorText)
                            Text("Tap + to add one", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                    }
                }
            }

            // Pending quests
            if (pending.isNotEmpty()) {
                item { SectionHeader("Active (${pending.size})") }
                items(pending, key = { it.id }) { task ->
                    SwipeableQuestCard(task = task, now = now,
                        onComplete = { vm.complete(task) },
                        onDelete   = { vm.delete(task) },
                        onStartFocus = { vm.startFocus(task) },
                        onStopFocus  = { vm.stopFocus(task) },
                    )
                }
            }

            // Completed
            if (done.isNotEmpty()) {
                item { SectionHeader("Completed (${done.size})") }
                items(done, key = { it.id }) { task ->
                    SwipeableQuestCard(task = task, now = now,
                        onComplete = {}, onDelete = { vm.delete(task) },
                        onStartFocus = {}, onStopFocus = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SwipeableQuestCard(
    task: TaskEntity, now: Long,
    onComplete: () -> Unit, onDelete: () -> Unit,
    onStartFocus: () -> Unit, onStopFocus: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                EndToStart -> { onDelete(); true }
                StartToEnd -> { if (!task.done) onComplete(); true }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dir) {
                    StartToEnd -> ColorPrimary.copy(0.3f)
                    EndToStart -> ColorDanger.copy(0.3f)
                    else       -> Color.Transparent
                }, label = "swipe_bg"
            )
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp)).background(color),
                contentAlignment = if (dir == StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                Icon(
                    if (dir == StartToEnd) Icons.Default.Check else Icons.Default.Delete,
                    contentDescription = null,
                    tint   = ColorText,
                    modifier = Modifier.padding(16.dp).size(22.dp),
                )
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        QuestCard(task = task, now = now,
            onComplete = onComplete, onDelete = onDelete,
            onStartFocus = onStartFocus, onStopFocus = onStopFocus)
    }
}

@Composable
private fun QuestCard(
    task: TaskEntity, now: Long,
    onComplete: () -> Unit, onDelete: () -> Unit,
    onStartFocus: () -> Unit, onStopFocus: () -> Unit,
) {
    val diffColor  = DIFF_COLORS.getOrElse(task.difficulty - 1) { ColorPrimary }
    val catColor   = CatColors[task.category] ?: ColorPrimary
    val isOverdue  = !task.done && task.dueAt != null && task.dueAt < now
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (task.done) 0.5f else 1f)) {
        Row(Modifier.fillMaxWidth()) {
            // Category colour strip
            Box(Modifier.width(4.dp).fillMaxHeight().background(catColor))

            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Priority dot
                    val priColor = when (task.priority) { 1 -> ColorDanger; 3 -> ColorPrimary; else -> ColorWarning }
                    Box(Modifier.size(8.dp).background(priColor, CircleShape))

                    Text(task.title, style = MaterialTheme.typography.bodyMedium, color = ColorText,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)

                    if (!task.done) {
                        IconButton(onClick = onComplete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                                tint = ColorPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = ColorMuted, modifier = Modifier.size(16.dp))
                    }
                }

                // Tags row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        TagChip(DIFF_NAMES.getOrElse(task.difficulty - 1) { "Easy" }, diffColor)
                    }
                    item { TagChip(task.category, catColor) }
                    item { TagChip("+${baseReward(task.difficulty)}🪙", ColorCoin) }
                    if (task.isFocusQuest) item { TagChip("⏱️ Focus", ColorAccent) }
                    if (task.isRecurring)  item { TagChip("🔁 ${task.recurType}", ColorMuted) }
                }

                // Due date
                if (task.dueAt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp),
                            tint = if (isOverdue) ColorDanger else ColorMuted)
                        Text(
                            if (isOverdue) "Overdue: ${sdf.format(Date(task.dueAt))}"
                            else sdf.format(Date(task.dueAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) ColorDanger else ColorMuted,
                        )
                    }
                }

                // Notes preview
                if (task.notes.isNotBlank()) {
                    Text(task.notes, style = MaterialTheme.typography.bodySmall, color = ColorMuted, maxLines = 1)
                }

                // Focus quest controls
                if (task.isFocusQuest && !task.done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!task.focusActive) {
                            OutlinedButton(onClick = onStartFocus,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val started = task.focusStarted ?: now
                            val elapsed = now - started
                            val rem = (task.focusDurationMs - elapsed).coerceAtLeast(0)
                            Text("⏱ %02d:%02d left".format(rem / 60_000, (rem % 60_000) / 1000),
                                style = MaterialTheme.typography.labelSmall, color = ColorAccent, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = onStopFocus,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Text("Stop", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(0.15f)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color)
    }
}
