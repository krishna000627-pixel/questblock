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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
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
) : ViewModel() {

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

    fun save(
        title: String, difficulty: Int, category: String, priority: Int,
        notes: String, dueAt: Long?, isRecurring: Boolean, recurType: String,
        linkedApps: List<String>, isTimerQuest: Boolean, timerDurationMs: Long,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        repo.addTask(
            title = title, difficulty = difficulty, category = category,
            priority = priority, notes = notes, dueAt = dueAt,
            isRecurring = isRecurring, recurType = recurType,
            linkedApps = linkedApps.joinToString(","),
            isTimerQuest = isTimerQuest, timerDurationMs = timerDurationMs,
        )
        repo.unlockFirstQuestAchievement()
        onDone()
    }
}

private val CATEGORIES = listOf("Study","Work","Health","Personal","Custom")
private val DIFFICULTIES = listOf("Easy" to 1, "Medium" to 2, "Hard" to 3)
private val PRIORITIES = listOf("🔴 High" to 1, "🟡 Med" to 2, "🟢 Low" to 3)
private val RECUR_TYPES = listOf("daily","weekly")

@Composable
fun QuestCreatorScreen(
    onBack: () -> Unit,
    vm: QuestCreatorViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val installed by vm.installed.collectAsState()

    var title      by remember { mutableStateOf("") }
    var notes      by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(1) }
    var category   by remember { mutableStateOf("Personal") }
    var priority   by remember { mutableIntStateOf(2) }
    var dueAt      by remember { mutableStateOf<Long?>(null) }
    var isRecurring by remember { mutableStateOf(false) }
    var recurType  by remember { mutableStateOf("daily") }
    var isTimerQuest by remember { mutableStateOf(false) }
    var timerHours by remember { mutableIntStateOf(1) }
    var timerMins  by remember { mutableIntStateOf(0) }
    var linkedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAppPicker by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        containerColor = ColorBg,
        topBar = {
            TopAppBar(
                title = { Text("New Quest", fontWeight = FontWeight.Bold, color = ColorText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorBg),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val durMs = (timerHours * 3600L + timerMins * 60L) * 1000L
                            vm.save(title, difficulty, category, priority, notes,
                                dueAt, isRecurring, recurType, linkedApps.toList(),
                                isTimerQuest, if (isTimerQuest) durMs else 0L, onBack)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = title.isNotBlank(),
                ) { Text("Create Quest", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
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

            // ── Notes ──────────────────────────────────────────────────────
            item {
                SectionLabel("Notes (optional)")
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    placeholder = { Text("Add details…", color = ColorMuted) },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    maxLines = 4,
                    colors = creatorFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // ── Category ───────────────────────────────────────────────────
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
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(cat, color = if (selected) color else ColorMuted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

            // ── Difficulty + Priority ──────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Difficulty")
                        DIFFICULTIES.forEach { (label, d) ->
                            val diffColors = listOf(ColorPrimary, ColorCoin, ColorDanger)
                            val c = diffColors[d - 1]
                            val sel = difficulty == d
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) c.copy(0.2f) else ColorSurface)
                                    .border(1.dp, if (sel) c else ColorDivider, RoundedCornerShape(10.dp))
                                    .clickable { difficulty = d }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, color = if (sel) c else ColorText,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall)
                                Text("+${com.krishna.questblock.data.baseReward(d)}🪙",
                                    style = MaterialTheme.typography.labelSmall, color = ColorCoin)
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Priority")
                        PRIORITIES.forEach { (label, p) ->
                            val sel = priority == p
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) ColorAccent.copy(0.2f) else ColorSurface)
                                    .border(1.dp, if (sel) ColorAccent else ColorDivider, RoundedCornerShape(10.dp))
                                    .clickable { priority = p }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { Text(label, color = if (sel) ColorAccent else ColorText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }
            }

            // ── Due Date/Time ──────────────────────────────────────────────
            item {
                SectionLabel("Due Date & Time")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDateTimePicker(ctx, dueAt) { dueAt = it } },
                        modifier = Modifier.weight(1f),
                        border   = ButtonDefaults.outlinedButtonBorder.copy(),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = if (dueAt != null) ColorPrimary else ColorMuted),
                        shape    = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (dueAt != null) sdf.format(Date(dueAt!!)) else "Set due date",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (dueAt != null) {
                        IconButton(onClick = { dueAt = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = ColorDanger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Recurring ─────────────────────────────────────────────────
            item {
                CreatorToggleRow(
                    label = "Recurring quest",
                    sub   = if (isRecurring) "Repeats ${recurType}ly after completion" else "One-time quest",
                    emoji = "🔁",
                    checked = isRecurring,
                    onToggle = { isRecurring = it },
                )
                if (isRecurring) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RECUR_TYPES.forEach { rt ->
                            FilterChip(
                                selected = recurType == rt,
                                onClick  = { recurType = rt },
                                label    = { Text(rt.replaceFirstChar { it.uppercase() }) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ColorPrimary.copy(0.2f),
                                    selectedLabelColor     = ColorPrimary,
                                    containerColor = ColorSurface, labelColor = ColorMuted,
                                ),
                            )
                        }
                    }
                }
            }

            // ── Timer Quest ───────────────────────────────────────────────
            item {
                CreatorToggleRow(
                    label = "Timer Quest",
                    sub   = "Unlock selected apps for quest duration",
                    emoji = "⏱️",
                    checked = isTimerQuest,
                    onToggle = { isTimerQuest = it },
                )
                if (isTimerQuest) {
                    Spacer(Modifier.height(10.dp))
                    // Duration picker
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Duration:", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                        NumberPicker(value = timerHours, range = 0..8, label = "h") { timerHours = it }
                        NumberPicker(value = timerMins, range = 0..55, step = 5, label = "m") { timerMins = it }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Linked apps
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
            }

            item { Spacer(Modifier.height(80.dp)) }
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
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = ColorMuted, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun CreatorToggleRow(label: String, sub: String, emoji: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ColorText, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        Switch(checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh))
    }
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
