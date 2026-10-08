@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.krishna.questblock.data.DAY_LABELS
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.dowIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val tasks        = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player       = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val unlocks      = repo.recentUnlocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements = repo.achievements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val studySessions = repo.studySessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val focusLogs = repo.focusLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private val DATE_FILTERS = listOf("Today", "Week", "Month", "All")

@Composable
fun StatsSection(vm: StatsViewModel = hiltViewModel()) {
    val tasks        by vm.tasks.collectAsStateWithLifecycle()
    val player       by vm.player.collectAsStateWithLifecycle()
    val unlocks      by vm.unlocks.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val sessions     by vm.studySessions.collectAsStateWithLifecycle()

    var dateFilter by remember { mutableStateOf("Week") }
    val now = System.currentTimeMillis()

    val cutoff = when (dateFilter) {
        "Today" -> now - 86_400_000L
        "Week"  -> now - 7 * 86_400_000L
        "Month" -> now - 30 * 86_400_000L
        else    -> 0L
    }

    val filteredDone  = tasks.filter { it.done && (it.completedAt ?: 0L) >= cutoff }
    val filteredSpent = unlocks.filter { it.timestamp >= cutoff }.sumOf { it.coinsSpent }
    val studyCoins    = sessions.filter { it.startedAt >= cutoff }.sumOf { it.coinsEarned }

    // Weekly XP bar chart data (from player log)
    val weeklyData = buildWeeklyXpData(player.weeklyXpLog)

    // Embedded in the Profile page (which already scrolls), so this is a plain Column of blocks.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Date filter
        item {
            Text("📊 Stats", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = ColorText)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DATE_FILTERS) { f ->
                    val sel = dateFilter == f
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = if (sel) ColorPrimary.copy(0.2f) else ColorSurface,
                        modifier = Modifier.clickable { dateFilter = f }) {
                        Text(f, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (sel) ColorPrimary else ColorMuted,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Summary cards
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(Modifier.weight(1f), "✅", "${filteredDone.size}", "Quests Done")
                SummaryCard(Modifier.weight(1f), "🔥", "${player.streak}d", "Streak")
                SummaryCard(Modifier.weight(1f), "⚡", "${player.xp}", "Total XP")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(Modifier.weight(1f), "🪙", "$studyCoins", "Study Coins")
                SummaryCard(Modifier.weight(1f), "💸", "$filteredSpent", "Coins Spent")
                SummaryCard(Modifier.weight(1f), "📚", "${sessions.filter { it.startedAt >= cutoff }.size}", "Study Sessions")
            }
        }

        // Weekly XP bar chart
        if (weeklyData.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("XP This Week", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        XpBarChart(weeklyData)
                    }
                }
            }
        }

        // Quest category breakdown
        if (filteredDone.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("By Category", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        val bycat = filteredDone.groupBy { it.category }
                        bycat.forEach { (cat, list) ->
                            val color = CatColors[cat] ?: ColorPrimary
                            val frac  = list.size.toFloat() / filteredDone.size
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(cat, style = MaterialTheme.typography.bodySmall,
                                    color = color, modifier = Modifier.width(70.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                                    .background(ColorSurfaceHigh)) {
                                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight()
                                        .background(color, RoundedCornerShape(4.dp)))
                                }
                                Text("${list.size}", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            }
                        }
                    }
                }
            }
        }

        // Daily leaderboard
        item { LeaderboardCard(player, weeklyData) }

        // Shorts and Reels counters
        item {
            val sctx = androidx.compose.ui.platform.LocalContext.current
            val yt = remember { com.krishna.questblock.data.ShortsStats.lastDays(sctx, 7, com.krishna.questblock.data.ShortsStats.YT) }
            val ig = remember { com.krishna.questblock.data.ShortsStats.lastDays(sctx, 7, com.krishna.questblock.data.ShortsStats.IG) }
            val igInst = remember { com.krishna.questblock.data.isInstagramInstalled(sctx) }
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎬 Shorts and Reels".let { if (igInst) it else "▶ Shorts" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard(Modifier.weight(1f), "▶", "${yt[0].n}", "Shorts today · ${yt[0].ms / 60_000L}m")
                        if (igInst) SummaryCard(Modifier.weight(1f), "📸", "${ig[0].n}", "Reels today · ${ig[0].ms / 60_000L}m")
                    }
                    Text("Last 7 days (videos · minutes)", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    yt.indices.forEach { i ->
                        val label = if (i == 0) "Today" else if (i == 1) "Yesterday" else "$i days ago"
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = ColorText, modifier = Modifier.weight(1f))
                            Text("▶ ${yt[i].n} · ${yt[i].ms / 60_000L}m" + if (igInst) "     📸 ${ig[i].n} · ${ig[i].ms / 60_000L}m" else "",
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                    }
                    Text("Counted from swipes inside YouTube and Instagram (only YouTube if Instagram is not installed). It is an estimate: comments and replays can add a swipe.",
                        style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }
        }

        // Achievements
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val unlocked = achievements.count { it.unlockedAt != null }
                    Text("🏅 Achievements ($unlocked/${ACHIEVEMENTS_CATALOG.size})",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    ACHIEVEMENTS_CATALOG.forEach { catalog ->
                        val earned = achievements.find { it.id == catalog.id }?.unlockedAt != null
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(catalog.emoji, fontSize = 22.sp,
                                modifier = Modifier.alpha(if (earned) 1f else 0.3f))
                            Column(Modifier.weight(1f)) {
                                Text(catalog.name, style = MaterialTheme.typography.bodySmall,
                                    color = if (earned) ColorText else ColorMuted,
                                    fontWeight = if (earned) FontWeight.Bold else FontWeight.Normal)
                                Text(catalog.description, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            }
                            if (earned) Text("✓", color = ColorPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Keeps the old `item { }` call sites working inside a plain Column. */
@Composable
private fun item(content: @Composable () -> Unit) = content()

@Composable
private fun SummaryCard(modifier: Modifier, emoji: String, value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
        }
    }
}

@Composable
private fun XpBarChart(data: List<Pair<String, Int>>) {
    val maxXp = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom) {
        data.forEach { (day, xp) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(ColorSurfaceHigh), contentAlignment = Alignment.BottomCenter) {
                    val frac = xp.toFloat() / maxXp
                    Box(Modifier.fillMaxWidth().fillMaxHeight(frac.coerceIn(0.05f, 1f))
                        .background(ColorPrimary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                }
                Text(day, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
        }
    }
}

private fun buildWeeklyXpData(log: String): List<Pair<String, Int>> {
    
    val days = (0..6).map { offset ->
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6 + offset) }
        val dayNum = (c.timeInMillis + TimeZone.getDefault().getOffset(c.timeInMillis)) / 86_400_000L
        val dayLabel = SimpleDateFormat("EEE", Locale.getDefault()).format(c.time).take(2)
        dayLabel to dayNum
    }
    val xpByDay = log.split(",").filter { it.contains(":") }.associate {
        val (d, x) = it.split(":"); d.toLong() to (x.toIntOrNull() ?: 0)
    }
    return days.map { (label, dayNum) -> label to (xpByDay[dayNum] ?: 0) }
}

// ── Dedicated Stats screen ─────────────────────────────────────────────────
@Composable
fun StatsScreen(onBack: () -> Unit, onOpenUsage: () -> Unit, vm: StatsViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("Stats", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }
        OutlinedButton(onClick = onOpenUsage, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("\uD83D\uDCF1 Daily app usage")
        }
        StatsSection(vm)
        StatsExtras(vm)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatsExtras(vm: StatsViewModel) {
    val tasks    by vm.tasks.collectAsStateWithLifecycle()
    val sessions by vm.studySessions.collectAsStateWithLifecycle()
    val focus    by vm.focusLogs.collectAsStateWithLifecycle()

    val done = tasks.filter { it.done && it.completedAt != null }
    val byWeekday = IntArray(7).also { arr -> done.forEach { arr[dowIndex(it.completedAt!!)]++ } }
    val maxDay = (byWeekday.maxOrNull() ?: 0).coerceAtLeast(1)

    val today = dayNumber(System.currentTimeMillis())
    val activeDays = done.map { dayNumber(it.completedAt!!) }.toSet()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Completion by weekday
        Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📅 Quests by weekday", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom) {
                    byWeekday.forEachIndexed { i, n ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            Text("$n", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            Box(Modifier.fillMaxWidth().height((56 * n / maxDay).coerceAtLeast(2).dp)
                                .clip(RoundedCornerShape(4.dp)).background(ColorPrimary.copy(if (n == 0) 0.2f else 0.9f)))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEach {
                        Text(it.take(2), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                }
            }
        }

        // Focus + study
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val focusMin = focus.sumOf { it.minutes }
            val finished = focus.count { it.completed }
            ExtraCard(Modifier.weight(1f), "🎯", "$focusMin min", "focus · $finished/${focus.size} finished")
            ExtraCard(Modifier.weight(1f), "📚", "${sessions.sumOf { it.coinsEarned }}🪙", "study · ${sessions.size} sessions")
        }

        // 28-day activity calendar
        Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔥 Last 28 days", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = ColorText)
                for (row in 0 until 4) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until 7) {
                            val day = today - 27 + row * 7 + col
                            val active = day in activeDays
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (active) ColorPrimary else ColorSurfaceHigh))
                        }
                    }
                }
                Text("${activeDays.count { it > today - 28 }} active days", style = MaterialTheme.typography.bodySmall,
                    color = ColorMuted)
            }
        }
    }
}

@Composable
private fun ExtraCard(modifier: Modifier, emoji: String, value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted, maxLines = 1)
        }
    }
}
