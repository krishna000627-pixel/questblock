package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
    val ctx = LocalContext.current
    val picks = remember(seed) {
        val ch = chapterSuggestions(ctx).shuffled().take(2)
        (ch + STARTER_QUESTS.shuffled()).take(3)
    }
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

/** Full "💡 Suggested" tab: subject chips + a longer shuffled list. */
@Composable
fun SuggestedPage(header: String, onAdd: (StarterQuest) -> Unit) {
    val subjects = listOf("All", "Physics", "Chemistry", "Maths", "Boards")
    var subject by remember { mutableStateOf("All") }
    var seed by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val picks = remember(subject, seed) {
        val ch = chapterSuggestions(ctx).filter { subject == "All" || it.subject == subject }.shuffled().take(3)
        (ch + STARTER_QUESTS.filter { subject == "All" || it.subject == subject }.shuffled()).take(6)
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
        Text("Class 12 Boards + JEE \u00b7 basics first", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subjects) { sub ->
                val sel = subject == sub
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) ColorPrimary.copy(0.2f) else ColorSurfaceHigh)
                        .clickable { subject = sub }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(sub, color = if (sel) ColorPrimary else ColorMuted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        picks.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorCard).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(q.emoji, fontSize = 24.sp)
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(q.title, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = ColorText)
                    Text(q.note, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                IconButton(onClick = { onAdd(q) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add quest", tint = ColorPrimary)
                }
            }
        }
        TextButton(onClick = { seed++ }, modifier = Modifier.fillMaxWidth()) {
            Text("\uD83D\uDD00 Shuffle", fontWeight = FontWeight.Bold)
        }
    }
}
