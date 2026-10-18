@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.agoText
import com.krishna.questblock.service.BlockerAccessibilityService
import com.krishna.questblock.service.KeepAlive
import com.krishna.questblock.util.Autostart
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class ReliabilityViewModel @Inject constructor(val prefs: Prefs) : ViewModel()

/**
 * One-tap shortcuts for the things that decide whether Android lets the blocker live.
 * No app can guarantee survival on very aggressive phones; these are the standard fixes.
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit, vm: ReliabilityViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val prefs = vm.prefs

    // status refreshes while the page is open
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000L); tick++ } }
    val stamp = tick
    val now = remember(stamp) { System.currentTimeMillis() }

    val accessibilityOn = remember(stamp) { Perms.accessibilityEnabled(ctx) }
    val batteryOk = remember(stamp) { Perms.batteryExempt(ctx) }
    val notifOk = remember(stamp) { Perms.notificationsOk(ctx) }
    var keepAlive by remember { mutableStateOf(prefs.keepAlive) }
    val keepAliveRunning = keepAlive && now - prefs.keepAliveBeat < 30_000L
    val lastActive = prefs.lastActiveAt
    val pkgUri = remember { Uri.parse("package:${ctx.packageName}") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("Reliability", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        // ── Status ──────────────────────────────────────────────────────
        RCard("📡 Status") {
            StatusLine("Blocker (accessibility)", accessibilityOn)
            StatusLine("Keep-alive service", keepAliveRunning)
            Text("Blocker last active: " + (if (lastActive == 0L) "never" else agoText(now - lastActive)),
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            if (!accessibilityOn) {
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(10.dp)) { Text("Turn the blocker back on", fontWeight = FontWeight.Bold) }
            }
        }

        // ── Strict mode ─────────────────────────────────────────────────
        RpgPanel("🛡️ Strict mode", accent = ColorDanger) {
            var strict by remember { mutableStateOf(prefs.pendingFlag("strict") ?: prefs.strictMode) }
            var note by remember { mutableStateOf(prefs.pendingSummary()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", color = ColorText, fontWeight = FontWeight.SemiBold)
                    Text("Kicks you out of system Settings pages that show QuestBlock, so you can't uninstall it or turn off its accessibility service on impulse.",
                        color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = strict, onCheckedChange = {
                    prefs.strictMode = it
                    strict = prefs.pendingFlag("strict") ?: prefs.strictMode
                    note = prefs.pendingSummary()
                }, colors = SwitchDefaults.colors(checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                    uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh))
            }
            Text("🔒 Strict is off by default. Once on, turning it off takes effect at midnight, and the 5-minute pause can be used once a day.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            if (note.contains("strict")) Text(note, style = MaterialTheme.typography.bodySmall, color = ColorWarning, fontWeight = FontWeight.SemiBold)
            if (strict) {
                val paused = prefs.strictPausedUntil > now
                val used = prefs.strictPauseUsedToday()
                OutlinedButton(
                    onClick = {
                        if (prefs.pauseStrict(5)) Toast.makeText(ctx, "Strict mode paused for 5 minutes", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(ctx, "You already used today's pause", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !used && !paused,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorWarning),
                ) { Text(if (paused) "Paused (5 min)" else if (used) "Pause used today" else "Pause for 5 minutes") }
            }
        }

        // ── Permissions ─────────────────────────────────────────────────
        RpgPanel("✅ Permissions", accent = ColorPrimary) {
            val pkgUriP = remember { Uri.parse("package:${ctx.packageName}") }
            PermRow("Accessibility service", "Detects blocked apps", accessibilityOn) {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PermRow("Draw over other apps", "Shows the lock screen", remember(stamp) { Perms.overlayGranted(ctx) }) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUriP))
            }
            PermRow("Battery unrestricted", "Keeps blocking alive", batteryOk) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUriP)) }
                    .onFailure { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
            PermRow("Device admin", "Protects against uninstall", remember(stamp) { Perms.adminActive(ctx) }) {
                ctx.startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                )
            }
            PermRow("Usage access", "Study coins and the App usage page", remember(stamp) { com.krishna.questblock.data.UsageTracker.hasAccess(ctx) }) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermRow("Notifications", "Reminders, unlock timer, alerts", notifOk) {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            PermRow("Notification access", "Names the app playing music", remember(stamp) { Perms.notificationAccess(ctx) }) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        // ── Battery ─────────────────────────────────────────────────────
        RCard("🔋 Battery") {
            StatusLine("Unrestricted", batteryOk)
            Text("Lets QuestBlock keep running while the screen is off.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            if (!batteryOk) {
                Button(onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) }
                        .onFailure { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }, colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(10.dp)) { Text("Allow unrestricted", fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } },
                    shape = RoundedCornerShape(10.dp)) { Text("Battery settings") }
            }
        }

        // ── Autostart ───────────────────────────────────────────────────
        RCard("🚀 Autostart") {
            Text("${Autostart.brandName()} phones often stop apps in the background unless autostart is on. " +
                "Turn it on for QuestBlock.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Button(onClick = {
                if (!Autostart.open(ctx)) Toast.makeText(ctx,
                    "Couldn't find a ${Autostart.brandName()} autostart page — opened App info. Look for Battery / Auto-launch.",
                    Toast.LENGTH_LONG).show()
            }, colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(10.dp)) { Text("Open autostart settings", fontWeight = FontWeight.Bold) }
        }

        // ── Recents ─────────────────────────────────────────────────────
        RCard("📌 Lock in recents") {
            Text("Swiping QuestBlock away in Recents can stop it. Lock its card so \"Clear all\" skips it.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Text(Autostart.recentsHint(), style = MaterialTheme.typography.bodyMedium, color = ColorText)
            OutlinedButton(onClick = {
                if (!BlockerAccessibilityService.openRecents())
                    Toast.makeText(ctx, "Turn the blocker on first, or open Recents yourself.", Toast.LENGTH_SHORT).show()
            }, shape = RoundedCornerShape(10.dp)) { Text("Open Recents") }
        }

        // ── Keep-alive + notifications ──────────────────────────────────
        RCard("🔔 Keep-alive notification") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Keep-alive service", color = ColorText, style = MaterialTheme.typography.bodyMedium)
                    Text("A small persistent notification. Also shows unlock time left and alerts you if blocking turns off.",
                        color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = keepAlive, onCheckedChange = {
                    keepAlive = it; prefs.keepAlive = it
                    if (it) KeepAlive.start(ctx) else KeepAlive.stop(ctx)
                }, colors = SwitchDefaults.colors(checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                    uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh))
            }
            StatusLine("Notifications allowed", notifOk)
            if (!notifOk) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }, shape = RoundedCornerShape(10.dp)) { Text("Allow notifications") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorPrimary)
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ok) "✅" else "⚠️")
        Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PermRow(label: String, sub: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (ok) "✅" else "⚠️", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(label, color = ColorText, fontWeight = FontWeight.SemiBold)
            Text(sub, color = ColorMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) TextButton(onClick = onFix) { Text("Fix", fontWeight = FontWeight.Bold) }
    }
}
