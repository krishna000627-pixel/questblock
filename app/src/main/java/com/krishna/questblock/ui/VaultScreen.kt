@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.data.fmtMinute
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(val pkg: String, val label: String)
data class AppRow(val app: InstalledApp, val rule: BlockedApp?)

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val query = MutableStateFlow("")

    val rows = combine(installed, repo.blockedApps, query) { apps, blocked, q ->
        val rules = blocked.associateBy { it.packageName }
        apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
            .map { AppRow(it, rules[it.pkg]) }
            .sortedBy { it.app.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Apps that can still be added as study apps (independent of the search box). */
    val studyCandidates = combine(installed, repo.blockedApps) { apps, blocked ->
        val study = blocked.filter { it.isStudyApp }.map { it.packageName }.toSet()
        apps.filter { it.pkg !in study }.sortedBy { it.label.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether the initial app list has been loaded (prevents flash of empty state). */
    val isLoaded = MutableStateFlow(false)

    // BroadcastReceiver to refresh on install/uninstall
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadInstalledApps()
        }
    }

    init {
        // Register for package changes
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ctx.registerReceiver(pkgReceiver, filter)
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            installed.value = apps
            isLoaded.value = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { ctx.unregisterReceiver(pkgReceiver) }
    }

    fun rate() = repo.ratePer5()
    fun studyMin() = repo.studyMinPerCoin()
    fun setOptions(pkg: String, limit: Int, start: Int, end: Int, mult: Int, rate: Int) =
        viewModelScope.launch { repo.setAppOptions(pkg, limit, start, end, mult, rate) }
    fun setQuery(q: String) { query.value = q }
    /** Is this clips screen picked as a distraction (a removal waiting for midnight counts as still on)? */
    fun clipsOn(kind: String): Boolean = repo.shortPicked(kind)
    fun setClips(kind: String, on: Boolean) { repo.changeShortPicked(kind, on) }
    fun clipsPendingOff(kind: String): Boolean = repo.shortPending(kind) == false
    fun clipsNote(): String = repo.shortPending("yt").let { y -> repo.shortPending("ig").let { i -> if (y == false || i == false) "A pick you removed ends at midnight." else "" } }
    fun setBlocked(app: InstalledApp, on: Boolean) =
        viewModelScope.launch { repo.setBlocked(app.pkg, app.label, on) }
    fun setStudyApp(app: InstalledApp, isStudy: Boolean) =
        viewModelScope.launch { repo.setStudyApp(app.pkg, app.label, isStudy) }
}

@Composable
fun VaultScreen(vm: AppsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val candidates by vm.studyCandidates.collectAsStateWithLifecycle()
    val isLoaded by vm.isLoaded.collectAsStateWithLifecycle()
    var showStudyPicker by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<AppRow?>(null) }
    val rate = remember { vm.rate() }
    val studyMin = remember { vm.studyMin() }

    val studyRows  = rows.filter { it.rule?.isStudyApp == true }
    val lockedRows = rows.filter { it.rule != null && it.rule.isStudyApp == false }
    val freeRows   = rows.filter { it.rule == null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Vault", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
            Text("${lockedRows.size} locked · ${studyRows.size} study",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery,
                placeholder = { Text("Search apps", color = ColorMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ColorMuted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary, unfocusedBorderColor = ColorSurfaceHigh,
                    cursorColor = ColorPrimary, focusedTextColor = ColorText, unfocusedTextColor = ColorText,
                    focusedContainerColor = ColorSurface, unfocusedContainerColor = ColorSurface,
                ),
            )
        }

        // ── Clips: Shorts and Reels as distraction "apps" ───────────────
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎬 Clips (distraction)", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Pick Shorts or Reels and they need coins to run ($rate🪙 per 5 min). YouTube and Instagram themselves stay free. After the fun limit they stay locked.",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    var yt by remember { mutableStateOf(vm.clipsOn("yt")) }
                    var ig by remember { mutableStateOf(vm.clipsOn("ig")) }
                    var note by remember { mutableStateOf(vm.clipsNote()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("▶ YouTube Shorts", color = ColorText, modifier = Modifier.weight(1f))
                        Switch(checked = yt, onCheckedChange = { vm.setClips("yt", it); yt = vm.clipsOn("yt") || vm.clipsPendingOff("yt"); note = vm.clipsNote() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📸 Instagram Reels", color = ColorText, modifier = Modifier.weight(1f))
                        Switch(checked = ig, onCheckedChange = { vm.setClips("ig", it); ig = vm.clipsOn("ig") || vm.clipsPendingOff("ig"); note = vm.clipsNote() })
                    }
                    if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = ColorWarning)
                    Text("🔒 Turning a pick off applies at midnight. Counts are on the Stats page.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
        }

        // ── Study apps ───────────────────────────────────────────────────
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorPrimary.copy(0.08f)),
                shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = ColorPrimary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Study Apps", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showStudyPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Apps you learn in (Anki, Kindle, Duolingo…). They never lock, and you earn " +
                        "1🪙 for every $studyMin minutes you spend inside them.",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    if (studyRows.isEmpty()) {
                        Text("No study apps yet — tap Add.", style = MaterialTheme.typography.labelMedium,
                            color = ColorPrimary)
                    }
                }
            }
        }
        items(studyRows, key = { "s_${it.app.pkg}" }) { row ->
            AppCard(row, studyMin, onToggle = { vm.setStudyApp(row.app, it) })
        }

        // ── Locked apps ──────────────────────────────────────────────────
        item {
            Column {
                Text("🔒 Locked apps", style = MaterialTheme.typography.labelLarge, color = ColorMuted,
                    fontWeight = FontWeight.SemiBold)
                Text("Unlock: $rate🪙 per 5 min  ·  ⚙ per-app rate, limits & schedule",
                    style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
        }
        if (lockedRows.isEmpty() && isLoaded) {
            item { Text("Toggle an app below to lock it.", style = MaterialTheme.typography.bodySmall, color = ColorMuted) }
        }
        items(lockedRows, key = { it.app.pkg }) { row ->
            AppCard(row, studyMin, onToggle = { vm.setBlocked(row.app, it) }, onOptions = { optionsFor = row })
        }

        // ── Loading state ─────────────────────────────────────────────────
        if (!isLoaded) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ── Everything else ──────────────────────────────────────────────
        if (freeRows.isNotEmpty()) {
            item { Text("All apps", style = MaterialTheme.typography.labelLarge, color = ColorMuted,
                fontWeight = FontWeight.SemiBold) }
            items(freeRows, key = { "u_${it.app.pkg}" }) { row ->
                AppCard(row, studyMin, onToggle = { vm.setBlocked(row.app, it) })
            }
        }

        if (rows.isEmpty() && query.isBlank() && isLoaded) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔒", style = MaterialTheme.typography.displayMedium)
                    Text("No apps found", style = MaterialTheme.typography.titleMedium, color = ColorText)
                }
            }
        }
    }

    optionsFor?.let { row ->
        AppOptionsDialog(
            row = row,
            onSave = { limit, start, end, mult, rate ->
                vm.setOptions(row.app.pkg, limit, start, end, mult, rate); optionsFor = null
            },
            onDismiss = { optionsFor = null },
        )
    }

    if (showStudyPicker) {
        StudyPickerDialog(
            apps = candidates,
            onPick = { vm.setStudyApp(it, true); showStudyPicker = false },
            onDismiss = { showStudyPicker = false },
        )
    }
}

