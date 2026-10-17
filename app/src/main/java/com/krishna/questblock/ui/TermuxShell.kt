package com.krishna.questblock.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import com.krishna.questblock.engine.AdvisorProvider
import com.krishna.questblock.engine.HubStore
import com.krishna.questblock.engine.InsightsBuilder
import com.krishna.questblock.service.KeepAlive
import com.krishna.questblock.service.QuestWidget
import com.krishna.questblock.util.Backup
import com.krishna.questblock.util.DebugLog
import com.krishna.questblock.util.Health
import com.krishna.questblock.util.Perms
import com.krishna.questblock.util.Sounds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Where the terminal is running as a lock page (BlockActivity) instead of the main app. */
class TermuxBlockCtx(
    val pkg: String,
    val funLimit: Boolean,
    val onHome: () -> Unit,
    val onMain: () -> Unit,
    val onOpen: () -> Unit,
)

private val TOP_COMMANDS = listOf(
    "help", "man", "clear", "history", "status", "whoami", "alias", "unalias", "theme", "home", "setup",
    "quests", "quest", "undo",
    "focus", "play", "stop",
    "vault", "unlock", "sites", "limits", "categories",
    "coins", "shop", "inv",
    "stats", "usage", "weekly", "attendance", "marks", "chapters", "badges", "goals", "hub",
    "set", "strict", "dnd", "perms", "backup", "restore", "reliability", "selftest", "debuglog", "versions", "about", "ideas",
)

private val SUB_COMMANDS: Map<String, List<String>> = mapOf(
    "help" to listOf("core", "quests", "focus", "blocking", "economy", "progress", "settings"),
    "man" to TOP_COMMANDS,
    "theme" to listOf("set"),
    "quests" to listOf("--today", "--done", "--overdue", "--focus"),
    "quest" to listOf("add", "show", "edit", "done", "rm", "focus", "link", "step", "skip"),
    "focus" to listOf("start", "status", "stop", "apps", "last", "log"),
    "play" to listOf("rain", "brown", "pink", "white"),
    "vault" to listOf("ls", "add", "rm", "study", "unstudy", "info", "opt", "clips"),
    "sites" to listOf("ls", "add", "rm"),
    "categories" to listOf("ls", "set", "safe"),
    "coins" to listOf("log"),
    "shop" to listOf("ls", "buy", "rewards", "reward"),
    "inv" to listOf("ls", "use", "equip", "unequip"),
    "usage" to listOf("--week"),
    "attendance" to listOf("mark", "s", "c1", "c2"),
    "marks" to listOf("set", "rm"),
    "chapters" to listOf("ls", "study", "revise", "taught", "copy"),
    "goals" to listOf("claim"),
    "set" to listOf("ls"),
    "strict" to listOf("on", "off"),
    "perms" to listOf("grant"),
    "debuglog" to listOf("clear"),
    "ideas" to listOf("add", "done", "rm", "clear"),
    "about" to listOf("open"),
    "theme set" to listOf("default", "termux"),
)

private val GROUPS: Map<String, List<String>> = linkedMapOf(
    "core" to listOf("help", "man", "clear", "history", "status", "whoami", "alias", "theme", "home", "setup"),
    "quests" to listOf("quests", "quest", "undo"),
    "focus" to listOf("focus", "play", "stop"),
    "blocking" to listOf("vault", "unlock", "sites", "limits", "categories"),
    "economy" to listOf("coins", "shop", "inv"),
    "progress" to listOf("stats", "usage", "weekly", "attendance", "marks", "chapters", "badges", "goals", "hub"),
    "settings" to listOf("set", "strict", "dnd", "perms", "backup", "restore", "reliability", "selftest", "debuglog", "versions", "about", "ideas"),
)

private val MAN: Map<String, String> = mapOf(
    "help" to "help [group]        list commands (groups: core quests focus blocking economy progress settings)",
    "man" to "man <cmd>           how one command works",
    "clear" to "clear               empty the screen",
    "history" to "history             commands you typed (up arrow walks them)",
    "status" to "status              coins, level, streak, focus, blocking, fun time, open quests",
    "whoami" to "whoami              hero name, goal, birthday, level",
    "alias" to "alias [name=cmd]    list or make a shortcut, e.g. alias qt=quests --today",
    "unalias" to "unalias <name>     remove a shortcut",
    "theme" to "theme [set default|termux]   switch the look (default is refused during a strict session)",
    "home" to "home                go to the phone home screen",
    "setup" to "setup               first-run steps: set name/goal/dob, perms, then setup done",
    "quests" to "quests [--today|--done|--overdue|--focus]",
    "quest" to "quest add \"title\" -d easy|med|hard -c Study|Work|Health|Personal|Custom -t 25m --due \"fri 18:00\" --repeat daily|weekly|mon,wed --challenge 7 --steps \"a;b;c\"\n" +
        "quest show|done|rm <id>     quest edit <id> [-d] [-c] [-t] [--due] [--title]\n" +
        "quest focus <id>            start the quest's timer\n" +
        "quest link <id> <app>       app unlocks while the quest runs\n" +
        "quest step <id> <n>         tick a routine step      quest skip <id> <n>   skip it (5 coins)",
    "undo" to "undo                take back the last finished quest",
    "focus" to "focus start 25m --apps whatsapp,telegram [--strict] [--quest <id>]\n" +
        "focus status | stop | apps | last [25m] | log",
    "play" to "play rain|brown|pink|white   background sound (only while a session runs)",
    "stop" to "stop                stop the background sound",
    "vault" to "vault ls | add <app> | rm <app> | info <app> | study <app> | unstudy <app>\n" +
        "vault opt <app> [--limit min] [--price pct] [--rate coins] [--from HH:MM --to HH:MM|off]\n" +
        "vault clips yt|ig on|off",
    "unlock" to "unlock <app> [minutes]   pay coins for unlock time (you type the app name to confirm)",
    "sites" to "sites ls | add <domain> | rm <domain>   (removing waits until midnight)",
    "limits" to "limits              fun limit, quiet hours, study hours, exam mode (change them with: set)",
    "categories" to "categories ls [text] | set <app> study|coding|neutral|fun|auto | safe <app> on|off",
    "coins" to "coins [log [n]]     balance, today's total, coin history",
    "shop" to "shop ls [powerups|frames|themes|badges] | buy <item> | rewards | reward add \"name\" <price> | reward rm <n>",
    "inv" to "inv [ls] | equip <n> | unequip <n> | use <n>",
    "stats" to "stats               14 days of study/coding/neutral/fun time, quests and focus",
    "usage" to "usage [--week]      time per app and per category",
    "weekly" to "weekly              the weekly report",
    "attendance" to "attendance | attendance mark s|c1|c2 p|a|l|clear   (today only)",
    "marks" to "marks | marks set <mt1|qtr|mt2|half|mt3> <P|C|M|H|E> <marks> | marks rm <test> <subject>",
    "chapters" to "chapters | chapters ls <P|C|M|H|E> | chapters study|revise|taught|copy <subject> <n>",
    "badges" to "badges              all badges, unlocked or locked",
    "goals" to "goals | goals claim <n>   challenges and season goals",
    "hub" to "hub                 rank, attributes, discipline score, season",
    "set" to "set ls | set <key> <value>   name goal dob rate fun_limit quiet quiet_from ... (see set ls)",
    "strict" to "strict on|off       strict mode (off waits until midnight)",
    "dnd" to "dnd                 Do Not Disturb access (Focus turns it on when allowed)",
    "perms" to "perms | perms grant <acc|usage|overlay|battery|notif|access|dnd|admin>   (opens the system page)",
    "backup" to "backup              save an encrypted copy to Downloads/QuestBlock",
    "restore" to "restore             pick a .qbak file (system file picker), keeps your current coins",
    "reliability" to "reliability         what must stay on for blocking to work",
    "selftest" to "selftest            runs the app's checks",
    "debuglog" to "debuglog [clear]",
    "versions" to "versions [number]   change log, or one version in full",
    "about" to "about [open <n>]",
    "ideas" to "ideas | add <text> | done <n> | rm <n> | clear",
)

/** Commands that still work while a strict Focus session runs. */
private val STRICT_OK = setOf("help", "man", "clear", "history", "status", "whoami", "focus", "play", "stop", "home", "theme", "versions", "about", "alias", "unalias")

/** Commands on a lock page. */
private val BLOCK_OK = setOf("help", "man", "clear", "history", "status", "quests", "coins", "unlock", "open", "home", "main", "focus")

private class SetKey(val key: String, val desc: String, val get: () -> String, val set: (String) -> String?)

