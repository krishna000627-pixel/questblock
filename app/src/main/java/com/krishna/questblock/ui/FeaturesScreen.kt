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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.AppFeatures

/** v260: everything the app does, in groups. Opened by tapping "Why I built QuestBlock" on About me. Text lives in data/AppFeatures.kt. */
@Composable
fun FeaturesScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text("About me", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                Text("What the app does", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
            }
        }
        Text(AppFeatures.INTRO, style = MaterialTheme.typography.bodyMedium, color = ColorMuted, modifier = Modifier.padding(horizontal = 4.dp))
        AppFeatures.GROUPS.forEach { g ->
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    g.items.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("\u2022", color = ColorPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = ColorText, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
