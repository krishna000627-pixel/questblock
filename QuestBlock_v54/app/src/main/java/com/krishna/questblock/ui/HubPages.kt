@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Season goals and challenges. */
@Composable
fun GoalsScreen(onBack: () -> Unit, vm: HubViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.store.refresh() }
    val st = vm.store.state
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HubHeader("Season & challenges", onBack) }
        st.season?.let { se ->
            item {
                HubCard {
                    Text("🌌 Season ${se.number} — ${se.name}", fontWeight = FontWeight.Bold, color = ColorAccent, style = MaterialTheme.typography.titleSmall)
                    Text("Day ${se.dayIn}/${se.length}", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    Bar(se.dayIn.toFloat() / se.length, Modifier.fillMaxWidth())
                    st.seasonGoals.forEach { g -> GoalRow(g, g.key in st.claimed) { vm.store.claim(g) } }
                }
            }
        }
        item {
            HubCard {
                Text("🏁 Challenges", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.challenges.forEach { g -> GoalRow(g, g.key in st.claimed) { vm.store.claim(g) } }
            }
        }
    }
}

/** Weekly report and coin economy. */
@Composable
fun ReportsScreen(onBack: () -> Unit, vm: HubViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.store.refresh() }
    val st = vm.store.state
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HubHeader("Weekly report", onBack) }
        st.weekly?.let { w ->
            item {
                HubCard {
                    w.lines.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
                    Text("Best day: ${w.bestDay}", color = ColorText, style = MaterialTheme.typography.bodyMedium)
                    Text("⚠️ ${w.weakness}", color = ColorWarning, style = MaterialTheme.typography.bodyMedium)
                    Text("💡 ${w.recommendation}", color = ColorPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            HubCard {
                Text("🪙 Coins, 7 days", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                st.economy.forEach { Text(it, color = ColorText, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
