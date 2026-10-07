@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.ALL_DAYS_MASK
import com.krishna.questblock.data.dowIndex
import com.krishna.questblock.data.fmtMinute
import com.krishna.questblock.data.DAY_LABELS
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.QuestDraft
import com.krishna.questblock.data.questTypeInfo
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.WEEKDAYS_MASK
import com.krishna.questblock.data.WEEKEND_MASK
import com.krishna.questblock.data.daysLabel
import com.krishna.questblock.data.maskHas
import com.krishna.questblock.ui.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class QuestCreatorViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** > 0 when editing an existing quest (nav arg "taskId"). */
    val editId: Long = savedStateHandle.get<Long>("taskId") ?: -1L
    /** Type chosen on the picker page (nav arg "type"); edit mode takes the saved quest's type instead. */
    val initialType: String = savedStateHandle.get<String>("type").orEmpty().ifBlank { "onetime" }
    suspend fun load(): TaskEntity? = if (editId > 0) repo.getTask(editId) else null
    suspend fun loadSteps(): List<String> = if (editId > 0) repo.getSubTasks(editId).map { it.title } else emptyList()

    private val _installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed = _installed.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _installed.value = pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun save(draft: QuestDraft, onDone: () -> Unit) = viewModelScope.launch {
        if (editId > 0) {
            repo.updateTask(editId, draft)
        } else {
            repo.addTask(draft)
            repo.unlockFirstQuestAchievement()
        }
        onDone()
    }
}

private val CATEGORIES = listOf("Study","Work","Health","Personal","Custom")
private val DIFFICULTIES = listOf("Easy" to 1, "Medium" to 2, "Hard" to 3)
private val PRIORITIES = listOf("🔴 High" to 1, "🟡 Med" to 2, "🟢 Low" to 3)
private val REPEAT_MODES = listOf("once" to "Once", "daily" to "Every day", "days" to "Chosen days")

