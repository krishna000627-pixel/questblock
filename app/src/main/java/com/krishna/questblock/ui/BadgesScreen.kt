@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krishna.questblock.engine.Badge

@Composable
fun BadgesScreen(onBack: () -> Unit, vm: HubViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.store.refresh() }
    val list = vm.store.state.badges
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                TitleWithIcon("\uD83C\uDFC5", "Badges")
            }
        }
        if (list.isEmpty()) return@LazyColumn
        val got = list.count { it.unlocked }
        item {
            HubCard {
                Text("$got of ${list.size} unlocked", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleMedium)
                Bar(got.toFloat() / list.size.coerceAtLeast(1), Modifier.fillMaxWidth())
            }
        }
        items(list.sortedWith(compareByDescending<Badge> { it.unlocked }.thenBy { it.hidden })) { b ->
            val shown = b.unlocked || !b.hidden
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(54.dp).clip(HexShape)
                            .background(if (b.unlocked) Brush.verticalGradient(listOf(ColorPrimary.copy(0.55f), ColorPrimary.copy(0.12f))) else Brush.verticalGradient(listOf(ColorSurfaceHigh, ColorSurfaceHigh)))
                            .border(2.dp, if (b.unlocked) ColorPrimary else ColorDivider, HexShape),
                            contentAlignment = Alignment.Center) {
                            Text(if (b.unlocked) b.emoji else "\uD83D\uDD12", fontSize = 24.sp)
                        }
                    Column(Modifier.weight(1f)) {
                        Text(if (shown) b.name else "???", fontWeight = FontWeight.Bold,
                            color = if (b.unlocked) ColorText else ColorMuted)
                        if (shown) Text(b.desc, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        vm.store.unlockedOn(b)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                        }
                    }
                }
            }
        }
    }
}
