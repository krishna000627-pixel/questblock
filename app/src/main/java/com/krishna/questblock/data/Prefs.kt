package com.krishna.questblock.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Prefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    var streakFreezes: Int
        get() = sp.getInt("streak_freezes", 0)
        set(v) = sp.edit().putInt("streak_freezes", v.coerceAtLeast(0)).apply()
    /** "Routine first": fun unlocks cost +50% while today's routine is unfinished. */
    var routineFirst: Boolean
        get() = sp.getBoolean("routine_first", false)
        set(v) = sp.edit().putBoolean("routine_first", v).apply()
    fun stepTimerEnd(id: Long): Long = sp.getLong("step_timer_$id", 0L)
    fun setStepTimerEnd(id: Long, t: Long) {
        if (t == 0L) sp.edit().remove("step_timer_$id").apply() else sp.edit().putLong("step_timer_$id", t).apply()
    }

    /** Routine steps that already paid their coins (ids are unique per day's copy of the routine). */
    fun stepPaid(id: Long): Boolean = sp.getString("paid_steps", "").orEmpty().split(',').contains(id.toString())
    fun markStepPaid(id: Long) {
        val l = sp.getString("paid_steps", "").orEmpty().split(',').filter { it.isNotEmpty() }.takeLast(400) + id.toString()
        sp.edit().putString("paid_steps", l.joinToString(",")).apply()
    }

    /** 0 = dark, 1 = light, 2 = follow system */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(value) = sp.edit().putInt("theme_mode", value).apply()

    // ── App categories (manual choices), one "package=category" per line ──
    private fun readCats(): Map<String, String> =
        (sp.getString("app_categories", "") ?: "").lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i > 0) line.substring(0, i) to line.substring(i + 1) else null
            }
            .toMap()

    fun categoryMap(): Map<String, String> { applyDue(); applyDueCats(); return readCats() }

    fun categoryOverride(pkg: String): String? = categoryMap()[pkg]

    fun setCategory(pkg: String, cat: String?) {
        val m = readCats().toMutableMap()
        if (cat == null) m.remove(pkg) else m[pkg] = cat
        sp.edit().putString("app_categories", m.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    // ── Unlock carry-over: when the user left an unlocked app (0 = inside / not set) ──
    fun awayAt(pkg: String): Long = sp.getLong("away_$pkg", 0L)
    fun setAwayAt(pkg: String, t: Long) { sp.edit().putLong("away_$pkg", t).apply() }
    fun clearAway(pkg: String) { sp.edit().remove("away_$pkg").apply() }

    // ── Study tracking via Usage access ──
    var studyCheckpoint: Long
        get() = sp.getLong("study_checkpoint", 0L)
        set(value) = sp.edit().putLong("study_checkpoint", value).apply()
    fun studyCarryMs(pkg: String): Long = sp.getLong("study_carry_$pkg", 0L)
    fun setStudyCarryMs(pkg: String, v: Long) { sp.edit().putLong("study_carry_$pkg", v).apply() }
    fun studySessionId(pkg: String): Long = sp.getLong("study_sid_$pkg", -1L)
    fun studySessionAt(pkg: String): Long = sp.getLong("study_sat_$pkg", 0L)
    fun setStudySession(pkg: String, id: Long, at: Long) {
        sp.edit().putLong("study_sid_$pkg", id).putLong("study_sat_$pkg", at).apply()
    }

    // ── Commitment: for fun rules, tightening applies at once, loosening waits until the next midnight ──
    private fun today(): Long = dayNumber(System.currentTimeMillis())

    /** key -> (new value, first day it applies). Stored as "key<TAB>value<TAB>day" lines. */
    private fun readPending(): MutableMap<String, Pair<String, Long>> {
        val raw = sp.getString("commit_pending", "") ?: ""
        val out = LinkedHashMap<String, Pair<String, Long>>()
        raw.lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 3) p[2].toLongOrNull()?.let { out[p[0]] = p[1] to it }
        }
        return out
    }
    private fun writePending(m: Map<String, Pair<String, Long>>) {
        sp.edit().putString("commit_pending", m.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" }).apply()
    }

    /** Apply every change whose day has come. Cheap when nothing is waiting. */
    fun applyDue() {
        if ((sp.getString("commit_pending", "") ?: "").isEmpty()) return
        val m = readPending()
        val t = today()
        var changed = false
        val it = m.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value.second <= t) { applyRaw(e.key, e.value.first); it.remove(); changed = true }
        }
        if (changed) writePending(m)
    }

    private fun applyRaw(key: String, v: String) {
        when {
            key == "limit" -> sp.edit().putInt("fun_limit_min", v.toIntOrNull() ?: 0).apply()
            key == "weekend" -> sp.edit().putInt("weekend_fun_limit", v.toIntOrNull() ?: 0).apply()
            key == "exam" -> sp.edit().putBoolean("exam_mode", v == "1").apply()
            key == "quiet" -> sp.edit().putBoolean("quiet_on", v == "1").apply()
            key == "study" -> sp.edit().putBoolean("studyblock_on", v == "1").apply()
            key == "music" -> sp.edit().putBoolean("pause_music_limit", v == "1").apply()
            key.startsWith("cat:") -> setCategory(key.removePrefix("cat:"), v.ifEmpty { null })
            key.startsWith("safe:") -> setFunSafe(key.removePrefix("safe:"), v == "1")
            key.startsWith("site:") -> if (v == "0") removeSiteNow(key.removePrefix("site:"))
        }
    }

    /** What is waiting for midnight for this key (raw text), or null. */
    fun pendingValue(key: String): String? { applyDue(); return readPending()[key]?.first }
    fun pendingInt(key: String): Int? = pendingValue(key)?.toIntOrNull()
    fun pendingFlag(key: String): Boolean? = pendingValue(key)?.let { it == "1" }

    /** One line for the UI, empty when nothing is waiting. */
    fun pendingSummary(): String {
        applyDue()
        val m = readPending()
        if (m.isEmpty()) return ""
        val parts = m.map { (k, v) ->
            when {
                k == "limit" -> "fun limit \u2192 " + (v.first.toIntOrNull()?.takeIf { it > 0 }?.let { "$it min" } ?: "off")
                k == "weekend" -> "weekend limit \u2192 " + (v.first.toIntOrNull()?.takeIf { it > 0 }?.let { "$it min" } ?: "same as normal")
                k == "exam" -> "exam mode \u2192 " + if (v.first == "1") "on" else "off"
                k == "quiet" -> "quiet hours \u2192 " + if (v.first == "1") "on" else "off"
                k == "study" -> "study hours \u2192 " + if (v.first == "1") "on" else "off"
                k == "music" -> "pause fun music \u2192 " + if (v.first == "1") "on" else "off"
                k.startsWith("cat:") -> "an app leaves Fun & Games"
                k.startsWith("safe:") -> "a \uD83D\uDEE1 switch turns on"
                k.startsWith("site:") -> "a website is unblocked"
                else -> k
            }
        }
        return "Waiting for midnight: " + parts.joinToString(", ")
    }

    /** Larger number = looser. 0 means "off" = loosest. */
    private fun looseness(v: Int): Int = if (v <= 0) Int.MAX_VALUE else v

    private fun commitLimit(key: String, spKey: String, new: Int) {
        val cur = sp.getInt(spKey, 0)
        val m = readPending()
        // weekend 0 means "use the normal limit", so compare what actually applies
        val normal = sp.getInt("fun_limit_min", 0)
        fun eff(v: Int) = if (key == "weekend" && v <= 0) normal else v
        val looser = looseness(eff(new)) > looseness(eff(cur))
        if (new == cur) {
            if (m.remove(key) != null) writePending(m)
        } else if (looser) {
            m[key] = new.toString() to today() + 1
            writePending(m)
        } else {
            sp.edit().putInt(spKey, new).apply()
            if (m.remove(key) != null) writePending(m)
        }
    }

    /** Switches: turning a fun rule OFF is loosening, turning it ON applies at once. */
    private fun commitFlag(key: String, spKey: String, new: Boolean) {
        val cur = sp.getBoolean(spKey, false)
        val m = readPending()
        if (new == cur) {
            if (m.remove(key) != null) writePending(m)
        } else if (!new) {
            m[key] = "0" to today() + 1
            writePending(m)
        } else {
            sp.edit().putBoolean(spKey, true).apply()
            if (m.remove(key) != null) writePending(m)
        }
    }

    /** Any fun rule in force? Then loosening one is a commitment break. */
    val funRulesActive: Boolean get() = effectiveFunLimitMin > 0 || quietEnabled || studyBlockEnabled || examMode

    // ── Weekly category lock: one change per app per week, starting at a time you choose (15 min steps) ──
    /** pkg -> (category or "" for automatic, start time in ms). Stored as "pkg<TAB>cat<TAB>startMs". */
    private fun readSched(): MutableMap<String, Pair<String, Long>> {
        val out = LinkedHashMap<String, Pair<String, Long>>()
        (sp.getString("cat_sched", "") ?: "").lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size == 3) p[2].toLongOrNull()?.let { out[p[0]] = p[1] to it }
        }
        return out
    }
    private fun writeSched(m: Map<String, Pair<String, Long>>) {
        sp.edit().putString("cat_sched", m.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" }).apply()
    }

    /** Put due scheduled category changes into effect. */
    fun applyDueCats() {
        if ((sp.getString("cat_sched", "") ?: "").isEmpty()) return
        val m = readSched()
        val now = System.currentTimeMillis()
        var changed = false
        val it = m.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value.second <= now) { setCategory(e.key, e.value.first.ifEmpty { null }); it.remove(); changed = true }
        }
        if (changed) writeSched(m)
    }

    /** When this app may be moved again (0 = now). */
    fun catLockedUntil(pkg: String): Long = sp.getLong("catlock_$pkg", 0L)

    /** The change waiting for its start time: (category or "" for automatic, start ms). */
    fun pendingCat(pkg: String): Pair<String, Long>? { applyDueCats(); return readSched()[pkg] }

    /** Move an app to [cat] (null = automatic) from [startMs]. Starts at once if that time has come. Locks the app for a week from the start. */
    fun scheduleCategory(pkg: String, cat: String?, startMs: Long) {
        val now = System.currentTimeMillis()
        val start = maxOf(startMs, now)
        val m = readSched()
        if (startMs <= now) { setCategory(pkg, cat); m.remove(pkg) } else m[pkg] = (cat ?: "") to startMs
        writeSched(m)
        sp.edit().putLong("catlock_$pkg", start + 7 * 86_400_000L).apply()
    }

    /** For the Usage page, which changes at once: record the week lock. */
    fun lockCategoryFromNow(pkg: String) { sp.edit().putLong("catlock_$pkg", System.currentTimeMillis() + 7 * 86_400_000L).apply() }

    // ── Website blocker ──
    fun blockedSites(): List<String> =
        (sp.getString("blocked_sites", "") ?: "").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

    private fun writeSites(l: List<String>) { sp.edit().putString("blocked_sites", l.joinToString("\n")).apply() }

    /** "https://www.Instagram.com/x" -> "instagram.com" */
    fun normalizeSite(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        return s
    }

    /** Adding a block applies at once. Returns false if it was empty or already there. */
    fun addSite(raw: String): Boolean {
        val d = normalizeSite(raw)
        if (d.isEmpty() || !d.contains('.')) return false
        val l = blockedSites()
        if (d in l) return false
        writeSites(l + d)
        val m = readPending()
        if (m.remove("site:$d") != null) writePending(m)
        return true
    }

    /** Removing a block is loosening: it waits until midnight. */
    fun removeSite(d: String) {
        val m = readPending()
        m["site:$d"] = "0" to today() + 1
        writePending(m)
    }
    fun removeSiteNow(d: String) { writeSites(blockedSites().filter { it != d }) }
    fun siteUnblockPending(d: String): Boolean { applyDue(); return readPending().containsKey("site:$d") }

    /** The list entry that matches [host] (or one of its parent domains), or null. */
    fun matchedSite(host: String): String? {
        applyDue()
        val h = normalizeSite(host)
        return blockedSites().firstOrNull { h == it || h.endsWith(".$it") }
    }

    /** Paid unlock for a blocked site: until this time (ms) it is open. */
    fun siteUnlockedUntil(d: String): Long = sp.getLong("site_unlock_$d", 0L)
    fun setSiteUnlockedUntil(d: String, t: Long) { sp.edit().putLong("site_unlock_$d", t).apply() }

    // ── Ghost time: music from fun apps while they are not on screen ──
    val pauseMusicAtLimit: Boolean get() = sp.getBoolean("pause_music_limit", true)

    /** Turning it ON applies now; turning it OFF waits until midnight (a fun rule, like the others). */
    fun changePauseMusic(on: Boolean) {
        if (!sp.contains("pause_music_limit")) sp.edit().putBoolean("pause_music_limit", true).apply()
        commitFlag("music", "pause_music_limit", on)
    }

    /** Change an app's category. Moving a fun app OUT of Fun & Games waits until tomorrow while fun rules are on. Returns true if applied now. */
    fun changeCategory(pkg: String, cat: String?, oldCat: String, newCat: String): Boolean {
        val m = readPending()
        if (oldCat == "fun" && newCat != "fun" && funRulesActive) {
            m["cat:$pkg"] = (cat ?: "") to today() + 1
            writePending(m)
            return false
        }
        setCategory(pkg, cat)
        if (m.remove("cat:$pkg") != null) writePending(m)
        return true
    }

    /** Turn the "never cut mid-use" shield on/off. Turning it ON waits until tomorrow while fun rules are on. Returns true if applied now. */
    fun changeFunSafe(pkg: String, on: Boolean): Boolean {
        val m = readPending()
        if (on && pkg !in readSafe() && funRulesActive) {
            m["safe:$pkg"] = "1" to today() + 1
            writePending(m)
            return false
        }
        setFunSafe(pkg, on)
        if (m.remove("safe:$pkg") != null) writePending(m)
        return true
    }

    // ── Backup 2.0 / focus breaks ──
    var lastAutoBackupDay: Long
        get() = sp.getLong("auto_backup_day", 0L)
        set(value) = sp.edit().putLong("auto_backup_day", value).apply()
    /** When the current focus break ends (0 = no break) and whether it is the long one. */
    var breakUntil: Long
        get() = sp.getLong("break_until", 0L)
        set(value) = sp.edit().putLong("break_until", value).apply()
    var breakIsLong: Boolean
        get() = sp.getBoolean("break_long", false)
        set(value) = sp.edit().putBoolean("break_long", value).apply()
    /** Finished focus sessions today (used to give a long break after every 4th). */
    fun focusDoneToday(day: Long): Int = if (sp.getLong("focus_done_day", 0L) == day) sp.getInt("focus_done_n", 0) else 0
    fun setFocusDoneToday(day: Long, n: Int) { sp.edit().putLong("focus_done_day", day).putInt("focus_done_n", n).apply() }

    // ── Fun & Games daily limit ──
    /** Minutes allowed per day in the Fun & Games category. 0 = off. */
    var funLimitMin: Int
        get() { applyDue(); return sp.getInt("fun_limit_min", 0) }
        set(value) { commitLimit("limit", "fun_limit_min", value) }

    /** Apps that are never cut in the middle of use (locked only the next time they are opened). Free Fire by default. */
    private fun readSafe(): Set<String> {
        val raw = sp.getString("fun_safe", null) ?: return setOf("com.dts.freefireth", "com.dts.freefiremax")
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }
    fun funSafeSet(): Set<String> { applyDue(); return readSafe() }
    fun isFunSafe(pkg: String): Boolean = pkg in funSafeSet()
    fun setFunSafe(pkg: String, on: Boolean) {
        val s = readSafe().toMutableSet()
        if (on) s += pkg else s -= pkg
        sp.edit().putString("fun_safe", s.joinToString("\n")).apply()
    }

    var strictMode: Boolean
        get() = sp.getBoolean("strict", false)
        set(value) = sp.edit().putBoolean("strict", value).apply()

    var strictPausedUntil: Long
        get() = sp.getLong("strict_paused_until", 0L)
        set(value) = sp.edit().putLong("strict_paused_until", value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(value) = sp.edit().putBoolean("onboarding_done", value).apply()

    // Active study session package (persists across app kills)
    var activeStudyPkg: String
        get() = sp.getString("active_study_pkg", "") ?: ""
        set(value) = sp.edit().putString("active_study_pkg", value).apply()

    var activeStudySessionId: Long
        get() = sp.getLong("active_study_session_id", -1L)
        set(value) = sp.edit().putLong("active_study_session_id", value).apply()

    // ── Settings (v9) ─────────────────────────────────────────────────────
    var remindersEnabled: Boolean
        get() = sp.getBoolean("reminders", false)
        set(value) = sp.edit().putBoolean("reminders", value).apply()

    var reminderLeadMin: Int
        get() = sp.getInt("reminder_lead", 10)
        set(value) = sp.edit().putInt("reminder_lead", value).apply()

    var focusBonus: Int
        get() = sp.getInt("focus_bonus", 15)
        set(value) = sp.edit().putInt("focus_bonus", value).apply()

    var dailyLoginEnabled: Boolean
        get() = sp.getBoolean("login_enabled", true)
        set(value) = sp.edit().putBoolean("login_enabled", value).apply()

    var dailyLoginCoins: Int
        get() = sp.getInt("login_coins", 5)
        set(value) = sp.edit().putInt("login_coins", value).apply()

    /** Minutes inside a study app per 1 coin. */
    var studyMinPerCoin: Int
        get() = sp.getInt("study_min", 5).coerceAtLeast(1)
        set(value) = sp.edit().putInt("study_min", value.coerceAtLeast(1)).apply()

    var holdSeconds: Int
        get() = sp.getInt("hold_seconds", 3).coerceIn(1, 10)
        set(value) = sp.edit().putInt("hold_seconds", value.coerceIn(1, 10)).apply()

    var showQuotes: Boolean
        get() = sp.getBoolean("quotes", true)
        set(value) = sp.edit().putBoolean("quotes", value).apply()

    var accentArgb: Long
        get() = sp.getLong("accent", 0xFF00E5A0)
        set(value) = sp.edit().putLong("accent", value).apply()

    /** Coins per 5-minute block of unlock time (apps can override it). */
    var ratePer5: Int
        get() = sp.getInt("rate_per_5", DEFAULT_RATE_PER_5).coerceIn(1, 100)
        set(value) = sp.edit().putInt("rate_per_5", value.coerceIn(1, 100)).apply()

    // ── Reliability (v10) ────────────────────────────────────────────────
    var keepAlive: Boolean
        get() = sp.getBoolean("keepalive", true)
        set(value) = sp.edit().putBoolean("keepalive", value).apply()

    /** Last time the blocker (accessibility service) reported in. */
    var lastActiveAt: Long
        get() = sp.getLong("blocker_last_active", 0L)
        set(value) = sp.edit().putLong("blocker_last_active", value).apply()

    /** Heartbeat of the keep-alive service. */
    var keepAliveBeat: Long
        get() = sp.getLong("keepalive_beat", 0L)
        set(value) = sp.edit().putLong("keepalive_beat", value).apply()

    // ── v17: Crash-safe unlock session tracking ───────────────────────────
    /** Package currently being used under an unlock (persists across kills). */
    var activeUnlockPkg: String
        get() = sp.getString("active_unlock_pkg", "") ?: ""
        set(value) = sp.edit().putString("active_unlock_pkg", value).apply()

    /** Timestamp when user entered the currently-active unlocked app. */
    var activeUnlockEnteredAt: Long
        get() = sp.getLong("active_unlock_entered_at", 0L)
        set(value) = sp.edit().putLong("active_unlock_entered_at", value).apply()

    // ── v44: player hub ──────────────────────────────────────────────────
    var autoVerify: Boolean
        get() = sp.getBoolean("auto_verify", true)
        set(value) = sp.edit().putBoolean("auto_verify", value).apply()
    var examMode: Boolean
        get() { applyDue(); return sp.getBoolean("exam_mode", false) }
        set(value) { commitFlag("exam", "exam_mode", value) }
    /** Fun & Games limit used on Saturday and Sunday. 0 = same as the normal limit. */
    var weekendFunLimitMin: Int
        get() { applyDue(); return sp.getInt("weekend_fun_limit", 0) }
        set(value) { commitLimit("weekend", "weekend_fun_limit", value) }
    /** The limit actually enforced today: weekend value, then exam mode, on top of the normal limit. */
    val effectiveFunLimitMin: Int get() {
        var l = funLimitMin
        val dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        if ((dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY) && weekendFunLimitMin > 0) l = weekendFunLimitMin
        if (examMode) l = if (l > 0) minOf(l, EXAM_FUN_LIMIT_MIN) else EXAM_FUN_LIMIT_MIN
        return l
    }
    /** Minutes of a category already used by auto quest verification today, so one minute is never paid twice. */
    fun autoUsed(day: Long, cat: String): Int = if (sp.getLong("auto_${cat}_day", 0L) == day) sp.getInt("auto_${cat}_used", 0) else 0
    fun setAutoUsed(day: Long, cat: String, min: Int) { sp.edit().putLong("auto_${cat}_day", day).putInt("auto_${cat}_used", min).apply() }

    // ── Context-aware locking: quiet hours and study hours lock Fun & Games apps ──
    var quietEnabled: Boolean
        get() { applyDue(); return sp.getBoolean("quiet_on", false) }
        set(value) { commitFlag("quiet", "quiet_on", value) }
    var quietStartMin: Int
        get() = sp.getInt("quiet_start", 22 * 60)
        set(value) = sp.edit().putInt("quiet_start", ((value % 1440) + 1440) % 1440).apply()
    var quietEndMin: Int
        get() = sp.getInt("quiet_end", 6 * 60)
        set(value) = sp.edit().putInt("quiet_end", ((value % 1440) + 1440) % 1440).apply()
    var studyBlockEnabled: Boolean
        get() { applyDue(); return sp.getBoolean("studyblock_on", false) }
        set(value) { commitFlag("study", "studyblock_on", value) }
    var studyStartMin: Int
        get() = sp.getInt("studyblock_start", 8 * 60)
        set(value) = sp.edit().putInt("studyblock_start", ((value % 1440) + 1440) % 1440).apply()
    var studyEndMin: Int
        get() = sp.getInt("studyblock_end", 20 * 60)
        set(value) = sp.edit().putInt("studyblock_end", ((value % 1440) + 1440) % 1440).apply()

    /** True while Fun & Games apps must stay locked: quiet hours, study hours, or exam mode (08:00-20:00 unless study hours are set). */
    fun funBlockedNow(): Boolean {
        val cal = java.util.Calendar.getInstance()
        val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        fun inWin(s: Int, e: Int) = if (s == e) false else if (s < e) m in s until e else (m >= s || m < e)
        if (quietEnabled && inWin(quietStartMin, quietEndMin)) return true
        return if (studyBlockEnabled) inWin(studyStartMin, studyEndMin) else examMode && inWin(8 * 60, 20 * 60)
    }

    fun badgeAt(id: String): Long = sp.getLong("badge_$id", 0L)
    fun setBadgeAt(id: String, t: Long) { sp.edit().putLong("badge_$id", t).apply() }
    var seasonStartDay: Long
        get() = sp.getLong("season_start", 0L)
        set(value) = sp.edit().putLong("season_start", value).apply()
    /** Coins lost for giving up a focus session after its first minute. 0 = no penalty. */
    var focusExitPenalty: Int
        get() = sp.getInt("focus_exit_penalty", 10)
        set(value) = sp.edit().putInt("focus_exit_penalty", value.coerceIn(0, 100)).apply()
    fun isClaimed(key: String): Boolean = sp.getBoolean("claimed_$key", false)
    fun setClaimed(key: String) { sp.edit().putBoolean("claimed_$key", true).apply() }

    fun clearAll() = sp.edit().clear().apply()
}

const val EXAM_FUN_LIMIT_MIN = 45