@Composable
private fun AppCard(row: AppRow, studyMin: Int, onToggle: (Boolean) -> Unit, onOptions: (() -> Unit)? = null) {
    val rule = row.rule
    val isOn = rule != null
    val isStudy = rule?.isStudyApp == true

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val unlockEnd = if (rule != null && !isStudy) rule.unlockedUntil else 0L
    LaunchedEffect(unlockEnd) {
        if (unlockEnd > 0L) {
            while (true) {
                now = System.currentTimeMillis()
                if (now > unlockEnd + EXPIRED_SHOW_MS) break
                delay(1000L)
            }
        }
    }
    val leftMs = if (unlockEnd > now) unlockEnd - now else 0L
    val justExpired = unlockEnd in 1..now && now - unlockEnd < EXPIRED_SHOW_MS

    Card(colors = CardDefaults.cardColors(containerColor = if (isOn) ColorSurface else ColorSurface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(if (isOn) ColorPrimary.copy(alpha = 0.15f) else ColorSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isStudy -> Icon(Icons.Default.MenuBook, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(18.dp))
                    isOn    -> Icon(Icons.Default.Lock, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(18.dp))
                    else    -> Text(row.app.label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall, color = ColorMuted, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(row.app.label, style = MaterialTheme.typography.bodyMedium, color = ColorText,
                    fontWeight = if (isOn) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    isStudy -> Text("Earning 1🪙 / $studyMin min", style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                    leftMs > 0 -> Text("Unlocked · ${fmtLeft(leftMs)} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (leftMs <= 60_000L) ColorWarning else ColorCoin)
                    justExpired -> Text("⌛ Expired · locked again",
                        style = MaterialTheme.typography.labelSmall, color = ColorDanger)
                    isOn && rule != null -> Text(lockSummary(rule), style = MaterialTheme.typography.labelSmall, color = ColorMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onOptions != null && isOn && !isStudy) {
                IconButton(onClick = onOptions, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "App options", tint = ColorMuted,
                        modifier = Modifier.size(18.dp))
                }
            }
            Switch(
                checked = isOn, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                    uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh),
            )
        }
    }
}

@Composable
private fun StudyPickerDialog(apps: List<InstalledApp>, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text("Add a study app", color = ColorText, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.height(340.dp)) {
                items(apps, key = { it.pkg }) { app ->
                    Text(app.label, color = ColorText, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 12.dp))
                    HorizontalDivider(color = ColorDivider, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}

private const val EXPIRED_SHOW_MS = 10 * 60_000L

private fun lockSummary(rule: BlockedApp): String {
    val parts = mutableListOf(if (rule.dailyLimitMin > 0) "Limit ${rule.dailyLimitMin}m/day" else "Locked")
    if (rule.schedStart >= 0 && rule.schedEnd >= 0 && rule.schedStart != rule.schedEnd)
        parts += "${fmtMinute(rule.schedStart)}–${fmtMinute(rule.schedEnd)}"
    if (rule.ratePer5 > 0) parts += "${rule.ratePer5}🪙/5m"
    if (rule.priceMult != 100) parts += "price ${rule.priceMult}%"
    return parts.joinToString(" · ")
}

private val LIMIT_OPTIONS = listOf(0, 15, 30, 60, 90, 120)
private val PRICE_OPTIONS = listOf(50, 100, 150, 200, 300)

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) ColorPrimary.copy(0.2f) else ColorSurfaceHigh)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) ColorPrimary else ColorMuted, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = ColorMuted, modifier = Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(34.dp)) { Text("−") }
        Text("%02d:00".format(hour), color = ColorText, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(54.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }, contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(34.dp)) { Text("+") }
    }
}

