@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StreakViewModel @Inject constructor(repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private const val MAP_WEEKS = 26
private val CELL = 18.dp
private val GAP = 3.dp

private fun epochDayOf(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

/** v283: the full streak map page (opened by tapping the calendar card on Profile). 26 weeks, month labels, tap a day to see what you finished. */
@Composable
fun StreakScreen(onBack: () -> Unit, vm: StreakViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    // epoch day -> titles finished that day
    val byDay = remember(tasks) {
        tasks.filter { it.done && it.completedAt != null }.groupBy({ epochDayOf(it.completedAt!!) }, { it.title })
    }
    val activeDays = byDay.keys.sorted()
    val bestStreak = remember(byDay) {
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in byDay.keys.sorted()) { run = if (d == prev + 1) run + 1 else 1; if (run > best) best = run; prev = d }
        best
    }
    val total = byDay.values.sumOf { it.size }
    val bestDayEntry = byDay.maxByOrNull { it.value.size }

    var sel by remember { mutableStateOf(today.toEpochDay()) }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Streak map", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorBg),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // numbers
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("\uD83D\uDD25", "${player.streak}d", "Current", Modifier.weight(1f))
                    StatTile("\uD83C\uDFC6", "${maxOf(bestStreak, player.streak)}d", "Best", Modifier.weight(1f))
                    StatTile("\uD83D\uDCC5", "${activeDays.size}", "Active days", Modifier.weight(1f))
                    StatTile("\u2705", "$total", "Quests", Modifier.weight(1f))
                }
            }
            // the map
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Last $MAP_WEEKS weeks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                        HeatMap(today = today, byDay = byDay, selected = sel, onSelect = { sel = it })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Text("Less", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            Spacer(Modifier.width(6.dp))
                            val lk = cellLook()
                            for (n in 0..3) {
                                Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(lk.shape).background(lk.fill(n)))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("More", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                        }
                    }
                }
            }
            // the picked day
            item {
                val date = LocalDate.ofEpochDay(sel)
                val titles = byDay[sel].orEmpty()
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                        if (titles.isEmpty()) {
                            Text(if (date.isAfter(today)) "That day has not come yet" else "No quests finished this day",
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        } else {
                            Text("${titles.size} quest${if (titles.size == 1) "" else "s"} finished",
                                style = MaterialTheme.typography.labelMedium, color = ColorPrimary, fontWeight = FontWeight.SemiBold)
                            titles.take(8).forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall, color = ColorText, maxLines = 2) }
                            if (titles.size > 8) Text("+${titles.size - 8} more", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                        }
                    }
                }
            }
            // best day
            if (bestDayEntry != null) item {
                val d = LocalDate.ofEpochDay(bestDayEntry.key)
                Text("Best day: ${bestDayEntry.value.size} quests on " + d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
                    style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            }
            if (byDay.isEmpty()) item {
                Text("Finish a quest and today lights up here.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
    }
}

/** Weeks as columns (oldest left, this week right), Monday to Sunday as rows, month names on top. Scrolls sideways, starts at this week. */
@Composable
private fun HeatMap(today: LocalDate, byDay: Map<Long, List<String>>, selected: Long, onSelect: (Long) -> Unit) {
    val start = remember(today) { today.minusDays(((today.dayOfWeek.value - 1) + 7 * (MAP_WEEKS - 1)).toLong()) }
    val scroll = rememberScrollState()
    val look = cellLook()
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }
    val dayNames = remember { listOf("M", "", "W", "", "F", "", "") }
    Row(Modifier.fillMaxWidth()) {
        // weekday letters stay fixed on the left
        Column(Modifier.padding(top = 18.dp, end = 6.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
            dayNames.forEach { Box(Modifier.size(width = 12.dp, height = CELL), contentAlignment = Alignment.Center) { Text(it, fontSize = 9.sp, color = ColorMuted) } }
        }
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            for (w in 0 until MAP_WEEKS) {
                val monday = start.plusDays((w * 7).toLong())
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    Box(Modifier.size(width = CELL, height = 15.dp)) {
                        if (monday.dayOfMonth <= 7) Text(monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            fontSize = 9.sp, color = ColorMuted, maxLines = 1, softWrap = false)
                    }
                    for (d in 0 until 7) {
                        val date = monday.plusDays(d.toLong())
                        val key = date.toEpochDay()
                        if (date.isAfter(today)) {
                            Box(Modifier.size(CELL))
                        } else {
                            val n = byDay[key]?.size ?: 0
                            var m = Modifier.size(CELL).clip(look.shape).background(look.fill(n))
                            if (key == selected) m = m.border(1.5.dp, ColorText, look.shape)
                            else if (date == today) m = m.border(1.dp, ColorText.copy(alpha = 0.5f), look.shape)
                            Box(m.clickable { onSelect(key) }.semantics { contentDescription = "$date: $n quests" })
                        }
                    }
                }
            }
        }
    }
}
