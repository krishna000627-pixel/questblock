package com.krishna.questblock.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.dayNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A reward goal plus the key that remembers it was taken. */
data class Goal(val c: Challenge, val key: String)

data class HubState(
    val loaded: Boolean = false,
    val snap: Snapshot? = null,
    val weekly: WeeklyReport? = null,
    val attrs: List<Attr> = emptyList(),
    val rank: String = "",
    val toNext: Int? = null,
    val points: Int = 0,
    val score: Int = 0,
    val badges: List<Badge> = emptyList(),
    val challenges: List<Goal> = emptyList(),
    val seasonGoals: List<Goal> = emptyList(),
    val seasonXp: Int = 0,
    val claimed: Set<String> = emptySet(),
    val season: SeasonInfo? = null,
    val autoDone: Int = 0,
    val economy: List<String> = emptyList(),
    val coins: Int = 0,
) {
    /** Rewards that are finished but not yet taken. */
    val readyToClaim: Int get() = (challenges + seasonGoals).count { it.c.progress >= it.c.target && it.key !in claimed }
}

/**
 * Keeps the last numbers in memory so the Hub pages open instantly and then update quietly
 * (like the App usage page). Home starts the first refresh, so the data is usually ready before you open the Hub.
 */
@Singleton
class HubStore @Inject constructor(
    private val repo: GameRepository,
    private val prefs: Prefs,
    private val builder: InsightsBuilder,
    private val verifier: AutoVerifier,
) {
    var state by mutableStateOf(HubState())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var lastFull = 0L

    /** Quiet refresh. Shows what is already known and updates it when the new numbers are ready. */
    fun refresh(force: Boolean = false) {
        scope.launch {
            if (!lock.tryLock()) return@launch
            try {
                if (!force && state.loaded && System.currentTimeMillis() - lastFull < 10_000L) return@launch
                if (!state.loaded) state = compute(builder.build(withUsage = false), 0)   // instant first paint
                val auto = runCatching { verifier.run() }.getOrDefault(0)
                state = compute(builder.build(withUsage = true), auto)
                lastFull = System.currentTimeMillis()
            } finally {
                lock.unlock()
            }
        }
    }

    fun claim(g: Goal) {
        scope.launch {
            if (g.c.progress >= g.c.target && !prefs.isClaimed(g.key)) {
                prefs.setClaimed(g.key)
                repo.grantBonus(g.c.coins, g.c.xp, "Challenge", g.c.title)
                refresh(force = true)
            }
        }
    }

    fun unlockedOn(b: Badge): String? {
        val t = prefs.badgeAt(b.id)
        return if (b.unlocked && t > 0L) SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t)) else null
    }

    private suspend fun compute(snap: Snapshot, auto: Int): HubState {
        val weekly = AdvisorProvider.get().weekly(snap)
        val points = Progression.rankPoints(snap)
        val (rank, toNext) = Progression.rank(points)
        val now = System.currentTimeMillis()

        val ch = Progression.challenges(snap).map { Goal(it, Progression.claimKey(it)) }
        val badges = Progression.badgesRemembered(snap, prefs, now)

        val player = repo.player.first()
        val tasks = repo.tasks.first()
        val logs = repo.focusLogs.first()
        val season = Progression.season(prefs, now)
        val sXp = Progression.seasonXp(player.weeklyXpLog, season.startDay)
        val sQuests = tasks.count { it.done && it.completedAt != null && dayNumber(it.completedAt) >= season.startDay }
        val sFocus = logs.filter { it.completed && dayNumber(it.startedAt) >= season.startDay }.sumOf { it.minutes }
        val goals = Progression.seasonGoals(sXp, sQuests, sFocus, snap.streak).map { Goal(it, Progression.seasonKey(season, it)) }

        val since = now - 7 * 86_400_000L
        val coinLog = repo.coinLog.first().filter { it.timestamp >= since }
        val earned = coinLog.filter { it.amount > 0 }.groupBy { it.source }
            .mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        val spent = coinLog.filter { it.amount < 0 }.groupBy { it.source }
            .mapValues { e -> -e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
        val eco = listOf(
            "Earned ${earned.sumOf { it.value }}\uD83E\uDE99  " + earned.take(3).joinToString(" · ") { "${it.key} ${it.value}" },
            "Spent ${spent.sumOf { it.value }}\uD83E\uDE99  " + spent.take(3).joinToString(" · ") { "${it.key} ${it.value}" },
        )

        return HubState(
            loaded = true, snap = snap, weekly = weekly,
            attrs = Progression.attributes(snap), rank = rank, toNext = toNext, points = points,
            score = Progression.disciplineScore(snap), badges = badges,
            challenges = ch, seasonGoals = goals, seasonXp = sXp,
            claimed = (ch + goals).map { it.key }.filter { prefs.isClaimed(it) }.toSet(),
            season = season, autoDone = auto, economy = eco, coins = player.coins,
        )
    }
}
