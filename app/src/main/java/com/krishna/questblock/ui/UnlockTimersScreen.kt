package com.krishna.questblock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.dayNumber
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.data.frozenAt
import com.krishna.questblock.data.leftMs
import com.krishna.questblock.data.pseudoLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UnlockTimersViewModel @Inject constructor(repo: GameRepository, val prefs: Prefs) : ViewModel() {
    /** null until the first read, so the page never flashes "nothing unlocked" while loading. */
    val apps = repo.blockedApps.stateIn(viewModelScope, SharingStarted.Eagerly, null as List<BlockedApp>?)
}

private data class TimerRow(val label: String, val leftMs: Long, val paused: Boolean)

/**
 * Opened only from the unlock notification (no button leads here). Plain text rows, no icons and no usage
 * queries, so it opens at once. Shows every app / website / clip that is unlocked or has saved time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockTimersScreen(onBack: () -> Unit, vm: UnlockTimersViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L); now = System.currentTimeMillis() } }

    val rows = apps?.let { list ->
        val prefs = vm.prefs
        val out = mutableListOf<TimerRow>()
        list.filter { !it.isStudyApp }.forEach { a ->
            val away = prefs.awayAt(a.packageName)
            val paused = a.frozenAt(away, now)
            if (a.unlockedUntil > now || paused) out += TimerRow(a.label, a.leftMs(away, now), paused)
        }
        prefs.unlockKeys().forEach { k ->
            val until = prefs.siteUnlockedUntil(k)
            if (until > now) {
                val away = if (k.startsWith("short:")) prefs.awayAt(k) else 0L
                val paused = away > 0L && until > away && dayNumber(away) == dayNumber(now)
                out += TimerRow(pseudoLabel(k) ?: k, if (paused) until - away else until - now, paused)
            }
        }
        out.sortedBy { it.leftMs }
    }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Unlock timers", fontWeight = FontWeight.Bold, color = ColorText,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rows != null && rows.isEmpty()) item {
                Text("Nothing is unlocked and no time is saved.", color = ColorMuted, modifier = Modifier.padding(8.dp))
            }
            items(rows ?: emptyList()) { r ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontWeight = FontWeight.SemiBold, color = ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (r.paused) "⏸ Saved, paused while you are away" else "▶ Running",
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Text(fmtLeft(r.leftMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (r.paused) ColorMuted else if (r.leftMs <= 60_000L) ColorDanger else ColorPrimary)
                    }
                }
            }
        }
    }
}
