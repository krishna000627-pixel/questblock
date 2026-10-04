package com.krishna.questblock.engine

import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.dayNumber

class Attr(val name: String, val emoji: String, val level: Int, val into: Int, val per: Int)
data class Badge(val id: String, val name: String, val emoji: String, val desc: String, val unlocked: Boolean, val hidden: Boolean)
class Challenge(val id: String, val title: String, val desc: String, val progress: Int, val target: Int, val coins: Int, val xp: Int)
class SeasonInfo(val number: Int, val name: String, val dayIn: Int, val length: Int, val startDay: Long)

object Progression {

    private fun attr(name: String, emoji: String, value: Int, per: Int) =
        Attr(name, emoji, value / per, value % per, per)

    fun attributes(s: Snapshot): List<Attr> {
        val daysUnderFun = if (s.funLimitMin > 0) s.days.drop(1).count { it.funMin <= s.funLimitMin } else 0
        return listOf(
            attr("Focus", "🎯", s.totalFocusMin, 60),
            attr("Knowledge", "📚", s.days.sumOf { it.studyMin }, 120),
            attr("Discipline", "🛡️", daysUnderFun, 2),
            attr("Consistency", "🔥", s.streak, 3),
            attr("Productivity", "⚡", s.totalQuestsDone, 5),
        )
    }

    private val RANKS = listOf(0 to "NOVICE", 8 to "INITIATE", 20 to "DISCIPLINED", 40 to "ELITE", 70 to "MASTER", 110 to "ASCENDANT")

    fun rankPoints(s: Snapshot): Int = s.level + attributes(s).sumOf { it.level }

    /** Rank name and points still needed for the next one (null at the top). */
    fun rank(points: Int): Pair<String, Int?> {
        val i = RANKS.indexOfLast { points >= it.first }.coerceAtLeast(0)
        val next = RANKS.getOrNull(i + 1)
        return RANKS[i].second to next?.let { it.first - points }
    }

    /** 0..100 from this week: quests, focus, study, fun control, streak. */
    fun disciplineScore(s: Snapshot): Int {
        val w = s.thisWeek
        val quests = minOf(25.0, w.sumOf { it.questsDone } * 2.5)
        val focus = minOf(20.0, w.sumOf { it.focusMin } * 20.0 / 150.0)
        val study = minOf(25.0, w.sumOf { it.studyMin } * 25.0 / (if (s.examMode) 1200.0 else 300.0))
        val fun_ = if (s.funLimitMin > 0) {
            20.0 * w.count { it.funMin <= s.funLimitMin } / w.size.coerceAtLeast(1)
        } else {
            val share = w.sumOf { it.funMin }.toDouble() / w.sumOf { it.screenMin }.coerceAtLeast(1)
            20.0 * (1.0 - minOf(1.0, share / 0.6))
        }
        val streak = minOf(10.0, s.streak * 10.0 / 7.0)
        return (quests + focus + study + fun_ + streak).toInt().coerceIn(0, 100)
    }

    private val SEASON_NAMES = listOf("Awakening", "Ascension", "Resolve", "Dominion", "Eclipse", "Zenith")

    fun season(prefs: Prefs, now: Long = System.currentTimeMillis()): SeasonInfo {
        val today = dayNumber(now)
        if (prefs.seasonStartDay <= 0L) prefs.seasonStartDay = today
        val since = (today - prefs.seasonStartDay).toInt().coerceAtLeast(0)
        val n = since / 30 + 1
        return SeasonInfo(n, SEASON_NAMES[(n - 1) % SEASON_NAMES.size], since % 30 + 1, 30, prefs.seasonStartDay + (n - 1) * 30L)
    }

