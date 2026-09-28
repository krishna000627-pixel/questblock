package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.UnlockEvent
import com.krishna.questblock.data.baseReward
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unlocks = repo.recentUnlocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalSpent = repo.totalCoinsSpent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}

@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val unlocks by vm.unlocks.collectAsStateWithLifecycle()
    val totalSpent by vm.totalSpent.collectAsStateWithLifecycle()

    val totalEarned = tasks.filter { it.done }.sumOf { baseReward(it.difficulty) }
    val completedCount = tasks.count { it.done }
    val pendingCount = tasks.count { !it.done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Stats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
            )
        }

        // Summary row
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "Coins earned", value = "+$totalEarned 🪙", color = ColorCoin)
                StatCard(modifier = Modifier.weight(1f), label = "Coins spent", value = "-${totalSpent ?: 0} 🪙", color = ColorDanger)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "Quests done", value = "$completedCount ✓", color = ColorPrimary)
                StatCard(modifier = Modifier.weight(1f), label = "Pending", value = "$pendingCount", color = ColorMuted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "Best streak", value = "${player.streak} 🔥", color = ColorPrimary)
                StatCard(modifier = Modifier.weight(1f), label = "Level", value = "Lv ${player.level()}", color = ColorPrimary)
            }
        }

        // Unlock history
        item {
            Spacer(Modifier.height(4.dp))
            Text("Unlock history", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
        }

        if (unlocks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No unlocks yet. Stay focused! 💪", style = MaterialTheme.typography.bodyMedium, color = ColorMuted)
                    }
                }
            }
        } else {
            items(unlocks, key = { it.id }) { event ->
                UnlockEventRow(event)
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UnlockEventRow(event: UnlockEvent) {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .background(ColorDanger.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(6.dp),
            ) {
                Text("🔓", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f)) {
                Text(event.label, style = MaterialTheme.typography.bodyMedium, color = ColorText)
                Text(fmt.format(Date(event.timestamp)), style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
            Text("-${event.coinsSpent} 🪙", style = MaterialTheme.typography.labelMedium, color = ColorDanger, fontWeight = FontWeight.Bold)
        }
    }
}