@Composable
private fun AppOptionsDialog(row: AppRow, onSave: (Int, Int, Int, Int, Int) -> Unit, onDismiss: () -> Unit) {
    val rule = row.rule
    var limit by remember { mutableIntStateOf(rule?.dailyLimitMin ?: 0) }
    var schedOn by remember { mutableStateOf((rule?.schedStart ?: -1) >= 0 && (rule?.schedEnd ?: -1) >= 0) }
    var startH by remember { mutableIntStateOf(rule?.schedStart?.takeIf { it >= 0 }?.div(60) ?: 9) }
    var endH by remember { mutableIntStateOf(rule?.schedEnd?.takeIf { it >= 0 }?.div(60) ?: 17) }
    var mult by remember { mutableIntStateOf(rule?.priceMult ?: 100) }
    var rateOv by remember { mutableIntStateOf(rule?.ratePer5 ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text(row.app.label, color = ColorText, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mode", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LIMIT_OPTIONS) { m ->
                        OptionChip(if (m == 0) "Always locked" else "${m}m / day", limit == m) { limit = m }
                    }
                }
                Text(if (limit == 0) "Locked every time you open it."
                     else "Free to use until you've spent $limit min in it today, then it locks.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only lock during hours", color = ColorMuted, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f))
                    Switch(checked = schedOn, onCheckedChange = { schedOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                            uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh))
                }
                if (schedOn) {
                    HourStepper("From", startH) { startH = it }
                    HourStepper("Until", endH) { endH = it }
                }

                Text("Coins per 5 min", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rateOv = (rateOv - 1).coerceAtLeast(0) }, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(34.dp)) { Text("−") }
                    Text(if (rateOv == 0) "Default" else "$rateOv 🪙", color = ColorText, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedButton(onClick = { rateOv = (rateOv + 1).coerceAtMost(100) }, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(34.dp)) { Text("+") }
                }
                Text(if (rateOv == 0) "Uses the rate from Settings." else "Only for this app.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)

                Text("Price multiplier", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PRICE_OPTIONS) { m -> OptionChip("$m%", mult == m) { mult = m } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(limit, if (schedOn) startH * 60 else -1, if (schedOn) endH * 60 else -1, mult, rateOv)
            }) { Text("Save", color = ColorPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}
