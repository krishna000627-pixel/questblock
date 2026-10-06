@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.krishna.questblock.data.IdeaStore

/** Write update prompts and ideas, then copy them into a chat. */
@Composable
fun IdeasScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ideas by remember { mutableStateOf(IdeaStore.all(ctx)) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableIntStateOf(-1) }

    fun copy(text: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock idea", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("Update prompts & ideas", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
            placeholder = { Text("What should the next update do?") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (editing >= 0) IdeaStore.update(ctx, editing, draft) else IdeaStore.add(ctx, draft)
                draft = ""; editing = -1; ideas = IdeaStore.all(ctx)
            }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text(if (editing >= 0) "Save edit" else "Save") }
            OutlinedButton(onClick = { copy(draft) }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text("Copy") }
            if (ideas.size > 1) OutlinedButton(onClick = { copy(ideas.joinToString("\n\n") { "- $it" }) },
                shape = RoundedCornerShape(10.dp)) { Text("Copy all") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(ideas) { i, idea ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(idea, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                        Row {
                            TextButton(onClick = { copy(idea) }) { Text("Copy") }
                            TextButton(onClick = { draft = idea; editing = i }) { Text("Edit") }
                            TextButton(onClick = {
                                IdeaStore.delete(ctx, i); ideas = IdeaStore.all(ctx)
                                if (editing == i) { editing = -1; draft = "" }
                            }) { Text("Delete", color = ColorMuted) }
                        }
                    }
                }
            }
        }
    }
}
