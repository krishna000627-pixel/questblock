package com.krishna.questblock.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: GameRepository,
    val prefs: Prefs,
) : ViewModel() {
    fun saveProfile(name: String, goal: String, dob: String) = viewModelScope.launch {
        repo.saveProfile(name, goal, dob)
    }
    fun finishOnboarding() { prefs.onboardingDone = true }
}

// ── Step model ────────────────────────────────────────────────────────────────
private sealed class OnboardStep {
    data class InfoStep(
        val emoji: String,
        val title: String,
        val body: String,
    ) : OnboardStep()

    data class ProfileStep(
        val emoji: String,
        val title: String,
        val body: String,
    ) : OnboardStep()

    data class PermStep(
        val emoji: String,
        val title: String,
        val body: String,
        val actionLabel: String?,
        val isDone: (Context) -> Boolean,
        val onAction: (Context) -> Unit,
    ) : OnboardStep()
}

private fun buildSteps(pkgUri: Uri): List<OnboardStep> = listOf(
    OnboardStep.InfoStep(
        emoji = "⚔️",
        title = "Welcome to QuestBlock",
        body = "Turn your distractions into a game.\nLock apps, complete quests, earn coins!\nLet's set you up as a hero.",
    ),
    OnboardStep.ProfileStep(
        emoji = "🧙",
        title = "Create your hero",
        body = "Tell us a bit about yourself to personalise your quest journey.",
    ),
    OnboardStep.PermStep(
        emoji = "♿",
        title = "Enable accessibility",
        body = "QuestBlock watches which app is open to enforce locks. On Android 13+, open App info → ⋮ → Allow restricted settings first.",
        actionLabel = "Open accessibility settings",
        isDone = { Perms.accessibilityEnabled(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    ),
    OnboardStep.PermStep(
        emoji = "🪟",
        title = "Allow overlay",
        body = "Lets the lock screen appear reliably on top of blocked apps.",
        actionLabel = "Grant overlay permission",
        isDone = { Perms.overlayGranted(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)) },
    ),
    OnboardStep.PermStep(
        emoji = "🔋",
        title = "Battery exemption",
        body = "Stops Android from killing the blocker in the background.",
        actionLabel = "Exempt from battery saver",
        isDone = { Perms.batteryExempt(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) },
    ),
    OnboardStep.PermStep(
        emoji = "🔔",
        title = "Allow notifications",
        body = "Unlock timers, reminders and warnings are shown as notifications.",
        actionLabel = "Open notification settings",
        isDone = { Perms.notificationsOk(it) },
        onAction = { ctx ->
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        },
    ),
    OnboardStep.PermStep(
        emoji = "🎧",
        title = "Notification access (optional)",
        body = "Lets QuestBlock see which app is playing music, even if that app's notifications are off.",
        actionLabel = "Open notification access",
        isDone = { Perms.notificationAccess(it) },
        onAction = { it.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
    ),
    OnboardStep.PermStep(
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
fun OnboardingScreen(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    val steps = remember { buildSteps(pkgUri) }

    var step by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    // Profile fields (shared state lives here so Back doesn't wipe them)
    var heroName by remember { mutableStateOf("") }
    var heroGoal by remember { mutableStateOf("") }
    var heroDob  by remember { mutableStateOf("") }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val current = steps[step]
    val isLast = step == steps.size - 1

    val permDone = remember(tick, step) {
        if (current is OnboardStep.PermStep) current.isDone(ctx) else false
    }

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

        // Content area
        AnimatedContent(
            targetState = step,
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it } },
            label = "onboard_step",
        ) { s ->
            when (val st = steps[s]) {
                is OnboardStep.InfoStep -> InfoContent(st)
                is OnboardStep.ProfileStep -> ProfileContent(
                    heroName = heroName,
                    heroGoal = heroGoal,
                    heroDob  = heroDob,
                    onNameChange = { heroName = it },
                    onGoalChange = { heroGoal = it },
                    onDobChange  = { heroDob  = it },
                )
                is OnboardStep.PermStep -> PermContent(st, permDone)
            }
        }

        // Bottom buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Action button for perm steps
            if (current is OnboardStep.PermStep && current.actionLabel != null && !permDone) {
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

                val canProceed = when (current) {
                    is OnboardStep.InfoStep    -> true
                    is OnboardStep.ProfileStep -> heroName.isNotBlank()
                    is OnboardStep.PermStep    -> permDone || current.actionLabel == null
                }

                Button(
                    onClick = {
                        if (isLast) {
                            vm.saveProfile(heroName, heroGoal, heroDob)
                            vm.finishOnboarding()
                            onDone()
                        } else {
                            if (current is OnboardStep.ProfileStep) {
                                vm.saveProfile(heroName, heroGoal, heroDob)
                            }
                            step++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canProceed || isLast) ColorPrimary else ColorSurfaceHigh,
                        contentColor   = if (canProceed || isLast) ColorOnPrimary else ColorMuted,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        when {
                            isLast                                                     -> "Let's go!"
                            current is OnboardStep.PermStep && !permDone && current.actionLabel != null -> "Skip"
                            else                                                       -> "Next"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoContent(step: OnboardStep.InfoStep) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(112.dp).clip(HexShape)
            .background(Brush.verticalGradient(listOf(ColorPrimary.copy(0.55f), ColorPrimary.copy(0.10f))))
            .border(2.dp, ColorPrimary, HexShape),
            contentAlignment = Alignment.Center) {
            Text(step.emoji, style = MaterialTheme.typography.displayMedium)
        }
        Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = ColorText, textAlign = TextAlign.Center)
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileContent(
    heroName: String,
    heroGoal: String,
    heroDob: String,
    onNameChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onDobChange: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🧙", style = MaterialTheme.typography.displayLarge)
        Text("Create your hero", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)

        OutlinedTextField(
            value = heroName,
            onValueChange = onNameChange,
            label = { Text("Your name *", color = ColorMuted) },
            placeholder = { Text("e.g. Krishna", color = ColorMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        OutlinedTextField(
            value = heroGoal,
            onValueChange = onGoalChange,
            label = { Text("Your main goal", color = ColorMuted) },
            placeholder = { Text("e.g. Study 4 hours daily", color = ColorMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        OutlinedTextField(
            value = heroDob,
            onValueChange = onDobChange,
            label = { Text("Date of birth", color = ColorMuted) },
            placeholder = { Text("DD/MM/YYYY", color = ColorMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text("* Required to continue", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
    }
}

@Composable
private fun PermContent(step: OnboardStep.PermStep, done: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(step.emoji, style = MaterialTheme.typography.displayLarge)
        Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = ColorText, textAlign = TextAlign.Center)
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
        if (done) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorPrimary)
                Text("Done!", style = MaterialTheme.typography.labelLarge, color = ColorPrimary)
            }
        }
    }
}

@Composable
private fun onboardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = ColorPrimary,
    unfocusedBorderColor = ColorSurfaceHigh,
    cursorColor          = ColorPrimary,
    focusedTextColor     = ColorText,
    unfocusedTextColor   = ColorText,
    focusedLabelColor    = ColorPrimary,
)
