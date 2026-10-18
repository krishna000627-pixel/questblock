@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.AppDatabase
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.FUN_EXTEND_COST
import com.krishna.questblock.data.FUN_EXTEND_MIN
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.agoText
import com.krishna.questblock.util.Backup
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    val prefs: Prefs,
    private val repo: GameRepository,
    private val db: AppDatabase,
) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())

    fun saveProfile(name: String, goal: String, dob: String) =
        viewModelScope.launch { repo.saveProfile(name, goal, dob) }

    fun remindersChanged(on: Boolean) = viewModelScope.launch {
        prefs.remindersEnabled = on
        if (on) repo.rescheduleAllReminders()
    }

    fun export(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(withContext(Dispatchers.IO) { Backup.export(ctx, db, uri) })
    }

    fun restore(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { Backup.import(ctx, db, uri) }
        onResult(ok)
        if (ok) { delay(600L); Backup.restart(ctx) }
    }

    fun reset() = viewModelScope.launch {
        repo.resetAll()
        Backup.restart(ctx)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit, onOpenCategories: () -> Unit, onOpenUsage: () -> Unit, onOpenDebug: () -> Unit = {}, onOpenVersions: () -> Unit = {}, onOpenWeekly: () -> Unit = {}, onOpenSelfTest: () -> Unit = {}, onOpenIdeas: () -> Unit = {}, onOpenAbout: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val player by vm.player.collectAsStateWithLifecycle()

    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            TitleWithIcon("\u2699\uFE0F", "Settings")
        }

        // ── Profile ──────────────────────────────────────────────────────
        Section("👤 Hero info") {
            var name by remember(player.name) { mutableStateOf(player.name) }
            var goal by remember(player.goal) { mutableStateOf(player.goal) }
            var dob  by remember(player.dob)  { mutableStateOf(player.dob) }
            OutlinedTextField(name, { name = it }, label = { Text("Name", color = ColorMuted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(goal, { goal = it }, label = { Text("Goal", color = ColorMuted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(dob, { dob = it }, label = { Text("Date of birth", color = ColorMuted) },
                placeholder = { Text("DD/MM/YYYY", color = ColorMuted) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Button(onClick = { vm.saveProfile(name, goal, dob); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() },
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(10.dp)) { Text("Save hero info", fontWeight = FontWeight.Bold) }
            Text("This is the only place to edit it. Profile just shows it.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }

        // ── Look ─────────────────────────────────────────────────────────
        Section("🎨 Look") {
            Text("Theme", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("\uD83C\uDF19 Dark" to 0, "\u2600\uFE0F Light" to 1, "\uD83D\uDCF1 System" to 2).forEach { (label, m) ->
                    val sel = ThemeMode == m
                    Box(
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(if (sel) ColorPrimary.copy(alpha = 0.18f) else ColorSurfaceHigh)
                            .clickable { prefs.themeMode = m; applyThemeMode(m, sysDark) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) ColorPrimary else ColorText, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            Text("A shop theme picks its own white or black by itself. This choice is for the default look.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Spacer(Modifier.height(8.dp))
            Text("Accent colour", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
            var accent by remember { mutableLongStateOf(prefs.accentArgb) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEach { (label, argb) ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Color(argb))
                            .clickable {
                                accent = argb; prefs.accentArgb = argb; ColorPrimary = Color(argb)
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (accent == argb) Text("✓", color = ColorOnPrimary, fontWeight = FontWeight.Black) }
                }
            }
            Text("Some elements pick the new colour up after you reopen the app.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Spacer(Modifier.height(8.dp))
            Text("Termux theme", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { TermuxMode.set(ctx, true) }, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()) { Text("Switch to the Termux look (the whole app becomes a terminal)") }
            Text("Back to this look any time with: theme set default (refused while a strict Focus session runs).",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Spacer(Modifier.height(8.dp))
            Text("Quest Home (launcher)", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = {
                    QuestHomeMode.set(ctx, true)
                    runCatching { ctx.startActivity(Intent(ctx, QuestHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
            ) { Text(if (QuestHomeOn) "Open Quest Home" else "Switch to Quest Home (this app becomes your launcher)") }
            if (QuestHomeOn) {
                OutlinedButton(onClick = { qhPickHomeApp(ctx) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Make Quest Home my Home app")
                }
                OutlinedButton(onClick = { QuestHomeMode.set(ctx, false) }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Turn off Quest Home")
                }
            }
            Text("10 themes, app drawer, folders, dock, widgets. Long-press an empty spot on the launcher for its settings.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Spacer(Modifier.height(8.dp))
            Text("Name and icon on the home screen", color = ColorMuted, style = MaterialTheme.typography.labelLarge)
            var nameMode by remember { mutableStateOf(AppName.mode(ctx)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AppName.MODES.forEach { m ->
                    OutlinedButton(
                        onClick = { AppName.setMode(ctx, m); nameMode = m },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text(if (nameMode == m) "\u2713 " + m else m, maxLines = 1) }
                }
            }
            Text("follow = \"Terminal\" with a >_ icon while the Termux theme is on, \"QuestBlock\" otherwise. The icon can take a few seconds to change.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }

        // ── Economy ──────────────────────────────────────────────────────
        Section("💰 Economy") {
            var rate by remember { mutableIntStateOf(prefs.ratePer5) }
            StepperRow("Unlock cost", rate, 1..100, 1, "🪙 / 5 min") { rate = it; prefs.ratePer5 = it }
            Text("Unlock time is bought in 5-minute steps on the lock screen: cost = blocks × this rate. " +
                "Any app can have its own rate (Vault → ⚙).",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            HorizontalDivider(color = ColorDivider)
            var focusBonus by remember { mutableIntStateOf(prefs.focusBonus) }
            StepperRow("Focus session bonus", focusBonus, 0..100, 5, "🪙") { focusBonus = it; prefs.focusBonus = it }
            var studyMin by remember { mutableIntStateOf(prefs.studyMinPerCoin) }
            StepperRow("Study: minutes per 🪙", studyMin, 1..30, 1, "min") { studyMin = it; prefs.studyMinPerCoin = it }
            var loginOn by remember { mutableStateOf(prefs.dailyLoginEnabled) }
            ToggleRow("Daily login bonus", null, loginOn) { loginOn = it; prefs.dailyLoginEnabled = it }
            if (loginOn) {
                var loginCoins by remember { mutableIntStateOf(prefs.dailyLoginCoins) }
                StepperRow("Login bonus", loginCoins, 1..50, 1, "🪙") { loginCoins = it; prefs.dailyLoginCoins = it }
            }
        }

        // ── Cultivation ──────────────────────────────────────────────────
        Section("🌬️ Cultivation") {
            var cult by remember { mutableStateOf(prefs.cultivationOn) }
            ToggleRow("Cultivation realms", "Realm, stage and breakthroughs on Home, Profile and the widget. Your level and XP stay the same when it is off.", cult) {
                cult = it; prefs.cultivationOn = it
                com.krishna.questblock.service.QuestWidget.refresh(ctx)
            }
        }

        // ── Lock screen ──────────────────────────────────────────────────
        Section("🔒 Lock screen") {
            var hold by remember { mutableIntStateOf(prefs.holdSeconds) }
            StepperRow("Hold to unlock", hold, 1..10, 1, "s") { hold = it; prefs.holdSeconds = it }
            var quotes by remember { mutableStateOf(prefs.showQuotes) }
            ToggleRow("Motivational quotes", null, quotes) { quotes = it; prefs.showQuotes = it }
        }

        // ── Reminders ────────────────────────────────────────────────────
        Section("⏰ Reminders") {
            var on by remember { mutableStateOf(prefs.remindersEnabled) }
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) { on = true; vm.remindersChanged(true) }
                else Toast.makeText(ctx, "Notifications are blocked for QuestBlock", Toast.LENGTH_SHORT).show()
            }
            ToggleRow("Quest reminders", "Notify before a quest's due time", on) { want ->
                if (!want) { on = false; vm.remindersChanged(false) }
                else if (Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { on = true; vm.remindersChanged(true) }
            }
            if (on) {
                var lead by remember { mutableIntStateOf(prefs.reminderLeadMin) }
                StepperRow("Remind me", lead, 0..180, 5, "min before") { lead = it; prefs.reminderLeadMin = it }
                Text("Applies to quests saved from now on. Edit a quest to update its reminder.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        // ── Apps ─────────────────────────────────────────────────────────
        Section("\uD83D\uDCF1 Apps") {
            Text("Sort your apps into Study, Coding, Neutral and Fun, and see how long you use each one.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            OutlinedButton(onClick = onOpenCategories, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()) { Text("App categories") }
            OutlinedButton(onClick = onOpenUsage, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()) { Text("Daily app usage") }
            OutlinedButton(onClick = {
                val pm = ctx.packageManager
                val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }.sortedBy { it.second.lowercase() }
                val text = "Apps on this phone (${apps.size})\nName | package | category\n" + apps.joinToString("\n") { (pkg, label) ->
                    val cat = prefs.categoryOverride(pkg) ?: com.krishna.questblock.data.AppCategorizer.autoCategory(pm, pkg)
                    "$label | $pkg | $cat"
                }
                ctx.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock apps", text))
                Toast.makeText(ctx, "Copied ${apps.size} apps", Toast.LENGTH_SHORT).show()
            }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("Copy my apps list (names + packages)") }
            Text("\uD83C\uDFAE The fun time limit and the other fun rules now live in Hub \u2192 Rules.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }

        // ── Reliability ──────────────────────────────────────────────────
        Section("🛠️ Reliability") {
            Text("Permissions, strict mode, battery, autostart and keep-alive: everything that keeps the blocker alive and protected.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            OutlinedButton(onClick = onOpenReliability, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()) { Text("Open Reliability & permissions") }
        }

        // ── Data ─────────────────────────────────────────────────────────
        Section("💾 Data") {
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                if (uri != null) vm.export(uri) { ok ->
                    Toast.makeText(ctx, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
                }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.restore(uri) { ok ->
                    Toast.makeText(ctx, if (ok) "Restored — restarting…" else "Not a valid encrypted backup (.qbak)", Toast.LENGTH_SHORT).show()
                }
            }
            Text("Backup is encrypted (.qbak, old .zip/.db files no longer restore) and holds quests, coins, apps, rewards, stats, settings and Quest board scrolls. Restore keeps your current coins, never doubles or lowers them, and saves a safety copy to Downloads/QuestBlock first. A daily copy is kept there automatically.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    exportLauncher.launch("questblock-backup-${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.qbak")
                }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("Restore") }
            }
            OutlinedButton(onClick = { confirmReset = true }, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                modifier = Modifier.fillMaxWidth()) { Text("Reset all data") }
        }

        // ── Developer ────────────────────────────────────────────────────
        Section("🛠️ Developer") {
            val version = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("QuestBlock v$version", color = ColorText, fontWeight = FontWeight.SemiBold)
            listOf<Pair<String, () -> Unit>>(
                "🧪 Self test (checks everything)" to onOpenSelfTest,
                "🐞 Debug log" to onOpenDebug,
            ).forEach { (label, go) ->
                OutlinedButton(onClick = go, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            modifier = Modifier.grayWindow(),
            containerColor = ColorSurface,
            title = { Text("Reset everything?", color = ColorText, fontWeight = FontWeight.Bold) },
            text = { Text("Deletes all quests, coins, XP, rewards and blocked apps, and clears settings. This can't be undone.",
                color = ColorMuted) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.reset() }) {
                    Text("Reset", color = ColorDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = ColorMuted) } },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ColorPrimary, unfocusedBorderColor = ColorSurfaceHigh, cursorColor = ColorPrimary,
    focusedTextColor = ColorText, unfocusedTextColor = ColorText)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorPrimary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Text(sub, color = ColorMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh))
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range.first, range.last)) },
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
        Text("$value $suffix", color = ColorText, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range.first, range.last)) },
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
    }
}


private fun hhmmOf(m: Int) = "%02d:%02d".format(m / 60, m % 60)

/** v73: extra behaviours you switch on yourself. Nothing here changes by itself. */
@Composable
internal fun OptionalFeaturesSection(prefs: Prefs, ctx: Context) {
    Section("🧪 Experiments") {
        Text("Switches you turn on yourself. Nothing here changes on its own.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)

        // ── Fun limit ──
        Text("🎮 Fun limit", color = ColorText, fontWeight = FontWeight.SemiBold)
        var note by remember { mutableStateOf(prefs.pendingSummary()) }
        var ghost by remember { mutableStateOf(prefs.pendingFlag("opt:ghost_counts") ?: prefs.ghostCountsOn) }
        ToggleRow("Music from Fun apps counts toward the limit", "Off: only time on screen counts", ghost) {
            prefs.changeGhostCounts(it)
            ghost = prefs.pendingFlag("opt:ghost_counts") ?: prefs.ghostCountsOn
            note = prefs.pendingSummary()
        }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = ColorWarning, fontWeight = FontWeight.SemiBold)
        Text("🔒 Turning these off waits until midnight, like the other fun rules.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        var counter by remember { mutableStateOf(prefs.optCounter) }
        ToggleRow("Shorts and Reels counter over the app", "Videos swiped, live. Needs Draw over other apps. Totals and minutes are on the Stats page.", counter) {
            if (it && !Perms.overlayGranted(ctx)) {
                Toast.makeText(ctx, "Allow \"Draw over other apps\" first (Settings → Reliability → Permissions)", Toast.LENGTH_LONG).show()
            } else { counter = it; prefs.optCounter = it }
        }
        var warn80 by remember { mutableStateOf(prefs.optWarn80) }
        ToggleRow("Warn at 80% of the limit", "One notification a day", warn80) { warn80 = it; prefs.optWarn80 = it }
        var bubble by remember { mutableStateOf(prefs.optBubble) }
        ToggleRow("Time-left bubble", "Small bubble over fun apps and Shorts. Needs Draw over other apps.", bubble) {
            if (it && !Perms.overlayGranted(ctx)) {
                Toast.makeText(ctx, "Allow \"Draw over other apps\" first (Settings → Reliability → Permissions)", Toast.LENGTH_LONG).show()
            } else { bubble = it; prefs.optBubble = it }
        }

        HorizontalDivider(color = ColorDivider)
        // ── YouTube study gate ──
        Text("📺 YouTube study gate", color = ColorText, fontWeight = FontWeight.SemiBold)
        Text("A video of a Class 12 chapter (Physics, Chemistry, Maths) is free. Any other video costs ${com.krishna.questblock.data.YT_FUN_RATE_PER_5 / 5} coins a minute. Shorts always cost coins. Home and search stay free.",
            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        var ytg by remember { mutableStateOf(prefs.pendingFlag("opt:yt_gate") ?: prefs.ytGateOn) }
        ToggleRow("Study gate on YouTube", "Reads the video title. Off waits until midnight.", ytg) {
            prefs.changeYtGate(it)
            ytg = prefs.pendingFlag("opt:yt_gate") ?: prefs.ytGateOn
            note = prefs.pendingSummary()
        }
        var yti by remember { mutableStateOf(prefs.pendingFlag("opt:yt_info") ?: prefs.ytInfoOn) }
        ToggleRow("Check the video online too", "A few seconds after a video starts, its title and channel go to YouTube's own search to read its category, tags and description: a game or comedy video named like a chapter is caught, a study video with a plain title is freed. Needs internet. Off waits until midnight.", yti) {
            prefs.changeYtInfo(it)
            yti = prefs.pendingFlag("opt:yt_info") ?: prefs.ytInfoOn
            note = prefs.pendingSummary()
        }

        HorizontalDivider(color = ColorDivider)
        // ── Music ──
        Text("🎧 Music", color = ColorText, fontWeight = FontWeight.SemiBold)
        Text("Music from Fun apps is paused at the limit (Rules → Pause fun music). YouTube is never paused.",
            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        var mwarn by remember { mutableStateOf(prefs.optMusicWarn) }
        ToggleRow("1-minute warning before music stops", "Notification with a +$FUN_EXTEND_MIN min button ($FUN_EXTEND_COST🪙)", mwarn) { mwarn = it; prefs.optMusicWarn = it }
        var mfocus by remember { mutableStateOf(prefs.optMusicOnFocus) }
        ToggleRow("Stop music when Focus starts", "Not YouTube", mfocus) { mfocus = it; prefs.optMusicOnFocus = it }

        HorizontalDivider(color = ColorDivider)
        // ── Bedtime mode ──
        Text("🌙 Bedtime mode", color = ColorText, fontWeight = FontWeight.SemiBold)
        var bed by remember { mutableStateOf(prefs.optBedtime) }
        ToggleRow("Turn on Bedtime mode at the limit",
            "Taps the Bedtime tile in Quick Settings (grayscale and silence). Once a day. Keep the tile on the first page.", bed) { bed = it; prefs.optBedtime = it }
        OutlinedButton(onClick = {
            val svc = com.krishna.questblock.service.BlockerAccessibilityService.instance
            if (svc == null) Toast.makeText(ctx, "Turn on the accessibility service first", Toast.LENGTH_LONG).show()
            else svc.runBedtimeTest { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
        }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("Test now") }
        Text("Test taps the tile once. If it turned Bedtime mode on, switch it off yourself.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)

        HorizontalDivider(color = ColorDivider)
        // ── Grayscale look ──
        Text("⚫ Grayscale look", color = ColorText, fontWeight = FontWeight.SemiBold)
        var gray by remember { mutableStateOf(prefs.optGray) }
        ToggleRow("Draw QuestBlock in grayscale",
            "Like the phone's own grayscale: the whole app, emojis and icons included, in sharp gray. Only inside QuestBlock; other apps stay in colour. Off by default.", gray) { gray = it; prefs.optGray = it; GrayMode = it }

        HorizontalDivider(color = ColorDivider)
        // ── Sound ──
        Text("\uD83D\uDD0A Sound", color = ColorText, fontWeight = FontWeight.SemiBold)
        var sfx by remember { mutableStateOf(com.krishna.questblock.util.Sounds.sfxOn) }
        ToggleRow("Sound effects", "Coin and level-up on quests, a chime when Focus ends, an alarm when an unlock runs out. The Focus background sound is picked on the Focus page.", sfx) { sfx = it; com.krishna.questblock.util.Sounds.sfxOn = it }

        HorizontalDivider(color = ColorDivider)
        // ── Focus ──
        Text("⚡ Focus", color = ColorText, fontWeight = FontWeight.SemiBold)
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        Text("Do Not Disturb is on during every Focus session (Priority only) and put back when it ends.", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
        if (!nm.isNotificationPolicyAccessGranted) {
            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text("⚠️ Allow Do Not Disturb access") }
        }
        var awake by remember { mutableStateOf(prefs.optFocusAwake) }
        ToggleRow("Keep the screen on", "While a Focus session runs and the Focus page is open", awake) { awake = it; prefs.optFocusAwake = it }
        var vibe by remember { mutableStateOf(prefs.optBreakVibrate) }
        ToggleRow("Vibrate when a break ends", null, vibe) { vibe = it; prefs.optBreakVibrate = it }
        var fnotif by remember { mutableStateOf(prefs.optFocusNotif) }
        ToggleRow("Hide Fun app notifications during Focus", "Needs Notification access", fnotif) { fnotif = it; prefs.optFocusNotif = it }
        if (fnotif && !Perms.notificationAccess(ctx)) {
            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("⚠️ Allow Notification access") }
        }

        HorizontalDivider(color = ColorDivider)
        // ── Quests and coins ──
        Text("🎯 Quests and coins", color = ColorText, fontWeight = FontWeight.SemiBold)
        var eve by remember { mutableStateOf(prefs.optEvening) }
        ToggleRow("Evening nudge", "If quests are still open", eve) { eve = it; prefs.optEvening = it }
        var hws by remember { mutableStateOf(prefs.optHwSweep) }
        ToggleRow("Homework sweep", "Evening: anything not written down yet? Same hour as the nudge", hws) { hws = it; prefs.optHwSweep = it }
        if (eve || hws) {
            var h by remember { mutableIntStateOf(prefs.optEveningHour) }
            StepperRow("Nudge at", h, 15..23, 1, "h") { h = it; prefs.optEveningHour = it }
        }
        var sum by remember { mutableStateOf(prefs.optSummary) }
        ToggleRow("Daily summary", "Quests done, coins, streak and fun time", sum) { sum = it; prefs.optSummary = it }
        if (sum) {
            var m by remember { mutableIntStateOf(prefs.optSummaryMin) }
            Text("Summary at ${hhmmOf(m)}", color = ColorText, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { m = ((m - 30) + 1440) % 1440; prefs.optSummaryMin = m }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("− 30 min") }
                OutlinedButton(onClick = { m = (m + 30) % 1440; prefs.optSummaryMin = m }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("+ 30 min") }
            }
        }
        var conf by remember { mutableStateOf(prefs.optConfirm) }
        ToggleRow("Ask before deleting a quest or skipping a step", null, conf) { conf = it; prefs.optConfirm = it }
        var undo by remember { mutableStateOf(prefs.optUndo) }
        ToggleRow("Undo after finishing a quest", "About 10 seconds to take it back (coins and XP too)", undo) { undo = it; prefs.optUndo = it }
        var hide by remember { mutableStateOf(prefs.optHideCoins) }
        ToggleRow("Hide the coin balance on Home", null, hide) { hide = it; prefs.optHideCoins = it }
        var wd by remember { mutableStateOf(prefs.optWidgetDone) }
        ToggleRow("Done button on the widget", "Ticks the next step or finishes the quest shown", wd) {
            wd = it; prefs.optWidgetDone = it
            com.krishna.questblock.service.QuestWidget.refresh(ctx)
        }
    }
}
