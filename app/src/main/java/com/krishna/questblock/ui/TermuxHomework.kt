package com.krishna.questblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Hw
import com.krishna.questblock.data.HwItem
import com.krishna.questblock.data.Prefs
import kotlinx.coroutines.flow.first

/**
 * v211: the Homework tracker as terminal commands (hw). Same store and rules as the Homework page: [Hw] and [HwService].
 * Reading commands (ls, board, week, copy) work during strict Focus; the rest are refused there by the shell.
 */
class TermuxHomework(
    private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
    private val confirm: (TermuxSession, String, suspend () -> Unit) -> Unit,
    private val labelOf: (String) -> String,
) {
    suspend fun run(s: TermuxSession, a: TermuxArgs) {
        // before every command: settle the days that ended (carry-over, streak) and add the minutes of finished Focus sessions
        Hw.dailyTick(ctx, repo).forEach { s.warn(it.tm()) }
        HwService.sync(ctx, repo)?.let { s.dim(it.tm()) }
        when (a.pos.getOrNull(1)?.lowercase()) {
            null, "board" -> board(s)
            "ls", "list" -> ls(s, a)
            "add" -> add(s, a)
            "done" -> done(s, a)
            "undo" -> undo(s, a)
            "rm" -> rm(s, a)
            "edit" -> edit(s, a)
            "focus" -> focus(s, a)
            "week" -> week(s)
            "copy" -> copy(s)
            "bed" -> bed(s, a)
            else -> s.err("usage: hw add|ls|board|done|undo|rm|edit|focus|week|copy|bed  (man hw)")
        }
    }

    private fun line(h: HwItem, now: Long): String {
        val box = if (h.done) "{g}[x]{/}" else "[ ]"
        val meta = ArrayList<String>()
        meta.add(Hw.dueLabel(h.due, now))
        meta.add("~${Hw.estimate(ctx, h)}m" + if (Hw.estimateIsLearned(ctx, h)) "*" else "")
        if (h.carried > 0) meta.add("carried ${h.carried}x")
        val link = HwService.linkText(ctx, h)
        if (link.isNotEmpty()) meta.add(link)
        val late = !h.done && h.due < now
        return "{c}#${h.id}{/} $box {y}${h.subj}{/} ${h.text.tm()}  ${if (late) "{r}" else "{d}"}${meta.joinToString(" \u00b7 ")}{/}"
    }

    private fun itemArg(s: TermuxSession, a: TermuxArgs): HwItem? {
        val id = a.pos.getOrNull(2)?.removePrefix("#")?.toLongOrNull()
        if (id == null) { s.err("which homework? give its #id (see: hw ls)"); return null }
        val h = Hw.get(ctx, id)
        if (h == null) { s.err("no homework #$id"); return null }
        return h
    }

    private fun board(s: TermuxSession) {
        val now = System.currentTimeMillis()
        s.head("homework")
        Hw.board(ctx, now).forEach { r ->
            s.out("{y}${r.subj}{/} ${r.name.padEnd(10)} due {c}${r.due}{/}  late ${if (r.overdue > 0) "{r}${r.overdue}{/}" else "0"}  done this week {g}${r.doneWeek}{/}")
        }
        val soon = Hw.dueSoon(ctx, now)
        if (soon.isNotEmpty()) {
            s.out("{b}DUE IN 24 HOURS{/}")
            soon.take(8).forEach { s.out(line(it, now)) }
            if (soon.size > 8) s.dim("\u2026 ${soon.size - 8} more: hw ls --due")
        } else {
            s.dim("nothing due in the next 24 hours")
        }
        Hw.fitWarning(ctx, now)?.let { s.warn(it.tm()) }
        s.out("clean days in a row {c}${Hw.streak(ctx)}{/} {d}(best ${Hw.bestStreak(ctx)}){/}")
        s.dim("hw add <subject> \"text\" --due fri \u00b7 hw done <id> \u00b7 hw focus <id> \u00b7 hw week")
    }

    private fun ls(s: TermuxSession, a: TermuxArgs) {
        val now = System.currentTimeMillis()
        val subjArg = a.pos.getOrNull(2) ?: a.value("--due")?.takeIf { Hw.subjectOf(it) != null }   // "hw ls --due maths": the flag took the subject
        val subj = Hw.subjectOf(subjArg)
        if (subjArg != null && subj == null) { s.err("subject: p c m h e"); return }
        val all = Hw.all(ctx).filter { subj == null || it.subj == subj }
        val weekStart = com.krishna.questblock.data.startOfToday(now) - 6L * 86_400_000L
        val title: String
        val list: List<HwItem>
        when {
            a.has("--done") -> { title = "homework handed in this week"; list = all.filter { it.done && it.doneAt >= weekStart }.sortedByDescending { it.doneAt } }
            a.has("--overdue") -> { title = "homework overdue"; list = all.filter { !it.done && it.due < now }.sortedBy { it.due } }
            a.has("--due") -> { title = "homework due in 24 hours"; list = all.filter { !it.done && it.due <= now + Hw.DUE_SOON_MS }.sortedBy { it.due } }
            else -> { title = "homework to hand in"; list = all.filter { !it.done }.sortedBy { it.due } }
        }
        s.head(title + if (subj != null) " \u00b7 ${Hw.subjectName(subj)}" else "")
        if (list.isEmpty()) { s.dim("nothing here. hw add <subject> \"text\" --due fri"); return }
        list.take(40).forEach { s.out(line(it, now)) }
        if (list.size > 40) s.dim("\u2026 ${list.size - 40} more: filter with a subject, --due, --overdue or --done")
        s.dim("~25m* = learned from your Focus sessions")
    }

    private fun add(s: TermuxSession, a: TermuxArgs) {
        val subj = Hw.subjectOf(a.pos.getOrNull(2))
        if (subj == null) { s.err("subject: p c m h e (physics chemistry maths hindi english)"); return }
        val text = a.rest(3).trim()
        if (text.isEmpty()) { s.err("usage: hw add <subject> \"what it is\" [--due fri|\"fri 18:00\"] [--ch 3] [--copy] [-t 30m]"); return }
        val now = System.currentTimeMillis()
        val dueText = a.value("--due")
        val due = if (dueText == null) Hw.defaultDue(now) else Hw.parseDue(dueText, now)
        if (due == null) { s.err("due: today, tomorrow, +2d or a weekday, with an optional time, e.g. \"fri 18:00\""); return }
        val chArg = a.value("--ch")
        val ch = chArg?.toIntOrNull() ?: 0
        if (chArg != null && ch <= 0) { s.err("--ch: the chapter number, like 3 (see: chapters ls ${subj})"); return }
        if (ch > 0) {
            val n = termuxChapterCount(ctx, subj)
            if (ch > n) { s.err("${Hw.subjectName(subj)} has $n chapters"); return }
        }
        val estArg = a.value("-t", "--time")
        val est = if (estArg == null) 0 else (Hw.parseMinutes(estArg) ?: run { s.err("time: like 30m or 1h"); return })
        if (a.has("--copy") && ch == 0) s.warn("--copy only works with --ch <chapter>: nothing will be ticked")
        val kind = if (ch > 0) (if (a.has("--copy")) "copy" else "study") else ""
        val item = Hw.add(ctx, subj, text, due, ch, kind, est, now)
        s.ok("hw {c}#${item.id}{/} added: ${Hw.subjectName(subj)} ${text.tm()}  {d}${Hw.dueLabel(due, now)} \u00b7 ~${Hw.estimate(ctx, item)}m${if (Hw.estimateIsLearned(ctx, item)) "*" else ""}{/}")
        val link = HwService.linkText(ctx, item)
        if (link.isNotEmpty()) s.dim("handing it in ticks: $link")
        Hw.fitWarning(ctx, now)?.let { s.warn(it.tm()) }
    }

    private suspend fun done(s: TermuxSession, a: TermuxArgs) {
        val h = itemArg(s, a) ?: return
        val r = HwService.complete(ctx, repo, h.id)
        if (r == null) { s.err("no homework #${h.id}"); return }
        s.ok("handed in: ${Hw.subjectName(r.item.subj)} ${r.item.text.tm()}")
        r.lines.forEach { s.dim(it.tm()) }
    }

    private fun undo(s: TermuxSession, a: TermuxArgs) {
        val h = itemArg(s, a) ?: return
        if (!h.done) { s.warn("#${h.id} is not handed in"); return }
        Hw.reopen(ctx, h.id)
        s.ok("#${h.id} is open again")
        s.dim("coins already paid for it are not paid twice; a chapter tick stays (chapters ... to change it)")
    }

    private fun rm(s: TermuxSession, a: TermuxArgs) {
        val h = itemArg(s, a) ?: return
        confirm(s, "delete homework #${h.id} '${h.text.tm()}'?") {
            Hw.remove(ctx, h.id)
            s.ok("deleted #${h.id}")
        }
    }

    private fun edit(s: TermuxSession, a: TermuxArgs) {
        val h = itemArg(s, a) ?: return
        val now = System.currentTimeMillis()
        var n = h
        var changed = false
        a.value("--text")?.let { if (it.isNotBlank()) { n = n.copy(text = it.trim()); changed = true } }
        a.value("--subj")?.let {
            val x = Hw.subjectOf(it)
            if (x == null) { s.err("subject: p c m h e"); return }
            n = n.copy(subj = x); changed = true
        }
        a.value("--due")?.let {
            val d = Hw.parseDue(it, now)
            if (d == null) { s.err("due: today, tomorrow, +2d or a weekday, optional time"); return }
            n = n.copy(due = d); changed = true
        }
        a.value("--ch")?.let {
            val c = it.toIntOrNull()
            if (c == null || c < 0) { s.err("--ch: a chapter number, or 0 to unlink"); return }
            if (c > 0 && c > termuxChapterCount(ctx, n.subj)) { s.err("${Hw.subjectName(n.subj)} has ${termuxChapterCount(ctx, n.subj)} chapters"); return }
            n = n.copy(ch = c, kind = if (c == 0) "" else n.kind.ifEmpty { "study" }); changed = true
        }
        if (a.has("--copy") && n.ch > 0) { n = n.copy(kind = "copy"); changed = true }
        if (a.has("--study") && n.ch > 0) { n = n.copy(kind = "study"); changed = true }
        a.value("-t", "--time")?.let {
            val m = Hw.parseMinutes(it)
            if (m == null) { s.err("time: like 30m"); return }
            n = n.copy(est = m); changed = true
        }
        if (!changed) { s.err("nothing to change. flags: --text --subj --due --ch (0 unlinks) --copy --study -t"); return }
        Hw.edit(ctx, h.id) { n }
        s.ok("#${h.id} updated")
        s.out(line(n, now))
    }

    private suspend fun focus(s: TermuxSession, a: TermuxArgs) {
        val item = itemArg(s, a) ?: return
        if (item.done) { s.warn("already handed in"); return }
        val now = System.currentTimeMillis()
        if (repo.player.first().focusModeUntil > now) { s.err("a session is already running (focus status)"); return }
        val tArg = a.value("-t", "--time")
        val asked = if (tArg == null) null else Hw.parseMinutes(tArg)
        if (tArg != null && asked == null) { s.err("time: like 30m or 1h"); return }
        val minutes = (asked ?: Hw.estimate(ctx, item)).coerceIn(5, 120)
        val strict = a.has("--strict") || prefs.strictMode
        val picks = repo.focusAllowed()
        val names = if (picks.isEmpty()) "none" else picks.map { labelOf(it) }.sorted().joinToString(", ")
        s.step("focus {c}${minutes}m{/} on ${Hw.subjectName(item.subj)}: ${item.text.tm()}   strict: ${if (strict) "{y}yes{/}" else "no"}")
        s.dim("apps picked last time (free): ${names.tm()}. everything else is locked and Do Not Disturb turns on. change them with: focus start ... --apps")
        confirm(s, "Start $minutes min focus on this homework?") {
            val penalty = repo.focusPenalty()
            val cost = if (strict) "strict: you can't give up, and turning accessibility off breaks the session (penalty, no reward)"
            else if (penalty > 0) "giving up after the first minute costs $penalty coins and gives no reward" else "giving up gives no reward"
            confirm(s, "Commit for $minutes minutes? last check. $cost") {
                val err = HwService.startFocus(ctx, repo, item, minutes, strict)
                if (err == null) {
                    s.ok("focus started on #${item.id}: {c}${minutes}m{/}")
                    s.dim("the minutes are added to the item when the session ends, and teach the estimate for ${Hw.subjectName(item.subj)}")
                } else s.err(err.tm())
            }
        }
    }

    private fun week(s: TermuxSession) {
        val w = Hw.week(ctx)
        s.head("homework week")
        s.out("handed in on time {g}${w.onTime}{/}   late {y}${w.late}{/}")
        s.out("still open ${w.open}   overdue ${if (w.overdue > 0) "{r}${w.overdue}{/}" else "0"}   carried over ${w.carried}x")
        s.out("estimated ${w.estMin} min \u00b7 focused ${w.focusMin} min on finished items")
        s.out("clean days in a row {c}${w.streak}{/} {d}(best ${w.best}; every 7th pays ${Hw.STREAK_BONUS} coins){/}")
        w.perSubject.forEach { (id, d, t) ->
            val frac = if (t == 0) 0f else d.toFloat() / t.toFloat()
            s.out("{y}$id{/} ${Hw.subjectName(id).padEnd(10)} ${termuxBar(frac)} $d of $t")
        }
        s.dim("on time +${Hw.ON_TIME_COINS} coins \u00b7 carried over costs ${Hw.CARRY_COST} coin per item per day")
    }

    private fun copy(s: TermuxSession) {
        val now = System.currentTimeMillis()
        val open = Hw.all(ctx).filter { !it.done }.sortedBy { it.due }
        if (open.isEmpty()) { s.warn("nothing to copy: no open homework"); return }
        val text = "Homework to hand in:\n" + open.mapIndexed { i, h ->
            "${i + 1}. ${Hw.subjectName(h.subj)}: ${h.text} (${Hw.dueLabel(h.due, now)})"
        }.joinToString("\n")
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("QuestBlock homework", text))
        s.ok("copied ${open.size} open item${if (open.size == 1) "" else "s"}")
    }

    private fun bed(s: TermuxSession, a: TermuxArgs) {
        val t = a.pos.getOrNull(2)
        if (t == null) {
            s.out("planning bedtime {c}${Hw.hhmm(Hw.bedMin(ctx))}{/}   {d}hw bed HH:MM to change{/}")
            return
        }
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(t.trim())
        val h = m?.groupValues?.get(1)?.toInt()
        val mi = m?.groupValues?.get(2)?.toInt()
        if (h == null || mi == null || h > 23 || mi > 59) { s.err("bedtime: a time like 23:00"); return }
        Hw.setBedMin(ctx, h * 60 + mi)
        s.ok("planning bedtime ${Hw.hhmm(h * 60 + mi)}")
    }
}
