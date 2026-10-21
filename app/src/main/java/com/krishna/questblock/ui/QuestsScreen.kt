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
import androidx.compose.ui.text.style.TextOverflow
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
class QuestsViewModel @Inject constructor(private val repo: GameRepository, val prefs: Prefs) : ViewModel() {
    val tasks  = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val subs   = repo.allSubTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun toggleStep(step: SubTask) = viewModelScope.launch { repo.toggleSubTask(step) }
    fun add(title: String, difficulty: Int) = viewModelScope.launch { repo.addTask(title, difficulty) }
    fun complete(task: TaskEntity) = viewModelScope.launch {
        if (prefs.optUndo) repo.completeTaskUndoable(task)?.let { UndoBus.events.tryEmit(it) } else repo.completeTask(task)
    }
    fun delete(task: TaskEntity)   = viewModelScope.launch { repo.deleteTask(task) }
    fun startFocus(task: TaskEntity) = viewModelScope.launch {
        repo.startFocus(if (task.timerDurationMs > 0) task.timerDurationMs else 60 * 60_000L, task)
    }
    fun stopFocus() = viewModelScope.launch { repo.abandonFocus() }
    /** Turns a suggestion into a real quest due today. */
    fun addSuggestion(q: StarterQuest) = viewModelScope.launch {
        val dueToday = nextRunDayStart(0, System.currentTimeMillis()) - 1
        repo.addTask(QuestDraft(title = q.title, difficulty = q.difficulty, category = "Study", notes = q.note, dueAt = dueToday))
    }
}

private val DIFF_COLORS: List<androidx.compose.ui.graphics.Color> get() = listOf(ColorPrimary, ColorCoin, ColorDanger)
private val DIFF_NAMES  = listOf("Easy", "Medium", "Hard")
private val FILTER_TABS = listOf("All", "Today", "Active", "Done", "Overdue", "Suggested")

