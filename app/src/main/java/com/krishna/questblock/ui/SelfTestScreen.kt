@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.krishna.questblock.service.QuestWidget
import com.krishna.questblock.service.Reminders
import kotlinx.coroutines.Dispatchers
import com.krishna.questblock.service.WidgetFeed
import com.krishna.questblock.util.DebugLog
import com.krishna.questblock.util.MusicLog
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

enum class TestStatus { OK, WARN, FAIL }
class TestResult(val group: String, val name: String, val status: TestStatus, val detail: String)

/** Runs a fixed set of checks against the real app (settings, database, rules, permissions). It never changes your data for good. */
@HiltViewModel
class SelfTestViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: Prefs,
    private val repo: GameRepository,
) : ViewModel() {
    var results by mutableStateOf<List<TestResult>>(emptyList()); private set
    var running by mutableStateOf(false); private set

    private fun ok(m: String) = TestStatus.OK to m
    private fun warn(m: String) = TestStatus.WARN to m
    private fun fail(m: String) = TestStatus.FAIL to m

    fun run() {
        if (running) return
        running = true
        results = emptyList()
        viewModelScope.launch {
            val out = mutableListOf<TestResult>()
            suspend fun t(group: String, name: String, block: suspend () -> Pair<TestStatus, String>) {
                val r = runCatching { withTimeout(6_000L) { block() } }
                    .getOrElse { TestStatus.FAIL to "crashed: ${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}" }
                out += TestResult(group, name, r.first, r.second)
                results = out.toList()
            }
            val now = System.currentTimeMillis()

            // ── Permissions and services
            t("Permissions", "Accessibility service") {
                if (Perms.accessibilityEnabled(ctx)) ok("on") else fail("off: blocking and the Shorts counter need it")
            }
            t("Permissions", "Blocker is running") {
                val ago = now - prefs.lastActiveAt
                when {
                    prefs.lastActiveAt == 0L -> fail("never active")
                    ago < 10 * 60_000L -> ok("last active ${ago / 1000}s ago")
                    else -> warn("last active ${ago / 60_000L} min ago (open an app and run again)")
                }
            }
            t("Permissions", "Usage access") {
                if (UsageTracker.hasAccess(ctx)) ok("on") else fail("off: usage page, study coins and the fun limit need it")
            }
            t("Permissions", "Notifications") { if (Perms.notificationsOk(ctx)) ok("on") else fail("off: timers and warnings cannot show") }
            t("Permissions", "Notification access (music app)") {
                if (Perms.notificationAccess(ctx)) ok("on") else warn("off (optional): music app is a guess")
            }
            t("Permissions", "Battery optimisation") { if (Perms.batteryExempt(ctx)) ok("exempt") else warn("not exempt: Android may stop the blocker") }

            // ── Data
            t("Data", "Database") {
                val p = repo.player.first()
                val tasks = repo.tasks.first().size
                val log = repo.coinLog.first().size
                ok("coins ${p.coins}, streak ${p.streak}, $tasks quests, $log coin events")
            }
            t("Data", "Settings storage") {
                val before = prefs.streakFreezes
                prefs.streakFreezes = before + 1
                val moved = prefs.streakFreezes == before + 1
                prefs.streakFreezes = before
                if (moved && prefs.streakFreezes == before) ok("write and read work") else fail("a setting did not save")
            }
            t("Data", "Debug log") {
                DebugLog.d("selftest: ping")
                if (DebugLog.read().contains("selftest: ping")) ok("writes") else fail("could not write")
            }
            t("Data", "Ideas page storage") {
                IdeaStore.add(ctx, "__selftest__")
                val first = IdeaStore.all(ctx).firstOrNull()?.text
                if (first == "__selftest__") IdeaStore.delete(ctx, 0)
                if (first == "__selftest__") ok("save and delete work") else fail("saved idea not found")
            }
            t("Data", "Shorts and Reels counter storage") {
                ShortsStats.add(ctx, "selftest", 1000L, 1)
                val d = ShortsStats.today(ctx, "selftest")
                ShortsStats.add(ctx, "selftest", -1000L, -1)
                if (d.n >= 1 && d.ms >= 1000L) {
                    val yt = ShortsStats.today(ctx, ShortsStats.YT)
                    val ig = ShortsStats.today(ctx, ShortsStats.IG)
                    ok("works. today: Shorts ${yt.n}, Reels ${ig.n}")
                } else fail("count was not stored")
            }
            t("Data", "Music log") {
                val n = MusicLog.periods(ctx, now - 24 * 3_600_000L, now).size
                ok("$n plays in the last 24 h")
            }

            // ── Rules and prices
            t("Rules", "Unlock price") {
                val a = unlockCost(1, 10, null)
                val b = unlockCost(3, 10, null)
                if (a > 0 && b >= a) ok("1 block $a, 3 blocks $b") else fail("1 block $a, 3 blocks $b")
            }
            t("Rules", "Routine first surcharge") {
                if (withSurcharge(100, 50) == 150 && withSurcharge(100, 0) == 100) ok("+50% works") else fail("wrong surcharge maths")
            }
            t("Rules", "Routine step coins") {
                val bad = (1..3).filter { baseReward(it) <= 0 }
                if (bad.isEmpty()) ok("easy ${baseReward(1)}, medium ${baseReward(2)}, hard ${baseReward(3)} coins per routine, split over its steps")
                else fail("difficulty ${bad.joinToString()} pays 0")
            }
            t("Rules", "Streak Freeze in the shop") {
                if (ShopCatalog.items.any { it.id == "streak_freeze" }) ok("listed") else fail("missing from the shop")
            }
            t("Rules", "Every theme has an SVG logo") {
                val bad = ShopCatalog.items.filter { it.category == "cosmetic_theme" && !SvgIcons.EMOJI.containsKey(it.emoji.replace("\uFE0F", "")) }
                if (bad.isEmpty()) ok("all ${ShopCatalog.items.count { it.category == "cosmetic_theme" }} themes") else fail("no SVG for ${bad.joinToString { it.name }}: see #svg-logo in ShopCatalog.kt")
            }
            t("Rules", "Blocked websites") {
                val sites = prefs.blockedSites()
                if (sites.isEmpty()) warn("no sites blocked, nothing to test")
                else {
                    val bad = sites.filter { prefs.matchedSite(it) != it || prefs.matchedSite("www.$it") != it }
                    if (prefs.matchedSite("example.invalid") != null) fail("a site that is not blocked matched")
                    else if (bad.isNotEmpty()) fail("not matched: ${bad.joinToString()}")
                    else ok("${sites.size} sites match, others do not")
                }
            }
            t("Rules", "Fun limit") {
                val st = FunLimit.state(ctx, prefs)
                if (!st.enabled) warn("no fun limit set")
                else ok("used ${st.usedMs / 60_000L} of ${st.limitMs / 60_000L} min" + if (st.reached) " (reached)" else "")
            }

            // ── App
            t("App", "Version log") {
                val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
                val top = VERSION_LOG.firstOrNull()?.version
                if (v == top) ok("v$v listed") else fail("installed $v but the log's newest is $top")
            }
            t("App", "Widget") {
                val n = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, QuestWidget::class.java)).size
                val empty = WidgetFeed.lineFor(emptyList(), emptyList())
                if (empty != "All clear today") fail("empty text is \"$empty\"")
                else if (n == 0) warn("logic works, widget not on your home screen (optional)") else ok("$n on your home screen")
            }
            t("App", "Music source lookup") {
                if (!Perms.notificationAccess(ctx)) warn("skipped (needs notification access)")
                else {
                    val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
                    val cn = ComponentName(ctx, com.krishna.questblock.service.MediaListenerService::class.java)
                    val s = msm.getActiveSessions(cn)
                    ok("${s.size} media apps open right now")
                }
            }
            running = false
        }
    }

    // ── Manual checks: the app does the work, you confirm what you see
    var manual by mutableStateOf<List<TestResult>>(emptyList()); private set
    private fun setManual(name: String, s: TestStatus, d: String) {
        manual = manual.filter { it.name != name } + TestResult("Manual", name, s, d)
    }

    var notifSent by mutableStateOf(false); private set
    @android.annotation.SuppressLint("MissingPermission")
    fun sendTestNotification() {
        if (!Perms.notificationsOk(ctx)) { setManual("Test notification", TestStatus.FAIL, "notifications are off for QuestBlock"); return }
        runCatching {
            Reminders.ensureChannel(ctx)
            val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("QuestBlock self test")
                .setContentText("If you can read this, notifications work.")
                .setContentIntent(openRoutePending(ctx, "selftest"))
                .setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(9901, n)
            notifSent = true
        }.onFailure { setManual("Test notification", TestStatus.FAIL, "could not send: ${it.javaClass.simpleName}") }
    }
    fun confirmNotification(seen: Boolean) {
        notifSent = false
        runCatching { NotificationManagerCompat.from(ctx).cancel(9901) }
        setManual("Test notification", if (seen) TestStatus.OK else TestStatus.FAIL,
            if (seen) "you saw it" else "sent, but you did not see it: check Do Not Disturb and the notification settings")
    }

    var musicGuess by mutableStateOf<String?>(null); private set
    var musicHow by mutableStateOf(""); private set
    fun checkMusic() {
        viewModelScope.launch(Dispatchers.Default) {
            val am = ctx.getSystemService(android.media.AudioManager::class.java)
            if (am == null || !am.isMusicActive) {
                musicGuess = null
                setManual("Music app guess", TestStatus.WARN, "no music was playing: start music in an app first")
                return@launch
            }
            val (pkg, how) = MusicLog.guessNow(ctx)
            musicGuess = pkg?.let {
                runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(it, 0)).toString() }.getOrDefault(it)
            } ?: "an unknown app"
            musicHow = how
        }
    }
    fun confirmMusic(correct: Boolean) {
        setManual("Music app guess", if (correct) TestStatus.OK else TestStatus.FAIL,
            "guessed ${musicGuess ?: "?"} from $musicHow: you said ${if (correct) "correct" else "wrong"}")
        musicGuess = null
    }

    var shortsStart by mutableStateOf<Int?>(null); private set
    private fun shortsTotal() = ShortsStats.today(ctx, ShortsStats.YT).n + ShortsStats.today(ctx, ShortsStats.IG).n
    fun startShortsTest() { shortsStart = shortsTotal() }
    fun finishShortsTest() {
        val s = shortsStart ?: return
        val d = shortsTotal() - s
        shortsStart = null
        setManual("Shorts swipe count", when { d == 3 -> TestStatus.OK; d > 0 -> TestStatus.WARN; else -> TestStatus.FAIL },
            (when { d == 3 -> "swiped 3 videos, counted 3"; d > 0 -> "swiped 3 videos, counted $d"; else -> "swiped 3 videos, counted 0" }) +
                if (d == 3) "" else "\n" + DebugLog.read().lines().filter { it.contains("clips:") }.takeLast(10).joinToString("\n"))
    }

    fun report(): String {
        val all = results + manual
        val ok = all.count { it.status == TestStatus.OK }
        return buildString {
            append("QuestBlock self test: $ok/${all.size} passed\n")
            all.forEach { append("${icon(it.status)} ${it.group} / ${it.name}: ${it.detail}\n") }
        }
    }
}

