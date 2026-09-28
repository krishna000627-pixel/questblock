package com.krishna.questblock.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(private val prefs: Prefs) : ViewModel() {
    var strict by mutableStateOf(prefs.strictMode)
        private set

    fun setStrict(on: Boolean) {
        prefs.strictMode = on
        strict = on
    }
}

@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalContext.current as LifecycleOwner
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
fun SetupScreen(vm: SetupViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }

    val accessibility = remember(tick) { Perms.accessibilityEnabled(ctx) }
    val overlay = remember(tick) { Perms.overlayGranted(ctx) }
    val battery = remember(tick) { Perms.batteryExempt(ctx) }
    val admin = remember(tick) { Perms.adminActive(ctx) }
    val pkgUri = Uri.parse("package:${ctx.packageName}")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineSmall)

        StepCard(
            title = "1. Accessibility service (required)",
            body = "Lets QuestBlock see which app is open. On Android 13+ a sideloaded app shows this greyed out: " +
                "open App info, tap the ⋮ menu, choose Allow restricted settings, then come back.",
            done = accessibility,
            action = "Open accessibility",
            onClick = { ctx.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        StepCard(
            title = "App info (for restricted settings)",
            body = "Use this if the accessibility toggle is greyed out.",
            done = null,
            action = "Open app info",
            onClick = {
                ctx.safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            },
        )
        StepCard(
            title = "2. Display over other apps (recommended)",
            body = "Makes the lock screen appear reliably on top of a blocked app.",
            done = overlay,
            action = "Grant",
            onClick = { ctx.safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)) },
        )
        StepCard(
            title = "3. Ignore battery optimization (recommended)",
            body = "Stops the system from killing the service. On Xiaomi, Oppo, Vivo, Realme and Samsung also " +
                "enable Autostart and set battery to No restrictions.",
            done = battery,
            action = "Allow",
            onClick = { ctx.safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) },
        )
        StepCard(
            title = "4. Device admin (optional)",
            body = "Uninstalling then requires deactivating admin first.",
            done = admin,
            action = "Activate",
            onClick = {
                ctx.safeStart(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Makes QuestBlock harder to uninstall on a whim.",
                        )
                )
            },
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sends you home if you open Settings or uninstall screens that show QuestBlock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.strict, onCheckedChange = vm::setStrict)
            }
        }
    }
}

@Composable
private fun StepCard(title: String, body: String, done: Boolean?, action: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (done == true) {
                Text("✅ Done", color = MaterialTheme.colorScheme.secondary)
            } else {
                Button(onClick = onClick) { Text(action) }
            }
        }
    }
}