@HiltViewModel
class TermuxViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
    private val db: AppDatabase,
    private val hub: HubStore,
    private val builder: InsightsBuilder,
) : ViewModel(), TermuxHost {

    private val sessionList = mutableStateListOf<TermuxSession>()
    override val sessions: List<TermuxSession> get() = sessionList
    private val hist = ArrayList<String>()
    override val history: List<String> get() = hist
    private var nextId = 1
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
    private val player = repo.player.stateIn(viewModelScope, SharingStarted.Eagerly, Player())
    private var lastUndo: GameRepository.CompletionUndo? = null
    private var installedCache: List<InstalledApp>? = null

    /** Set when this terminal is a lock page. */
    var block: TermuxBlockCtx? = null
        private set

    /** The shell watches this: when it turns true it opens the system file picker for a restore. */
    var restorePick by mutableStateOf(false)

    init {
        sp.getString("termux_hist", "")?.split("\n")?.filter { it.isNotBlank() }?.takeLast(100)?.let { hist.addAll(it) }
        val main = create("main")
        banner(main)
        viewModelScope.launch {
            while (true) {
                runCatching { tickFocus() }
                delay(1000L)
            }
        }
    }

    // ── sessions ─────────────────────────────────────────────────────────
    private fun create(name: String): TermuxSession {
        val s = TermuxSession(nextId++, name)
        sessionList.add(s)
        return s
    }

    override fun newSession(): TermuxSession {
        val s = create("sh")
        s.dim("new session")
        return s
    }

    override fun closeSession(s: TermuxSession) {
        if (sessionList.size > 1 && s !== sessionList.first()) {
            s.job?.cancel()
            sessionList.remove(s)
        }
    }

    private fun versionName(): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun banner(s: TermuxSession) {
        s.out("{b}QuestBlock{/} {d}termux theme \u00b7 v${versionName()}{/}")
        s.dim("every page of the app is a command. type help, or tap a chip.")
        if (!prefs.onboardingDone) s.warn("first run: type {c}setup{/}")
        s.out("")
    }

    /** Opens the lock page in a fresh terminal. */
    fun enterBlock(b: TermuxBlockCtx, kind: String) {
        block = b
        val s = sessionList.first()
        s.lines.clear()
        s.prompt = "~/block $"
        s.job?.cancel()
        s.pending = null
        s.live = emptyList()
        while (sessionList.size > 1) sessionList.removeAt(sessionList.size - 1)
        viewModelScope.launch { guarded(s) { showBlockPage(s, b, kind) } }
    }

    /** Puts a command in the prompt of the main session when the app opened from a notification: the screen does that, this runs it for the strict check. */
    fun routeCommand(route: String): String = termuxCommandForRoute(route)

    // ── history, aliases ─────────────────────────────────────────────────
    private fun addHistory(line: String) {
        if (hist.lastOrNull() != line) hist.add(line)
        while (hist.size > 100) hist.removeAt(0)
        sp.edit().putString("termux_hist", hist.joinToString("\n")).apply()
    }

    private fun aliases(): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        (sp.getString("termux_alias", "") ?: "").split("\n").forEach { l ->
            val i = l.indexOf('=')
            if (i > 0) m[l.substring(0, i)] = l.substring(i + 1)
        }
        return m
    }

    private fun saveAliases(m: Map<String, String>) {
        sp.edit().putString("termux_alias", m.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    // ── suggestions ──────────────────────────────────────────────────────
    override fun suggestions(input: String): List<String> {
        val t = input.trimStart()
        val allowed: Collection<String> = if (block != null) BLOCK_OK else TOP_COMMANDS
        if (t.isEmpty()) {
            return if (block != null) listOf("unlock 15", "quests", "coins", "home", "help")
            else listOf("status", "quests", "focus status", "coins", "stats", "help")
        }
        val parts = t.split(" ")
        if (parts.size == 1) return allowed.filter { it.startsWith(parts[0]) && it != parts[0] }.take(8)
        val key2 = if (parts.size >= 3) parts[0] + " " + parts[1] else parts[0]
        val sub = SUB_COMMANDS[key2] ?: return emptyList()
        val cur = parts.last()
        return sub.filter { it.startsWith(cur) && it != cur }.take(8)
    }

    // ── running ──────────────────────────────────────────────────────────
    override fun run(session: TermuxSession, line: String) {
        val pend = session.pending
        if (pend != null) {
            session.pending = null
            pend(line)
            return
        }
        val raw = line.trim()
        if (raw.isEmpty()) return
        addHistory(raw)
        var toks = termuxTokenize(raw)
        val al = aliases()
        if (toks.isNotEmpty() && al.containsKey(toks[0])) toks = termuxTokenize(al.getValue(toks[0])) + toks.drop(1)
        session.job = viewModelScope.launch { guarded(session) { dispatch(session, toks) } }
    }

    override fun cancel(session: TermuxSession) {
        session.job?.cancel()
        session.pending = null
    }

    private suspend fun guarded(s: TermuxSession, body: suspend () -> Unit) {
        try {
            body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            s.err("${e.javaClass.simpleName}: ${e.message.orEmpty().take(140)}".tm())
        }
    }

    private fun confirm(s: TermuxSession, question: String, onYes: suspend () -> Unit) {
        s.out("{y}?{/} $question [y/N]")
        s.pending = { ans ->
            s.pending = null
            if (ans.trim().lowercase() == "y" || ans.trim().lowercase() == "yes") {
                s.job = viewModelScope.launch { guarded(s) { onYes() } }
            } else {
                s.dim("cancelled")
            }
        }
    }

    /** Waits for the exact word [word] (used for unlocks, in place of the hold). */
    private fun confirmWord(s: TermuxSession, question: String, word: String, onYes: suspend () -> Unit) {
        s.out("{y}?{/} $question")
        s.out("{d}type  ${word.tm()}  to confirm, anything else cancels{/}")
        s.pending = { ans ->
            s.pending = null
            if (ans.trim().lowercase() == word.lowercase()) {
                s.job = viewModelScope.launch { guarded(s) { onYes() } }
            } else {
                s.dim("cancelled")
            }
        }
    }

    private suspend fun dispatch(s: TermuxSession, toks: List<String>) {
        if (toks.isEmpty()) return
        val cmd = toks[0].lowercase()
        val a = termuxParse(toks)
        val b = block
        if (b != null && cmd !in BLOCK_OK) {
            s.err("permission denied: ${cmd.tm()} (locked). allowed here: ${BLOCK_OK.sorted().joinToString(" ")}")
            return
        }
        if (b == null && TermuxMode.strictNow(ctx) && cmd !in STRICT_OK) {
            s.err("strict focus is on: only ${STRICT_OK.sorted().joinToString(" ")} work until it ends")
            return
        }
        when (cmd) {
            "help", "?" -> cmdHelp(s, a)
            "man" -> cmdMan(s, a)
            "clear" -> s.lines.clear()
            "history" -> hist.takeLast(30).forEachIndexed { i, l -> s.out("{d}${i + 1}{/}  ${l.tm()}") }
            "status" -> cmdStatus(s)
            "whoami" -> cmdWhoami(s)
            "alias" -> cmdAlias(s, toks)
            "unalias" -> cmdUnalias(s, a)
            "theme" -> cmdTheme(s, a)
            "home" -> cmdHome(s)
            "main" -> { block?.onMain?.invoke() }
            "open" -> cmdOpen(s)
            "setup" -> cmdSetup(s, a)
            "quests" -> cmdQuests(s, a)
            "quest" -> cmdQuest(s, a)
            "undo" -> cmdUndo(s)
            "focus" -> cmdFocus(s, a)
            "play" -> cmdPlay(s, a)
            "stop" -> { Sounds.stopAmbient(); s.ok("background sound stopped") }
            "vault" -> cmdVault(s, a)
            "unlock" -> cmdUnlock(s, a)
            "sites" -> cmdSites(s, a)
            "limits" -> cmdLimits(s)
            "categories" -> cmdCategories(s, a)
            "coins" -> cmdCoins(s, a)
            "shop" -> cmdShop(s, a)
            "inv" -> cmdInv(s, a)
            "stats" -> cmdStats(s)
            "usage" -> cmdUsage(s, a)
            "weekly" -> cmdWeekly(s)
            "attendance" -> cmdAttendance(s, a)
            "marks" -> cmdMarks(s, a)
            "chapters" -> cmdChapters(s, a)
            "badges" -> cmdBadges(s)
            "goals" -> cmdGoals(s, a)
            "hub" -> cmdHub(s)
            "set" -> cmdSet(s, a)
            "strict" -> cmdStrict(s, a)
            "dnd" -> cmdDnd(s)
            "perms" -> cmdPerms(s, a)
            "backup" -> cmdBackup(s)
            "restore" -> cmdRestore(s)
            "reliability" -> cmdReliability(s)
            "selftest" -> cmdSelfTest(s)
            "debuglog" -> cmdDebugLog(s, a)
            "versions" -> cmdVersions(s, a)
            "about" -> cmdAbout(s, a)
            "ideas" -> cmdIdeas(s, a)
            "exit", "logout" -> s.err("logout: not allowed. to leave the terminal: theme set default")
            else -> {
                s.err("${cmd.tm()}: command not found")
                val near = TOP_COMMANDS.filter { it.startsWith(cmd.take(2)) }.take(5)
                if (near.isNotEmpty()) s.dim("did you mean: ${near.joinToString(" ")}")
            }
        }
    }

    // ── small helpers ────────────────────────────────────────────────────
    private fun fmtTime(ms: Long): String = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(ms))
    private fun fmtMin(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
    private fun fmtMs(ms: Long): String = fmtMin(ms / 60_000L)

    private fun labelOfPkg(pkg: String): String {
        if (pkg.startsWith("site:")) return pkg.removePrefix("site:")
        pseudoLabel(pkg)?.let { return it }
        return runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private suspend fun installed(): List<InstalledApp> {
        installedCache?.let { return it }
        val l = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
        installedCache = l
        return l
    }

    /** Finds one app by package, name or part of the name. Prints why when it can't. */
    private suspend fun pickApp(s: TermuxSession, query: String, includeBlocked: Boolean = true): InstalledApp? {
        val q = query.trim()
        if (q.isEmpty()) { s.err("which app?"); return null }
        val all = ArrayList<InstalledApp>(installed())
        if (includeBlocked) {
            repo.blockedApps.first().forEach { r -> if (all.none { it.pkg == r.packageName }) all.add(InstalledApp(r.packageName, r.label)) }
        }
        all.firstOrNull { it.pkg == q }?.let { return it }
        val ql = q.lowercase()
        val exact = all.filter { it.label.lowercase() == ql }
        val found = if (exact.isNotEmpty()) exact else all.filter { it.label.lowercase().contains(ql) || it.pkg.lowercase().contains(ql) }
        if (found.size == 1) return found[0]
        if (found.isEmpty()) { s.err("no app matches '${q.tm()}'"); return null }
        s.warn("${found.size} apps match, be more specific:")
        found.take(8).forEach { s.out("   ${it.label.tm()}  {d}${it.pkg}{/}") }
        return null
    }

    private fun parseMinutes(text: String): Int? {
        val t = text.trim().lowercase()
        t.toIntOrNull()?.let { return it }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m?)?$").matchEntire(t) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val mi = m.groupValues[2].toIntOrNull() ?: 0
        val total = h * 60 + mi
        return if (total > 0) total else null
    }

    private val dowNames = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    private fun parseDue(text: String): Long? {
        val parts = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val cal = Calendar.getInstance()
        var minute = 23 * 60 + 59
        for (p in parts) {
            val t = Regex("^(\\d{1,2}):(\\d{2})$").find(p)
            if (t != null) {
                minute = t.groupValues[1].toInt() * 60 + t.groupValues[2].toInt()
            } else if (p == "today") {
                // nothing to add
            } else if (p == "tomorrow") {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } else if (p.length >= 3 && dowNames.contains(p.take(3))) {
                val want = dowNames.indexOf(p.take(3))
                val add = (want - dowIndex(cal.timeInMillis) + 7) % 7
                cal.add(Calendar.DAY_OF_YEAR, add)
            } else {
                return null
            }
        }
        cal.set(Calendar.HOUR_OF_DAY, minute / 60)
        cal.set(Calendar.MINUTE, minute % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parseDays(text: String): Int? {
        var mask = 0
        for (p in text.lowercase().split(",", " ").filter { it.isNotBlank() }) {
            val i = dowNames.indexOf(p.take(3))
            if (i < 0) return null
            mask = mask or (1 shl i)
        }
        return if (mask == 0) null else mask
    }

    private fun parseBool(text: String): Boolean? = when (text.trim().lowercase()) {
        "on", "true", "1", "yes", "y" -> true
        "off", "false", "0", "no", "n" -> false
        else -> null
    }

    private fun parseHm(text: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    }

    private suspend fun tickFocus() {
        val p = player.value
        val now = System.currentTimeMillis()
        val running = p.focusModeUntil > now
        var fs = sessionList.firstOrNull { it.name == "focus" }
        if (running && block == null) {
            if (fs == null) {
                fs = create("focus")
                fs.prompt = "~/focus $"
                fs.step("a focus session is running")
            }
            val left = p.focusModeUntil - now
            val total = p.focusTotalMs.coerceAtLeast(1L)
            fs.live = listOf(
                "{b}FOCUS{/} {c}${fmtLeft(left)}{/} left  ${termuxBar(1f - left.toFloat() / total.toFloat(), 20)}",
                "{d}strict=${prefs.focusStrict}  apps=${prefs.focusAllowed.split(",").count { it.isNotBlank() }}  sound=${Sounds.ambient}{/}",
            )
        } else {
            if (p.focusModeUntil > 0L && !running) repo.settleFocus()
            if (fs != null && fs.live.isNotEmpty()) {
                fs.live = emptyList()
                fs.ok("focus session finished")
            }
        }
    }

    // ═══════════════ core ═══════════════
    private fun cmdHelp(s: TermuxSession, a: TermuxArgs) {
        if (block != null) {
            s.head("lock page")
            s.out("{c}unlock [minutes]{/}  pay coins for unlock time (default 15)")
            s.out("{c}open{/}              open the app once it is unlocked")
            s.out("{c}quests{/}            what you could do instead")
            s.out("{c}main{/}              open QuestBlock (the terminal)")
            s.out("{c}home{/}              go home")
            s.out("{c}coins{/} {c}status{/}      balance and state")
            return
        }
        val g = a.pos.getOrNull(1)?.lowercase()
        if (g == null) {
            s.head("QuestBlock terminal")
            s.dim("every page of the app is a command. help <group> for details, man <cmd> for one command.")
            GROUPS.forEach { (name, cmds) -> s.out("{g}${name.padEnd(9)}{/} ${cmds.joinToString(" ")}") }
            s.dim("tap the chips to autocomplete. \u21C6 completes, \u2191 \u2193 walk history, CTRL cancels.")
            return
        }
        val cmds = GROUPS[g]
        if (cmds == null) { s.err("no group '${g.tm()}'. groups: ${GROUPS.keys.joinToString(" ")}"); return }
        s.head(g)
        cmds.forEach { c -> MAN[c]?.split("\n")?.forEach { l -> s.out("{c}\u203a{/} ${l.tm()}") } }
    }

    private fun cmdMan(s: TermuxSession, a: TermuxArgs) {
        val c = a.pos.getOrNull(1)?.lowercase()
        if (c == null) { s.err("usage: man <command>"); return }
        val m = MAN[c]
        if (m == null) { s.err("no manual entry for ${c.tm()}"); return }
        s.head(c)
        m.split("\n").forEach { s.out(it.tm()) }
    }

    private suspend fun cmdStatus(s: TermuxSession) {
        val p = repo.player.first()
        val now = System.currentTimeMillis()
        s.head("status")
        s.out("coins {c}${p.coins}{/}   level {c}${p.level()}{/} ${termuxBar(p.xpIntoLevel() / 100f)} ${p.xpIntoLevel()}/100   streak {c}${p.streak}{/}")
        if (p.focusModeUntil > now) {
            s.step("focus {c}${fmtLeft(p.focusModeUntil - now)}{/} left" + if (prefs.focusStrict) " {y}(strict){/}" else "")
        } else {
            s.dim("focus: idle")
        }
        if (Perms.accessibilityEnabled(ctx)) s.ok("blocking is on") else s.warn("blocking is off: run {c}reliability{/}")
        val fl = FunLimit.state(ctx, prefs)
        if (prefs.funFreeToday) s.dim("fun: free day, no limit")
        else if (fl.enabled) s.out("fun: {c}${fmtMs(fl.usedMs)}{/} of ${fmtMs(fl.limitMs)} today ${termuxBar(fl.usedMs.toFloat() / fl.limitMs.toFloat())}")
        else s.dim("fun: no limit set")
        val tasks = repo.tasks.first()
        val dayEnd = nextRunDayStart(0, now) - 1
        val open = tasks.count { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }
        s.out("quests: {c}$open{/} open today")
        if (p.hasXpBoost()) s.dim("xp boost until " + fmtTime(p.xpBoostUntil))
        if (p.hasCoinMultiplier()) s.dim("coin multiplier until " + fmtTime(p.coinMultiplierUntil))
        if (p.hasStreakShield()) s.dim("streak shield until " + fmtTime(p.streakShieldUntil))
    }

    private suspend fun cmdWhoami(s: TermuxSession) {
        val p = repo.player.first()
        s.out("{b}${p.name.ifBlank { "(no name)" }.tm()}{/}")
        s.out("goal      ${p.goal.ifBlank { "-" }.tm()}")
        s.out("born      ${p.dob.ifBlank { "-" }.tm()}")
        s.out("level     {c}${p.level()}{/}   xp ${p.xp}   coins ${p.coins}   streak ${p.streak}")
        s.out("look      ${p.equippedTheme}  frame=${p.equippedFrame.ifBlank { "-" }}  badge=${shownBadgeOf(p).ifBlank { "-" }}")
        s.dim("change with: set name|goal|dob <text>")
    }

    private fun cmdAlias(s: TermuxSession, toks: List<String>) {
        val m = aliases()
        if (toks.size < 2) {
            if (m.isEmpty()) s.dim("no aliases. example: alias qt=quests --today")
            m.forEach { (k, v) -> s.out("{c}${k.tm()}{/} = ${v.tm()}") }
            return
        }
        val text = toks.drop(1).joinToString(" ")
        val i = text.indexOf('=')
        if (i <= 0) { s.err("usage: alias name=command"); return }
        val name = text.substring(0, i).trim()
        val value = text.substring(i + 1).trim().trim('"', '\'')
        if (name.isEmpty() || value.isEmpty() || name.contains(" ")) { s.err("usage: alias name=command"); return }
        if (name in TOP_COMMANDS) { s.err("${name.tm()} is a real command"); return }
        m[name] = value
        saveAliases(m)
        s.ok("alias ${name.tm()} = ${value.tm()}")
    }

    private fun cmdUnalias(s: TermuxSession, a: TermuxArgs) {
        val n = a.pos.getOrNull(1)
        val m = aliases()
        if (n == null || m.remove(n) == null) { s.err("no such alias"); return }
        saveAliases(m)
        s.ok("removed ${n.tm()}")
    }

    private suspend fun cmdTheme(s: TermuxSession, a: TermuxArgs) {
        val sub = a.pos.getOrNull(1)?.lowercase()
        if (sub == null) {
            s.out("theme: {c}termux{/}   shop look: ${repo.player.first().equippedTheme}")
            s.dim("theme set default|termux")
            return
        }
        if (sub != "set") { s.err("usage: theme set default|termux"); return }
        when (a.pos.getOrNull(2)?.lowercase()) {
            "termux" -> s.ok("theme is already termux")
            "default" -> {
                if (TermuxMode.strictNow(ctx)) { s.err("refused: a strict Focus session is running"); return }
                s.ok("theme: default. switching\u2026")
                delay(400L)
                TermuxMode.set(ctx, false)
            }
            else -> s.err("usage: theme set default|termux")
        }
    }

    private fun cmdHome(s: TermuxSession) {
        block?.let { it.onHome(); return }
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { s.err("could not go home") }
    }

    private suspend fun cmdOpen(s: TermuxSession) {
        val b = block
        if (b == null) { s.dim("nothing to open here"); return }
        val until = blockUnlockedUntil(b.pkg)
        if (until > System.currentTimeMillis()) b.onOpen() else s.err("still locked: run {c}unlock{/} first")
    }

    private suspend fun blockUnlockedUntil(pkg: String): Long =
        if (pkg.startsWith("site:") || pkg.startsWith("short:") || pkg.startsWith("yt:")) repo.siteUnlockedUntil(lockKeyOf(pkg))
        else repo.getBlockedApp(pkg)?.unlockedUntil ?: 0L

    private suspend fun cmdSetup(s: TermuxSession, a: TermuxArgs) {
        if (a.pos.getOrNull(1)?.lowercase() == "done") {
            prefs.onboardingDone = true
            KeepAlive.start(ctx)
            s.ok("setup done. type {c}help{/}")
            return
        }
        val p = repo.player.first()
        s.head("setup")
        s.out("1. who are you:   {c}set name <text>{/}   {c}set goal <text>{/}   {c}set dob DD/MM/YYYY{/}")
        s.out("   name: ${p.name.ifBlank { "-" }.tm()}   goal: ${p.goal.ifBlank { "-" }.tm()}")
        s.out("2. permissions:   {c}perms{/} then {c}perms grant acc|usage|overlay|battery|notif|access|dnd{/}")
        s.out("   accessibility: ${if (Perms.accessibilityEnabled(ctx)) "{g}on{/}" else "{y}off{/}"}   usage access: ${if (UsageTracker.hasAccess(ctx)) "{g}on{/}" else "{y}off{/}"}")
        s.out("3. finish:        {c}setup done{/}")
        s.dim("the permission pages are Android's own: they open normally and you come back here.")
    }

    // ═══════════════ quests ═══════════════
    private val diffNames = listOf("easy", "med", "hard")
    private val categoryNames = listOf("Study", "Work", "Health", "Personal", "Custom")

    private fun questLine(t: TaskEntity, now: Long): String {
        val box = if (t.done) "{g}[x]{/}" else "[ ]"
        val meta = ArrayList<String>()
        meta.add(diffNames[(t.difficulty - 1).coerceIn(0, 2)])
        meta.add(t.category)
        meta.add("+${baseReward(t.difficulty)}c")
        if (t.questType != "onetime") meta.add(t.questType)
        val st = t.streakNow(dayNumber(now))
        if (st > 0) meta.add("streak $st")
        if (t.dueAt != null) meta.add("due " + fmtTime(t.dueAt))
        val overdue = !t.done && t.dueAt != null && t.dueAt < now
        return "{c}#${t.id}{/} $box ${t.title.tm()}  {d}${meta.joinToString(" \u00b7 ")}{/}" + if (overdue) " {r}overdue{/}" else ""
    }

    private suspend fun cmdQuests(s: TermuxSession, a: TermuxArgs) {
        val now = System.currentTimeMillis()
        val dayEnd = nextRunDayStart(0, now) - 1
        val all = repo.tasks.first()
        val title: String
        val list: List<TaskEntity>
        when {
            a.has("--done") -> { title = "quests done"; list = all.filter { it.done }.sortedByDescending { it.completedAt ?: 0L } }
            a.has("--overdue") -> { title = "quests overdue"; list = all.filter { !it.done && it.dueAt != null && it.dueAt < now } }
            a.has("--focus") -> { title = "focus quests"; list = all.filter { !it.done && (it.isTimerQuest || it.questType == "focus") } }
            a.has("--today") -> { title = "quests today"; list = all.filter { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) } }
            else -> { title = "quests"; list = all.filter { !it.done } }
        }
        s.head(title)
        if (list.isEmpty()) { s.dim("nothing here. add one: quest add \"title\" -d easy"); return }
        list.take(40).forEach { s.out(questLine(it, now)) }
        if (list.size > 40) s.dim("\u2026 ${list.size - 40} more, use a filter (--today --overdue --focus --done)")
        if (!a.has("--done")) s.dim("${all.count { it.done }} finished \u00b7 quest done <id> \u00b7 quest show <id>")
    }

    private fun difficultyOf(t: String?): Int? = when (t?.lowercase()) {
        null -> null
        "e", "easy", "1" -> 1
        "m", "med", "medium", "2" -> 2
        "h", "hard", "3" -> 3
        else -> -1
    }

    private fun categoryOf(t: String): String? = categoryNames.firstOrNull { it.equals(t, ignoreCase = true) }

    private suspend fun cmdQuest(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "add" -> questAdd(s, a)
            "show" -> questShow(s, a)
            "edit" -> questEdit(s, a)
            "done" -> questDone(s, a)
            "rm" -> questRm(s, a)
            "focus" -> questFocus(s, a)
            "link" -> questLink(s, a)
            "step" -> questStep(s, a, skip = false)
            "skip" -> questStep(s, a, skip = true)
            else -> s.err("usage: quest add|show|edit|done|rm|focus|link|step|skip  (man quest)")
        }
    }

    private suspend fun linkedPackages(s: TermuxSession, names: String?): String? {
        if (names == null) return ""
        val out = ArrayList<String>()
        for (n in names.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val app = pickApp(s, n) ?: return null
            out.add(app.pkg)
        }
        return out.joinToString(",")
    }

    private suspend fun questAdd(s: TermuxSession, a: TermuxArgs) {
        val title = a.rest(2).trim()
        if (title.isEmpty()) { s.err("usage: quest add \"title\" [-d easy|med|hard] [-c Study] [-t 25m] [--due \"fri 18:00\"] [--repeat daily]"); return }
        val d = difficultyOf(a.value("-d", "--diff")) ?: 1
        if (d < 0) { s.err("difficulty: easy, med or hard"); return }
        val catArg = a.value("-c", "--cat")
        val cat = if (catArg == null) "Personal" else categoryOf(catArg)
        if (cat == null) { s.err("category: ${categoryNames.joinToString(", ")}"); return }
        val dueText = a.value("--due")
        val due = if (dueText != null) parseDue(dueText) else null
        if (dueText != null && due == null) { s.err("due: today, tomorrow or a weekday, with an optional time, e.g. \"fri 18:00\""); return }
        val rep = a.value("--repeat")?.lowercase()
        val repMask = if (rep != null && rep != "daily" && rep != "weekly") parseDays(rep) else null
        if (rep != null && rep != "daily" && rep != "weekly" && repMask == null) { s.err("repeat: daily, weekly or days like mon,wed"); return }
        val steps = a.value("--steps")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val minutes = a.value("-t", "--time")?.let { parseMinutes(it) }
        if (a.value("-t", "--time") != null && minutes == null) { s.err("time: like 25m, 1h or 1h30m"); return }
        val challenge = a.value("--challenge")?.toIntOrNull()?.coerceIn(1, 365)
        val linked = linkedPackages(s, a.value("--apps")) ?: return
        val type = when {
            steps.isNotEmpty() -> "routine"
            minutes != null -> "focus"
            challenge != null -> "challenge"
            rep == "daily" -> "daily"
            rep == "weekly" || repMask != null -> "weekly"
            else -> "onetime"
        }
        var rec = false; var rt = ""; var mask = 0; var dueAt: Long? = due
        when (type) {
            "daily" -> { rec = true; rt = "daily"; dueAt = null }
            "weekly" -> { rec = true; rt = "weekly"; mask = repMask ?: (1 shl dowIndex(System.currentTimeMillis())); dueAt = null }
            "challenge" -> { rec = true; rt = "daily"; dueAt = null }
            "routine", "focus" -> {
                if (rep == "daily") { rec = true; rt = "daily" }
                else if (repMask != null) { rec = true; rt = "daily"; mask = repMask }
            }
        }
        val draft = QuestDraft(
            title = title, difficulty = d, category = cat, notes = a.value("--notes").orEmpty(), dueAt = dueAt,
            isRecurring = rec, recurType = rt,
            linkedApps = if (type == "focus") linked else "",
            isTimerQuest = type == "focus", timerDurationMs = if (type == "focus") (minutes ?: 25) * 60_000L else 0L,
            daysMask = mask, questType = type, challengeDays = if (type == "challenge") (challenge ?: 7) else 0,
            steps = if (type == "routine") steps else emptyList(),
        )
        val id = repo.addTask(draft)
        s.ok("quest {c}#$id{/} added: ${title.tm()}  {d}$type \u00b7 ${diffNames[d - 1]} \u00b7 +${baseReward(d)} coins{/}")
    }

    private suspend fun taskArg(s: TermuxSession, a: TermuxArgs): TaskEntity? {
        val id = a.pos.getOrNull(2)?.removePrefix("#")?.toLongOrNull()
        if (id == null) { s.err("which quest? give its #id (see: quests)"); return null }
        val t = repo.getTask(id)
        if (t == null) { s.err("no quest #$id"); return null }
        return t
    }

    private suspend fun questShow(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        val now = System.currentTimeMillis()
        s.out(questLine(t, now))
        if (t.notes.isNotBlank()) s.out("notes   ${t.notes.tm()}")
        if (t.isTimerQuest || t.timerDurationMs > 0) s.out("timer   ${fmtMs(t.timerDurationMs)}")
        if (t.linkedApps.isNotBlank()) s.out("apps    " + t.linkedApps.split(",").filter { it.isNotBlank() }.joinToString(", ") { labelOfPkg(it).tm() })
        if (t.daysMask != 0) s.out("days    ${daysLabel(t.daysMask).ifBlank { "every day" }}")
        if (t.questType == "challenge") s.out("challenge ${t.challengeDays} days")
        val steps = repo.getSubTasks(t.id)
        steps.forEachIndexed { i, st -> s.out("  ${i + 1}. ${if (st.done) "{g}[x]{/}" else "[ ]"} ${st.title.tm()}") }
    }

    private suspend fun questEdit(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        val steps = repo.getSubTasks(t.id).map { it.title }
        var d = QuestDraft(
            title = t.title, difficulty = t.difficulty, category = t.category, priority = t.priority, notes = t.notes,
            dueAt = t.dueAt, isRecurring = t.isRecurring, recurType = t.recurType, linkedApps = t.linkedApps,
            isTimerQuest = t.isTimerQuest, timerDurationMs = t.timerDurationMs, daysMask = t.daysMask,
            questType = t.questType, challengeDays = t.challengeDays, steps = steps,
        )
        var changed = false
        a.value("--title")?.let { d = d.copy(title = it); changed = true }
        a.value("--notes")?.let { d = d.copy(notes = it); changed = true }
        a.value("-d", "--diff")?.let {
            val x = difficultyOf(it) ?: -1
            if (x < 0) { s.err("difficulty: easy, med or hard"); return }
            d = d.copy(difficulty = x); changed = true
        }
        a.value("-c", "--cat")?.let {
            val c = categoryOf(it)
            if (c == null) { s.err("category: ${categoryNames.joinToString(", ")}"); return }
            d = d.copy(category = c); changed = true
        }
        a.value("-t", "--time")?.let {
            val m = parseMinutes(it)
            if (m == null) { s.err("time: like 25m"); return }
            d = d.copy(timerDurationMs = m * 60_000L); changed = true
        }
        a.value("--due")?.let {
            val x = if (it == "none") null else parseDue(it)
            if (it != "none" && x == null) { s.err("due: today, tomorrow or a weekday, optional time (or none)"); return }
            d = d.copy(dueAt = x); changed = true
        }
        if (!changed) { s.err("nothing to change. flags: --title --notes -d -c -t --due"); return }
        repo.updateTask(t.id, d)
        s.ok("quest #${t.id} updated")
    }

    private suspend fun finishQuest(s: TermuxSession, t: TaskEntity) {
        val u = repo.completeTaskUndoable(t)
        if (u == null) {
            s.warn("not finished: it is done already, not scheduled for today, or has open steps")
        } else {
            lastUndo = u
            s.ok("quest done: ${t.title.tm()}  {c}+${u.coinsGained}{/} coins  {c}+${u.xpGained}{/} xp")
            s.dim("type undo to take it back")
        }
    }

    private suspend fun questDone(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        if (t.questType == "routine" && repo.getSubTasks(t.id).any { !it.done }) {
            s.warn("a routine finishes when all steps are ticked: quest step ${t.id} <n>")
            return
        }
        finishQuest(s, t)
    }

    private suspend fun questStep(s: TermuxSession, a: TermuxArgs, skip: Boolean) {
        val t = taskArg(s, a) ?: return
        val steps = repo.getSubTasks(t.id)
        val n = a.pos.getOrNull(3)?.toIntOrNull()
        if (steps.isEmpty()) { s.err("#${t.id} has no steps"); return }
        if (n == null || n < 1 || n > steps.size) {
            steps.forEachIndexed { i, st -> s.out("  ${i + 1}. ${if (st.done) "{g}[x]{/}" else "[ ]"} ${st.title.tm()}") }
            s.err("which step? 1-${steps.size}")
            return
        }
        val sub = steps[n - 1]
        if (sub.done) { s.warn("step $n is already done"); return }
        val tail: suspend () -> Unit = {
            val after = repo.getSubTasks(t.id)
            if (after.all { it.done }) finishQuest(s, t)
        }
        if (skip) {
            confirm(s, "skip step $n for $STEP_SKIP_COST coins? no reward for it") {
                if (repo.skipStep(t, sub)) { s.ok("step $n skipped (-$STEP_SKIP_COST coins)"); tail() } else s.err("not enough coins")
            }
        } else {
            repo.completeStep(t, sub)
            s.ok("step $n done: ${sub.title.tm()}")
            tail()
        }
    }

    private suspend fun questRm(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        confirm(s, "delete quest #${t.id} '${t.title.tm()}'?") {
            repo.deleteTask(t)
            s.ok("deleted #${t.id}")
        }
    }

    private suspend fun questFocus(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        val ms = if (t.timerDurationMs > 0) t.timerDurationMs else 60 * 60_000L
        val ok = repo.startFocus(ms, t)
        if (ok) { KeepAlive.start(ctx); s.ok("focus timer started for #${t.id}: ${fmtMs(ms)}") } else s.err("a session is already running")
    }

    private suspend fun questLink(s: TermuxSession, a: TermuxArgs) {
        val t = taskArg(s, a) ?: return
        val app = pickApp(s, a.rest(3)) ?: return
        val cur = t.linkedApps.split(",").filter { it.isNotBlank() }.toMutableList()
        val on = if (app.pkg in cur) { cur.remove(app.pkg); false } else { cur.add(app.pkg); true }
        val steps = repo.getSubTasks(t.id).map { it.title }
        repo.updateTask(t.id, QuestDraft(
            title = t.title, difficulty = t.difficulty, category = t.category, priority = t.priority, notes = t.notes,
            dueAt = t.dueAt, isRecurring = t.isRecurring, recurType = t.recurType, linkedApps = cur.joinToString(","),
            isTimerQuest = t.isTimerQuest, timerDurationMs = t.timerDurationMs, daysMask = t.daysMask,
            questType = t.questType, challengeDays = t.challengeDays, steps = steps,
        ))
        s.ok("${app.label.tm()} ${if (on) "unlocks while #${t.id} runs" else "is no longer linked to #${t.id}"}")
        if (!t.isTimerQuest) s.dim("links only work on focus quests (quest add ... -t 25m)")
    }

    private suspend fun cmdUndo(s: TermuxSession) {
        val u = lastUndo
        if (u == null) { s.warn("nothing to undo"); return }
        repo.undoCompletion(u)
        lastUndo = null
        s.ok("undone: ${u.task.title.tm()}")
    }

    // ═══════════════ focus ═══════════════
    private suspend fun cmdFocus(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "start" -> focusStart(s, a, useLast = false)
            "last" -> focusStart(s, a, useLast = true)
            "status", null -> focusStatus(s)
            "stop" -> focusStop(s)
            "apps" -> {
                val picks = repo.focusAllowed()
                if (picks.isEmpty()) s.dim("no apps picked for the next session")
                else picks.map { labelOfPkg(it) }.sorted().forEach { s.out("  ${it.tm()}") }
            }
            "log" -> {
                val f = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                val l = repo.focusLogs.first().take(12)
                if (l.isEmpty()) s.dim("no sessions yet")
                l.forEach { s.out("${f.format(Date(it.startedAt))}  ${it.minutes}m  ${if (it.completed) "{g}\u2713{/}" else "{r}\u2717{/}"}  ${it.questTitle.tm()}") }
            }
            else -> s.err("usage: focus start 25m --apps a,b [--strict] | status | stop | apps | last | log")
        }
    }

    private suspend fun focusStart(s: TermuxSession, a: TermuxArgs, useLast: Boolean) {
        val now = System.currentTimeMillis()
        if (repo.player.first().focusModeUntil > now) { s.err("a session is already running (focus status)"); return }
        val questId = a.value("--quest")?.removePrefix("#")?.toLongOrNull()
        val quest = if (questId != null) repo.getTask(questId) else null
        if (a.value("--quest") != null && quest == null) { s.err("no such quest"); return }
        val minutes: Int
        if (quest != null) {
            minutes = ((if (quest.timerDurationMs > 0) quest.timerDurationMs else 60 * 60_000L) / 60_000L).toInt()
        } else {
            val arg = a.pos.getOrNull(2)
            minutes = if (arg == null) 25 else (parseMinutes(arg) ?: run { s.err("time: like 25m, 1h or 1h30m"); return })
            if (minutes < 5 || minutes > 120) { s.err("a session is 5 to 120 minutes"); return }
        }
        val picks: Set<String>
        val appsArg = a.value("--apps")
        if (appsArg != null && !useLast) {
            val found = LinkedHashSet<String>()
            for (n in appsArg.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
                val app = pickApp(s, n, includeBlocked = false) ?: return
                found.add(app.pkg)
            }
            picks = found
        } else {
            picks = repo.focusAllowed()
        }
        val strict = a.has("--strict") || prefs.strictMode
        val names = if (picks.isEmpty()) "none" else picks.map { labelOfPkg(it) }.sorted().joinToString(", ")
        val reward = repo.focusBonus() + (quest?.let { baseReward(it.difficulty) } ?: 0)
        s.step("focus {c}${minutes}m{/}   apps: ${names.tm()}   strict: ${if (strict) "{y}yes{/}" else "no"}")
        s.dim("everything else is locked and Do Not Disturb turns on. phone, home screen and QuestBlock always open. finish for +$reward coins")
        confirm(s, "Start $minutes min focus?") {
            val penalty = repo.focusPenalty()
            val cost = if (strict) "strict: you can't give up, and turning accessibility off breaks the session (penalty, no reward)"
            else if (penalty > 0) "giving up after the first minute costs $penalty coins and gives no reward" else "giving up gives no reward"
            confirm(s, "Commit for $minutes minutes? last check, the apps can't change once it starts. $cost") {
                repo.setFocusAllowed(picks)
                repo.setFocusStrict(strict)
                Sounds.ambient = "off"
                val ok = repo.startFocus(minutes * 60_000L, quest)
                if (ok) {
                    KeepAlive.start(ctx)
                    s.ok("focus started: {c}${minutes}m{/}")
                    s.dim("play rain|brown|pink|white \u00b7 focus status \u00b7 see the focus session in the drawer")
                } else {
                    s.err("a session is already running")
                }
            }
        }
    }

    private suspend fun focusStatus(s: TermuxSession) {
        val p = repo.player.first()
        val now = System.currentTimeMillis()
        s.head("focus")
        if (p.focusModeUntil > now) {
            val left = p.focusModeUntil - now
            val total = p.focusTotalMs.coerceAtLeast(1L)
            s.step("{c}${fmtLeft(left)}{/} left of ${fmtMs(total)}  ${termuxBar(1f - left.toFloat() / total.toFloat(), 20)}")
            s.out("strict: ${if (prefs.focusStrict) "{y}yes{/}" else "no"}   sound: ${Sounds.ambient}")
            val picks = repo.focusAllowed()
            s.out("apps:   ${if (picks.isEmpty()) "none" else picks.map { labelOfPkg(it) }.sorted().joinToString(", ").tm()}")
            if (p.focusQuestId > 0) repo.getTask(p.focusQuestId)?.let { s.out("quest:  #${it.id} ${it.title.tm()}") }
        } else {
            s.dim("idle")
            val br = repo.breakUntil() - now
            if (br > 0) s.out("break: {c}${fmtLeft(br)}{/} left ${if (repo.breakIsLong()) "(long)" else ""}")
        }
        val dayStart = startOfToday(now)
        val logs = repo.focusLogs.first()
        val today = logs.filter { it.startedAt >= dayStart }
        s.out("today: {c}${today.count { it.completed }}{/} sessions done, {c}${today.sumOf { it.minutes }}{/} min   total ${fmtMin(logs.filter { it.completed }.sumOf { it.minutes }.toLong())}")
    }

    private suspend fun focusStop(s: TermuxSession) {
        val p = repo.player.first()
        val now = System.currentTimeMillis()
        if (p.focusModeUntil <= now) { s.warn("no session is running"); return }
        if (prefs.focusStrict) { s.err("strict session: giving up is not allowed"); return }
        val elapsed = now - (p.focusModeUntil - p.focusTotalMs)
        val pen = repo.focusPenalty()
        val cost = if (elapsed < 60_000L || pen <= 0) "free (first minute)" else "costs up to $pen coins, no reward"
        confirm(s, "give up the session? $cost") {
            val lost = repo.abandonFocus()
            Sounds.stopAmbient()
            if (lost > 0) s.warn("session ended, -$lost coins") else s.ok("session ended")
        }
    }

    private suspend fun cmdPlay(s: TermuxSession, a: TermuxArgs) {
        val k = a.pos.getOrNull(1)?.lowercase()
        if (k == null || k !in listOf("rain", "brown", "pink", "white")) { s.err("usage: play rain|brown|pink|white"); return }
        val p = repo.player.first()
        if (p.focusModeUntil <= System.currentTimeMillis()) { s.err("background sound plays only while a Focus session runs"); return }
        Sounds.ambient = k
        Sounds.startAmbient(p.focusModeUntil)
        s.ok("playing $k until the session ends  (stop)")
    }

    // ═══════════════ blocking ═══════════════
    private suspend fun cmdVault(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "ls", null -> vaultLs(s)
            "add" -> {
                val app = pickApp(s, a.rest(2)) ?: return
                repo.setBlocked(app.pkg, app.label, true)
                s.ok("${app.label.tm()} is locked now")
            }
            "rm" -> {
                val app = pickApp(s, a.rest(2)) ?: return
                repo.setBlocked(app.pkg, app.label, false)
                s.ok("${app.label.tm()} left the vault")
            }
            "study" -> {
                val app = pickApp(s, a.rest(2)) ?: return
                repo.setStudyApp(app.pkg, app.label, true)
                s.ok("${app.label.tm()} is a study app: time in it earns coins")
            }
            "unstudy" -> {
                val app = pickApp(s, a.rest(2)) ?: return
                repo.setStudyApp(app.pkg, app.label, false)
                s.ok("${app.label.tm()} is no longer a study app")
            }
            "info" -> {
                val app = pickApp(s, a.rest(2)) ?: return
                val r = repo.getBlockedApp(app.pkg)
                if (r == null) { s.warn("${app.label.tm()} is not in the vault"); return }
                vaultInfo(s, r)
            }
            "opt" -> vaultOpt(s, a)
            "clips" -> {
                val kind = a.pos.getOrNull(2)?.lowercase()
                val on = a.pos.getOrNull(3)?.let { parseBool(it) }
                if ((kind != "yt" && kind != "ig") || on == null) { s.err("usage: vault clips yt|ig on|off"); return }
                if (kind == "yt" && !isYouTubeInstalled(ctx)) { s.err("YouTube is not installed"); return }
                if (kind == "ig" && !isInstagramInstalled(ctx)) { s.err("Instagram is not installed"); return }
                repo.changeShortPicked(kind, on)
                s.ok("${if (kind == "yt") "YouTube Shorts" else "Instagram Reels"} lock: ${if (on) "on" else "off"}")
            }
            else -> s.err("usage: vault ls|add|rm|info|study|unstudy|opt|clips  (man vault)")
        }
    }

    private suspend fun vaultLs(s: TermuxSession) {
        val rules = repo.blockedApps.first()
        val now = System.currentTimeMillis()
        val locked = rules.filter { !it.isStudyApp }
        val study = rules.filter { it.isStudyApp }
        s.head("vault")
        s.dim("${locked.size} locked \u00b7 ${study.size} study")
        locked.forEach { r ->
            val rate = if (r.ratePer5 > 0) r.ratePer5 else prefs.ratePer5
            val extra = ArrayList<String>()
            extra.add("${rate}c/5m" + if (r.priceMult != 100) " x${r.priceMult}%" else "")
            if (r.dailyLimitMin > 0) extra.add("free ${r.dailyLimitMin}m/day")
            if (r.schedStart >= 0 && r.schedEnd >= 0 && r.schedStart != r.schedEnd) extra.add("${fmtMinute(r.schedStart)}-${fmtMinute(r.schedEnd)}")
            val left = r.unlockedUntil - now
            s.out("{c}${r.label.tm()}{/}  {d}${extra.joinToString(" \u00b7 ")}{/}" + if (left > 0) "  {g}unlocked ${fmtLeft(left)}{/}" else "")
        }
        if (study.isNotEmpty()) {
            s.out("{b}STUDY{/}")
            study.forEach { s.out("{c}${it.label.tm()}{/}  {d}earns 1 coin / ${prefs.studyMinPerCoin} min{/}") }
        }
        val yt = if (isYouTubeInstalled(ctx)) (if (repo.shortPicked("yt")) "on" else "off") else "n/a"
        val ig = if (isInstagramInstalled(ctx)) (if (repo.shortPicked("ig")) "on" else "off") else "n/a"
        s.dim("clips lock:  YouTube Shorts $yt \u00b7 Instagram Reels $ig   (vault clips yt|ig on|off)")
    }

    private fun vaultInfo(s: TermuxSession, r: BlockedApp) {
        val now = System.currentTimeMillis()
        s.head(r.label)
        s.out("package   ${r.packageName}")
        s.out("kind      ${if (r.isStudyApp) "study app" else "locked"}")
        s.out("price     ${if (r.ratePer5 > 0) r.ratePer5 else prefs.ratePer5} coins / 5 min  x ${r.priceMult}%")
        s.out("daily     ${if (r.dailyLimitMin > 0) "free until ${r.dailyLimitMin} min used per day" else "always locked"}")
        s.out("hours     ${if (r.schedStart >= 0 && r.schedEnd >= 0 && r.schedStart != r.schedEnd) "${fmtMinute(r.schedStart)}-${fmtMinute(r.schedEnd)}" else "all day"}")
        s.out("unlocked  ${if (r.unlockedUntil > now) fmtLeft(r.unlockedUntil - now) + " left" else "no"}")
    }

    private suspend fun vaultOpt(s: TermuxSession, a: TermuxArgs) {
        val app = pickApp(s, a.pos.getOrNull(2) ?: "") ?: return
        val r = repo.getBlockedApp(app.pkg)
        if (r == null) { s.err("${app.label.tm()} is not in the vault"); return }
        val limit = a.value("--limit")?.toIntOrNull() ?: r.dailyLimitMin
        val price = a.value("--price")?.toIntOrNull() ?: r.priceMult
        val rate = a.value("--rate")?.toIntOrNull() ?: r.ratePer5
        if (limit !in 0..600 || price !in 10..500 || rate !in 0..100) { s.err("limit 0-600 min, price 10-500 %, rate 0-100 coins"); return }
        var from = r.schedStart
        var to = r.schedEnd
        val f = a.value("--from"); val t = a.value("--to")
        if (f == "off" || t == "off") { from = -1; to = -1 }
        else {
            if (f != null) { from = parseHm(f) ?: run { s.err("--from: time like 09:00"); return } }
            if (t != null) { to = parseHm(t) ?: run { s.err("--to: time like 17:00"); return } }
        }
        repo.setAppOptions(app.pkg, limit, from, to, price, rate)
        s.ok("${app.label.tm()}: limit ${limit}m, price ${price}%, rate ${rate}c")
    }

    private suspend fun cmdUnlock(s: TermuxSession, a: TermuxArgs) {
        val b = block
        val arg1 = a.pos.getOrNull(1)
        var minutes = 15
        val target: String
        if (b != null) {
            target = b.pkg
            if (arg1 != null) minutes = parseMinutes(arg1) ?: run { s.err("minutes: like 15"); return }
        } else {
            if (arg1 == null) { s.err("usage: unlock <app> [minutes]"); return }
            val site = prefs.blockedSites().firstOrNull { it == arg1.lowercase() }
            if (site != null) {
                target = "site:$site"
            } else {
                val app = pickApp(s, arg1) ?: return
                target = app.pkg
            }
            val m = a.pos.getOrNull(2)
            if (m != null) minutes = parseMinutes(m) ?: run { s.err("minutes: like 15"); return }
        }
        val blocks = ((minutes + 4) / 5).coerceIn(1, MAX_UNLOCK_BLOCKS)
        val label = labelOfPkg(target)
        val p = repo.player.first()
        val now = System.currentTimeMillis()
        val pseudo = target.startsWith("site:") || target.startsWith("short:") || target.startsWith("yt:")
        if (b != null && (b.pkg.startsWith("ytstudy:") || b.pkg.startsWith("focuslock:") || b.funLimit)) { s.err("no unlock here: this lock does not sell time"); return }
        if (p.focusModeUntil > now) {
            if (!pseudo && target in repo.focusAllowed()) s.ok("${label.tm()} is free for this Focus session") else s.err("Focus is on: ${label.tm()} is locked until the session ends")
            return
        }
        val rule = if (pseudo) null else repo.getBlockedApp(target)
        if (!pseudo && rule == null) { s.err("${label.tm()} is not in the vault"); return }
        if (rule != null && rule.isStudyApp) { s.ok("${label.tm()} is a study app: always free"); return }
        if (!pseudo && !prefs.funFreeToday) {
            val isFun = AppCategorizer.categoryOf(prefs, ctx.packageManager, target) == "fun"
            if (isFun && (prefs.funBlockedNow() || FunLimit.state(ctx, prefs).reached)) { s.err("fun time is used up or locked: no unlock for ${label.tm()} now"); return }
        }
        val rate = if (target == "yt:fun") YT_FUN_RATE_PER_5 else repo.ratePer5()
        val pct = repo.routineSurchargePct()
        val cost = withSurcharge(unlockCost(blocks, rate, rule), pct)
        s.step("unlock {c}${label.tm()}{/}  ${blocks * 5} min for {c}$cost{/} coins   balance {c}${p.coins}{/}")
        if (pct > 0) s.warn("routine first: +$pct% until today's routine is done")
        if (p.coins < cost) { s.err("not enough coins: have ${p.coins}, need $cost"); s.dim("earn some: quests"); return }
        val word = label.split(" ").first().let { if (it.length >= 2) it else label }
        confirmWord(s, "pay $cost coins for ${blocks * 5} min of ${label.tm()}?", word) {
            val res = repo.unlock(target, blocks)
            if (res.ok) {
                if (res.charged) s.ok("-${res.cost} coins (${res.before} \u2192 ${res.after})  +${res.minutes} min") else s.ok("already unlocked")
                KeepAlive.start(ctx)
                if (block != null) s.step("run {c}open{/} to open it")
            } else {
                s.err("not enough coins")
            }
        }
    }

    private suspend fun cmdSites(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "ls", null -> {
                s.head("sites")
                val l = prefs.blockedSites()
                if (l.isEmpty()) s.dim("no blocked sites. sites add instagram.com")
                l.forEach { s.out("{c}${it.tm()}{/}" + if (prefs.siteUnblockPending(it)) "  {y}unblocks at midnight{/}" else "") }
                s.dim("unlock them like apps: unlock <domain>. works in browsers that show an address bar")
            }
            "add" -> {
                val raw = a.pos.getOrNull(2)
                if (raw == null || !prefs.addSite(raw)) s.err("enter a site like instagram.com") else s.ok("blocked ${prefs.normalizeSite(raw).tm()}")
            }
            "rm" -> {
                val raw = a.pos.getOrNull(2)
                val d = if (raw == null) null else prefs.normalizeSite(raw)
                if (d == null || d !in prefs.blockedSites()) { s.err("not a blocked site"); return }
                prefs.removeSite(d)
                s.warn("${d.tm()} unblocks at midnight")
            }
            else -> s.err("usage: sites ls|add|rm")
        }
    }

    private suspend fun cmdLimits(s: TermuxSession) {
        s.head("limits")
        val fl = FunLimit.state(ctx, prefs, force = true)
        s.out("fun limit     ${if (prefs.funLimitMin > 0) "${prefs.funLimitMin} min" else "off"}   weekend ${if (prefs.weekendFunLimitMin > 0) "${prefs.weekendFunLimitMin} min" else "same"}   today ${if (prefs.effectiveFunLimitMin > 0) "${prefs.effectiveFunLimitMin} min" else "off"}")
        if (prefs.funFreeToday) s.out("today         {g}free day: no fun limit{/}")
        else if (fl.enabled) s.out("used          {c}${fmtMs(fl.usedMs)}{/} of ${fmtMs(fl.limitMs)} ${termuxBar(fl.usedMs.toFloat() / fl.limitMs.toFloat())}")
        s.out("exam mode     ${if (prefs.examMode) "{y}on{/}" else "off"}")
        s.out("quiet hours   ${if (prefs.quietEnabled) "{y}on{/}" else "off"}  ${fmtMinute(prefs.quietStartMin)}-${fmtMinute(prefs.quietEndMin)}")
        s.out("study hours   ${if (prefs.studyBlockEnabled) "{y}on{/}" else "off"}  ${fmtMinute(prefs.studyStartMin)}-${fmtMinute(prefs.studyEndMin)}")
        s.out("shorts block  ${onOff(prefs.shortsBlockOn)}   yt study gate ${onOff(prefs.ytGateOn)}   online check ${onOff(prefs.ytInfoOn)}   music counts ${onOff(prefs.ghostCountsOn)}")
        s.out("strict mode   ${onOff(prefs.strictMode)}   routine first ${onOff(prefs.routineFirst)}")
        val pend = prefs.pendingSummary()
        if (pend.isNotEmpty()) s.warn(pend)
        s.dim("loosening a rule waits until midnight. change with: set fun_limit 90, set exam on, set quiet_from 22:00 ...")
    }

    private fun onOff(b: Boolean) = if (b) "on" else "off"

    private suspend fun cmdCategories(s: TermuxSession, a: TermuxArgs) {
        val pm = ctx.packageManager
        when (a.pos.getOrNull(1)?.lowercase()) {
            "ls", null -> {
                val q = a.rest(2).lowercase()
                val apps = installed().filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
                s.head("categories")
                apps.take(45).forEach { app ->
                    val cat = AppCategorizer.categoryOf(prefs, pm, app.pkg)
                    val manual = prefs.categoryOverride(app.pkg) != null
                    s.out("${app.label.tm().padEnd(22).take(22)} {c}${cat.padEnd(8)}{/}${if (manual) " {d}(yours){/}" else ""}")
                }
                if (apps.size > 45) s.dim("\u2026 ${apps.size - 45} more: categories ls <text>")
                s.dim("categories set <app> study|coding|neutral|fun|auto")
            }
            "set" -> {
                val app = pickApp(s, a.pos.getOrNull(2) ?: "", includeBlocked = false) ?: return
                val cat = a.pos.getOrNull(3)?.lowercase()
                if (cat == null || cat !in listOf("study", "coding", "neutral", "fun", "auto")) { s.err("category: study, coding, neutral, fun or auto"); return }
                val current = AppCategorizer.categoryOf(prefs, pm, app.pkg)
                val auto = AppCategorizer.autoCategory(pm, app.pkg)
                val applied = if (cat == "auto") prefs.changeCategory(app.pkg, null, current, auto) else prefs.changeCategory(app.pkg, cat, current, cat)
                if (applied) s.ok("${app.label.tm()} \u2192 $cat") else s.warn("${app.label.tm()} leaves Fun & Games at midnight (fun rules are on)")
            }
            "safe" -> {
                val app = pickApp(s, a.pos.getOrNull(2) ?: "", includeBlocked = false) ?: return
                val on = a.pos.getOrNull(3)?.let { parseBool(it) }
                if (on == null) { s.err("usage: categories safe <app> on|off"); return }
                val applied = prefs.changeFunSafe(app.pkg, on)
                if (applied) s.ok("${app.label.tm()} safe shield ${onOff(on)}") else s.warn("the shield turns on tomorrow (fun rules are on)")
            }
            else -> s.err("usage: categories ls|set|safe")
        }
    }

    // ═══════════════ economy ═══════════════
    private suspend fun cmdCoins(s: TermuxSession, a: TermuxArgs) {
        val p = repo.player.first()
        val log = repo.coinLog.first()
        if (a.pos.getOrNull(1)?.lowercase() == "log") {
            val n = a.pos.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val f = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
            s.head("coin log")
            if (log.isEmpty()) s.dim("empty")
            log.take(n).forEach { l ->
                val amt = if (l.amount > 0) "{g}+${l.amount}{/}" else if (l.amount < 0) "{r}${l.amount}{/}" else "{d}0{/}"
                s.out("{d}${f.format(Date(l.timestamp))}{/}  ${amt.padEnd(14)} ${l.source.tm()} {d}${l.detail.tm()}{/}")
            }
            return
        }
        val start = startOfToday(System.currentTimeMillis())
        val today = log.filter { it.timestamp >= start }
        s.out("coins {c}${p.coins}{/}")
        s.out("today  {g}+${today.filter { it.amount > 0 }.sumOf { it.amount }}{/}  {r}${today.filter { it.amount < 0 }.sumOf { it.amount }}{/}")
        s.dim("coins log [n]")
    }

    private fun shopItemFor(q: String): ShopItem? {
        val items = ShopCatalog.items
        q.toIntOrNull()?.let { n -> return items.getOrNull(n - 1) }
        val ql = q.lowercase()
        return items.firstOrNull { it.id == ql } ?: items.firstOrNull { it.name.lowercase() == ql }
            ?: items.firstOrNull { it.name.lowercase().contains(ql) || it.id.contains(ql) }
    }

    private suspend fun cmdShop(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "ls", null -> {
                val cat = when (a.pos.getOrNull(2)?.lowercase()) {
                    "powerups" -> "Power-ups"; "frames" -> "Frames"; "themes" -> "Themes"; "badges" -> "Badges"; else -> "All"
                }
                val owned = repo.inventory.first().map { it.itemId }.toSet()
                s.head("shop")
                s.dim("balance ${repo.player.first().coins} coins \u00b7 shop buy <n|name>")
                ShopCatalog.items.forEachIndexed { i, it ->
                    if (cat != "All" && ShopCatalog.filter(cat).none { x -> x.id == it.id }) return@forEachIndexed
                    val price = if (it.category == "cosmetic_theme") (if (it.price == 0) "free" else "${it.price}c/day") else "${it.price}c"
                    val own = if (!it.isConsumable && it.id in owned) " {g}owned{/}" else ""
                    s.out("{c}${(i + 1).toString().padStart(2)}{/} ${it.name.tm()}  {y}$price{/}$own  {d}${it.description.tm()}{/}")
                }
            }
            "buy" -> shopBuy(s, a)
            "rewards" -> {
                val l = repo.customRewards.first()
                s.head("my rewards")
                if (l.isEmpty()) s.dim("none. shop reward add \"name\" <price>")
                l.forEachIndexed { i, r -> s.out("{c}${i + 1}{/} ${r.emoji} ${r.name.tm()}  {y}${r.price}c{/}  {d}${r.type}${if (r.oncePerDay) " \u00b7 once a day" else ""}${if (r.description.isNotBlank()) " \u00b7 " + r.description.tm() else ""}{/}") }
                s.dim("shop buy -r <n>")
            }
            "reward" -> shopReward(s, a)
            else -> s.err("usage: shop ls|buy|rewards|reward  (man shop)")
        }
    }

    private suspend fun shopBuy(s: TermuxSession, a: TermuxArgs) {
        val r = a.value("-r", "--reward")
        if (r != null) {
            val l = repo.customRewards.first()
            val rw = r.toIntOrNull()?.let { l.getOrNull(it - 1) } ?: l.firstOrNull { it.name.equals(r, ignoreCase = true) }
            if (rw == null) { s.err("no such reward (shop rewards)"); return }
            confirm(s, "buy ${rw.name.tm()} for ${rw.price} coins?") {
                when (repo.buyCustomReward(rw)) {
                    1 -> s.err("not enough coins")
                    2 -> s.warn("you already got it today")
                    else -> s.ok(if (rw.type == "reward") "enjoy ${rw.name.tm()}! -${rw.price} coins" else "${rw.name.tm()} saved in inv")
                }
            }
            return
        }
        val q = a.rest(2)
        val item = if (q.isBlank()) null else shopItemFor(q)
        if (item == null) { s.err("no such item. see: shop ls"); return }
        if (item.id == "streak_repair" && !repo.canRepairStreak()) { s.warn("no broken streak to repair (only within 3 days, and not while a shield is on)"); return }
        if (item.category == "cosmetic_theme" && repo.inventory.first().any { it.itemId == item.id }) { s.warn("you already have ${item.name.tm()}. equip it: inv equip <n>"); return }
        val priceText = if (item.category == "cosmetic_theme") "${item.price} coins a day while it is equipped" else "${item.price} coins"
        confirm(s, "get ${item.name.tm()} for $priceText?") {
            if (repo.buyItem(item)) {
                s.ok(if (item.category == "cosmetic_theme") "got ${item.name.tm()}. equip it in inv" else "bought ${item.name.tm()}")
            } else s.err("not enough coins")
        }
    }

    private suspend fun shopReward(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(2)?.lowercase()) {
            "add" -> {
                val name = a.pos.getOrNull(3)
                val price = a.pos.getOrNull(4)?.toIntOrNull()
                if (name.isNullOrBlank() || price == null || price < 1) { s.err("usage: shop reward add \"name\" <price> [--type reward|consumable] [--once] [--desc text]"); return }
                val type = a.value("--type") ?: "reward"
                if (type != "reward" && type != "consumable") { s.err("type: reward or consumable"); return }
                repo.addCustomReward(name, a.value("--emoji").orEmpty(), price, a.value("--desc").orEmpty(), type, a.has("--once"))
                s.ok("reward added: ${name.tm()} for $price coins")
            }
            "rm" -> {
                val l = repo.customRewards.first()
                val rw = a.pos.getOrNull(3)?.toIntOrNull()?.let { l.getOrNull(it - 1) }
                if (rw == null) { s.err("usage: shop reward rm <n>  (see shop rewards)"); return }
                confirm(s, "delete reward ${rw.name.tm()}?") { repo.deleteCustomReward(rw); s.ok("deleted") }
            }
            else -> s.err("usage: shop reward add|rm")
        }
    }

    private suspend fun cmdInv(s: TermuxSession, a: TermuxArgs) {
        val inv = repo.inventory.first()
        val sub = a.pos.getOrNull(1)?.lowercase()
        if (sub == null || sub == "ls") {
            val p = repo.player.first()
            val now = System.currentTimeMillis()
            s.head("inventory")
            if (p.hasXpBoost()) s.out("{g}active{/} xp boost until ${fmtTime(p.xpBoostUntil)}")
            if (p.hasCoinMultiplier()) s.out("{g}active{/} coin multiplier until ${fmtTime(p.coinMultiplierUntil)}")
            if (p.hasStreakShield()) s.out("{g}active{/} streak shield until ${fmtTime(p.streakShieldUntil)}")
            if (prefs.streakFreezes > 0) s.out("streak freezes: {c}${prefs.streakFreezes}{/}")
            if (inv.isEmpty()) s.dim("empty. shop ls")
            inv.forEachIndexed { i, it ->
                val st = if (it.isEquipped) " {g}equipped{/}" else if (it.category == "custom" && it.usedAt == null) " {y}unused{/}" else ""
                s.out("{c}${i + 1}{/} ${it.name.tm()} {d}${it.category.removePrefix("cosmetic_")}{/}$st")
            }
            s.dim("now: ${fmtTime(now)} \u00b7 inv equip|unequip|use <n>")
            return
        }
        val n = a.pos.getOrNull(2)?.toIntOrNull()
        val item = if (n == null) null else inv.getOrNull(n - 1)
        if (item == null) { s.err("usage: inv $sub <n>  (numbers from: inv)"); return }
        when (sub) {
            "equip" -> {
                if (item.category == "powerup" || item.category == "custom") { s.err("only frames, themes and badges can be equipped"); return }
                if (repo.equipItem(item)) s.ok("equipped ${item.name.tm()}") else s.err("not enough coins for today's rent")
            }
            "unequip" -> { repo.unequipItem(item); s.ok("unequipped ${item.name.tm()}") }
            "use" -> {
                if (item.category != "custom" || item.usedAt != null) { s.err("nothing to use there"); return }
                repo.redeemItem(item)
                s.ok("redeemed ${item.name.tm()}")
            }
            else -> s.err("usage: inv ls|equip|unequip|use")
        }
    }

    // ═══════════════ progress ═══════════════
    private suspend fun cmdStats(s: TermuxSession) {
        s.step("reading your numbers\u2026")
        val snap = builder.build()
        s.head("stats")
        if (!snap.usageAccess) s.warn("usage access is off: screen time shows 0 (perms grant usage)")
        s.out("{d}day       study coding neutral  fun  quests focus{/}")
        val f = SimpleDateFormat("EEE d", Locale.getDefault())
        snap.days.take(8).forEachIndexed { i, d ->
            val name = if (i == 0) "today" else if (i == 1) "yesterday" else f.format(Date(d.dayStart))
            s.out("${name.padEnd(9)} ${fmtMin(d.studyMin.toLong()).padEnd(6)} ${fmtMin(d.codingMin.toLong()).padEnd(6)} ${fmtMin(d.neutralMin.toLong()).padEnd(7)} ${fmtMin(d.funMin.toLong()).padEnd(5)} ${d.questsDone.toString().padEnd(6)} ${d.focusMin}m")
        }
        s.out("level {c}${snap.level}{/}  xp ${snap.xp}  streak {c}${snap.streak}{/}  quests done {c}${snap.totalQuestsDone}{/}  focus {c}${snap.totalFocusMin}m{/} (longest ${snap.longestFocusMin}m)")
        val w = snap.thisWeek
        s.out("this week: study ${fmtMin(w.sumOf { it.studyMin }.toLong())}  coding ${fmtMin(w.sumOf { it.codingMin }.toLong())}  fun ${fmtMin(w.sumOf { it.funMin }.toLong())}  focus ${w.sumOf { it.focusMin }}m")
    }

    private suspend fun cmdUsage(s: TermuxSession, a: TermuxArgs) {
        if (!UsageTracker.hasAccess(ctx)) { s.warn("usage access is off: perms grant usage"); return }
        val days = if (a.has("--week")) 7 else 1
        val now = System.currentTimeMillis()
        s.step("reading the usage record\u2026")
        val per = withContext(Dispatchers.IO) {
            val m = HashMap<String, Long>()
            for (i in 0 until days) {
                val from = UsageTracker.dayStart(i)
                val to = if (i == 0) now else from + 86_400_000L
                UsageTracker.foreground(ctx, from, to).perPkg.forEach { (k, v) -> m[k] = (m[k] ?: 0L) + v }
            }
            m
        }
        val pm = ctx.packageManager
        val cats = HashMap<String, Long>()
        per.forEach { (pkg, ms) ->
            val c = AppCategorizer.categoryOf(prefs, pm, pkg)
            cats[c] = (cats[c] ?: 0L) + ms
        }
        s.head(if (days == 1) "usage today" else "usage last 7 days")
        val total = per.values.sum()
        s.out("total {c}${fmtMs(total)}{/}")
        USAGE_CATEGORIES.forEach { c ->
            val v = cats[c.id] ?: 0L
            s.out("${c.short.padEnd(8)} ${fmtMs(v).padEnd(8)} ${termuxBar(if (total > 0) v.toFloat() / total.toFloat() else 0f)}")
        }
        s.out("{b}TOP APPS{/}")
        per.entries.sortedByDescending { it.value }.take(15).forEach { e ->
            if (e.value >= 60_000L) s.out("${labelOfPkg(e.key).tm().padEnd(24).take(24)} {c}${fmtMs(e.value)}{/}")
        }
    }

    private suspend fun cmdWeekly(s: TermuxSession) {
        s.step("building the weekly report\u2026")
        val r = AdvisorProvider.get().weekly(builder.build())
        s.head("weekly report")
        r.lines.forEach { s.out(it.tm()) }
        s.out("best day:        ${r.bestDay.tm()}")
        s.out("weakest spot:    ${r.weakness.tm()}")
        s.out("{g}do this:{/}        ${r.recommendation.tm()}")
        s.dim("source: ${r.source}")
    }

    private fun markFor(p: String?): String? = when (p?.lowercase()) {
        "p", "present" -> "P"; "a", "absent" -> "A"; "l", "leave" -> "L"; "clear", "none" -> ""; else -> null
    }

    private suspend fun cmdAttendance(s: TermuxSession, a: TermuxArgs) {
        if (a.pos.getOrNull(1)?.lowercase() == "mark") {
            val slot = a.pos.getOrNull(2)?.lowercase()
            val mark = markFor(a.pos.getOrNull(3))
            if (slot == null || mark == null) { s.err("usage: attendance mark s|c1|c2 p|a|l|clear"); return }
            val msg = termuxAttendanceSet(ctx, slot, mark) { delta, detail -> viewModelScope.launch { repo.attendanceCoins(delta, detail) } }
            s.ok(msg.tm())
            return
        }
        s.head("attendance")
        termuxAttendanceSummary(ctx).forEach { s.out(it.tm()) }
        s.dim("attendance mark s|c1|c2 p|a|l|clear  (today only; school +5, coaching +3 when present)")
    }

    private suspend fun cmdMarks(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "set" -> {
                val test = a.pos.getOrNull(2)?.lowercase()
                val subj = a.pos.getOrNull(3)?.uppercase()
                val v = a.pos.getOrNull(4)?.toFloatOrNull()
                if (test == null || subj == null || v == null) { s.err("usage: marks set <mt1|qtr|mt2|half|mt3> <P|C|M|H|E> <marks>"); return }
                val err = termuxMarkSet(ctx, test, subj, v)
                if (err != null) s.err(err.tm()) else s.ok("saved $test $subj = ${termuxMarkText(v)}")
            }
            "rm" -> {
                val test = a.pos.getOrNull(2)?.lowercase()
                val subj = a.pos.getOrNull(3)?.uppercase()
                if (test == null || subj == null) { s.err("usage: marks rm <test> <subject>"); return }
                val err = termuxMarkSet(ctx, test, subj, null)
                if (err != null) s.err(err.tm()) else s.ok("removed $test $subj")
            }
            null -> {
                val tests = termuxMarkTests()
                s.head("marks")
                s.out("{d}" + "".padEnd(10) + tests.joinToString("") { it.second.take(8).padEnd(9) } + "{/}")
                termuxMarkSubjects().forEach { (id, name) ->
                    val row = tests.joinToString("") { (tid, _) ->
                        val m = termuxMarkGet(ctx, tid, id)
                        (if (m == null) "-" else termuxMarkText(m.first) + "/" + termuxMarkText(m.second)).padEnd(9)
                    }
                    s.out("{c}$id{/} ${name.tm().take(7).padEnd(8)}$row")
                }
                tests.forEach { (tid, tname) ->
                    var got = 0f; var max = 0f; var n = 0
                    termuxMarkSubjects().forEach { (id, _) -> termuxMarkGet(ctx, tid, id)?.let { got += it.first; max += it.second; n++ } }
                    if (n > 0 && max > 0f) s.out("${tname.padEnd(12)} total ${termuxMarkText(got)}/${termuxMarkText(max)}  {c}${(got * 100f / max).toInt()}%{/}")
                }
                s.dim("marks set <test> <subject> <marks>   (a mark below 33% of its total is a fail)")
            }
            else -> s.err("usage: marks | marks set | marks rm")
        }
    }

    private suspend fun cmdChapters(s: TermuxSession, a: TermuxArgs) {
        val sub = a.pos.getOrNull(1)?.lowercase()
        val all = termuxChapters(ctx)
        if (sub == null) {
            s.head("chapters")
            s.dim("study % is counted out of the chapters school has taught")
            all.groupBy { it.subj }.forEach { (id, l) ->
                val taught = l.count { it.taught }
                val studied = l.count { it.studied && it.taught }
                val rev = l.count { it.revised }
                val copy = l.count { it.copy }
                val frac = if (taught == 0) 0f else studied.toFloat() / taught.toFloat()
                s.out("{c}$id{/} ${l.first().subjName.tm().take(9).padEnd(9)} ${termuxBar(frac)} ${(frac * 100).toInt()}%  taught $taught/${l.size}  studied $studied  revised $rev  copy $copy")
            }
            s.dim("chapters ls <P|C|M|H|E> \u00b7 chapters study|revise|taught|copy <subject> <n>")
            return
        }
        if (sub == "ls") {
            val id = a.pos.getOrNull(2)?.uppercase()
            val l = all.filter { it.subj == id }
            if (id == null || l.isEmpty()) { s.err("usage: chapters ls <P|C|M|H|E>"); return }
            s.head(l.first().subjName)
            s.dim("T taught in school \u00b7 S studied \u00b7 R revised this week \u00b7 C copy complete")
            l.forEach {
                val flags = (if (it.taught) "T" else "-") + (if (it.studied) "S" else "-") + (if (it.revised) "R" else "-") + (if (it.copy) "C" else "-")
                s.out("{c}${it.n.toString().padStart(2)}{/} {y}$flags{/} ${it.title.tm()}")
            }
            return
        }
        if (sub == "study" || sub == "revise" || sub == "taught" || sub == "copy") {
            val id = a.pos.getOrNull(2)
            val n = a.pos.getOrNull(3)?.toIntOrNull()
            if (id == null || n == null) { s.err("usage: chapters $sub <P|C|M|H|E> <n>"); return }
            val msg = termuxChapterToggle(ctx, id, n, sub) { coins, detail -> viewModelScope.launch { repo.grantBonus(coins, 0, "Chapter", detail) } }
            val bad = msg.startsWith("no ") || msg.startsWith("School") || msg.startsWith("Mark it") || msg.startsWith("unknown")
            if (bad) s.err(msg.tm()) else s.ok(msg.tm())
            return
        }
        s.err("usage: chapters | chapters ls <subject> | chapters study|revise|taught|copy <subject> <n>")
    }

    private suspend fun hubWait() {
        s0Wait()
    }

    private suspend fun s0Wait() {
        hub.refresh(force = true)
        var waited = 0
        while (!hub.state.loaded && waited < 8000) { delay(200L); waited += 200 }
        delay(600L)
    }

    private suspend fun cmdBadges(s: TermuxSession) {
        s.step("reading your numbers\u2026")
        hubWait()
        val st = hub.state
        s.head("badges")
        val got = st.badges.count { it.unlocked }
        s.dim("$got of ${st.badges.size} unlocked")
        st.badges.forEach { b ->
            if (b.unlocked) s.out("{g}[x]{/} ${b.name.tm()}  {d}${b.desc.tm()}${hub.unlockedOn(b)?.let { " \u00b7 $it" } ?: ""}{/}")
            else if (b.hidden) s.out("[ ] {d}??? hidden badge{/}")
            else s.out("[ ] ${b.name.tm()}  {d}${b.desc.tm()}{/}")
        }
    }

    private suspend fun cmdGoals(s: TermuxSession, a: TermuxArgs) {
        s.step("reading your numbers\u2026")
        hubWait()
        val st = hub.state
        val goals = st.challenges + st.seasonGoals
        if (a.pos.getOrNull(1)?.lowercase() == "claim") {
            val n = a.pos.getOrNull(2)?.toIntOrNull()
            val g = if (n == null) null else goals.getOrNull(n - 1)
            if (g == null) { s.err("usage: goals claim <n>"); return }
            if (g.key in st.claimed) { s.warn("already claimed"); return }
            if (g.c.progress < g.c.target) { s.warn("not finished yet: ${g.c.progress}/${g.c.target}"); return }
            hub.claim(g)
            s.ok("claimed ${g.c.title.tm()}: +${g.c.coins} coins, +${g.c.xp} xp")
            return
        }
        s.head("goals")
        if (goals.isEmpty()) s.dim("none right now")
        goals.forEachIndexed { i, g ->
            val done = g.c.progress >= g.c.target
            val claimed = g.key in st.claimed
            val state = if (claimed) "{d}claimed{/}" else if (done) "{g}ready: goals claim ${i + 1}{/}" else ""
            s.out("{c}${i + 1}{/} ${g.c.title.tm()}  ${termuxBar(g.c.progress.toFloat() / g.c.target.coerceAtLeast(1).toFloat())} ${g.c.progress}/${g.c.target}  {y}+${g.c.coins}c +${g.c.xp}xp{/} $state")
            s.dim("   ${g.c.desc.tm()}")
        }
    }

    private suspend fun cmdHub(s: TermuxSession) {
        s.step("reading your numbers\u2026")
        hubWait()
        val st = hub.state
        s.head("hub")
        s.out("rank {c}${st.rank.tm()}{/}  points ${st.points}" + (st.toNext?.let { "  ($it to the next rank)" } ?: ""))
        s.out("discipline score {c}${st.score}{/}/100 ${termuxBar(st.score / 100f)}")
        st.attrs.forEach { at -> s.out("${at.emoji} ${at.name.tm().padEnd(12)} lv {c}${at.level}{/} ${termuxBar(at.into.toFloat() / at.per.coerceAtLeast(1).toFloat())}") }
        st.season?.let { se -> s.out("season ${se.number} ${se.name.tm()}: day ${se.dayIn}/${se.length}  season xp ${st.seasonXp}") }
        st.economy.forEach { s.dim(it.tm()) }
        s.dim("badges \u00b7 goals \u00b7 weekly \u00b7 limits \u00b7 sites ls")
    }

    // ═══════════════ settings ═══════════════
    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private val setKeys: List<SetKey> by lazy {
        fun intKey(key: String, desc: String, range: IntRange, get: () -> Int, set: (Int) -> Unit) =
            SetKey(key, desc, { get().toString() }) { v ->
                val n = v.toIntOrNull()
                if (n == null || n !in range) "$key: a number from ${range.first} to ${range.last}" else { set(n); null }
            }
        fun boolKey(key: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) =
            SetKey(key, desc, { if (get()) "on" else "off" }) { v ->
                val b = parseBool(v)
                if (b == null) "$key: on or off" else { set(b); null }
            }
        fun timeKey(key: String, desc: String, get: () -> Int, set: (Int) -> Unit) =
            SetKey(key, desc, { hm(get()) }) { v ->
                val m = parseHm(v)
                if (m == null) "$key: a time like 22:30" else { set(m); null }
            }
        fun overlayKey(key: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) =
            SetKey(key, desc, { if (get()) "on" else "off" }) { v ->
                val b = parseBool(v)
                if (b == null) "$key: on or off"
                else if (b && !Perms.overlayGranted(ctx)) "$key needs Draw over other apps: perms grant overlay"
                else { set(b); null }
            }
        listOf(
            intKey("rate", "unlock cost, coins per 5 min", 1..100, { prefs.ratePer5 }, { prefs.ratePer5 = it }),
            intKey("focus_bonus", "coins for a finished Focus session", 0..100, { prefs.focusBonus }, { prefs.focusBonus = it }),
            intKey("study_min", "study minutes per coin", 1..30, { prefs.studyMinPerCoin }, { prefs.studyMinPerCoin = it }),
            boolKey("login", "daily login bonus", { prefs.dailyLoginEnabled }, { prefs.dailyLoginEnabled = it }),
            intKey("login_coins", "daily login coins", 1..50, { prefs.dailyLoginCoins }, { prefs.dailyLoginCoins = it }),
            intKey("hold", "hold-to-unlock seconds", 1..10, { prefs.holdSeconds }, { prefs.holdSeconds = it }),
            boolKey("quotes", "motivational lines on lock pages", { prefs.showQuotes }, { prefs.showQuotes = it }),
            boolKey("reminders", "quest reminders", { prefs.remindersEnabled }, { on -> prefs.remindersEnabled = on; if (on) viewModelScope.launch { repo.rescheduleAllReminders() } }),
            intKey("lead", "remind me N minutes before", 0..180, { prefs.reminderLeadMin }, { prefs.reminderLeadMin = it }),
            boolKey("cultivation", "cultivation realms", { prefs.cultivationOn }, { prefs.cultivationOn = it; QuestWidget.refresh(ctx) }),
            intKey("fun_limit", "fun minutes per day (0 = off)", 0..360, { prefs.funLimitMin }, { prefs.funLimitMin = it }),
            intKey("weekend_limit", "fun minutes on Sat/Sun (0 = same)", 0..360, { prefs.weekendFunLimitMin }, { prefs.weekendFunLimitMin = it }),
            boolKey("exam", "exam mode", { prefs.examMode }, { prefs.examMode = it }),
            boolKey("quiet", "quiet hours lock fun apps", { prefs.quietEnabled }, { prefs.quietEnabled = it }),
            timeKey("quiet_from", "quiet hours start", { prefs.quietStartMin }, { prefs.quietStartMin = it }),
            timeKey("quiet_until", "quiet hours end", { prefs.quietEndMin }, { prefs.quietEndMin = it }),
            boolKey("study_block", "study hours lock fun apps", { prefs.studyBlockEnabled }, { prefs.studyBlockEnabled = it }),
            timeKey("study_from", "study hours start", { prefs.studyStartMin }, { prefs.studyStartMin = it }),
            timeKey("study_until", "study hours end", { prefs.studyEndMin }, { prefs.studyEndMin = it }),
            boolKey("strict", "strict mode", { prefs.strictMode }, { prefs.strictMode = it }),
            intKey("penalty", "coins lost for giving up a session", 0..100, { prefs.focusExitPenalty }, { prefs.focusExitPenalty = it }),
            boolKey("routine_first", "fun costs +50% while the routine is open", { prefs.routineFirst }, { prefs.routineFirst = it }),
            boolKey("shorts_block", "block Shorts when fun is used up", { prefs.shortsBlockOn }, { prefs.changeShortsBlock(it) }),
            boolKey("yt_gate", "YouTube study gate", { prefs.ytGateOn }, { prefs.changeYtGate(it) }),
            boolKey("yt_info", "online video check", { prefs.ytInfoOn }, { prefs.changeYtInfo(it) }),
            boolKey("ghost", "music from fun apps counts as fun time", { prefs.ghostCountsOn }, { prefs.changeGhostCounts(it) }),
            boolKey("music_pause", "pause fun music at the limit", { prefs.pauseMusicAtLimit }, { prefs.changePauseMusic(it) }),
            overlayKey("counter", "Shorts/Reels counter over the app", { prefs.optCounter }, { prefs.optCounter = it }),
            boolKey("warn80", "warn at 80% of fun time", { prefs.optWarn80 }, { prefs.optWarn80 = it }),
            overlayKey("bubble", "time-left bubble", { prefs.optBubble }, { prefs.optBubble = it }),
            boolKey("bedtime", "bedtime nudge", { prefs.optBedtime }, { prefs.optBedtime = it }),
            boolKey("music_focus", "pause music when Focus starts", { prefs.optMusicOnFocus }, { prefs.optMusicOnFocus = it }),
            boolKey("music_warn", "warn about fun music", { prefs.optMusicWarn }, { prefs.optMusicWarn = it }),
            boolKey("focus_awake", "keep the screen on in Focus", { prefs.optFocusAwake }, { prefs.optFocusAwake = it }),
            boolKey("break_vibrate", "vibrate when a break ends", { prefs.optBreakVibrate }, { prefs.optBreakVibrate = it }),
            boolKey("focus_notif", "Focus notification", { prefs.optFocusNotif }, { prefs.optFocusNotif = it }),
            boolKey("evening", "evening check-in", { prefs.optEvening }, { prefs.optEvening = it }),
            intKey("evening_hour", "evening check-in hour", 15..23, { prefs.optEveningHour }, { prefs.optEveningHour = it }),
            boolKey("confirm", "ask before skipping a step", { prefs.optConfirm }, { prefs.optConfirm = it }),
            boolKey("undo", "offer undo after a quest", { prefs.optUndo }, { prefs.optUndo = it }),
            boolKey("hide_coins", "hide coins in notifications", { prefs.optHideCoins }, { prefs.optHideCoins = it }),
            boolKey("widget_done", "show done quests on the widget", { prefs.optWidgetDone }, { prefs.optWidgetDone = it }),
            boolKey("summary", "daily summary", { prefs.optSummary }, { prefs.optSummary = it }),
            boolKey("gray", "grayscale look", { prefs.optGray }, { prefs.optGray = it; GrayMode = it }),
            boolKey("sfx", "sound effects", { Sounds.sfxOn }, { Sounds.sfxOn = it }),
            boolKey("keepalive", "keep-alive notification", { prefs.keepAlive }, { prefs.keepAlive = it }),
            boolKey("auto_verify", "auto-verify quests from usage", { prefs.autoVerify }, { prefs.autoVerify = it }),
            SetKey("accent", "accent colour: " + ACCENTS.joinToString(" ") { it.first.lowercase() },
                { ACCENTS.firstOrNull { it.second == prefs.accentArgb }?.first?.lowercase() ?: "custom" }) { v ->
                val c = ACCENTS.firstOrNull { it.first.equals(v, ignoreCase = true) }
                if (c == null) "accent: ${ACCENTS.joinToString(" ") { it.first.lowercase() }}" else { prefs.accentArgb = c.second; ColorPrimary = androidx.compose.ui.graphics.Color(c.second); null }
            },
            SetKey("mode", "default look: dark light system", { listOf("dark", "light", "system").getOrElse(prefs.themeMode) { "system" } }) { v ->
                val m = listOf("dark", "light", "system").indexOf(v.lowercase())
                if (m < 0) "mode: dark, light or system" else {
                    prefs.themeMode = m
                    val sysDark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    applyThemeMode(m, sysDark)
                    null
                }
            },
        )
    }

    private suspend fun cmdSet(s: TermuxSession, a: TermuxArgs) {
        val key = a.pos.getOrNull(1)?.lowercase()
        if (key == null || key == "ls") {
            val p = repo.player.first()
            s.head("settings")
            s.out("{c}name{/} = ${p.name.ifBlank { "-" }.tm()}   {c}goal{/} = ${p.goal.ifBlank { "-" }.tm()}   {c}dob{/} = ${p.dob.ifBlank { "-" }.tm()}")
            setKeys.forEach { s.out("{c}${it.key.padEnd(13)}{/} ${it.get().padEnd(7)} {d}${it.desc.tm()}{/}") }
            val pend = prefs.pendingSummary()
            if (pend.isNotEmpty()) s.warn(pend)
            s.dim("set <key> <value> \u00b7 more: strict, dnd, perms, backup, restore, reliability, selftest, debuglog, versions, about, ideas")
            return
        }
        val value = a.rest(2).trim()
        if (key == "name" || key == "goal" || key == "dob") {
            val p = repo.player.first()
            repo.saveProfile(
                if (key == "name") value else p.name,
                if (key == "goal") value else p.goal,
                if (key == "dob") value else p.dob,
            )
            s.ok("$key = ${value.tm()}")
            return
        }
        val k = setKeys.firstOrNull { it.key == key }
        if (k == null) { s.err("no setting '${key.tm()}'. see: set ls"); return }
        if (value.isEmpty()) { s.out("{c}${k.key}{/} = ${k.get()}   {d}${k.desc.tm()}{/}"); return }
        val err = k.set(value)
        if (err != null) { s.err(err.tm()); return }
        s.ok("${k.key} = ${k.get()}")
        val pend = prefs.pendingSummary()
        if (pend.isNotEmpty()) s.warn(pend)
    }

    private suspend fun cmdStrict(s: TermuxSession, a: TermuxArgs) {
        val on = a.pos.getOrNull(1)?.let { parseBool(it) }
        if (on == null) { s.out("strict mode: ${onOff(prefs.strictMode)}   (strict on|off)"); return }
        prefs.strictMode = on
        s.ok("strict mode ${onOff(prefs.strictMode)}")
        val pend = prefs.pendingSummary()
        if (pend.isNotEmpty()) s.warn(pend)
    }

    private fun cmdDnd(s: TermuxSession) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val granted = nm?.isNotificationPolicyAccessGranted == true
        s.out("Do Not Disturb access: ${if (granted) "{g}allowed{/}" else "{y}not allowed{/}"}")
        s.dim("Focus turns Priority-only Do Not Disturb on for every session when this is allowed.")
        if (!granted) s.dim("perms grant dnd opens Android's page for it")
    }

    private fun permLine(s: TermuxSession, name: String, on: Boolean, how: String) {
        s.out("${name.padEnd(20)} ${if (on) "{g}on{/}" else "{y}off{/}"}   {d}${if (on) "" else how}{/}")
    }

    private fun cmdPerms(s: TermuxSession, a: TermuxArgs) {
        if (a.pos.getOrNull(1)?.lowercase() == "grant") {
            val name = a.pos.getOrNull(2)?.lowercase()
            val intent: Intent? = when (name) {
                "acc" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                "access" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                "admin" -> Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, Perms.adminComponent(ctx))
                else -> null
            }
            if (intent == null) { s.err("usage: perms grant acc|usage|overlay|battery|notif|access|dnd|admin"); return }
            runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onSuccess { s.step("opening Android's own page. come back here when done") }
                .onFailure { s.err("could not open that page") }
            return
        }
        s.head("permissions")
        permLine(s, "accessibility", Perms.accessibilityEnabled(ctx), "perms grant acc   (blocking needs it)")
        permLine(s, "usage access", UsageTracker.hasAccess(ctx), "perms grant usage   (study coins, fun limit)")
        permLine(s, "draw over apps", Perms.overlayGranted(ctx), "perms grant overlay")
        permLine(s, "battery unrestricted", Perms.batteryExempt(ctx), "perms grant battery")
        permLine(s, "notifications", Perms.notificationsOk(ctx), "perms grant notif")
        permLine(s, "notification access", Perms.notificationAccess(ctx), "perms grant access   (music detection)")
        permLine(s, "device admin", Perms.adminActive(ctx), "perms grant admin")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        permLine(s, "do not disturb", nm?.isNotificationPolicyAccessGranted == true, "perms grant dnd")
        s.dim("these pages belong to Android: they open normally, then you return to the terminal")
    }

    private suspend fun cmdBackup(s: TermuxSession) {
        s.step("saving an encrypted copy\u2026")
        val ok = withContext(Dispatchers.IO) { Backup.autoBackup(ctx, db) }
        if (ok) s.ok("saved to Downloads/QuestBlock (encrypted .qbak, the newest 7 are kept)")
        else s.warn("no new file: today's copy is already there, or the phone refused it")
    }

    private var restoreSession: TermuxSession? = null

    private fun cmdRestore(s: TermuxSession) {
        restoreSession = s
        s.dim("restore keeps your current coins (never doubles or lowers them) and saves a safety copy to Downloads/QuestBlock first")
        confirm(s, "restore from a .qbak file? you pick it in Android's file picker, and the app restarts") {
            restorePick = true
        }
    }

    /** Called by the shell when the file picker came back. */
    fun onRestorePicked(uri: Uri?) {
        val s = restoreSession ?: sessionList.first()
        if (uri == null) { s.dim("restore cancelled"); return }
        viewModelScope.launch {
            guarded(s) {
                s.step("restoring\u2026")
                val ok = withContext(Dispatchers.IO) { Backup.import(ctx, db, uri) }
                if (ok) {
                    s.ok("restored. restarting\u2026")
                    delay(600L)
                    Backup.restart(ctx)
                } else {
                    s.err("not a valid encrypted backup (.qbak)")
                }
            }
        }
    }

    fun needsSetup(): Boolean = !prefs.onboardingDone

    private suspend fun cmdReliability(s: TermuxSession) {
        s.head("reliability")
        val problems = Health.problems(ctx, prefs)
        if (problems.isEmpty()) s.ok("everything that keeps blocking alive is on")
        problems.forEach { p ->
            val fix = when (p.id) { "acc" -> "perms grant acc"; "usage" -> "perms grant usage"; "battery" -> "perms grant battery"; else -> "perms" }
            s.warn("${p.title.tm()}: ${p.text.tm()}  {c}$fix{/}")
        }
        s.out("keep-alive: ${onOff(prefs.keepAlive)}   strict mode: ${onOff(prefs.strictMode)}")
        s.dim("full list: perms")
    }

    private suspend fun cmdSelfTest(s: TermuxSession) {
        s.step("running the self test\u2026")
        val st = SelfTestViewModel(ctx, prefs, repo)
        st.run()
        var waited = 0
        while (st.running && waited < 45_000) { delay(300L); waited += 300 }
        s.head("self test")
        var ok = 0
        st.results.forEach { r ->
            val icon = when (r.status) { TestStatus.OK -> { ok++; "{g}\u2713{/}" }; TestStatus.WARN -> "{y}!{/}"; TestStatus.FAIL -> "{r}\u2717{/}" }
            s.out("$icon ${r.group.tm()} / ${r.name.tm()}: ${r.detail.tm()}")
        }
        s.out("{b}$ok/${st.results.size} passed{/}")
    }

    private fun cmdDebugLog(s: TermuxSession, a: TermuxArgs) {
        if (a.pos.getOrNull(1)?.lowercase() == "clear") { DebugLog.clear(); s.ok("debug log cleared"); return }
        val l = DebugLog.read().trim()
        if (l.isEmpty()) { s.dim("(empty: try an unlock, then run debuglog again)"); return }
        s.head("debug log")
        l.split("\n").takeLast(80).forEach { s.out("{d}${it.tm()}{/}") }
    }

    private fun cmdVersions(s: TermuxSession, a: TermuxArgs) {
        val q = a.pos.getOrNull(1)
        if (q != null) {
            val e = VERSION_LOG.firstOrNull { it.version == q || it.version.startsWith("$q.") }
            if (e == null) { s.err("no version $q"); return }
            s.head("v${e.version}")
            s.out("{b}${e.headline.tm()}{/}")
            e.items.forEach { s.out("{c}\u203a{/} ${it.tm()}") }
            return
        }
        s.head("versions")
        VERSION_LOG.take(12).forEach { s.out("{c}v${it.version}{/} ${it.headline.tm()}") }
        s.dim("versions <number> for the full notes of one version")
    }

    private fun cmdAbout(s: TermuxSession, a: TermuxArgs) {
        if (a.pos.getOrNull(1)?.lowercase() == "open") {
            val n = a.pos.getOrNull(2)?.toIntOrNull()
            val l = if (n == null) null else AboutMe.LINKS.getOrNull(n - 1)
            if (l == null) { s.err("usage: about open <n>"); return }
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { s.err("could not open the link") }
            return
        }
        s.head(AboutMe.NAME)
        s.out(AboutMe.TAGLINE.tm())
        s.out("")
        s.out(AboutMe.BIO.tm())
        s.out("")
        s.out("{b}WHY{/}")
        s.out(AboutMe.WHY.tm())
        s.out("")
        s.out("{b}OTHER PROJECTS{/}")
        s.out(AboutMe.OTHER_PROJECTS.tm())
        s.out("{b}TOOLS{/} ${AboutMe.TOOLS.tm()}")
        AboutMe.LINKS.forEachIndexed { i, l -> s.out("{c}${i + 1}{/} ${l.label.tm()}  {d}${l.shown.tm()}{/}") }
        s.dim("about open <n>")
    }

    private fun cmdIdeas(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            "add" -> {
                val t = a.rest(2).trim()
                if (t.isEmpty()) { s.err("usage: ideas add <text>"); return }
                IdeaStore.add(ctx, t)
                s.ok("noted")
            }
            "done" -> {
                val n = a.pos.getOrNull(2)?.toIntOrNull()
                if (n == null || n < 1 || n > IdeaStore.all(ctx).size) { s.err("usage: ideas done <n>"); return }
                IdeaStore.toggle(ctx, n - 1)
                s.ok("toggled #$n")
            }
            "rm" -> {
                val n = a.pos.getOrNull(2)?.toIntOrNull()
                if (n == null || n < 1 || n > IdeaStore.all(ctx).size) { s.err("usage: ideas rm <n>"); return }
                IdeaStore.delete(ctx, n - 1)
                s.ok("removed #$n")
            }
            "clear" -> { IdeaStore.clearDone(ctx); s.ok("done ideas cleared") }
            null -> {
                val l = IdeaStore.all(ctx)
                s.head("ideas")
                if (l.isEmpty()) s.dim("none. ideas add <text>")
                l.forEachIndexed { i, it -> s.out("{c}${i + 1}{/} ${if (it.done) "{g}[x]{/}" else "[ ]"} ${it.text.tm()}") }
            }
            else -> s.err("usage: ideas | add | done | rm | clear")
        }
    }

    // ═══════════════ lock pages ═══════════════
    private val lockLines = listOf(
        "The reel will wait. Your rank won't climb itself.",
        "Scrolling gives zero XP. Quests give real XP.",
        "Close it. Level up instead.",
        "Five focused minutes beat fifty scrolled ones.",
        "Tiny discipline today, big flex tomorrow.",
    )

    private suspend fun showBlockPage(s: TermuxSession, b: TermuxBlockCtx, kind: String) {
        val pkg = b.pkg
        val p = repo.player.first()
        if (pkg.startsWith("ytstudy:")) {
            val ch = pkg.removePrefix("ytstudy:")
            s.err("Permission denied: video  {d}(study first){/}")
            s.step("chapter not studied yet: {y}${ch.tm()}{/}")
            s.dim("study it, tick it with  chapters study <subject> <n>  (run main), and its videos open free.")
            s.dim("home = back to YouTube home \u00b7 main = open QuestBlock")
            return
        }
        if (pkg.startsWith("focuslock:")) {
            val label = labelOfPkg(pkg.removePrefix("focuslock:"))
            s.err("Permission denied: {c}${label.tm()}{/}  {d}(focus){/}")
            s.step("Focus is on: this app was not picked for this session")
            s.dim("finish the session to use it, or run main to see the timer. home = go home")
            return
        }
        if (b.funLimit) {
            val label = labelOfPkg(pkg)
            val st = FunLimit.last
            s.err("Permission denied: {c}${label.tm()}{/}  {d}(${if (FunSignal.quiet) "fun apps are locked" else "fun limit reached"}){/}")
            if (FunSignal.quiet) s.step("quiet hours, study hours or exam mode are on. fun apps open again when they end")
            else if (st != null && st.enabled) s.step("you've had ${fmtMs(st.usedMs)} of ${fmtMs(st.limitMs)} fun time today. it resets at midnight")
            else s.step("you've used today's fun time. it resets at midnight")
            s.dim("quests = do one instead \u00b7 home = go home")
            return
        }
        val label = labelOfPkg(pkg)
        val pseudo = pkg.startsWith("site:") || pkg.startsWith("short:") || pkg.startsWith("yt:")
        val until = blockUnlockedUntil(pkg)
        val now = System.currentTimeMillis()
        s.err("Permission denied: {c}${label.tm()}{/}  {d}(locked){/}")
        if (until > now) {
            s.ok("unlocked: {c}${fmtLeft(until - now)}{/} left")
            s.step("run {c}open{/} to open it, or {c}unlock{/} to add time")
            return
        }
        val rule = if (pseudo) null else repo.getBlockedApp(pkg)
        val rate = if (pkg == "yt:fun") YT_FUN_RATE_PER_5 else repo.ratePer5()
        val pct = repo.routineSurchargePct()
        val per5 = withSurcharge(unlockCost(1, rate, rule), pct)
        s.step("unlock costs {c}$per5{/} coins per 5 min   balance {c}${p.coins}{/}")
        if (pct > 0) s.warn("routine first: +$pct% until today's routine is done")
        if (!pseudo && rule == null) s.warn("${label.tm()} isn't in your blocked list (Vault)")
        if (prefs.showQuotes) s.dim("\"${lockLines.random()}\"")
        s.dim("unlock 15  (asks you to type the app name) \u00b7 quests \u00b7 home")
    }
}

/** The whole app, as one terminal. */
@androidx.compose.runtime.Composable
fun TermuxShell(
    initialRoute: String? = null,
    onRouteHandled: () -> Unit = {},
    vm: TermuxViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pre by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect2(initialRoute) {
        val r = initialRoute
        if (r != null) {
            pre = if (TermuxMode.strictNow(ctx)) "focus status" else termuxCommandForRoute(r)
            onRouteHandled()
        } else if (vm.needsSetup()) {
            pre = "setup"
        }
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> vm.onRestorePicked(uri) }
    LaunchedEffect2(vm.restorePick) {
        if (vm.restorePick) { vm.restorePick = false; picker.launch(arrayOf("*/*")) }
    }
    var strict by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(TermuxMode.strictNow(ctx)) }
    LaunchedEffect2(Unit) {
        while (true) { strict = TermuxMode.strictNow(ctx); delay(1000L) }
    }
    androidx.activity.compose.BackHandler(enabled = strict) { }
    TermuxTerminal(host = vm, preTyped = pre, onPreTypedUsed = { pre = "" })
}

@androidx.compose.runtime.Composable
private fun LaunchedEffect2(key: Any?, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
    androidx.compose.runtime.LaunchedEffect(key, block)
