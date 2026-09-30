@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
}

private val DATE_FILTERS = listOf("Today", "Week", "Month", "All")

@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
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

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Date filter
        item {
            Text("Stats", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = ColorText)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DATE_FILTERS) { f ->
                    val sel = dateFilter == f
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = if (sel) ColorPrimary.copy(0.2f) else ColorSurface,
                        modifier = Modifier.clickable { dateFilter = f }) {
                        Text(f, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = if (sel) ColorPrimary else ColorMuted,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Summary cards
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(Modifier.weight(1f), "✅", "${filteredDone.size}", "Quests Done")
                SummaryCard(Modifier.weight(1f), "🔥", "${player.streak}d", "Streak")
                SummaryCard(Modifier.weight(1f), "⚡", "${player.xp}", "Total XP")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Simulated Leaderboard
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🏆 Weekly Leaderboard", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Simulated — based on your XP history",
                        style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    val board = buildLeaderboard(player, weeklyData)
                    board.forEachIndexed { i, (name, xp) ->
                        val isYou = name == (player.name.ifBlank { "You" })
                        val rankColor = when (i) { 0 -> ColorCoin; 1 -> ColorMuted; 2 -> ColorWarning; else -> ColorMuted }
                        Row(Modifier.fillMaxWidth()
                            .background(if (isYou) ColorPrimary.copy(0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black, color = rankColor, modifier = Modifier.width(24.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium,
                                color = if (isYou) ColorPrimary else ColorText,
                                fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            Text("$xp XP", style = MaterialTheme.typography.labelMedium,
                                color = if (isYou) ColorPrimary else ColorMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Achievements
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun SummaryCard(modifier: Modifier, emoji: String, value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 20.sp)
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

private fun buildLeaderboard(player: Player, weeklyData: List<Pair<String, Int>>): List<Pair<String, Int>> {
    val yourXp = weeklyData.sumOf { it.second }
    val you = (player.name.ifBlank { "You" }) to yourXp
    // Simulated rivals based on your XP so it's always competitive
    val rivals = listOf(
        "Alex_Dev" to (yourXp * 0.85).toInt().coerceAtLeast(10),
        "StudyPro" to (yourXp * 1.12).toInt().coerceAtLeast(15),
        "NightOwl" to (yourXp * 0.6).toInt().coerceAtLeast(5),
        "Focused_K" to (yourXp * 1.3).toInt().coerceAtLeast(20),
        "QuestMstr" to (yourXp * 0.45).toInt().coerceAtLeast(3),
    )
    return (rivals + you).sortedByDescending { it.second }
}
