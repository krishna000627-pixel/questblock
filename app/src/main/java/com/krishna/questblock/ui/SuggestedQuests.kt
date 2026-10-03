package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.STARTER_QUESTS
import com.krishna.questblock.data.StarterQuest

/**
 * Card with 3 random starter quests. Used on Home (when there are no quests) and on the Quests screen.
 * [onAddOwn] = null hides the "write my own" button.
 */
@Composable
fun SuggestedQuests(
    onAdd: (StarterQuest) -> Unit,
    onAddOwn: (() -> Unit)? = null,
    title: String = "Suggested to start",
) {
    var seed by remember { mutableIntStateOf(0) }
    val picks = remember(seed) { STARTER_QUESTS.shuffled().take(3) }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Class 12 Boards + JEE \u00b7 basics first",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { seed++ }) { Text("\uD83D\uDD00 Shuffle", fontWeight = FontWeight.Bold) }
            }
            picks.forEach { q ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ColorSurfaceHigh).padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(q.emoji, fontSize = 22.sp)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(q.title, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(q.note, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    IconButton(onClick = { onAdd(q) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add quest", tint = ColorPrimary)
                    }
                }
            }
            if (onAddOwn != null) {
                TextButton(onClick = onAddOwn) { Text("+ Write my own quest", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
