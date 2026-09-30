package com.krishna.questblock.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val prefs: Prefs) : ViewModel() {
    var isStrict by mutableStateOf(prefs.strictMode)
        private set

    // Pause strict mode for 5 minutes so user can complete setup
    fun pauseStrict() {
        prefs.strictPausedUntil = System.currentTimeMillis() + 5 * 60 * 1000L
    }

    fun toggleStrict(on: Boolean) {
        prefs.strictMode = on
        if (!on) prefs.strictPausedUntil = 0L
        isStrict = on
    }
}

@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) block()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent) }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }

    val accessibility = remember(tick) { Perms.accessibilityEnabled(ctx) }
    val overlay = remember(tick) { Perms.overlayGranted(ctx) }
    val battery = remember(tick) { Perms.batteryExempt(ctx) }
    val admin = remember(tick) { Perms.adminActive(ctx) }
    val allGood = accessibility && overlay

    val pkgUri = Uri.parse("package:${ctx.packageName}")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profile & Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ColorText)

        // Health summary
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (allGood) ColorPrimary.copy(alpha = 0.1f) else ColorDanger.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (allGood) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (allGood) ColorPrimary else ColorDanger,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    if (allGood) "All permissions granted. Blocker is active." else "Some permissions are missing. Blocker may not work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (allGood) ColorPrimary else ColorDanger,
                )
            }
        }

        // Step cards
        PermCard(
            title = "Accessibility service",
            body = "Required. On Android 13+: open App info → ⋮ menu → Allow restricted settings, then return here.",
            done = accessibility,
            actionLabel = "Open settings",
            onAction = {
                vm.pauseStrict()
                ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )
        PermCard(
            title = "App info (restricted settings)",
            body = "Use this first if the accessibility toggle is greyed out.",
            done = null,
            actionLabel = "Open app info",
            onAction = {
                vm.pauseStrict()
                ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            },
        )
        PermCard(
            title = "Display over other apps",
            body = "Makes the lock screen appear on top reliably.",
            done = overlay,
            actionLabel = "Grant overlay",
            onAction = {
                vm.pauseStrict()
                ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
            },
        )
        PermCard(
            title = "Battery optimization",
            body = "Prevents the system from killing the blocker service. On Xiaomi/Samsung also enable Autostart.",
            done = battery,
            actionLabel = "Allow",
            onAction = {
                vm.pauseStrict()
                ctx.safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
            },
        )
        PermCard(
            title = "Device admin (optional)",
            body = "Makes QuestBlock harder to uninstall on impulse.",
            done = admin,
            actionLabel = "Activate",
            onAction = {
                vm.pauseStrict()
                ctx.safeStart(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Makes QuestBlock harder to uninstall on a whim."),
                )
            },
        )

        // Strict mode
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorSurface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium, color = ColorText, fontWeight = FontWeight.Medium)
                    Text(
                        "Kicks you home if you open Settings screens that show QuestBlock. Auto-paused 5 min when you tap any setup button above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorMuted,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = vm.isStrict,
                    onCheckedChange = vm::toggleStrict,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ColorOnPrimary,
                        checkedTrackColor = ColorPrimary,
                        uncheckedThumbColor = ColorMuted,
                        uncheckedTrackColor = ColorSurfaceHigh,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PermCard(title: String, body: String, done: Boolean?, actionLabel: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done == true) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(18.dp))
                } else if (done == false) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ColorDanger, modifier = Modifier.size(18.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, color = ColorText, fontWeight = FontWeight.Medium)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            if (done != true) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorPrimary,
                        contentColor = ColorOnPrimary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}
