package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Hw
import com.krishna.questblock.data.HwItem
import com.krishna.questblock.data.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    val prefs: Prefs,
) : ViewModel() {
    var items by mutableStateOf<List<HwItem>>(emptyList())
        private set
    var notice by mutableStateOf<String?>(null)
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    fun refresh() {
        viewModelScope.launch {
            val lines = Hw.dailyTick(ctx, repo) + listOfNotNull(HwService.sync(ctx, repo))
            if (lines.isNotEmpty()) notice = lines.joinToString("\n")
            reload()
        }
    }

    private fun reload() {
        items = Hw.all(ctx)
        now = System.currentTimeMillis()
    }

    /** Returns an error line, or null when it was added. */
    fun add(subj: String, text: String, dueText: String, ch: Int, kind: String, estText: String): String? {
        if (text.isBlank()) return "Write what the homework is"
        val due = if (dueText.isBlank()) Hw.defaultDue() else Hw.parseDue(dueText) ?: return "Time must look like 18:30"
        if (ch > 0) {
            val n = termuxChapterCount(ctx, subj)
            if (ch > n) return "${Hw.subjectName(subj)} has $n chapters"
        }
        val est = if (estText.isBlank()) 0 else Hw.parseMinutes(estText) ?: return "Estimate: minutes like 30"
        Hw.add(ctx, subj, text, due, ch, kind, est)
        reload()
        return null
    }

    fun complete(id: Long) {
        viewModelScope.launch {
            HwService.complete(ctx, repo, id)?.let { notice = it.lines.joinToString("\n") }
            reload()
        }
    }

    fun reopen(id: Long) { Hw.reopen(ctx, id); reload() }
    fun delete(id: Long) { Hw.remove(ctx, id); reload() }
    fun estimate(item: HwItem): Int = Hw.estimate(ctx, item)
    fun learnedNote(item: HwItem): Boolean = Hw.estimateIsLearned(ctx, item)
    fun link(item: HwItem): String = HwService.linkText(ctx, item)
    fun fit(): String? = Hw.fitWarning(ctx, now)
    fun week() = Hw.week(ctx, now)
    fun board() = Hw.board(ctx, now)
    fun streak() = Hw.streak(ctx)
    fun best() = Hw.bestStreak(ctx)
    fun bed(): Int = Hw.bedMin(ctx)
    fun setBed(m: Int) { Hw.setBedMin(ctx, ((m % 1440) + 1440) % 1440); reload() }

    fun focus(item: HwItem, onStarted: () -> Unit) {
        viewModelScope.launch {
            val err = HwService.startFocus(ctx, repo, item, Hw.estimate(ctx, item).coerceIn(5, 120), prefs.strictMode)
            if (err != null) notice = err else { notice = null; onStarted() }
        }
    }
}

@Composable
private fun HwPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) ColorPrimary.copy(0.22f) else ColorSurfaceHigh,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ColorPrimary else ColorMuted, fontWeight = FontWeight.Bold)
    }
}

private val DUE_CHOICES = listOf("Tomorrow" to "tomorrow", "In 2 days" to "+2d", "Today" to "today", "Fri" to "fri", "Mon" to "mon")

