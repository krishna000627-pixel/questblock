@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.engine.Badge
import com.krishna.questblock.engine.InsightsBuilder
import com.krishna.questblock.engine.Progression
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BadgesViewModel @Inject constructor(
    private val builder: InsightsBuilder,
    val prefs: Prefs,
) : ViewModel() {
    var badges by mutableStateOf<List<Badge>?>(null)
        private set

    init {
        viewModelScope.launch {
            badges = Progression.badgesRemembered(builder.build(), prefs)
        }
    }

    fun unlockedOn(b: Badge): String? {
        val t = prefs.badgeAt(b.id)
        return if (b.unlocked && t > 0L) SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t)) else null
    }
}

@Composable
fun BadgesScreen(onBack: () -> Unit, vm: BadgesViewModel = hiltViewModel()) {
    val list = vm.badges
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Text("Badges", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
            }
        }
        if (list == null) {
            item { HubCard { Text("Loading…", color = ColorMuted) } }
            return@LazyColumn
        }
        val got = list.count { it.unlocked }
        item {
            HubCard {
                Text("$got of ${list.size} unlocked", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleMedium)
                Bar(got.toFloat() / list.size.coerceAtLeast(1), Modifier.fillMaxWidth())
                Text("Badges stay unlocked once you earn them. Hidden ones show up when you unlock them.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        items(list.sortedWith(compareByDescending<Badge> { it.unlocked }.thenBy { it.hidden })) { b ->
            val shown = b.unlocked || !b.hidden
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (b.unlocked) b.emoji else "🔒", fontSize = 30.sp)
                    Column(Modifier.weight(1f)) {
                        Text(if (shown) b.name else "Hidden badge", fontWeight = FontWeight.Bold,
                            color = if (b.unlocked) ColorText else ColorMuted)
                        Text(if (shown) b.desc else "Keep playing to find out what it needs.",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        vm.unlockedOn(b)?.let {
                            Text("Unlocked $it", style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                        }
                    }
                }
            }
        }
    }
}
