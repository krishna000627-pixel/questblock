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
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val player by vm.player.collectAsStateWithLifecycle()

    // Permission status refreshes while you're on this screen
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000L); tick++ } }

    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("Settings", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
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

        // ── Reliability ──────────────────────────────────────────────────
        Section("🛠️ Reliability") {
            Text("Battery, autostart and keep-alive shortcuts so the blocker isn't killed in the background.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            OutlinedButton(onClick = onOpenReliability, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()) { Text("Open Reliability page") }
        }

        // ── Strictness ───────────────────────────────────────────────────
        Section("🛡️ Strict mode") {
            var strict by remember { mutableStateOf(prefs.strictMode) }
            ToggleRow("Strict mode", "Kicks you out of system Settings pages that show QuestBlock — so you can't uninstall it or turn off its accessibility service on impulse",
                strict) { strict = it; prefs.strictMode = it }
            if (strict) {
                val paused = prefs.strictPausedUntil > System.currentTimeMillis()
                OutlinedButton(
                    onClick = {
                        prefs.strictPausedUntil = System.currentTimeMillis() + 10 * 60_000L
                        Toast.makeText(ctx, "Strict mode paused for 10 minutes", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorWarning),
                ) { Text(if (paused) "Paused (10 min)" else "Pause for 10 minutes") }
            }
        }

        // ── Permissions ──────────────────────────────────────────────────
        Section("✅ Permissions") {
            // reading `tick` here re-checks the status every second
            val stamp = tick
            val pkgUri = remember(stamp) { Uri.parse("package:${ctx.packageName}") }
            PermRow("Accessibility service", "Detects blocked apps", Perms.accessibilityEnabled(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermRow("Draw over other apps", "Shows the lock screen", Perms.overlayGranted(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
            }
            PermRow("Battery unrestricted", "Keeps blocking alive", Perms.batteryExempt(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            }
            PermRow("Device admin", "Protects against uninstall", Perms.adminActive(ctx)) {
                ctx.startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                )
            }
            val notifOk = Perms.notificationsOk(ctx)
            PermRow("Notifications", "Reminders, unlock timer, alerts", notifOk) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            val lastActive = prefs.lastActiveAt
            Text("Blocker last active: " +
                (if (lastActive == 0L) "never" else agoText(System.currentTimeMillis() - lastActive)),
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
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
                    Toast.makeText(ctx, if (ok) "Restored — restarting…" else "Not a valid backup file", Toast.LENGTH_SHORT).show()
                }
            }
            Text("Backup contains quests, coins, apps, rewards and stats. Settings above are not included.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    exportLauncher.launch("questblock-backup-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.db")
                }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) { Text("Restore") }
            }
            OutlinedButton(onClick = { confirmReset = true }, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDanger),
                modifier = Modifier.fillMaxWidth()) { Text("Reset all data") }
        }

        // ── About ────────────────────────────────────────────────────────
        Section("ℹ️ About") {
            val version = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("QuestBlock v$version", color = ColorText, fontWeight = FontWeight.SemiBold)
            Text("v11: no double charge on unlock · coins per 5 min · live timer · coin log · reliability · quest types",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        Spacer(Modifier.height(12.dp))
        Section("\uD83D\uDC1E Debug log") {
            var logText by remember { mutableStateOf(com.krishna.questblock.util.DebugLog.read()) }
            Text(if (logText.isBlank()) "(empty \u2014 try an unlock, then come back and tap Refresh)" else logText.takeLast(2500),
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { logText = com.krishna.questblock.util.DebugLog.read() }) { Text("Refresh") }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock log", com.krishna.questblock.util.DebugLog.read()))
                    android.widget.Toast.makeText(ctx, "Log copied", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                OutlinedButton(onClick = { com.krishna.questblock.util.DebugLog.clear(); logText = "" }) { Text("Clear") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
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

@Composable
private fun PermRow(label: String, sub: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✅" else "⚠️")
        Column(Modifier.weight(1f)) {
            Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
            Text(sub, color = ColorMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) TextButton(onClick = onFix) { Text("Fix", color = ColorPrimary, fontWeight = FontWeight.Bold) }
    }
}
