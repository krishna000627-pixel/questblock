package com.krishna.questblock.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Prefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    /** 0 = dark, 1 = light, 2 = follow system */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(value) = sp.edit().putInt("theme_mode", value).apply()

    // ── App categories (manual choices), one "package=category" per line ──
    fun categoryMap(): Map<String, String> =
        (sp.getString("app_categories", "") ?: "").lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i > 0) line.substring(0, i) to line.substring(i + 1) else null
            }
            .toMap()

    fun categoryOverride(pkg: String): String? = categoryMap()[pkg]

    fun setCategory(pkg: String, cat: String?) {
        val m = categoryMap().toMutableMap()
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

    // ── Fun & Games daily limit ──
    /** Minutes allowed per day in the Fun & Games category. 0 = off. */
    var funLimitMin: Int
        get() = sp.getInt("fun_limit_min", 0)
        set(value) = sp.edit().putInt("fun_limit_min", value).apply()

    /** Apps that are never cut in the middle of use (locked only the next time they are opened). Free Fire by default. */
    fun funSafeSet(): Set<String> {
        val raw = sp.getString("fun_safe", null) ?: return setOf("com.dts.freefireth", "com.dts.freefiremax")
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }
    fun isFunSafe(pkg: String): Boolean = pkg in funSafeSet()
    fun setFunSafe(pkg: String, on: Boolean) {
        val s = funSafeSet().toMutableSet()
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

    fun clearAll() = sp.edit().clear().apply()
}