    /** Badges worked out from the last 14 days, so they are not stored anywhere. */
    fun badges(s: Snapshot): List<Badge> {
        val d = s.days
        val ironWill = s.funLimitMin > 0 && d.drop(1).take(7).size == 7 && d.drop(1).take(7).all { it.funMin <= s.funLimitMin }
        return listOf(
            Badge("iron_will", "Iron Will", "🛡️", "Stay under your Fun limit 7 days in a row", ironWill, false),
            Badge("deep_diver", "Deep Diver", "🎯", "Finish a focus session of 60 min or more", s.longestFocusMin >= 60, false),
            Badge("scholar", "Scholar", "📚", "10 hours of study in one week", s.thisWeek.sumOf { it.studyMin } >= 600, false),
            Badge("machine", "Quest Machine", "⚙️", "Finish 5 quests in one day", d.any { it.questsDone >= 5 }, false),
            Badge("triple", "Triple Threat", "🔱", "In one day: 60 min study, 25 min focus, 3 quests",
                d.any { it.studyMin >= 60 && it.focusMin >= 25 && it.questsDone >= 3 }, false),
            Badge("ghost", "Ghost Mode", "👻", "A day with zero Fun & Games and 60+ min study",
                d.any { it.funMin == 0 && it.studyMin >= 60 && it.screenMin > 0 }, true),
            Badge("unbroken", "Unbroken", "💎", "Reach a 14-day streak", s.streak >= 14, true),
        )
    }

    private fun runLength(days: List<DayStats>, ok: (DayStats) -> Boolean): Int {
        var n = 0
        // start from today if today already counts, otherwise from yesterday
        val start = if (days.isNotEmpty() && ok(days[0])) 0 else 1
        for (i in start until days.size) { if (ok(days[i])) n++ else break }
        return n
    }

    fun challenges(s: Snapshot): List<Challenge> {
        val out = ArrayList<Challenge>()
        val limit = if (s.funLimitMin > 0) s.funLimitMin else 60
        out += Challenge("fun_diet", "7-Day Distraction Diet", "Fun & Games under $limit min a day",
            minOf(7, runLength(s.days) { it.funMin <= limit && it.screenMin > 0 }), 7, 100, 500)
        out += Challenge("study_streak", "Study Streak", "45+ min of study a day",
            minOf(5, runLength(s.days) { it.studyMin >= 45 }), 5, 60, 300)
        out += Challenge("focus_week", "Focus Habit", "25+ min of focus a day",
            minOf(3, runLength(s.days) { it.focusMin >= 25 }), 3, 40, 200)
        return out
    }

    /** XP earned from quests since the season started (read from the player's XP log "day:xp,..."). */
    fun seasonXp(log: String, startDay: Long): Int = log.split(",").sumOf { part ->
        val a = part.split(":")
        val d = a.getOrNull(0)?.toLongOrNull()
        val x = a.getOrNull(1)?.toIntOrNull()
        if (d != null && x != null && d >= startDay) x else 0
    }

    /** Season missions and XP milestones. Each can be claimed once per season. */
    fun seasonGoals(xp: Int, questsDone: Int, focusMin: Int, streak: Int): List<Challenge> = listOf(
        Challenge("sq", "Season quests", "Finish 30 quests this season", minOf(30, questsDone), 30, 100, 300),
        Challenge("sf", "Season focus", "300 focus minutes this season", minOf(300, focusMin), 300, 60, 200),
        Challenge("ss", "Season streak", "Reach a 14-day streak", minOf(14, streak), 14, 80, 250),
        Challenge("x500", "XP milestone 500", "Earn 500 quest XP this season", minOf(500, xp), 500, 50, 0),
        Challenge("x1500", "XP milestone 1500", "Earn 1500 quest XP this season", minOf(1500, xp), 1500, 120, 0),
    )

    fun seasonKey(season: SeasonInfo, c: Challenge): String = "s${season.number}_${c.id}"

    /** A reward can be taken once per challenge per week. */
    fun claimKey(c: Challenge, now: Long = System.currentTimeMillis()): String = "${c.id}_${dayNumber(now) / 7}"
}
