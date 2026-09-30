package com.krishna.questblock.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.krishna.questblock.util.Perms

private data class OnboardStep(
    val emoji: String,
    val title: String,
    val body: String,
    val actionLabel: String?,
    val isDone: (Context) -> Boolean,
    val onAction: (Context) -> Unit,
)

private fun onboardSteps(pkgUri: Uri) = listOf(
    OnboardStep(
        emoji = "⚔️",
        title = "Welcome to QuestBlock",
        body = "Turn your distractions into a game. Lock apps, complete quests, earn coins. Let's get you set up.",
        actionLabel = null,
        isDone = { false },
        onAction = {},
    ),
    OnboardStep(
        emoji = "♿",
        title = "Enable accessibility",
        body = "QuestBlock watches which app is open to enforce locks. On Android 13+, open App info → ⋮ → Allow restricted settings first.",
        actionLabel = "Open accessibility settings",
        isDone = { Perms.accessibilityEnabled(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    ),
    OnboardStep(
        emoji = "🪟",
        title = "Allow overlay",
        body = "This lets the lock screen appear on top of blocked apps reliably.",
        actionLabel = "Grant overlay permission",
        isDone = { Perms.overlayGranted(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)) },
    ),
    OnboardStep(
        emoji = "🔋",
        title = "Battery exemption",
        body = "Stops Android from killing the blocker service in the background.",
        actionLabel = "Exempt from battery saver",
        isDone = { Perms.batteryExempt(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) },
    ),
    OnboardStep(
        emoji = "🛡️",
        title = "Device admin (optional)",
        body = "Makes QuestBlock harder to uninstall on a whim. You can skip this.",
        actionLabel = "Activate device admin",
        isDone = { Perms.adminActive(it) },
        onAction = { ctx ->
            ctx.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Harder to uninstall on a whim.")
            )
        },
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    val steps = remember { onboardSteps(pkgUri) }

    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val current = steps[step]
    val done = remember(tick, step) { current.isDone(ctx) }
    val isLast = step == steps.size - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(ColorBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(if (i == step) 20.dp else 6.dp, 6.dp)
                        .background(if (i == step) ColorPrimary else ColorSurfaceHigh, CircleShape)
                )
            }
        }

        // Content
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "onboard_step",
        ) { s ->
            val st = steps[s]
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(st.emoji, style = MaterialTheme.typography.displayLarge)
                Text(
                    st.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ColorText,
                    textAlign = TextAlign.Center,
                )
                Text(
                    st.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorMuted,
                    textAlign = TextAlign.Center,
                )
                if (done) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorPrimary)
                        Text("Done!", style = MaterialTheme.typography.labelLarge, color = ColorPrimary)
                    }
                }
            }
        }

        // Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current.actionLabel != null && !done) {
                Button(
                    onClick = { runCatching { current.onAction(ctx) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(current.actionLabel, fontWeight = FontWeight.Bold)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) {
                        Text("Back", color = ColorMuted)
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }

                Button(
                    onClick = {
                        if (isLast) onDone() else step++
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (done || current.actionLabel == null || isLast) ColorPrimary else ColorSurfaceHigh,
                        contentColor = if (done || current.actionLabel == null || isLast) ColorOnPrimary else ColorMuted,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        when {
                            isLast -> "Let's go!"
                            done || current.actionLabel == null -> "Next"
                            else -> "Skip"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
