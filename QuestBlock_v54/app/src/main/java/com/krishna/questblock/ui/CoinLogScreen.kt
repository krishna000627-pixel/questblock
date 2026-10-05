@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.CoinLog
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CoinLogViewModel @Inject constructor(repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val log = repo.coinLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private val LOG_FILTERS = listOf("All", "Earned", "Spent", "Free")

/**
 * Every earn and spend. Zero-coin lines are "free" opens (daily-limit app, study app, Focus Quest,
 * outside lock hours) and name the reason, so you can see why an app didn't charge you.
 */
@Composable
fun CoinLogScreen(onBack: () -> Unit, vm: CoinLogViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("All") }
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val shown = log.filter {
        when (filter) {
            "Earned" -> it.amount > 0
            "Spent"  -> it.amount < 0
            "Free"   -> it.amount == 0
            else     -> true
        }
    }
    val earned = log.filter { it.amount > 0 }.sumOf { it.amount }
    val spent = log.filter { it.amount < 0 }.sumOf { -it.amount }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Coin log", fontWeight = FontWeight.Bold, color = ColorText,
                    style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColorBg),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Summary("Balance", "${player.coins} 🪙", ColorCoin)
                        Summary("Earned", "+$earned", ColorPrimary)
                        Summary("Spent", "−$spent", ColorDanger)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    items(LOG_FILTERS) { f ->
                        val sel = filter == f
                        Surface(shape = RoundedCornerShape(20.dp),
                            color = if (sel) ColorPrimary.copy(0.2f) else ColorSurface,
                            modifier = Modifier.clickable { filter = f }) {
                            Text(f, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (sel) ColorPrimary else ColorMuted,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(if (log.isEmpty()) "Nothing logged yet. The log starts with v10 — earlier coins aren't listed."
                         else "Nothing in this filter.",
                        color = ColorMuted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(shown, key = { it.id }) { l -> LogRow(l, sdf.format(Date(l.timestamp))) }
        }
    }
}

@Composable
private fun Summary(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(label, color = ColorMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LogRow(l: CoinLog, whenText: String) {
    val color = when { l.amount > 0 -> ColorPrimary; l.amount < 0 -> ColorDanger; else -> ColorAccent }
    Row(
        Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.source, color = ColorText, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (l.detail.isNotBlank()) "${l.detail} · $whenText" else whenText,
                color = ColorMuted, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when { l.amount > 0 -> "+${l.amount} 🪙"; l.amount < 0 -> "−${-l.amount} 🪙"; else -> "free" },
            color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
        )
    }
}