private fun icon(s: TestStatus) = when (s) { TestStatus.OK -> "✅"; TestStatus.WARN -> "⚠️"; TestStatus.FAIL -> "❌" }

@Composable
fun SelfTestScreen(onBack: () -> Unit, vm: SelfTestViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.run() }   // auto test when the page opens
    val res = vm.results
    val all = res + vm.manual
    val ok = all.count { it.status == TestStatus.OK }
    val warns = all.count { it.status == TestStatus.WARN }
    val fails = all.count { it.status == TestStatus.FAIL }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            TitleIcon("Self test", 24.dp, endPad = 8.dp)
            Column {
                Text("Self test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                Text("Checks permissions, storage, rules and features", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (vm.running) "Testing… ${res.size} done" else "✅ $ok passed · ⚠️ $warns warnings · ❌ $fails failed",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.run() }, enabled = !vm.running, shape = RoundedCornerShape(10.dp)) { Text("Run again") }
                    OutlinedButton(onClick = {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Self test", vm.report()))
                        android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                    }, enabled = res.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text("Copy report") }
                }
            }
        }
        res.groupBy { it.group }.forEach { (group, list) ->
            Text(group, style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
            list.forEach { r ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(icon(r.status))
                        Column {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ColorText)
                            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                    }
                }
            }
        }
        Text("Manual checks", style = MaterialTheme.typography.labelLarge, color = ColorMuted, fontWeight = FontWeight.SemiBold)
        ManualCard("🔔 Test notification", "The app sends one. You tell it if you saw it.", vm.manual.firstOrNull { it.name == "Test notification" }) {
            if (vm.notifSent) {
                Text("A notification was just sent. Did you see it?", style = MaterialTheme.typography.bodySmall, color = ColorText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.confirmNotification(true) }, shape = RoundedCornerShape(10.dp)) { Text("Yes ✅") }
                    OutlinedButton(onClick = { vm.confirmNotification(false) }, shape = RoundedCornerShape(10.dp)) { Text("No ❌") }
                }
            } else OutlinedButton(onClick = { vm.sendTestNotification() }, shape = RoundedCornerShape(10.dp)) { Text("Send test notification") }
        }
        ManualCard("🎧 Music app guess", "Play music in any app, then tap Check. It tells you which app it thinks is playing.", vm.manual.firstOrNull { it.name == "Music app guess" }) {
            val g = vm.musicGuess
            if (g != null) {
                Text("I think the music is playing in: $g (from ${vm.musicHow}). Is that right?", style = MaterialTheme.typography.bodySmall, color = ColorText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.confirmMusic(true) }, shape = RoundedCornerShape(10.dp)) { Text("Correct ✅") }
                    OutlinedButton(onClick = { vm.confirmMusic(false) }, shape = RoundedCornerShape(10.dp)) { Text("Wrong ❌") }
                }
            } else OutlinedButton(onClick = { vm.checkMusic() }, shape = RoundedCornerShape(10.dp)) { Text("Check now") }
        }
        ManualCard("📱 Shorts swipe count", "Tap Start, open YouTube Shorts or Instagram Reels, swipe exactly 3 videos, come back and tap Finish.", vm.manual.firstOrNull { it.name == "Shorts swipe count" }) {
            if (vm.shortsStart != null) {
                Text("Counting… swipe 3 videos in Shorts or Reels, then come back.", style = MaterialTheme.typography.bodySmall, color = ColorText)
                Button(onClick = { vm.finishShortsTest() }, shape = RoundedCornerShape(10.dp)) { Text("Finish") }
            } else OutlinedButton(onClick = { vm.startShortsTest() }, shape = RoundedCornerShape(10.dp)) { Text("Start") }
        }
        Text("Tests use your real settings. The few they touch are put back.", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ManualCard(title: String, hint: String, result: TestResult?, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ColorText)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            content()
            if (result != null) Text("${icon(result.status)} ${result.detail}", style = MaterialTheme.typography.bodySmall, color = ColorText)
        }
    }
}
