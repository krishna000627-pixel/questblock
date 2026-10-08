@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.UsageTracker
import com.krishna.questblock.util.MusicLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WeeklyViewModel @Inject constructor(repo: GameRepository) : ViewModel() {
    val log = repo.coinLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** The last 7 days in one place, with one suggestion. */
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit, vm: WeeklyViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { hub.store.refresh() }
    val hubSt = hub.store.state
    val log by vm.log.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val from = UsageTracker.dayStart(6)

    val week = log.filter { it.timestamp >= from }
    val earned = week.filter { it.amount > 0 }.sumOf { it.amount }
    val prevFrom = UsageTracker.dayStart(13)
    val prevEarned = log.filter { it.timestamp in prevFrom until from && it.amount > 0 }.sumOf { it.amount }
    val spent = week.filter { it.amount < 0 }.sumOf { -it.amount }
    val questsDone = tasks.count { it.done && (it.completedAt ?: 0L) >= from }
    val stepsDone = week.count { it.source == "Routine step" }
    val skipped = week.count { it.source == "Skipped step" }
    val musicMin = remember(from) { runCatching { MusicLog.periods(ctx, from, now).sumOf { it.end - it.start } / 60_000L }.getOrDefault(0L) }
    val dayFmt = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val bestDay = week.filter { it.amount > 0 }.groupBy { dayFmt.format(Date(it.timestamp)) }
        .mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }

    val tip = when {
        questsDone == 0 && stepsDone == 0 -> "No quests or routine steps this week. Start with one small routine tomorrow."
        spent > earned -> "You spent more coins than you earned. Do one more quest a day, or raise the unlock price."
        skipped >= 3 -> "You skipped $skipped routine steps. Make the routine shorter so you can finish it."
        musicMin >= 180 -> "Lots of music time (${musicMin / 60}h). Check that it is not eating your fun limit."
        else -> "Good week. Keep the same routine and try one extra quest."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text("Weekly review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                Text("Last 7 days", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        WeekCard("🪙 Coins", "+$earned earned · -$spent spent" + if (prevEarned > 0) "  (${if (earned >= prevEarned) "+" else ""}${earned - prevEarned} vs last week)" else "")
        WeekCard("✅ Quests", "$questsDone finished · $stepsDone routine steps" + if (skipped > 0) " · $skipped skipped" else "")
        WeekCard("🎧 Music off screen", if (musicMin >= 60) "${musicMin / 60}h ${musicMin % 60}m" else "${musicMin}m")
        if (bestDay != null) WeekCard("⭐ Best day", "${bestDay.key} (+${bestDay.value} coins)")
        Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("💡 One change for next week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = ColorText)
            }
        }
        // from the old Hub report: the report lines, the weak spot and the 7-day coin summary
        hubSt.weekly?.let { w ->
            HubCard {
                Text("\uD83D\uDCC8 Report", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                w.lines.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                Text("\u26A0\uFE0F ${w.weakness}", color = ColorWarning, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (hubSt.economy.isNotEmpty()) HubCard {
            Text("\uD83E\uDE99 Coins, 7 days", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
            hubSt.economy.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
        }
        OutlinedButton(onClick = {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Weekly review",
                "Weekly review (last 7 days)\nCoins: +$earned / -$spent\nQuests: $questsDone, routine steps: $stepsDone\nMusic off screen: ${musicMin}m\nTip: $tip"))
            android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Copy review") }
        Text("Coin numbers use your last 500 coin events.", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WeekCard(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ColorMuted)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
        }
    }
}
