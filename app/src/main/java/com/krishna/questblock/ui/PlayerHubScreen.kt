@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
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
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.engine.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val challenges: List<Challenge> = emptyList(),
    val claimed: Set<String> = emptySet(),
    val season: SeasonInfo? = null,
    val autoDone: Int = 0,
    val economy: List<String> = emptyList(),
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
    var autoVerify by mutableStateOf(prefs.autoVerify)
        private set
    var examMode by mutableStateOf(prefs.examMode)
        private set
    var weekendLimit by mutableIntStateOf(prefs.weekendFunLimitMin)
        private set
    var exitPenalty by mutableIntStateOf(prefs.focusExitPenalty)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val auto = runCatching { verifier.run() }.getOrDefault(0)
            val snap = builder.build()
            val advisor = AdvisorProvider.get()
            val mission = advisor.mission(snap)
            val weekly = advisor.weekly(snap)
            val points = Progression.rankPoints(snap)
            val (rank, toNext) = Progression.rank(points)
            val ch = Progression.challenges(snap)
            val since = System.currentTimeMillis() - 7 * 86_400_000L
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
                score = Progression.disciplineScore(snap), badges = Progression.badges(snap),
                challenges = ch, claimed = ch.map { Progression.claimKey(it) }.filter { prefs.isClaimed(it) }.toSet(),
                season = Progression.season(prefs), autoDone = auto, economy = eco,
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

    fun claim(c: Challenge) {
        viewModelScope.launch {
            val key = Progression.claimKey(c)
            if (c.progress >= c.target && !prefs.isClaimed(key)) {
                prefs.setClaimed(key)
                repo.grantBonus(c.coins, c.xp, "Challenge", c.title)
                refresh()
            }
        }
    }

    // not named setAutoVerify: the property above already has a (private) setter with that JVM name
    fun updateAutoVerify(v: Boolean) { prefs.autoVerify = v; autoVerify = v }
    fun setExam(v: Boolean) { prefs.examMode = v; examMode = v; refresh() }
    fun setWeekend(v: Int) { prefs.weekendFunLimitMin = v; weekendLimit = v }
    fun setPenalty(v: Int) { prefs.focusExitPenalty = v; exitPenalty = prefs.focusExitPenalty }
}

@Composable
fun PlayerHubScreen(onBack: () -> Unit, vm: HubViewModel = hiltViewModel()) {
    val st = vm.state
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
            HubCard { Text("✅ Auto-completed ${st.autoDone} study quest${if (st.autoDone > 1) "s" else ""} from your study time",
                color = ColorPrimary, fontWeight = FontWeight.SemiBold) }
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
                st.season?.let {
                    Text("Season ${it.number} — ${it.name} • day ${it.dayIn}/${it.length}",
                        style = MaterialTheme.typography.labelMedium, color = ColorAccent)
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

        // ── Challenges ──────────────────────────────────────────────────
        item {
            HubCard {
                Text("🏁 Challenges", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.challenges.forEach { c ->
                    val key = Progression.claimKey(c)
                    val done = c.progress >= c.target
                    val claimed = key in st.claimed
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.title, color = ColorText, fontWeight = FontWeight.SemiBold)
                                Text("${c.desc} • ${c.progress}/${c.target} • +${c.xp} XP, +${c.coins}🪙",
                                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                            }
                            if (done && !claimed) Button(onClick = { vm.claim(c) }) { Text("Claim") }
                            else if (claimed) Text("Claimed", color = ColorMuted, style = MaterialTheme.typography.labelMedium)
                        }
                        Bar(c.progress.toFloat() / c.target, Modifier.fillMaxWidth())
                    }
                }
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
                Text("\uD83E\uDE99 Economy (last 7 days)", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.economy.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                Text("Bonuses: 7, 14 and 30 day streaks pay XP and coins; 3 hard quests in a day give 1 h of XP boost; each finished focus session gives XP.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        // ── Badges ──────────────────────────────────────────────────────
        item {
            HubCard {
                Text("🏆 Badges (last 14 days)", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.badges.forEach { b ->
                    val shown = b.unlocked || !b.hidden
                    Text(
                        if (shown) "${if (b.unlocked) b.emoji else "🔒"} ${b.name} — ${b.desc}" else "🔒 ??? — hidden badge",
                        color = if (b.unlocked) ColorText else ColorMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // ── Rules ───────────────────────────────────────────────────────
        item {
            HubCard {
                Text("⚙️ Rules", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-complete study quests", color = ColorText)
                        Text("A Study quest with a time in its name (e.g. \"Physics 45 min\") ticks itself from study app time.",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    Switch(checked = vm.autoVerify, onCheckedChange = { vm.updateAutoVerify(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Exam mode", color = ColorText)
                        Text("Fun limit capped at $EXAM_MIN min, study target 6 h, 4 focus sessions.",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    Switch(checked = vm.examMode, onCheckedChange = { vm.setExam(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Weekend fun limit", color = ColorText, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.setWeekend((vm.weekendLimit - 15).coerceIn(0, 360)) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
                    Text(if (vm.weekendLimit == 0) "same" else "${vm.weekendLimit} min", color = ColorText, fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 70.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.setWeekend((vm.weekendLimit + 15).coerceIn(0, 360)) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Focus give-up penalty", color = ColorText, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.setPenalty(vm.exitPenalty - 5) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
                    Text(if (vm.exitPenalty == 0) "off" else "${vm.exitPenalty} \uD83E\uDE99", color = ColorText, fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 70.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.setPenalty(vm.exitPenalty + 5) },
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
                }
            }
        }
    }
}

private const val EXAM_MIN = com.krishna.questblock.data.EXAM_FUN_LIMIT_MIN

@Composable
private fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Bar(frac: Float, modifier: Modifier) {
    val f = frac.coerceIn(0f, 1f)
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorSurfaceHigh)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(ColorPrimary))
    }
}
