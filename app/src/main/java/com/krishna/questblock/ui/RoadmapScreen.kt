@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.FEATURE_IDEAS
import com.krishna.questblock.data.IdeaStore

/** The built-in roadmap. Each idea can be put on the Quest board with one tap. */
@Composable
fun RoadmapScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var onBoard by remember { mutableStateOf(IdeaStore.all(ctx).map { it.text }.toSet()) }
    val left = FEATURE_IDEAS.count { it.text !in onBoard }

    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column(Modifier.weight(1f)) {
                    Text("🗺️ Roadmap", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = ColorText)
                    Text("Ideas for the next versions", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                if (left > 0) TextButton(onClick = {
                    FEATURE_IDEAS.filter { it.text !in onBoard }.reversed().forEach { IdeaStore.add(ctx, it.text) }
                    onBoard = IdeaStore.all(ctx).map { it.text }.toSet()
                    Toast.makeText(ctx, "Added $left to the Quest board", Toast.LENGTH_SHORT).show()
                }) { Text("Add all ($left)", fontWeight = FontWeight.Bold) }
            }
        }
        items(FEATURE_IDEAS) { idea ->
            val added = idea.text in onBoard
            RpgPanel("${idea.emoji} ${idea.title}", accent = if (added) ColorMuted else ColorCoin) {
                Text(idea.detail, color = ColorMuted, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        IdeaStore.add(ctx, idea.text)
                        onBoard = IdeaStore.all(ctx).map { it.text }.toSet()
                    },
                    enabled = !added, shape = RoundedCornerShape(10.dp),
                ) { Text(if (added) "✅ On the Quest board" else "📜 Add to Quest board", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
