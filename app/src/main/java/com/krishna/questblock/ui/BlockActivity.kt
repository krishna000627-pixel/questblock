package com.krishna.questblock.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.MAX_UNLOCK_BLOCKS
import com.krishna.questblock.data.fmtBlocks
import com.krishna.questblock.data.unlockCost
import com.krishna.questblock.service.KeepAlive
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val blocked = repo.blockedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())

    fun rate(): Int = repo.ratePer5()
    fun holdSeconds(): Int = repo.holdSeconds()
    fun showQuotes(): Boolean = repo.showQuotes()
    fun unlock(pkg: String, blocks: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.unlock(pkg, blocks)) }
    }
}

@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    private var target by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
        showingFor = target
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            QuestTheme {
                BlockScreen(
                    pkg = target,
                    onHome = ::goHome,
                    onQuests = ::openQuests,
                    onUnlocked = ::openTarget,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
        showingFor = target
        opened = false
    }

    // Never linger in the background. A leftover, half-alive lock screen is what ate taps on app icons.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) finishAndRemoveTask()
    }

    override fun onDestroy() {
        if (showingFor == target) showingFor = ""
        super.onDestroy()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    private fun openQuests() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private var opened = false

    private fun openTarget() {
        if (opened) return          // paid + "already unlocked" can both ask; open the app only once
        opened = true
        packageManager.getLaunchIntentForPackage(target)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PKG = "target_pkg"
        /** Package the lock screen is currently showing for ("" = not visible). Read by the accessibility service. */
        @Volatile var showingFor: String = ""
    }
}

private val MOTIVATIONAL_LINES = listOf(
    "Your future self will thank you.",
    "One distraction avoided. One step forward.",
    "Focus is a superpower. Use it.",
    "Real quests don't pause for reels.",
    "Close it. You don't need it right now.",
    "Stay locked in. You're on a streak.",
)

@Composable
fun BlockScreen(
    pkg: String,
    onHome: () -> Unit,
    onQuests: () -> Unit,
    onUnlocked: () -> Unit,
    vm: BlockViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val rule = blocked.firstOrNull { it.packageName == pkg }
    val label = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
    val quote = remember(pkg) { MOTIVATIONAL_LINES.random() }
    val rate = remember { vm.rate() }
    val holdSteps = remember { vm.holdSeconds() * 10 }
    val quotesOn = remember { vm.showQuotes() }

    // Unlock time in 5-minute blocks (3 = 15 min). Cost = blocks × rate.
    var blocks by remember { mutableIntStateOf(3) }
    val cost = unlockCost(blocks, rate, rule)
    val canAfford = player.coins >= cost
    val blocksNow by rememberUpdatedState(blocks)
    val costNow by rememberUpdatedState(cost)
    val coinsNow by rememberUpdatedState(player.coins)

    // Hold-to-unlock state
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val animatedHold by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 100),
        label = "hold_progress",
    )
    var isHolding by remember { mutableStateOf(false) }
    var paying by remember(pkg) { mutableStateOf(false) }   // one purchase per lock screen

    // Already unlocked (paid a moment ago, or from another lock screen): never ask to pay again.
    val unlockedUntil = rule?.unlockedUntil ?: 0L
    LaunchedEffect(unlockedUntil) {
        if (unlockedUntil > System.currentTimeMillis()) onUnlocked()
    }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val steps = holdSteps
            repeat(steps) {
                if (!isHolding) return@repeat
                delay(100L)
                holdProgress = (it + 1) / steps.toFloat()
            }
            if (isHolding && rule != null && !paying) {
                if (coinsNow >= costNow) {
                    paying = true
                    // Haptic
                    runCatching {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                                ?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                    }.getOrNull()?.let { vib ->
                        vib.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    vm.unlock(pkg, blocksNow) { ok ->
                        if (ok) {
                            KeepAlive.start(ctx)   // live tracker lives in the keep-alive service
                            onUnlocked()
                        } else {
                            paying = false
                            Toast.makeText(ctx, "Not enough coins", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(ctx, "Not enough coins — do a quest first", Toast.LENGTH_SHORT).show()
                }
                holdProgress = 0f
                isHolding = false
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ColorBg, ColorSurface))
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon placeholder
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ColorSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.firstOrNull()?.uppercase() ?: "?", fontSize = 36.sp, color = ColorText, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "$label is locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            if (quotesOn) {
                Text(
                    quote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Coin balance
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🪙", fontSize = 20.sp)
                Text(
                    "${player.coins} coins",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorCoin,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (rule != null) {
                val missing = cost - player.coins

                // − / + time picker: 5-minute steps, live cost. Hold a button to repeat.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton("−", enabled = blocks > 1) { blocks = (blocks - 1).coerceAtLeast(1) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtBlocks(blocks), fontSize = 32.sp, fontWeight = FontWeight.Black, color = ColorText)
                        Text(
                            "$cost 🪙",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canAfford) ColorCoin else ColorDanger,
                            fontWeight = FontWeight.Bold,
                        )
                        val r = rule.ratePer5.takeIf { it > 0 } ?: rate
                        Text("$r 🪙 per 5 min", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                    StepButton("+", enabled = blocks < MAX_UNLOCK_BLOCKS) {
                        blocks = (blocks + 1).coerceAtMost(MAX_UNLOCK_BLOCKS)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Hold-to-unlock button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canAfford) ColorPrimary.copy(alpha = 0.15f) else ColorSurfaceHigh)
                            .pointerInput(canAfford) {
                                if (canAfford) {
                                    detectTapGestures(
                                        onPress = {
                                            isHolding = true
                                            tryAwaitRelease()
                                            isHolding = false
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Progress fill
                        if (animatedHold > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(animatedHold)
                                    .height(54.dp)
                                    .background(ColorPrimary.copy(alpha = 0.3f))
                                    .align(Alignment.CenterStart)
                            )
                        }
                        Text(
                            if (canAfford) "Hold to unlock · $cost 🪙 · ${fmtBlocks(blocks)}"
                            else "Need $missing more coins",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canAfford) ColorPrimary else ColorMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (!canAfford) {
                        Text(
                            "Complete a quest to earn coins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onQuests,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceHigh, contentColor = ColorText),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Do a quest instead") }

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = onHome) {
                Text("Go home", color = ColorMuted)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (enabled) ColorPrimary.copy(alpha = 0.18f) else ColorSurfaceHigh)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            step()
                            coroutineScope {
                                // keep stepping while the finger stays down
                                val repeater = launch {
                                    delay(400L)
                                    while (true) { step(); delay(90L) }
                                }
                                tryAwaitRelease()
                                repeater.cancel()
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) ColorPrimary else ColorMuted)
    }
}
