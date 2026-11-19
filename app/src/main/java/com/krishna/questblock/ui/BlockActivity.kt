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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.krishna.questblock.data.UnlockResult
import com.krishna.questblock.data.fmtBlocks
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.data.lockKeyOf
import com.krishna.questblock.data.pseudoLabel
import com.krishna.questblock.data.unlockCost
import com.krishna.questblock.service.KeepAlive
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
    /** Extra percent on unlock prices while "Routine first" is on and today's routine is unfinished. */
    var surcharge by mutableIntStateOf(0)
        private set
    init { viewModelScope.launch { surcharge = repo.routineSurchargePct() } }
    fun siteUnlockedUntil(domain: String): Long = repo.siteUnlockedUntil(domain)
    fun holdSeconds(): Int = repo.holdSeconds()
    fun showQuotes(): Boolean = repo.showQuotes()
    /** onResult(ok, error): error is non-null only when something actually threw, so it can be shown on screen. */
    fun unlock(pkg: String, blocks: Int, onResult: (UnlockResult, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.unlock(pkg, blocks) }
                .onSuccess { onResult(it, null) }
                .onFailure {
                    android.util.Log.e("QuestBlock", "unlock failed", it)
                    onResult(UnlockResult(ok = false), "${it.javaClass.simpleName}: ${it.message}")
                }
        }
    }
}