private fun minuteOfDay(ms: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = ms
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** Next time the clock shows [minute] (today if still ahead, else tomorrow). */
private fun nextOccurrence(minute: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, minute / 60); c.set(Calendar.MINUTE, minute % 60)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

private data class Choice(val label: String, val sub: String?, val value: Int, val color: Color)

@Composable
fun QuestCreatorScreen(
    onBack: () -> Unit,
    vm: QuestCreatorViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val installed by vm.installed.collectAsState()

    val isEdit = vm.editId > 0
    var questType  by remember { mutableStateOf(vm.initialType) }
    var title      by remember { mutableStateOf("") }
    var notes      by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(1) }
    var category   by remember { mutableStateOf("Personal") }
    var priority   by remember { mutableIntStateOf(2) }
    var dueAt      by remember { mutableStateOf<Long?>(null) }
    var origDueAt  by remember { mutableStateOf<Long?>(null) }   // due time of the saved quest (edit mode)
    var repeat     by remember { mutableStateOf("once") }        // Routine / Focus only: once | daily | days
    var dailyMinute by remember { mutableStateOf<Int?>(null) }   // Daily only: optional time of day
    var daysMask   by remember { mutableIntStateOf(0) }          // Weekly, or Routine/Focus with "Chosen days"
    var timerHours by remember { mutableIntStateOf(if (vm.initialType == "focus") 0 else 1) }
    var timerMins  by remember { mutableIntStateOf(if (vm.initialType == "focus") 30 else 0) }
    var linkedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var challengeDays by remember { mutableIntStateOf(7) }
    var showMore   by remember { mutableStateOf(false) }
    val steps = remember { mutableStateListOf("") }

    // Edit mode: fill the form from the saved quest and map old combinations onto the new controls
    LaunchedEffect(Unit) {
        vm.load()?.let { t ->
            var type = t.questType
            val restricted = t.daysMask != 0 && t.daysMask != ALL_DAYS_MASK
            // A one-time quest that was made recurring is really a Daily / Weekly; a Daily on some days is a Weekly
            if (type == "onetime" && t.isRecurring) type = if (t.daysMask != 0 || t.recurType == "weekly") "weekly" else "daily"
            if (type == "daily" && restricted) type = "weekly"
            questType = type
            title = t.title; notes = t.notes; difficulty = t.difficulty; category = t.category
            priority = t.priority; dueAt = t.dueAt; origDueAt = t.dueAt
            val anchorDay = dowIndex(t.dueAt ?: t.createdAt)
            daysMask = when {
                t.daysMask != 0 -> t.daysMask
                t.isRecurring && t.recurType == "weekly" -> 1 shl anchorDay   // old "weekly, no days" = same weekday
                else -> 0
            }
            repeat = when {
                !t.isRecurring -> "once"
                restricted || (t.recurType == "weekly" && t.daysMask == 0) -> "days"
                else -> "daily"
            }
            dailyMinute = if (type == "daily" && t.dueAt != null) minuteOfDay(t.dueAt) else null
            timerHours = (t.timerDurationMs / 3_600_000L).toInt()
            timerMins = ((t.timerDurationMs % 3_600_000L) / 60_000L).toInt()
            linkedApps = t.linkedApps.split(",").filter { it.isNotBlank() }.toSet()
            if (t.challengeDays > 0) challengeDays = t.challengeDays
            val saved = vm.loadSteps()
            steps.clear(); steps.addAll(saved.ifEmpty { listOf("") })
            showMore = t.notes.isNotBlank() || ((type == "routine" || type == "focus") && !t.isRecurring && t.dueAt != null)
        }
    }

    val isDaily = questType == "daily"
    val isWeekly = questType == "weekly"
    val isOneTime = questType == "onetime"
    val isChallenge = questType == "challenge"
    val isRoutine = questType == "routine"
    val isFocus = questType == "focus"
    val hasRepeat = isRoutine || isFocus
    val typeInfo = questTypeInfo(questType)
    val pickedMask = daysMask and ALL_DAYS_MASK
    val needsDays = (isWeekly || (hasRepeat && repeat == "days")) && pickedMask == 0
    val routineNeedsSteps = isRoutine && steps.none { it.isNotBlank() }
    val canSave = title.isNotBlank() && !needsDays && !routineNeedsSteps
    val dueInMore = hasRepeat && repeat == "once"      // One-time shows its due date inline instead

    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    /** The type decides isRecurring / recurType / daysMask / dueAt, so no inconsistent combination can be saved. */
    fun buildDraft(): QuestDraft {
        val durMs = (timerHours * 3600L + timerMins * 60L) * 1000L
        var rec = false; var rt = ""; var mask = 0; var due: Long? = null
        when {
            isDaily -> {
                rec = true; rt = "daily"
                val m = dailyMinute
                if (m != null) {
                    // all-days mask + a due time = comes back tomorrow at the same time (no repo change needed)
                    mask = ALL_DAYS_MASK
                    val keep = origDueAt
                    due = if (keep != null && minuteOfDay(keep) == m) keep else nextOccurrence(m)
                }
            }
            isWeekly -> { rec = true; rt = "weekly"; mask = pickedMask; due = origDueAt }
            isChallenge -> { rec = true; rt = "daily" }
            isOneTime -> { due = dueAt }
            else -> when (repeat) {
                "daily" -> { rec = true; rt = "daily" }
                "days"  -> { rec = true; rt = "daily"; mask = if (pickedMask == ALL_DAYS_MASK) 0 else pickedMask }
                else    -> { due = dueAt }
            }
        }
        return QuestDraft(
            title = title, difficulty = difficulty, category = category, priority = priority,
            notes = notes, dueAt = due, isRecurring = rec, recurType = rt,
            linkedApps = if (isFocus) linkedApps.joinToString(",") else "",
            isTimerQuest = isFocus, timerDurationMs = if (isFocus) durMs else 0L,
            daysMask = mask, questType = questType,
            challengeDays = if (isChallenge) challengeDays else 0,
            steps = if (isRoutine) steps.toList() else emptyList(),
        )
    }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text((if (isEdit) "Edit " else "New ") + typeInfo.name + " Quest", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorBg),
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (needsDays) Text("Pick at least one day.", color = ColorWarning, style = MaterialTheme.typography.labelSmall)
                if (routineNeedsSteps) Text("Add at least one step.", color = ColorWarning, style = MaterialTheme.typography.labelSmall)
                Button(
                    onClick = { if (canSave) vm.save(buildDraft(), onBack) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = canSave,
                ) { Text(if (isEdit) "Save Quest" else "Create Quest", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Type: only the chosen one (to use another type, go back to the picker) ──
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorSurface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(typeInfo.emoji, fontSize = 22.sp)
                    Column {
                        Text(typeInfo.name, color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(typeInfo.blurb, color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Title ──────────────────────────────────────────────────────
            item {
                SectionLabel("Quest Title")
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    placeholder = { Text("What do you need to do?", color = ColorMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = creatorFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // ── Daily: only an optional time of day ────────────────────────
            if (isDaily) item {
                SectionLabel("Time of day (optional)")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showTimeOfDayPicker(ctx, dailyMinute) { dailyMinute = it } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dailyMinute != null) ColorPrimary else ColorMuted),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("⏰ " + (dailyMinute?.let { fmtMinute(it) } ?: "Any time"), style = MaterialTheme.typography.bodySmall) }
                    if (dailyMinute != null) {
                        IconButton(onClick = { dailyMinute = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = ColorDanger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text("Comes back every day. Miss a day and the streak restarts.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted, modifier = Modifier.padding(top = 4.dp))
            }

            // ── Weekly: days are the whole point ───────────────────────────
            if (isWeekly) item {
                SectionLabel("Repeats on (pick at least one)")
                DayPicker(daysMask) { daysMask = it }
            }

            // ── One-time: due date ─────────────────────────────────────────
            if (isOneTime) item {
                SectionLabel("Due date & time (optional)")
                DueDateRow(dueAt, sdf, { showDateTimePicker(ctx, dueAt) { v -> dueAt = v } }, { dueAt = null })
            }

            // ── Challenge length ───────────────────────────────────────────
            if (isChallenge) item {
                SectionLabel("Challenge length")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Do it for:", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    NumberPicker(value = challengeDays, range = 2..365, label = " days") { challengeDays = it }
                }
                Text("Complete it every day. Miss a day and the count starts over.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }

            // ── Routine steps ──────────────────────────────────────────────
            if (isRoutine) item {
                SectionLabel("Steps")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ROUTINE_TEMPLATES.forEach { (name, list) ->
                        OutlinedButton(
                            onClick = {
                                if (title.isBlank()) title = name.substringAfter(' ') + " routine"
                                steps.clear(); steps.addAll(list)
                            },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        ) { Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { idx, text ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = text, onValueChange = { steps[idx] = it },
                                placeholder = { Text("Step ${idx + 1}", color = ColorMuted) },
                                modifier = Modifier.weight(1f), singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                colors = creatorFieldColors(), shape = RoundedCornerShape(12.dp),
                            )
                            IconButton(onClick = { if (steps.size > 1) steps.removeAt(idx) else steps[0] = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove step", tint = ColorMuted)
                            }
                        }
                    }
                    OutlinedButton(onClick = { steps.add("") }, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorPrimary)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add step")
                    }
                }
            }

            // ── Focus session ──────────────────────────────────────────────
            if (isFocus) item {
                SectionLabel("Focus session")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Focus time:", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    NumberPicker(value = timerHours, range = 0..8, label = "h") { timerHours = it }
                    NumberPicker(value = timerMins, range = 0..55, step = 5, label = "m") { timerMins = it }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showAppPicker = true },
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = ColorAccent),
                    shape   = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select apps to unlock (${linkedApps.size} selected)",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (linkedApps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(linkedApps.toList()) { pkg ->
                            val label = installed.find { it.pkg == pkg }?.label ?: pkg
                            Surface(shape = RoundedCornerShape(20.dp), color = ColorAccent.copy(0.15f)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = ColorAccent)
                                    Icon(Icons.Default.Close, contentDescription = null,
                                        modifier = Modifier.size(12.dp).clickable { linkedApps = linkedApps - pkg },
                                        tint = ColorAccent)
                                }
                            }
                        }
                    }
                }
            }

            // ── Routine / Focus: ONE repeat control ────────────────────────
            if (hasRepeat) item {
                RepeatPicker(repeat, { repeat = it }, daysMask) { daysMask = it }
            }

            // ── Difficulty + Priority (compact rows) ───────────────────────
            item {
                SectionLabel("Difficulty")
                val diffColors = listOf(ColorPrimary, ColorCoin, ColorDanger)
                ChoiceRow(DIFFICULTIES.map { (l, d) -> Choice(l, "+${com.krishna.questblock.data.baseReward(d)}🪙", d, diffColors[d - 1]) },
                    difficulty) { difficulty = it }
            }
            item {
                SectionLabel("Priority")
                ChoiceRow(PRIORITIES.map { (l, p) -> Choice(l, null, p, ColorAccent) }, priority) { priority = it }
            }

            // ── More options (collapsed) ───────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorSurface)
                        .clickable { showMore = !showMore }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("More options", color = ColorText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(if (showMore) "▴" else "▾", color = ColorMuted, fontSize = 16.sp)
                }
            }
            if (showMore) {
                item {
                    SectionLabel("Notes (optional)")
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        placeholder = { Text("Add details…", color = ColorMuted) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        maxLines = 3,
                        colors = creatorFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                item {
                    SectionLabel("Category")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CATEGORIES) { cat ->
                            val color = CatColors[cat] ?: ColorPrimary
                            val selected = category == cat
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) color.copy(0.25f) else ColorSurface)
                                    .border(1.dp, if (selected) color else ColorDivider, RoundedCornerShape(20.dp))
                                    .clickable { category = cat }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(cat, color = if (selected) color else ColorMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
                if (dueInMore) item {
                    SectionLabel("Due date & time (optional)")
                    DueDateRow(dueAt, sdf, { showDateTimePicker(ctx, dueAt) { v -> dueAt = v } }, { dueAt = null })
                }
            }

            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    // App picker dialog
    if (showAppPicker) {
        AppPickerDialog(
            installed = installed,
            selected  = linkedApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { linkedApps = it; showAppPicker = false },
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = ColorPrimary.copy(0.2f),
    selectedLabelColor = ColorPrimary,
    containerColor = ColorSurface, labelColor = ColorMuted,
)

/** Seven day chips + Weekdays / Weekends shortcuts. No "Every day" shortcut: that isn't a weekly. */
@Composable
private fun DayPicker(mask: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_LABELS.forEachIndexed { i, d ->
                val on = maskHas(mask, i)
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) ColorPrimary.copy(0.22f) else ColorSurface)
                        .border(1.dp, if (on) ColorPrimary else ColorDivider, RoundedCornerShape(10.dp))
                        .clickable { onChange(mask xor (1 shl i)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                        color = if (on) ColorPrimary else ColorMuted,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Weekdays" to WEEKDAYS_MASK, "Weekends" to WEEKEND_MASK).forEach { (label, m) ->
                FilterChip(selected = mask == m, onClick = { onChange(m) }, label = { Text(label) }, colors = chipColors())
            }
        }
        val m = mask and ALL_DAYS_MASK
        if (m != 0) Text(if (m == ALL_DAYS_MASK) "Every day" else daysLabel(m),
            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
    }
}

/** Routine / Focus: Once · Every day · Chosen days. Replaces the old days + Recurring + Daily/Weekly trio. */
@Composable
private fun RepeatPicker(repeat: String, onRepeat: (String) -> Unit, mask: Int, onMask: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Repeat")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REPEAT_MODES.forEach { (id, label) ->
                FilterChip(selected = repeat == id, onClick = { onRepeat(id) }, label = { Text(label) }, colors = chipColors())
            }
        }
        when (repeat) {
            "once"  -> Text("Done once, no repeat.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            "daily" -> Text("Comes back every day.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            else    -> {
                DayPicker(mask, onMask)
                if ((mask and ALL_DAYS_MASK) == 0) Text("Pick at least one day.", style = MaterialTheme.typography.bodySmall, color = ColorWarning)
            }
        }
    }
}

@Composable
private fun DueDateRow(dueAt: Long?, sdf: SimpleDateFormat, onPick: () -> Unit, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.weight(1f),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = if (dueAt != null) ColorPrimary else ColorMuted),
            shape    = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (dueAt != null) sdf.format(Date(dueAt)) else "Set due date", style = MaterialTheme.typography.bodySmall)
        }
        if (dueAt != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = ColorDanger, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChoiceRow(choices: List<Choice>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            val sel = c.value == selected
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.color.copy(0.2f) else ColorSurface)
                    .border(1.dp, if (sel) c.color else ColorDivider, RoundedCornerShape(10.dp))
                    .clickable { onSelect(c.value) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.label, color = if (sel) c.color else ColorText, maxLines = 1,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall)
                if (c.sub != null) Text(c.sub, style = MaterialTheme.typography.labelSmall, color = ColorCoin)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = ColorMuted, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun NumberPicker(value: Int, range: IntRange, label: String, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { if (value - step >= range.first) onChange(value - step) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, contentDescription = null, tint = ColorMuted, modifier = Modifier.size(16.dp))
        }
        Text("$value$label", style = MaterialTheme.typography.titleMedium, color = ColorText, fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (value + step <= range.last) onChange(value + step) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AppPickerDialog(
    installed: List<InstalledApp>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var sel by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text("Select apps to unlock", color = ColorText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(350.dp)) {
                items(installed) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (app.pkg in sel) sel - app.pkg else sel + app.pkg }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = app.pkg in sel, onCheckedChange = { checked ->
                            sel = if (checked) sel + app.pkg else sel - app.pkg
                        }, colors = CheckboxDefaults.colors(checkedColor = ColorPrimary))
                        Text(app.label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = ColorDivider, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(sel) }) { Text("Done", color = ColorPrimary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}

@Composable
private fun creatorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = ColorPrimary, unfocusedBorderColor = ColorDivider,
    cursorColor = ColorPrimary, focusedTextColor = ColorText, unfocusedTextColor = ColorText,
    focusedLabelColor = ColorPrimary, focusedContainerColor = ColorSurface, unfocusedContainerColor = ColorSurface,
)

private fun showDateTimePicker(ctx: Context, initial: Long?, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { if (initial != null) timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            cal.set(y, m, d, h, min, 0); onResult(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

private fun showTimeOfDayPicker(ctx: Context, initial: Int?, onResult: (Int) -> Unit) {
    val m = initial ?: (8 * 60)
    TimePickerDialog(ctx, { _, h, min -> onResult(h * 60 + min) }, m / 60, m % 60, false).show()
}

private val ROUTINE_TEMPLATES: List<Pair<String, List<String>>> = listOf(
    "🌅 Morning" to listOf("Wake up and drink water", "Wash up", "Make the bed", "Plan the day"),
    "📚 Study" to listOf("Phone away", "Study 25 min", "Break 5 min", "Study 25 min", "Review notes"),
    "🌙 Night" to listOf("Pack bag for tomorrow", "Wash up", "Plan tomorrow", "Phone off"),
)