@Composable
fun QuestsScreen(onAddQuest: () -> Unit, onEditQuest: (Long) -> Unit, onOpenFocus: () -> Unit, vm: QuestsViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val subs  by vm.subs.collectAsStateWithLifecycle()
    val stepsByTask = remember(subs) { subs.groupBy { it.taskId } }
    val now   = System.currentTimeMillis()

    // "3/5 dailies done today": dailies finished since midnight + dailies still open today
    val dayStart = startOfToday(now)
    val dailies = tasks.filter { it.questType == "daily" }
    val dailyDone = dailies.count { it.done && (it.completedAt ?: 0L) >= dayStart }
    val dailyTotal = dailyDone + dailies.count { it.isAvailableNow(now) }

    var filterTab by remember { mutableStateOf("All") }
    var filterCat by remember { mutableStateOf("All") }
    var showAllDone by remember { mutableStateOf(false) }

    // Nothing planned for today (or all finished) -> land on the Suggested tab
    val dayEnd = nextRunDayStart(0, now) - 1
    val todayOpen = tasks.count { it.isAvailableNow(now) && it.isForToday(dayEnd) }
    val doneToday = tasks.count { it.done && (it.completedAt ?: 0L) >= dayStart }
    // Only when there are NO unfinished quests at all. The short wait covers the first frame, when the list is still loading.
    val openCount = tasks.count { !it.done }
    LaunchedEffect(openCount) {
        kotlinx.coroutines.delay(600L)
        if (openCount == 0) filterTab = "Suggested"
    }

    val filtered = tasks.filter { task ->
        val tabOk = when (filterTab) {
            "Today"   -> task.isAvailableNow(now)
            "Active"  -> !task.done
            "Done"    -> task.done
            "Overdue" -> !task.done && task.dueAt != null && task.dueAt < now
            "Suggested" -> false
            else      -> true
        }
        val catOk = filterCat == "All" || task.category == filterCat
        tabOk && catOk
    }
    val pending  = filtered.filter { !it.done }
    val done     = filtered.filter { it.done }
    // optional: ask before deleting a quest
    var askDelete by remember { mutableStateOf<TaskEntity?>(null) }
    val confirmDelete = vm.prefs.optConfirm
    val requestDelete: (TaskEntity) -> Unit = { t -> if (confirmDelete) askDelete = t else vm.delete(t) }
    askDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { askDelete = null },
            modifier = Modifier.grayWindow(),
            containerColor = ColorSurface,
            title = { Text("Delete this quest?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = { Text(t.title, color = ColorMuted) },
            confirmButton = { TextButton(onClick = { vm.delete(t); askDelete = null }) { Text("Delete", color = ColorDanger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text("Cancel", color = ColorMuted) } },
        )
    }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TitleWithIcon("\uD83D\uDCCB", "Quests")
                        Text("${tasks.count { it.done }}/${tasks.size} done",
                            style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                    }
                    if (tasks.isNotEmpty() && com.krishna.questblock.data.Flags.get("ui_swipe_hint")) QuestSwipeHint()
                    // Filter tabs
                    PillTabs(
                        labels = FILTER_TABS.map { if (it == "Suggested") "\uD83D\uDCA1 Suggested" else it },
                        selected = FILTER_TABS.indexOf(filterTab).coerceAtLeast(0),
                        onSelect = { filterTab = FILTER_TABS[it] },
                    )
                    // Category filter (the Suggested tab has its own subject chips)
                    if (filterTab != "Suggested") LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            // Empty state (no quests at all -> the suggestions card below takes its place)
            if (filterTab == "Suggested") {
                item(key = "suggested_page") {
                    SuggestedPage(
                        header = if (doneToday > 0) "All done \uD83C\uDF89 Pick your next one" else "Nothing planned today \u2014 pick one",
                        onAdd = { vm.addSuggestion(it) },
                    )
                }
            }
            if (filtered.isEmpty() && tasks.isNotEmpty() && filterTab != "Suggested") {
                item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EmptyArt("clipboard")
                            Text(ThemeExtras.word("emptyQuests", "Nothing here"), style = MaterialTheme.typography.titleMedium, color = ColorText)
                        }
                    }
                }
            }

            // Pending quests, grouped by type (dailies first)
            QUEST_TYPES.forEach { type ->
                val group = pending.filter { (if (questTypeInfo(it.questType).id == it.questType) it.questType else "onetime") == type.id }
                if (group.isNotEmpty()) {
                    item(key = "hdr_${type.id}") {
                        if (type.id == "daily") {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${type.emoji} Dailies", style = MaterialTheme.typography.labelLarge,
                                        color = ColorText, fontWeight = FontWeight.Bold)
                                    Text("$dailyDone/$dailyTotal dailies done today",
                                        style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                                }
                                LinearProgressIndicator(
                                    progress = { if (dailyTotal == 0) 0f else dailyDone / dailyTotal.toFloat() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
                                    color = ColorPrimary, trackColor = ColorSurfaceHigh,
                                )
                            }
                        } else {
                            SectionHeader("${type.emoji} ${type.name} (${group.size})")
                        }
                    }
                    items(group, key = { it.id }) { task ->
                        SwipeableQuestCard(task = task, now = now,
                            steps = stepsByTask[task.id].orEmpty(),
                            onToggleStep = { vm.toggleStep(it) },
                            onComplete = { vm.complete(task) },
                            onDelete   = { requestDelete(task) },
                            confirmDelete = confirmDelete,
                            onEdit     = { onEditQuest(task.id) },
                            onStartFocus = { vm.startFocus(task); onOpenFocus() },
                            onStopFocus  = { vm.stopFocus() },
                        )
                    }
                }
            }

            // Completed
            if (done.isNotEmpty()) {
                val doneSorted = done.sortedByDescending { it.completedAt ?: 0L }
                val doneShown = if (showAllDone || filterTab == "Done") doneSorted else doneSorted.take(10)
                item { SectionHeader("Completed (${done.size})") }
                items(doneShown, key = { it.id }) { task ->
                    SwipeableQuestCard(task = task, now = now,
                        steps = stepsByTask[task.id].orEmpty(), onToggleStep = {},
                        onComplete = {}, onDelete = { requestDelete(task) }, confirmDelete = confirmDelete, onEdit = {},
                        onStartFocus = {}, onStopFocus = {},
                    )
                }
                if (filterTab != "Done" && doneSorted.size > 10) item {
                    TextButton(onClick = { showAllDone = !showAllDone }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showAllDone) "Show fewer" else "Show all ${doneSorted.size}", color = ColorMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SwipeableQuestCard(
    task: TaskEntity, now: Long, steps: List<SubTask>, onToggleStep: (SubTask) -> Unit,
    onComplete: () -> Unit, onDelete: () -> Unit, confirmDelete: Boolean = false, onEdit: () -> Unit,
    onStartFocus: () -> Unit, onStopFocus: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                EndToStart -> { onDelete(); !confirmDelete }
                StartToEnd -> {
                    // only swipe-complete when it can really be completed (right day, routine steps all ticked)
                    val ok = !task.done && task.isAvailableNow(now) && steps.all { it.done }
                    if (ok) onComplete()
                    ok
                }
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
            Box(Modifier.fillMaxSize()
                .clip(RoundedCornerShape(14.dp)).background(color),
                contentAlignment = if (dir == StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                // v278: only while swiping (before, a faint Delete icon showed through the 50% faded done cards)
                if (dir == StartToEnd || dir == EndToStart) Icon(
                    if (dir == StartToEnd) Icons.Default.Check else Icons.Default.Delete,
                    contentDescription = a11yText(if (dir == StartToEnd) "Complete" else "Delete"),
                    tint   = ColorText,
                    modifier = Modifier.padding(16.dp).size(22.dp),
                )
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        QuestCard(task = task, now = now, steps = steps, onToggleStep = onToggleStep,
            onComplete = onComplete, onDelete = onDelete, onEdit = onEdit,
            onStartFocus = onStartFocus, onStopFocus = onStopFocus)
    }
}

@Composable
private fun QuestCard(
    task: TaskEntity, now: Long, steps: List<SubTask>, onToggleStep: (SubTask) -> Unit,
    onComplete: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit,
    onStartFocus: () -> Unit, onStopFocus: () -> Unit,
) {
    val availableNow = task.isAvailableNow(now)
    val isRoutine = task.questType == "routine"
    val stepsDone = steps.count { it.done }
    val canComplete = availableNow && (!isRoutine || steps.all { it.done })
    val streakNow = task.streakNow(dayNumber(now))
    val diffColor  = DIFF_COLORS.getOrElse(task.difficulty - 1) { ColorPrimary }
    val catColor   = CatColors[task.category] ?: ColorPrimary
    val isOverdue  = !task.done && task.dueAt != null && task.dueAt < now
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (task.done) 0.5f else 1f)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(catColor))

            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priColor = when (task.priority) { 1 -> ColorDanger; 3 -> ColorPrimary; else -> ColorWarning }
                    Box(Modifier.size(8.dp).background(priColor, CircleShape))

                    Text(task.title, style = MaterialTheme.typography.bodyMedium, color = ColorText,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None)

                    if (!task.done && canComplete) {
                        IconButton(onClick = onComplete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                                tint = ColorPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!task.done) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit",
                                tint = ColorMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = ColorMuted, modifier = Modifier.size(16.dp))
                    }
                }

                // Tags + due date share one row (plain Row: a scrollable row would fight the swipe gesture)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (SystemOn) TagChip("RANK ${questRank(task.difficulty, task.isTimerQuest)}", diffColor)
                    TagChip(ThemeExtras.diff(task.difficulty, DIFF_NAMES.getOrElse(task.difficulty - 1) { "Easy" }), diffColor)
                    TagChip(task.category, catColor)
                    TagChip("+${baseReward(task.difficulty)}🪙", ColorCoin)
                    if (task.isTimerQuest) TagChip("⏱️", ColorAccent)
                    if (task.questType == "daily" && streakNow > 0) TagChip("🔥 $streakNow", ColorWarning)
                    if (task.questType == "challenge" && task.challengeDays > 0)
                        TagChip("🏆 $streakNow/${task.challengeDays}", ColorWarning)
                    if (isRoutine && steps.isNotEmpty()) TagChip("📋 $stepsDone/${steps.size}", ColorAccent)
                    if (task.daysMask != 0 && task.daysMask != ALL_DAYS_MASK) TagChip("📅 ${daysLabel(task.daysMask)}", ColorAccent)
                    if (task.isRecurring && task.questType !in listOf("daily", "weekly", "challenge")) TagChip("🔁", ColorMuted)
                }

                if (task.dueAt != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp),
                            tint = if (isOverdue) ColorDanger else ColorMuted)
                        Text(
                            if (isOverdue) "Overdue: ${sdf.format(Date(task.dueAt))}" else sdf.format(Date(task.dueAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) ColorDanger else ColorMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!task.done && !availableNow) {
                    Text(
                        if (task.availableFrom > now) "💤 Back on ${SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(task.availableFrom))}"
                        else "💤 Not scheduled today",
                        style = MaterialTheme.typography.labelSmall, color = ColorAccent)
                }

                // Routine checklist
                if (isRoutine && !task.done) {
                    steps.forEach { step ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = availableNow) { onToggleStep(step) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (step.done) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = a11yText(if (step.done) "Done" else "Not done"), modifier = Modifier.size(18.dp),
                                tint = if (step.done) ColorPrimary else ColorMuted)
                            Text(step.title, style = MaterialTheme.typography.bodySmall,
                                color = if (step.done) ColorMuted else ColorText,
                                textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None)
                        }
                    }
                }

                if (task.notes.isNotBlank()) {
                    Text(task.notes, style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Timer quest = a Focus session with a goal attached
                if (task.isTimerQuest && !task.done && availableNow) {
                    if (!task.timerQuestActive) {
                        OutlinedButton(onClick = onStartFocus,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            val mins = if (task.timerDurationMs > 0) task.timerDurationMs / 60_000 else 60
                            Text("Focus ${mins}m", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🎯 In focus", style = MaterialTheme.typography.labelMedium,
                                color = ColorAccent, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = onStopFocus,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)) {
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
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
