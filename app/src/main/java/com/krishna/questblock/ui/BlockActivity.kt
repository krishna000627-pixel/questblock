package com.krishna.questblock.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.krishna.questblock.data.fmtLeft
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
    /** True once we've decided to open the target app — prevents finishAndRemoveTask in onStop. */
    private var launching = false

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
        launching = false
        opened = false
    }

    override fun onStop() {
        super.onStop()
        // Only self-destruct when we're backgrounded without the user actively launching the target.
        // `launching` is set to true just before we call startActivity so we don't kill ourselves
        // in the same frame. Config changes are excluded via isChangingConfigurations.
        if (!isChangingConfigurations && !isFinishing && !launching) {
            finishAndRemoveTask()
        }
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
        if (opened) return
        opened = true
        launching = true       // ← tell onStop not to destroy us mid-launch
        packageManager.getLaunchIntentForPackage(target)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PKG = "target_pkg"
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
    var paying by remember(pkg) { mutableStateOf(false) }

    // Already unlocked: exit immediately — single tap scenario covered here too
    val unlockedUntil = rule?.unlockedUntil ?: 0L
    LaunchedEffect(unlockedUntil) {
        if (unlockedUntil > System.currentTimeMillis()) onUnlocked()
    }

    // Live unlock-timer countdown (shown when currently unlocked)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(unlockedUntil) {
        while (unlockedUntil > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val unlockLeftMs = if (unlockedUntil > nowMs) unlockedUntil - nowMs else 0L

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
                            KeepAlive.start(ctx)
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

    val iconBitmap = remember(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 128, 128)
            d.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
    val questIsPrimary = rule != null && !canAfford

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ColorBg, ColorSurface)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(ColorSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (iconBitmap != null) {
                    Image(iconBitmap, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text(label.firstOrNull()?.uppercase() ?: "?", fontSize = 28.sp, color = ColorText, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "$label is locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
                textAlign = TextAlign.Center,
            )

            if (quotesOn) {
                Spacer(Modifier.height(6.dp))
                Text(quote, style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            if (rule != null) {
                val missing = cost - player.coins

                // Time selector row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton("−", enabled = blocks > 1) { blocks = (blocks - 1).coerceAtLeast(1) }
                    Row(
                        Modifier.width(190.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(fmtBlocks(blocks), fontSize = 30.sp, fontWeight = FontWeight.Black, color = ColorText)
                        Text(
                            "  ·  $cost 🪙",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canAfford) ColorCoin else ColorDanger,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    StepButton("+", enabled = blocks < MAX_UNLOCK_BLOCKS) {
                        blocks = (blocks + 1).coerceAtMost(MAX_UNLOCK_BLOCKS)
                    }
                }

                if (rule.ratePer5 > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Custom rate: ${rule.ratePer5} 🪙 per 5 min",
                        style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }

                // ── Unlock timer: show time remaining if currently unlocked ──
                if (unlockLeftMs > 0L) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ColorCoin.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "⏱ ${fmtLeft(unlockLeftMs)} remaining",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (unlockLeftMs <= 60_000L) ColorDanger else ColorCoin,
                            )
                            Text(
                                "Tap go to use it now, or add more time below",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // One-tap "Open now" when already unlocked
                    Button(
                        onClick = onUnlocked,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCoin, contentColor = ColorBg),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Open ${label}", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(10.dp))
                }

                if (unlockLeftMs == 0L) {
                    Spacer(Modifier.height(20.dp))

                    // Hold-to-unlock button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canAfford) ColorPrimary.copy(alpha = 0.22f) else ColorSurfaceHigh)
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
                        if (animatedHold > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(animatedHold)
                                    .height(56.dp)
                                    .background(ColorPrimary.copy(alpha = 0.5f))
                                    .align(Alignment.CenterStart)
                            )
                        }
                        Text(
                            if (canAfford) "Hold to unlock" else "Need $missing more 🪙",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (canAfford) ColorPrimary else ColorMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }

            if (questIsPrimary) {
                Button(
                    onClick = onQuests,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Do a quest instead", fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(
                    onClick = onQuests,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText),
                    border = BorderStroke(1.dp, ColorSurfaceHigh),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Do a quest instead") }
            }

            Spacer(Modifier.height(6.dp))

            TextButton(onClick = onHome) {
                Text("Go home", color = ColorMuted)
            }
        }

        // Coin balance chip
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ColorSurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🪙", fontSize = 14.sp)
            Text("${player.coins}", style = MaterialTheme.typography.labelLarge,
                color = ColorCoin, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) ColorPrimary.copy(alpha = 0.18f) else ColorSurfaceHigh)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            step()
                            coroutineScope {
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
