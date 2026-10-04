@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.QuestDraft
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.engine.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A reward goal plus the key that remembers it was taken. */
data class Goal(val c: Challenge, val key: String)

data class HubState(
    val loading: Boolean = true,
    val snap: Snapshot? = null,
    val mission: Mission? = null,
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
)

@HiltViewModel
class HubViewModel @Inject constructor(
    private val repo: GameRepository,
    private val prefs: Prefs,
    private val builder: InsightsBuilder,
    private val verifier: AutoVerifier,
) : ViewModel() {
    var state by mutableStateOf(HubState())
        private set


    fun refresh() {
        viewModelScope.launch {
            val auto = runCatching { verifier.run() }.getOrDefault(0)
            val snap = builder.build()
            val advisor = AdvisorProvider.get()
            val mission = advisor.mission(snap)
            val weekly = advisor.weekly(snap)
            val points = Progression.rankPoints(snap)
            val (rank, toNext) = Progression.rank(points)
            val now = System.currentTimeMillis()

            val ch = Progression.challenges(snap).map { Goal(it, Progression.claimKey(it)) }

            val badges = Progression.badgesRemembered(snap, prefs, now)

            // season
            val player = repo.player.first()
            val tasks = repo.tasks.first()
            val logs = repo.focusLogs.first()
            val season = Progression.season(prefs, now)
            val sXp = Progression.seasonXp(player.weeklyXpLog, season.startDay)
            val sQuests = tasks.count { it.done && it.completedAt != null && dayNumber(it.completedAt) >= season.startDay }
            val sFocus = logs.filter { it.completed && dayNumber(it.startedAt) >= season.startDay }.sumOf { it.minutes }
            val goals = Progression.seasonGoals(sXp, sQuests, sFocus, snap.streak).map { Goal(it, Progression.seasonKey(season, it)) }

            // economy
            val since = now - 7 * 86_400_000L
            val coinLog = repo.coinLog.first().filter { it.timestamp >= since }
            val earned = coinLog.filter { it.amount > 0 }.groupBy { it.source }
                .mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
            val spent = coinLog.filter { it.amount < 0 }.groupBy { it.source }
                .mapValues { e -> -e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
            val eco = listOf(
                "Earned ${earned.sumOf { it.value }}\uD83E\uDE99: " + (earned.take(3).joinToString { "${it.key} ${it.value}" }.ifBlank { "nothing yet" }),
                "Spent ${spent.sumOf { it.value }}\uD83E\uDE99: " + (spent.take(3).joinToString { "${it.key} ${it.value}" }.ifBlank { "nothing yet" }),
            )

            state = HubState(
                loading = false, snap = snap, mission = mission, weekly = weekly,
                attrs = Progression.attributes(snap), rank = rank, toNext = toNext, points = points,
                score = Progression.disciplineScore(snap), badges = badges,
                challenges = ch, seasonGoals = goals, seasonXp = sXp,
                claimed = (ch + goals).map { it.key }.filter { prefs.isClaimed(it) }.toSet(),
                season = season, autoDone = auto, economy = eco, coins = player.coins,
            )
        }
    }

    fun addSuggestion(q: SuggestedQuest) {
        viewModelScope.launch {
            val dueToday = nextRunDayStart(0, System.currentTimeMillis()) - 1
            repo.addTask(QuestDraft(title = q.title, difficulty = q.difficulty, category = q.category, notes = q.note, dueAt = dueToday))
            refresh()
        }
    }

    fun claim(g: Goal) {
        viewModelScope.launch {
            if (g.c.progress >= g.c.target && !prefs.isClaimed(g.key)) {
                prefs.setClaimed(g.key)
                repo.grantBonus(g.c.coins, g.c.xp, "Challenge", g.c.title)
                refresh()
            }
        }
    }

}

@Composable
fun PlayerHubScreen(onBack: () -> Unit, onOpenBadges: () -> Unit, onOpenRules: () -> Unit, vm: HubViewModel = hiltViewModel()) {
    val st = vm.state
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Text("Player hub", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = ColorText)
            }
        }
        if (st.loading || st.snap == null) {
            item { HubCard { Text("Working it out…", color = ColorMuted) } }
            return@LazyColumn
        }
        val snap = st.snap
        if (!snap.usageAccess) item {
            HubCard { Text("Turn on Usage access in Settings so study time, fun time and auto quest checks can work.",
                color = ColorWarning, style = MaterialTheme.typography.bodySmall) }
        }
        if (st.autoDone > 0) item {
            HubCard { Text("✅ Auto-completed ${st.autoDone} quest${if (st.autoDone > 1) "s" else ""} from your app usage",
                color = ColorPrimary, fontWeight = FontWeight.SemiBold) }
        }

        // ── Today at a glance (one dashboard) ───────────────────────────
        item {
            val t = snap.today
            HubCard {
                Text("📅 Today at a glance", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Glance("⚡", "Lv ${snap.level}"); Glance("🪙", "${st.coins}"); Glance("🔥", "${snap.streak} d")
                    Glance("✅", "${t.questsDone}/${t.questsDone + snap.openToday.size}"); Glance("🎯", "${t.focusMin} m")
                }
                val total = t.screenMin.coerceAtLeast(1)
                Text("📱 Screen time ${fmtMin(t.screenMin)}", color = ColorText, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorSurfaceHigh)) {
                    if (t.screenMin > 0) {
                        Box(Modifier.weight(maxOf(0.001f, t.studyMin.toFloat() / total)).fillMaxHeight().background(ColorPrimary))
                        Box(Modifier.weight(maxOf(0.001f, t.codingMin.toFloat() / total)).fillMaxHeight().background(ColorAccent))
                        Box(Modifier.weight(maxOf(0.001f, t.neutralMin.toFloat() / total)).fillMaxHeight().background(ColorMuted))
                        Box(Modifier.weight(maxOf(0.001f, t.funMin.toFloat() / total)).fillMaxHeight().background(ColorWarning))
                    }
                }
                Text("Study ${fmtMin(t.studyMin)} • Coding ${fmtMin(t.codingMin)} • Neutral ${fmtMin(t.neutralMin)} • Fun ${fmtMin(t.funMin)}",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        // ── Rank + score ────────────────────────────────────────────────
        item {
            HubCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(st.rank, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ColorPrimary)
                        Text("Level ${snap.level} • ${st.points} rank points" + (st.toNext?.let { " • $it to next rank" } ?: " • top rank"),
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${st.score}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = ColorText)
                        Text("Discipline", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                }
                st.attrs.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${a.emoji} ${a.name}", color = ColorText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(112.dp))
                        Bar(a.into.toFloat() / a.per, Modifier.weight(1f))
                        Text("Lv ${a.level}", color = ColorMuted, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }

        // ── Today's mission ─────────────────────────────────────────────
        st.mission?.let { m ->
            item {
                HubCard {
                    Text("🧭 ${m.headline}", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                    m.steps.forEach { Text("• $it", color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                    m.suggestions.forEach { q ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("➕ ${q.title}", color = ColorText, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.addSuggestion(q) }) { Text("Add", fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text("Source: ${m.source}", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }
        }

        // ── Season ──────────────────────────────────────────────────────
        st.season?.let { se ->
            item {
                HubCard {
                    Text("🌌 Season ${se.number} — ${se.name}", fontWeight = FontWeight.Bold, color = ColorAccent, style = MaterialTheme.typography.titleSmall)
                    Text("Day ${se.dayIn}/${se.length} • ${st.seasonXp} quest XP this season", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    Bar(se.dayIn.toFloat() / se.length, Modifier.fillMaxWidth())
                    st.seasonGoals.forEach { g -> GoalRow(g, g.key in st.claimed) { vm.claim(g) } }
                }
            }
        }

        // ── Challenges ──────────────────────────────────────────────────
        item {
            HubCard {
                Text("🏁 Challenges", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.challenges.forEach { g -> GoalRow(g, g.key in st.claimed) { vm.claim(g) } }
            }
        }

        // ── Weekly report ───────────────────────────────────────────────
        st.weekly?.let { w ->
            item {
                HubCard {
                    Text("📈 Weekly report", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                    w.lines.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                    Text("Best day: ${w.bestDay}", color = ColorText, style = MaterialTheme.typography.bodyMedium)
                    Text("Weak spot: ${w.weakness}", color = ColorWarning, style = MaterialTheme.typography.bodyMedium)
                    Text("Next step: ${w.recommendation}", color = ColorPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("Source: ${w.source}", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }
        }

        // ── Economy ─────────────────────────────────────────────────────
        item {
            HubCard {
                Text("🪙 Economy (last 7 days)", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.economy.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                Text("Bonuses: 7, 14 and 30 day streaks pay XP and coins; 3 hard quests in a day give 1 h of XP boost; each finished focus session gives XP.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        // ── Pages ───────────────────────────────────────────────────────
        item {
            NavCard("🏆 Badges", "${st.badges.count { it.unlocked }} of ${st.badges.size} unlocked", onOpenBadges)
        }
        item {
            NavCard("⚙️ Rules", "Auto-complete, exam mode, quiet hours, limits", onOpenRules)
        }
    }
}

private fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun Glance(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Text(value, color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GoalRow(g: Goal, claimed: Boolean, onClaim: () -> Unit) {
    val c = g.c
    val done = c.progress >= c.target
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, color = ColorText, fontWeight = FontWeight.SemiBold)
                Text("${c.desc} • ${c.progress}/${c.target} • " + (if (c.xp > 0) "+${c.xp} XP, " else "") + "+${c.coins}🪙",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            if (done && !claimed) Button(onClick = onClaim) { Text("Claim") }
            else if (claimed) Text("Claimed", color = ColorMuted, style = MaterialTheme.typography.labelMedium)
        }
        Bar(c.progress.toFloat() / c.target, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun Bar(frac: Float, modifier: Modifier) {
    val f = frac.coerceIn(0f, 1f)
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorSurfaceHigh)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(ColorPrimary))
    }
}

@Composable
private fun NavCard(title: String, sub: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            Text("›", fontSize = 24.sp, color = ColorMuted)
        }
    }
}