/** v211: the Homework tracker. Quick add with subject chips, a board per subject, due soon, estimates, weekly report. */
@Composable
fun HomeworkScreen(
    onBack: () -> Unit,
    onGoFocus: () -> Unit = {},
    vm: HomeworkViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    var subj by remember { mutableStateOf("P") }
    var text by remember { mutableStateOf("") }
    var dueChoice by remember { mutableIntStateOf(0) }
    var timeText by remember { mutableStateOf("") }
    var chText by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("study") }
    var estText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    var delItem by remember { mutableStateOf<HwItem?>(null) }
    var focusItem by remember { mutableStateOf<HwItem?>(null) }
    var showDone by remember { mutableStateOf(false) }

    val now = vm.now
    val weekStart = startOfTodayMs(now) - 6L * 86_400_000L
    val open = vm.items.filter { !it.done && (filter == null || it.subj == filter) }.sortedBy { it.due }
    val doneWk = vm.items.filter { it.done && it.doneAt >= weekStart }.sortedByDescending { it.doneAt }
    val dueSoon = vm.items.filter { !it.done && it.due <= now + Hw.DUE_SOON_MS }.sortedBy { it.due }
    val board = vm.board()
    val week = vm.week()
    val fit = vm.fit()

    delItem?.let { d ->
        AlertDialog(
            onDismissRequest = { delItem = null },
            containerColor = ColorSurface,
            title = { Text("Delete this homework?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = { Text("${Hw.subjectName(d.subj)}: ${d.text}", color = ColorMuted) },
            confirmButton = { TextButton(onClick = { vm.delete(d.id); delItem = null }) { Text("Delete", color = ColorDanger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { delItem = null }) { Text("Cancel", color = ColorMuted) } },
        )
    }
    focusItem?.let { f ->
        val m = vm.estimate(f).coerceIn(5, 120)
        AlertDialog(
            onDismissRequest = { focusItem = null },
            containerColor = ColorSurface,
            title = { Text("Focus for $m min?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = {
                Text("${Hw.subjectName(f.subj)}: ${f.text}\n\nEvery app you did not pick for Focus is locked until it ends" +
                    if (vm.prefs.strictMode) ". Strict mode is on: you can't give up." else ".", color = ColorMuted)
            },
            confirmButton = {
                TextButton(onClick = { focusItem = null; vm.focus(f) { onGoFocus() } }) { Text("Start", color = ColorPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { focusItem = null }) { Text("Cancel", color = ColorMuted) } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                TitleIcon("Homework", 24.dp, endPad = 8.dp)
                Column(Modifier.weight(1f)) {
                    Text("Homework", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = ColorText, maxLines = 1)
                    Text("Write it down, hand it in, keep the streak", style = MaterialTheme.typography.bodySmall, color = ColorMuted, maxLines = 1)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = ColorCoin.copy(alpha = 0.15f)) {
                    Text("\uD83D\uDD25 ${vm.streak()}d", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = ColorCoin, fontWeight = FontWeight.Bold)
                }
            }
        }

        vm.notice?.let { n ->
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = ColorSurfaceHigh, modifier = Modifier.fillMaxWidth().clickable { vm.notice = null }) {
                    Text(n, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = ColorText)
                }
            }
        }

        // quick add
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick add", color = ColorText, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Hw.SUBJECTS.forEach { (id, name) -> HwPill(name, subj == id) { subj = id } }
                    }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it; error = null },
                        label = { Text("e.g. ex 5.2 q1-10") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DUE_CHOICES.forEachIndexed { i, (label, _) -> HwPill(label, dueChoice == i) { dueChoice = i } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = timeText, onValueChange = { timeText = it; error = null }, label = { Text("time 08:00") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = chText, onValueChange = { chText = it.filter { c -> c.isDigit() }.take(2); error = null },
                            label = { Text("chapter #") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = estText, onValueChange = { estText = it; error = null }, label = { Text("est min") },
                            singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (chText.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("On hand-in tick:", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                            HwPill("Studied", kind == "study") { kind = "study" }
                            HwPill("Copy complete", kind == "copy") { kind = "copy" }
                        }
                    }
                    error?.let { Text(it, color = ColorDanger, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            val dueWord = DUE_CHOICES[dueChoice].second
                            val dueText = if (timeText.isBlank()) dueWord else "$dueWord ${timeText.trim()}"
                            val r = vm.add(subj, text, dueText, chText.toIntOrNull() ?: 0, kind, estText)
                            if (r == null) { text = ""; chText = ""; estText = ""; timeText = ""; error = null } else error = r
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    ) { Text("Add to ${Hw.subjectName(subj)}", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // tonight won't fit
        fit?.let { w ->
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = ColorWarning.copy(0.15f), modifier = Modifier.fillMaxWidth()) {
                    Text("\u26A0 $w", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = ColorWarning, fontWeight = FontWeight.Bold)
                }
            }
        }

        // due soon
        if (dueSoon.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("\u23F0 Due in the next 24 hours", color = ColorText, fontWeight = FontWeight.Bold)
                        dueSoon.take(5).forEach {
                            Text("${Hw.subjectName(it.subj)}: ${it.text}  \u00b7  ${Hw.dueLabel(it.due, now)}",
                                style = MaterialTheme.typography.bodySmall, color = if (it.due < now) ColorDanger else ColorMuted)
                        }
                    }
                }
            }
        }

        // subject board
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                board.forEach { r ->
                    val sel = filter == r.subj
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) ColorPrimary.copy(0.22f) else ColorSurface,
                        modifier = Modifier.weight(1f).clickable { filter = if (sel) null else r.subj },
                    ) {
                        Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(r.name.take(5), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (sel) ColorPrimary else ColorText, maxLines = 1)
                            Text("${r.due} due", style = MaterialTheme.typography.labelSmall, color = ColorMuted, maxLines = 1)
                            Text("${r.overdue} late", style = MaterialTheme.typography.labelSmall, color = if (r.overdue > 0) ColorDanger else ColorMuted, maxLines = 1)
                            Text("${r.doneWeek} done", style = MaterialTheme.typography.labelSmall, color = ColorPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Text(
                if (filter == null) "To hand in (${open.size})" else "${Hw.subjectName(filter!!)}: to hand in (${open.size})  \u00b7  tap the card again to see all",
                color = ColorText, fontWeight = FontWeight.Bold,
            )
        }
        if (open.isEmpty()) item { Text("Nothing open. Write tomorrow's homework down now.", color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
        items(open, key = { "hw_${it.id}" }) { h ->
            val late = h.due < now
            Surface(shape = RoundedCornerShape(12.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { vm.complete(h.id) })
                    Column(Modifier.weight(1f)) {
                        Text(h.text, color = ColorText, fontWeight = FontWeight.SemiBold)
                        val est = vm.estimate(h)
                        val link = vm.link(h)
                        Text(
                            "${Hw.subjectName(h.subj)}  \u00b7  ${Hw.dueLabel(h.due, now)}  \u00b7  ~${est}m${if (vm.learnedNote(h)) " (learned)" else ""}" +
                                (if (h.carried > 0) "  \u00b7  carried ${h.carried}x" else "") + (if (link.isNotEmpty()) "\n$link" else ""),
                            style = MaterialTheme.typography.bodySmall, color = if (late) ColorDanger else ColorMuted,
                        )
                    }
                    Text("\u26A1", modifier = Modifier.clickable { focusItem = h }.padding(8.dp), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { delItem = h }) { Icon(Icons.Default.Delete, "Delete", tint = ColorMuted) }
                }
            }
        }

        // done this week
        if (doneWk.isNotEmpty()) {
            item {
                Text(if (showDone) "\u25BE Handed in this week (${doneWk.size})" else "\u25B8 Handed in this week (${doneWk.size})",
                    color = ColorMuted, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showDone = !showDone }.padding(vertical = 4.dp))
            }
            if (showDone) items(doneWk, key = { "hwd_${it.id}" }) { h ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = true, onCheckedChange = { vm.reopen(h.id) })
                    Text("${Hw.subjectName(h.subj)}: ${h.text}${if (h.onTime) "" else "  (late)"}", color = ColorMuted,
                        textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // weekly report
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("\uD83D\uDCCA Weekly report (last 7 days)", color = ColorText, fontWeight = FontWeight.Bold)
                    Text("Handed in on time: ${week.onTime}   late: ${week.late}", color = ColorText, style = MaterialTheme.typography.bodySmall)
                    Text("Still open: ${week.open}   overdue: ${week.overdue}   carried over: ${week.carried}x", color = if (week.overdue > 0) ColorDanger else ColorText, style = MaterialTheme.typography.bodySmall)
                    Text("Estimated ${week.estMin} min \u00b7 focused ${week.focusMin} min on finished items", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    Text("Clean days in a row: ${week.streak} (best ${week.best}). Every 7th pays ${Hw.STREAK_BONUS} coins.", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    week.perSubject.forEach { (id, done, tracked) ->
                        Text("${Hw.subjectName(id)}: $done of $tracked", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("On time +${Hw.ON_TIME_COINS} coins \u00b7 carried over costs ${Hw.CARRY_COST} coin per item per day.", color = ColorMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // bedtime used for the "won't fit" warning
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Bedtime for planning: ${Hw.hhmm(vm.bed())}", color = ColorText, fontWeight = FontWeight.Bold)
                        Text("Used to warn when tonight's list won't fit.", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { vm.setBed(vm.bed() - 30) }, shape = RoundedCornerShape(10.dp)) { Text("\u2212 30") }
                    OutlinedButton(onClick = { vm.setBed(vm.bed() + 30) }, shape = RoundedCornerShape(10.dp)) { Text("+ 30") }
                }
            }
        }
    }
}

private fun startOfTodayMs(now: Long): Long = com.krishna.questblock.data.startOfToday(now)

/** v211: the homework due in the next 24 hours, on the Focus tab while a session runs (a strict session shows only that tab). */
@Composable
fun HwDueSoonCard() {
    val ctx = LocalContext.current
    val soon by produceState(emptyList<HwItem>(), ctx) {
        while (true) { value = Hw.dueSoon(ctx); kotlinx.coroutines.delay(10_000L) }
    }
    if (soon.isEmpty()) return
    val now = System.currentTimeMillis()
    Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("\uD83D\uDCDD Homework due soon (${soon.size})", color = ColorWarning, fontWeight = FontWeight.Bold)
            soon.take(3).forEach {
                Text("${Hw.subjectName(it.subj)}: ${it.text}  \u00b7  ${Hw.dueLabel(it.due, now)}",
                    style = MaterialTheme.typography.bodySmall, color = if (it.due < now) ColorDanger else ColorMuted)
            }
        }
    }
}