@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    private var target by mutableStateOf("")
    /** Bumped on every re-launch so a stale 'paying' / status from an earlier attempt can't freeze the new lock page. */
    private var nonce by mutableIntStateOf(0)
    /** True when the daily fun limit (not an unlock) is why this page is showing. */
    private var funLimit by mutableStateOf(false)
    /** True once we've decided to open the target app — prevents finishAndRemoveTask in onStop. */
    private var launching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
        funLimit = intent.getBooleanExtra(EXTRA_FUN_LIMIT, false)
        showingFor = target
        com.krishna.questblock.util.DebugLog.d("BlockActivity.onCreate pkg=$target funLimit=$funLimit")
        onBackPressedDispatcher.addCallback(this) { goHome() }
        loadThemeMode(this)
        TermuxMode.load(this)
        SvgIconsMode.load(this)   // v224: emoji drawn as SVG icons
        setContent {
            QuestTheme {
                if (TermuxOn) {
                    // v207: the lock page is a terminal page
                    TermuxBlockScreen(
                        pkg = target,
                        nonce = nonce,
                        funLimit = funLimit,
                        onHome = ::goHome,
                        onMain = ::openMainTerminal,
                        onOpen = ::openTarget,
                    )
                } else {
                    BlockScreen(
                        pkg = target,
                        nonce = nonce,
                        funLimit = funLimit,
                        onHome = ::goHome,
                        onQuests = ::openQuests,
                        onUnlocked = ::openTarget,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
        funLimit = intent.getBooleanExtra(EXTRA_FUN_LIMIT, false)
        showingFor = target
        launching = false
        opened = false
        nonce++
        com.krishna.questblock.util.DebugLog.d("BlockActivity.onNewIntent pkg=$target")
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
        // Shorts / Reels lock: go to the app's main screen (home feed), not into Shorts/Reels and not the phone home screen
        if (target.startsWith("short:") || target.startsWith("yt:") || target.startsWith("ytstudy:")) {
            val pkgs = if (target != "short:ig")
                listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube")
            else listOf("com.instagram.android")
            val intent = pkgs.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            if (intent != null) {
                // CLEAR_TASK restarts the app on its main screen, so it cannot resume inside Shorts/Reels
                runCatching {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.onSuccess { launching = true; finishAndRemoveTask(); return }
            }
        }
        // blocked website: send the browser to a neutral page so other sites stay usable
        if (target.startsWith("site:") && browserPkg.isNotEmpty()) {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
                        .setPackage(browserPkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess { finishAndRemoveTask(); return }
        }
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

    /** v207: Termux theme: open QuestBlock's terminal with a useful command already typed in. */
    private fun openMainTerminal() {
        val route = if (target.startsWith("ytstudy:")) "chapters" else "quests"
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OPEN_ROUTE, route)
        )
        finishAndRemoveTask()
    }

    private var opened = false

    private fun openTarget() {
        if (opened) return
        opened = true
        launching = true       // ← tell onStop not to destroy us mid-launch
        if (!target.startsWith("site:") && !target.startsWith("short:") && !target.startsWith("yt:")) packageManager.getLaunchIntentForPackage(target)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PKG = "target_pkg"
        const val EXTRA_FUN_LIMIT = "fun_limit"
        @Volatile var showingFor: String = ""
        @Volatile var browserPkg: String = ""
    }
}

private val MOTIVATIONAL_LINES = listOf(
    "Your future self just high-fived you. Keep going.",
    "The reel will wait. Your rank won't climb itself.",
    "Scrolling gives zero XP. Quests give real XP.",
    "Plot twist: you're the main character, not the scroller.",
    "Tiny discipline today, big flex tomorrow.",
    "Close it. Level up instead.",
    "Legends skip the doomscroll.",
    "Boss level is waiting. Your thumb is not the boss.",
    "You didn't come this far to only come this far.",
    "Five focused minutes beat fifty scrolled ones.",
    "Be the person your streak thinks you are.",
    "Future you is cheering from the topper's list.",
)

@Composable
fun BlockScreen(
    pkg: String,
    nonce: Int = 0,
    funLimit: Boolean = false,
    onHome: () -> Unit,
    onQuests: () -> Unit,
    onUnlocked: () -> Unit,
    vm: BlockViewModel = hiltViewModel(),
) {
    if (pkg.startsWith("ytstudy:")) {
        StudyFirstPage(chapter = pkg.removePrefix("ytstudy:"), onHome = onHome, onQuests = onQuests)
        return
    }
    if (pkg.startsWith("focuslock:")) {
        FocusLockPage(pkg = pkg.removePrefix("focuslock:"), onHome = onHome, onQuests = onQuests)
        return
    }
    if (funLimit) {
        FunLimitPage(pkg = pkg, onHome = onHome, onQuests = onQuests)
        return
    }
    val ctx = LocalContext.current
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val rule = blocked.firstOrNull { it.packageName == pkg }
    val label = remember(pkg) {
        if (pkg.startsWith("site:")) pkg.removePrefix("site:") else if (pkg.startsWith("short:") || pkg.startsWith("yt:")) (pseudoLabel(pkg) ?: pkg) else runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
    val rate = remember { if (pkg == "yt:fun") com.krishna.questblock.data.YT_FUN_RATE_PER_5 else vm.rate() }
    val holdSteps = remember { vm.holdSeconds() * 10 }
    val quotesOn = remember { vm.showQuotes() }
    // the streak line only appears when there really is a streak
    val quote = remember(pkg, player.streak) {
        val pool = if (player.streak > 0) MOTIVATIONAL_LINES + "Stay locked in. You're on a ${player.streak}-day streak." else MOTIVATIONAL_LINES
        pool.random()
    }

    var blocks by remember { mutableIntStateOf(3) }
    val cost = com.krishna.questblock.data.withSurcharge(unlockCost(blocks, rate, rule), vm.surcharge)
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
    var paying by remember(pkg, nonce) { mutableStateOf(false) }
    // On-screen status: toasts get hidden on some phones (notifications off), so show everything here.
    var status by remember(pkg, nonce) { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    val notifOk = remember { com.krishna.questblock.util.Perms.notificationsOk(ctx) }

    // Already unlocked: exit immediately — single tap scenario covered here too
    val isSite = pkg.startsWith("site:") || pkg.startsWith("short:") || pkg.startsWith("yt:")
    var siteUntil by remember(pkg, nonce) { mutableLongStateOf(if (isSite) vm.siteUnlockedUntil(lockKeyOf(pkg)) else 0L) }
    val unlockedUntil = if (isSite) siteUntil else (rule?.unlockedUntil ?: 0L)
    LaunchedEffect(unlockedUntil) {
        // while we are paying we show the receipt first and exit ourselves
        com.krishna.questblock.util.DebugLog.d("page: pkg=$pkg rule=${rule != null} unlockLeft=${unlockedUntil - System.currentTimeMillis()}ms paying=$paying coins=${player.coins}")
        // No auto-open: the page switches to the "Open now" panel and waits for the user to tap Open.
        if (unlockedUntil > System.currentTimeMillis()) {
            com.krishna.questblock.util.DebugLog.d("page: app is unlocked, waiting for user to tap Open")
            paying = false
        }
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
                val p = (it + 1) / steps.toFloat()
                holdProgress = p
                if (it % 2 == 0) com.krishna.questblock.util.Sounds.holdTick(p)
            }
            if (isHolding) {
                com.krishna.questblock.util.DebugLog.d("hold complete: rule=${rule != null} paying=$paying coins=$coinsNow cost=$costNow blocks=$blocksNow")
                when {
                    rule == null && !isSite -> status = "\u26A0 $label isn't in your blocked list (Vault)"
                    paying -> status = "Already processing\u2026"
                    coinsNow < costNow -> { com.krishna.questblock.util.Sounds.denied(); status = "Not enough coins: have $coinsNow, need $costNow" }
                    else -> {
                        paying = true
                        status = "Paying $costNow \uD83E\uDE99\u2026"
                        runCatching {
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                                    ?.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                            }
                        }.getOrNull()?.let { vib ->
                            // never let a haptic failure (e.g. missing permission) crash the unlock
                            runCatching { vib.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)) }
                                .onFailure { com.krishna.questblock.util.DebugLog.d("vibrate failed (ignored): ${it.message}") }
                        }
                        vm.unlock(pkg, blocksNow) { res, err ->
                            com.krishna.questblock.util.DebugLog.d("unlock result: ok=${res.ok} charged=${res.charged} cost=${res.cost} ${res.before}->${res.after} err=$err")
                            if (res.ok) {
                                com.krishna.questblock.util.Sounds.unlock()
                                status = if (res.charged) {
                                    val bad = res.after != res.before - res.cost
                                    "\u2705 -${res.cost} \uD83E\uDE99  (${res.before} \u2192 ${res.after})  +${res.minutes} min" +
                                        if (bad) "\n\u26A0 balance mismatch: another writer changed coins" else ""
                                } else "\u2705 Already unlocked"
                                if (isSite) siteUntil = vm.siteUnlockedUntil(lockKeyOf(pkg))
                                KeepAlive.start(ctx)
                                // stay on the page: the "Open now" panel appears, the app opens only when tapped
                                paying = false
                            } else {
                                paying = false
                                status = if (err != null) "\u274C Unlock failed: $err" else "Not enough coins"
                            }
                        }
                    }
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
            val d = ctx.packageManager.getApplicationIcon(iconPackageFor(ctx, pkg))
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 128, 128)
            d.draw(Canvas(bmp))
            bmp.asImageBitmap()
        }.getOrNull()
    }
    val questIsPrimary = (rule != null || isSite) && !canAfford

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ColorBg, ColorSurface)))
            .let { if (AnyFx.on) it.fxBackground() else it }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon (v253: inside the themed lock emblem while the theme's extras are on)
            val iconBox: @Composable () -> Unit = {
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
            }
            Box(contentAlignment = Alignment.Center) {
                HoldUnlockHalo(
                    progress = animatedHold,
                    isHolding = isHolding,
                    accent = ColorPrimary,
                    modifier = Modifier.size(118.dp),
                )
                if (AnyFx.on) FxLockEmblem(iconBox) else iconBox()
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "$label is locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
                textAlign = TextAlign.Center,
            )
            FxLockWord()

            if (quotesOn) {
                Spacer(Modifier.height(6.dp))
                Text(quote, style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))

            if (rule != null || isSite) {
                val missing = cost - player.coins
                if (vm.surcharge > 0) Text("📋 Routine unfinished: +${vm.surcharge}% on unlock prices",
                    style = MaterialTheme.typography.labelSmall, color = ColorWarning)

                // Time selector: hidden while a timer is running (nothing to buy until it ends)
                if (unlockLeftMs == 0L) {
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

                    if ((rule?.ratePer5 ?: 0) > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("Custom rate: ${rule?.ratePer5} 🪙 per 5 min",
                            style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
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
                                "Time is running \u2014 tap Open to use it",
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
                        colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
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
                                            com.krishna.questblock.util.DebugLog.d("hold: finger down")
                                            if (!paying) status = "Holding\u2026 keep your finger down"
                                            isHolding = true
                                            val released = tryAwaitRelease()
                                            if (isHolding && !paying) {
                                                status = if (released) "Released too early \u2014 hold until the bar fills"
                                                         else "Press got cancelled \u2014 hold still, don't slide"
                                            }
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

                if (status.isNotEmpty()) {
                    Text(status, color = ColorText, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                if (!notifOk) {
                    Text("\uD83D\uDD14 Notifications are OFF for QuestBlock \u2014 turn them on in phone Settings > Apps > QuestBlock. " +
                        "That's why the unlock timer notification (and popups) don't show.",
                        color = ColorDanger, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Go home", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onQuests,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorText),
                border = BorderStroke(1.dp, ColorSurfaceHigh),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Do a quest instead") }
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
