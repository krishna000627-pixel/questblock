package com.krishna.questblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.krishna.questblock.data.*
import com.krishna.questblock.engine.Cultivation
import com.krishna.questblock.util.DebugLog
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v208: the pages that had no command in v207, as terminal commands: unlock timers, poster, suggested quests,
 * achievements, leaderboard, mood, attendance month and past days, buy fun minutes, weekly category lock,
 * copying ideas, and the home notices (login bonus, theme rent, cultivation realm).
 */
class TermuxExtras(
    private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
    private val pickApp: suspend (TermuxSession, String) -> InstalledApp?,
    private val confirm: (TermuxSession, String, suspend () -> Unit) -> Unit,
) {
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    private fun keyFor(c: Calendar) = String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    private fun daysAgo(n: Int): Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }

    // ── unlock timers ────────────────────────────────────────────────────
    suspend fun timers(s: TermuxSession) {
        val now = System.currentTimeMillis()
        s.head("unlock timers")
        var n = 0
        repo.blockedApps.first().filter { !it.isStudyApp }.forEach { a ->
            val away = prefs.awayAt(a.packageName)
            val paused = a.frozenAt(away, now)
            if (a.unlockedUntil > now || paused) {
                n++
                s.out("${a.label.tm().padEnd(20).take(20)} {c}${fmtLeft(a.leftMs(away, now))}{/}  ${if (paused) "{y}paused, saved while you are away{/}" else "{g}running{/}"}")
            }
        }
        prefs.unlockKeys().forEach { k ->
            val until = prefs.siteUnlockedUntil(k)
            if (until > now) {
                val away = if (k.startsWith("short:")) prefs.awayAt(k) else 0L
                val paused = away > 0L && until > away && dayNumber(away) == dayNumber(now)
                val left = if (paused) until - away else until - now
                n++
                s.out("${(pseudoLabel(k) ?: k).tm().padEnd(20).take(20)} {c}${fmtLeft(left)}{/}  ${if (paused) "{y}paused, saved while you are away{/}" else "{g}running{/}"}")
            }
        }
        if (n == 0) s.dim("nothing is unlocked and no time is saved")
    }

    // ── poster ───────────────────────────────────────────────────────────
    /** Returns true when the caller must open the system gallery picker. */
    fun poster(s: TermuxSession, a: TermuxArgs): Boolean {
        val has = java.io.File(ctx.filesDir, "poster_user.jpg").exists()
        val sub = a.pos.getOrNull(1)?.lowercase()
        fun onOff(b: Boolean) = if (b) "{g}on{/}" else "{d}off{/}"
        when (sub) {
            null, "ls" -> {
                s.head("poster background")
                s.out("picture   ${if (has) "{g}set{/}" else "{d}none{/}"}")
                s.out("show      ${onOff(prefs.posterOn)}")
                s.out("tint      ${onOff(prefs.posterTint)}")
                s.out("darkness  {c}${prefs.posterDim}{/}%")
                s.dim("poster pick | on | off | tint on|off | dim 20-90")
                s.dim("it is drawn behind the normal look. the terminal look is plain black, so it shows after: theme set default")
            }
            "pick" -> { s.step("opening Android's gallery picker. come back here when done"); return true }
            "on", "off" -> {
                if (!has) { s.err("no picture yet: poster pick"); return false }
                val on = sub == "on"
                prefs.posterOn = on; PosterOn = on; PosterStamp++
                s.ok("poster ${if (on) "on" else "off"}")
            }
            "tint" -> {
                val on = a.pos.getOrNull(2)?.lowercase()
                if (on != "on" && on != "off") { s.err("usage: poster tint on|off"); return false }
                prefs.posterTint = on == "on"; PosterStamp++
                s.ok("tint $on")
            }
            "dim" -> {
                val n = a.pos.getOrNull(2)?.toIntOrNull()
                if (n == null || n !in 20..90) { s.err("usage: poster dim 20-90"); return false }
                prefs.posterDim = n; PosterStamp++
                s.ok("darkness $n%")
            }
            else -> s.err("usage: poster | pick | on | off | tint on|off | dim 20-90")
        }
        return false
    }

    // ── suggested quests ─────────────────────────────────────────────────
    private fun suggestions(): List<StarterQuest> = chapterSuggestions(ctx) + STARTER_QUESTS

    suspend fun suggest(s: TermuxSession, a: TermuxArgs) {
        val list = suggestions()
        if (a.pos.getOrNull(1)?.lowercase() == "add") {
            val n = a.pos.getOrNull(2)?.toIntOrNull()
            if (n == null || n < 1 || n > list.size) { s.err("usage: suggest add <n>  (see: suggest)"); return }
            val q = list[n - 1]
            val due = nextRunDayStart(0, System.currentTimeMillis()) - 1 + (if (a.has("--tomorrow")) 86_400_000L else 0L)
            val id = repo.addTask(QuestDraft(title = q.title, difficulty = q.difficulty, category = "Study", notes = q.note, dueAt = due))
            s.ok("quest {c}#$id{/} added: ${q.title.tm()}")
            return
        }
        val subject = a.pos.getOrNull(1)?.lowercase()
        s.head("suggested quests")
        var shown = 0
        list.forEachIndexed { i, q ->
            if (subject == null || q.subject.lowercase().startsWith(subject)) {
                s.out("{c}${i + 1}{/} ${q.title.tm()}  {d}${q.subject} \u00b7 ${q.note.tm()}{/}")
                shown++
            }
        }
        if (shown == 0) s.dim("none for that subject (Physics, Chemistry, Maths, Boards)")
        s.dim("suggest [subject] | suggest add <n> [--tomorrow]   (added as a Study quest due today)")
    }

    // ── achievements and leaderboard ─────────────────────────────────────
    suspend fun achievements(s: TermuxSession) {
        val got = repo.achievements.first()
        val f = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        s.head("achievements")
        var n = 0
        ACHIEVEMENTS_CATALOG.forEach { c ->
            val at = got.firstOrNull { it.id == c.id }?.unlockedAt
            if (at != null) n++
            s.out("${if (at != null) "{g}[x]{/}" else "[ ]"} ${c.name.padEnd(13)} {d}${c.description.tm()}{/}${if (at != null) "  {c}${f.format(Date(at))}{/}" else ""}")
        }
        s.dim("$n of ${ACHIEVEMENTS_CATALOG.size} unlocked. hub badges: badges")
    }

    suspend fun leaderboard(s: TermuxSession) {
        val p = repo.player.first()
        val today = dayNumber(System.currentTimeMillis())
        // XP of the last 7 days from the player's weekly log (day:xp,day:xp)
        val yourXp = p.weeklyXpLog.split(",").filter { it.contains(":") }.sumOf {
            val parts = it.split(":")
            val d = parts[0].toLongOrNull() ?: -1L
            if (d in (today - 6)..today) (parts.getOrNull(1)?.toIntOrNull() ?: 0) else 0
        }
        val you = p.name.ifBlank { "You" }
        val rows = termuxLeaderboardRows(you, yourXp)
        val me = rows.indexOfFirst { it.third }
        val half = if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 12) 1 else 0
        s.head("leaderboard")
        s.out("you are {c}#${me + 1}{/} of ${rows.size}   {c}$yourXp{/} xp this week")
        if (me > 0) s.dim("${rows[me - 1].second - yourXp + 1} xp to pass ${rows[me - 1].first.tm()}")
        else if (rows.size > 1) s.dim("you lead by ${yourXp - rows[1].second} xp")
        val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        rows.take(10).forEachIndexed { i, r ->
            s.out("${"${i + 1}".padStart(2)} ${termuxBar(r.second.toFloat() / max, 10)} ${r.first.tm().padEnd(16).take(16)} {c}${r.second}{/}${if (r.third) "  {g}< you{/}" else ""}")
        }
        if (me >= 10) { s.dim("..."); s.out("${"${me + 1}".padStart(2)} ${termuxBar(rows[me].second.toFloat() / max, 10)} ${rows[me].first.tm().padEnd(16).take(16)} {c}${rows[me].second}{/}  {g}< you{/}") }
        s.dim("80 simulated players. the board changes at 12:00 and midnight (next: ${if (half == 0) "12:00" else "midnight"})")
    }

    // ── mood ─────────────────────────────────────────────────────────────
    fun mood(s: TermuxSession, a: TermuxArgs) {
        val faces = listOf(":(", ":/", ":|", ":)", ":D")
        val first = a.pos.getOrNull(1)?.lowercase()
        if (first == "clear") {
            val back = a.pos.getOrNull(2)?.toIntOrNull() ?: 0
            sp.edit().remove("mood_${keyFor(daysAgo(back))}").apply()
            s.ok("mood cleared")
        } else if (first != null) {
            val n = first.toIntOrNull()
            val back = a.pos.getOrNull(2)?.toIntOrNull() ?: 0
            if (n == null || n !in 1..5 || back !in 0..60) { s.err("usage: mood 1-5 [days ago]  |  mood clear [days ago]"); return }
            sp.edit().putInt("mood_${keyFor(daysAgo(back))}", n).apply()
            s.ok("mood ${faces[n - 1]} saved for ${if (back == 0) "today" else "$back day${if (back > 1) "s" else ""} ago"}")
        }
        s.head("mood")
        val f = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        for (i in 13 downTo 0) {
            val c = daysAgo(i)
            val v = sp.getInt("mood_${keyFor(c)}", 0)
            s.out("${f.format(c.time)}  ${if (v == 0) "{d}-----  not set{/}" else "${termuxBar(v / 5f, 5)} {c}$v{/} ${faces[v - 1]}"}")
        }
        s.dim("mood 1-5 [days ago] | mood clear [days ago]   (1 = low, 5 = great)")
    }

    // ── attendance: month and any past day (read only; past days are closed) ──
    private fun isSunday(day: String): Boolean = Calendar.getInstance().apply {
        set(day.substring(0, 4).toInt(), day.substring(4, 6).toInt() - 1, day.substring(6, 8).toInt())
    }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    private fun mark(day: String, what: String): String {
        val v = sp.getString("att_${day}_$what", "") ?: ""
        val e = if (v.isEmpty() && what == "s" && isSunday(day)) "L" else v
        return when (e) {
            "P" -> "{g}P{/}"
            "A" -> "{r}A{/}"
            "L" -> "{y}L{/}"
            else -> "{d}-{/}"
        }
    }

    fun attendanceMonth(s: TermuxSession, a: TermuxArgs) {
        val back = a.pos.getOrNull(2)?.removePrefix("-")?.toIntOrNull() ?: 0
        val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
        val last = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = keyFor(Calendar.getInstance())
        s.head("attendance ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)}")
        s.dim("day        school  coach1  coach2")
        val dow = SimpleDateFormat("EEE", Locale.getDefault())
        for (d in 1..last) {
            val cal = (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) }
            val key = keyFor(cal)
            val todayTag = if (key == today) "  {c}< today{/}" else ""
            s.out("${"$d".padStart(2)} ${dow.format(cal.time)}      ${mark(key, "s")}       ${mark(key, "c1")}       ${mark(key, "c2")}$todayTag")
        }
        s.dim("P present  A absent  L leave  - not set.  attendance month [back] | attendance day <n days ago|yyyymmdd>")
    }

    fun attendanceDay(s: TermuxSession, a: TermuxArgs) {
        val arg = a.pos.getOrNull(2)
        val key = when {
            arg == null -> keyFor(Calendar.getInstance())
            arg.length == 8 && arg.toIntOrNull() != null -> arg
            arg.toIntOrNull() != null -> keyFor(daysAgo(arg.toInt().coerceIn(0, 400)))
            else -> { s.err("usage: attendance day <days ago|yyyymmdd>"); return }
        }
        s.head("attendance ${key.substring(6, 8)}/${key.substring(4, 6)}/${key.substring(0, 4)}")
        s.out("school     ${mark(key, "s")}")
        s.out("coaching 1 ${mark(key, "c1")}")
        s.out("coaching 2 ${mark(key, "c2")}")
        s.dim(if (key < keyFor(Calendar.getInstance())) "past days are closed: no changes" else "change today: attendance mark s|c1|c2 p|a|l|clear")
    }

    // ── buy fun minutes ──────────────────────────────────────────────────
    suspend fun funBuy(s: TermuxSession, a: TermuxArgs) {
        val p = repo.player.first()
        if (a.pos.getOrNull(1)?.lowercase() != "buy") {
            s.head("fun time")
            s.out("buy +{c}$FUN_EXTEND_MIN{/} fun minutes for {c}$FUN_EXTEND_COST{/} coins   (you have {c}${p.coins}{/})")
            s.dim("fun buy")
            return
        }
        if (p.coins < FUN_EXTEND_COST) { s.err("not enough coins: need $FUN_EXTEND_COST, you have ${p.coins}"); return }
        confirm(s, "Buy +$FUN_EXTEND_MIN fun minutes for $FUN_EXTEND_COST coins?") {
            if (repo.buyFunExtension()) s.ok("+$FUN_EXTEND_MIN fun minutes added for today (-$FUN_EXTEND_COST coins)")
            else s.err("not enough coins")
        }
    }

    // ── weekly category lock ─────────────────────────────────────────────
    private fun parseStart(text: String?): Long? {
        val t = text?.lowercase() ?: "now"
        if (t == "now") return System.currentTimeMillis()
        if (t == "midnight") return startOfToday() + 86_400_000L
        val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(t) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        var at = startOfToday() + (h * 60 + mi) * 60_000L
        if (at <= System.currentTimeMillis()) at += 86_400_000L
        return at
    }

    suspend fun categoriesLock(s: TermuxSession, a: TermuxArgs) {
        val app = pickApp(s, a.pos.getOrNull(2) ?: "") ?: return
        val cat = a.pos.getOrNull(3)?.lowercase()
        if (cat == null || cat !in listOf("study", "coding", "neutral", "fun", "auto")) {
            s.err("usage: categories lock <app> study|coding|neutral|fun|auto [now|midnight|HH:MM]"); return
        }
        val start = parseStart(a.pos.getOrNull(4))
        if (start == null) { s.err("start: now, midnight or a time like 21:30"); return }
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.catLockedUntil(app.pkg)
        if (lockedUntil > now) {
            s.err("${app.label.tm()} was changed this week. next change after ${SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(lockedUntil))}")
            return
        }
        val label = if (cat == "auto") "automatic" else cat
        confirm(s, "Move ${app.label} to $label ${if (start <= now) "now" else "from " + SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(start))}? It then stays for 7 days.") {
            prefs.scheduleCategory(app.pkg, if (cat == "auto") null else cat, start)
            s.ok("${app.label.tm()} \u2192 $label, locked for 7 days from the start")
        }
    }

    fun categoriesPending(s: TermuxSession) {
        val now = System.currentTimeMillis()
        val f = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
        val pm = ctx.packageManager
        s.head("category locks")
        var n = 0
        sp.all.keys.filter { it.startsWith("catlock_") }.forEach { k ->
            val pkg = k.removePrefix("catlock_")
            val until = prefs.catLockedUntil(pkg)
            val pend = prefs.pendingCat(pkg)
            if (until > now || pend != null) {
                n++
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                val p = if (pend != null) "  {y}pending: ${(pend.first.ifBlank { "automatic" }).tm()} from ${f.format(Date(pend.second))}{/}" else ""
                s.out("${label.tm().padEnd(20).take(20)} locked until {c}${f.format(Date(until))}{/}$p")
            }
        }
        if (n == 0) s.dim("no app is locked and nothing is waiting")
    }

    // ── ideas: copy ──────────────────────────────────────────────────────
    private fun clip(text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("QuestBlock idea", text))
    }

    fun ideasCopy(s: TermuxSession, a: TermuxArgs) {
        val all = IdeaStore.all(ctx)
        val target = a.pos.getOrNull(2)?.lowercase()
        if (target == null) { s.err("usage: ideas copy <n> | all | <tags like bug,ui> [--log]"); return }
        val wanted = target.split(',').filter { it.isNotBlank() }
        val byTag = target != "all" && target.toIntOrNull() == null && wanted.isNotEmpty() && wanted.all { it in IdeaStore.TAGS }
        if (target != "all" && !byTag) {
            val n = target.toIntOrNull()
            if (n == null || n < 1 || n > all.size) { s.err("usage: ideas copy <n> | all | <tags like bug,ui> [--log]"); return }
            clip(all[n - 1].text)
            s.ok("copied #$n")
            return
        }
        val open = all.filter { it.status != "done" && (!byTag || it.tag in wanted) }
        if (open.isEmpty()) { s.warn("no open ideas to copy"); return }
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        val cur = version.substringBefore('.').toIntOrNull()
        val head = if (cur != null)
            "QuestBlock v$cur (latest zip attached). Bump to v${cur + 1} and make these changes. Short reply, give the Termux command."
        else "QuestBlock (latest zip attached). Make these changes and bump the version. Short reply, give the Termux command."
        val logPart = if (a.has("--log")) "\n\nDebug log (last 15 lines):\n" +
            DebugLog.read().lines().filter { it.isNotBlank() }.takeLast(15).joinToString("\n") else ""
        val ordered = open.sortedWith(compareByDescending<com.krishna.questblock.data.Idea> { it.prio }.thenBy { ideaRank(it) })
        val changes = ordered.mapIndexed { i, it -> "${i + 1}. " + (if (it.tag.isNotEmpty()) "[${it.tag}] " else "") + (if (it.prio > 0) "(priority ${it.prio}) " else "") + it.text }.joinToString("\n")
        val rules = "Rules:\n" +
            "- Bump versionCode and versionName in app/build.gradle.kts.\n" +
            "- Add a new entry at the top of VERSION_LOG in data/VersionLog.kt. Read VersionLog.kt first for more info: it holds the full history of past updates and what each one changed, so do not redo or undo them.\n" +
            "- Change only what the list above needs.\n" +
            "- Keep the reply short." + (if (a.has("--plain")) "" else "\n" + TERMUX_RULE)
        val cmd = if (cur != null) {
            val n = cur + 1
            "\n\nEnd your reply with this Termux command:\n" +
                "cd ~ && unzip -oq ~/storage/downloads/QuestBlock_v$n.zip -d ~/qb$n && cp -r ~/qb$n/QuestBlock_v$n/. ~/QuestBlock/ && rm -rf ~/qb$n && cd ~/QuestBlock && bash termux/release.sh \"v$n: short summary\""
        } else ""
        clip(head + "\n\n" + changes + "\n\n" + rules + cmd + logPart)
        // everything copied is now sent: tick it off, like the Ideas page does
        all.indices.filter { all[it].status != "done" && (!byTag || all[it].tag in wanted) }.forEach { IdeaStore.setMeta(ctx, it, status = "done") }
        s.ok("copied the update prompt with ${open.size} idea${if (open.size > 1) "s" else ""} (ticked off)")
    }

    // ── home notices ─────────────────────────────────────────────────────
    suspend fun homeNotices(s: TermuxSession) {
        val bonus = runCatching { repo.claimDailyLogin() }.getOrDefault(0)
        if (bonus > 0) s.ok("daily login bonus {c}+$bonus{/} coins")
        val rent = runCatching { repo.chargeThemeRent() }.getOrNull()
        if (!rent.isNullOrBlank()) s.warn(rent.tm())
        val p = repo.player.first()
        val c = Cultivation.of(p.xp)
        val next = if (c.atTop) "top realm" else "${c.toNextRealm ?: 0} xp to ${c.nextRealm ?: "the next realm"}"
        s.dim("realm: ${c.realm.name} \u00b7 ${c.stageLabel} (${c.into}/${c.need})  $next")
    }
}
