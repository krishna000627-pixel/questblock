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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.IdeaStore

/** Write update prompts and ideas, tick them off, and copy them into a chat as one ready prompt. */
@Composable
fun IdeasScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ideas by remember { mutableStateOf(IdeaStore.all(ctx)) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val draft = field.text
    val focus = remember { FocusRequester() }
    fun setDraft(t: String) { field = TextFieldValue(t, TextRange(t.length)) }
    val tags = listOf("🐞 Bug: ", "✨ Feature: ", "🎨 UI: ")

    // put the tag at the start of the line the cursor is on, then leave the cursor ready to type
    fun applyTag(tag: String) {
        val t = field.text
        val pos = field.selection.start.coerceIn(0, t.length)
        val ls = if (pos == 0) 0 else t.lastIndexOf('\n', pos - 1) + 1
        val le = t.indexOf('\n', pos).let { if (it < 0) t.length else it }
        var line = t.substring(ls, le)
        tags.forEach { line = line.removePrefix(it) }
        val newLine = tag + line
        val caret = if (line.isBlank()) ls + tag.length else ls + newLine.length
        field = TextFieldValue(t.substring(0, ls) + newLine + t.substring(le), TextRange(caret))
        focus.requestFocus()
    }
    var editing by remember { mutableIntStateOf(-1) }
    var withLog by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val open = ideas.filter { !it.done }
    val doneCount = ideas.size - open.size

    fun copy(text: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock idea", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }
    fun reload() { ideas = IdeaStore.all(ctx) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text("Update prompts & ideas", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Text("${open.size} open · $doneCount done", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        // ── Write ───────────────────────────────────────────────────────
        Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        AssistChip(onClick = { applyTag(tag) }, label = { Text(tag.trim().removeSuffix(":")) })
                    }
                }
                OutlinedTextField(
                    value = field, onValueChange = { field = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 3, maxLines = 8,
                    placeholder = { Text("What should the next update do?") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (editing >= 0) IdeaStore.update(ctx, editing, draft)
                        else {
                            // each non-empty line becomes its own idea, in the order written
                            val tagOnly = tags.map { it.trim() }
                            draft.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in tagOnly }
                                .reversed().forEach { IdeaStore.add(ctx, it) }
                        }
                        setDraft(""); editing = -1; reload()
                    }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp)) {
                        Text(if (editing >= 0) "Save edit" else "Save")
                    }
                    if (editing >= 0) OutlinedButton(onClick = { setDraft(""); editing = -1 }, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    else OutlinedButton(onClick = { copy(draft) }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text("Copy") }
                }
            }
        }

        // ── Ready prompt ────────────────────────────────────────────────
        if (open.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = withLog, onCheckedChange = { withLog = it })
                Text("Add the last 15 debug log lines", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            Button(
                onClick = {
                    val cur = version.substringBefore('.').toIntOrNull()
                    val head = if (cur != null)
                        "QuestBlock v$cur (latest zip attached). Bump to v${cur + 1} and make these changes. Short reply, give the Termux command."
                    else "QuestBlock (latest zip attached). Make these changes and bump the version. Short reply, give the Termux command."
                    val logPart = if (withLog) "\n\nDebug log (last 15 lines):\n" +
                        com.krishna.questblock.util.DebugLog.read().lines().filter { it.isNotBlank() }.takeLast(15).joinToString("\n") else ""
                    copy(head + "\n\n" + open.mapIndexed { i, it -> "${i + 1}. ${it.text}" }.joinToString("\n") + logPart)
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            ) { Text("📋 Copy as update prompt (${open.size})") }
        }

        // ── List ────────────────────────────────────────────────────────
        if (ideas.isEmpty()) {
            Text("Nothing yet. Write what you want changed, tick it off when it's done.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(ideas) { i, idea ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(checked = idea.done, onCheckedChange = { IdeaStore.toggle(ctx, i); reload() })
                            Text(idea.text, Modifier.padding(top = 12.dp),
                                color = if (idea.done) ColorMuted else ColorText,
                                textDecoration = if (idea.done) TextDecoration.LineThrough else null,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            TextButton(onClick = { copy(idea.text) }) { Text("Copy") }
                            TextButton(onClick = { setDraft(idea.text); editing = i; focus.requestFocus() }) { Text("Edit") }
                            TextButton(onClick = {
                                IdeaStore.delete(ctx, i)
                                if (editing == i) { editing = -1; setDraft("") } else if (editing > i) editing--
                                reload()
                            }) { Text("Delete", color = ColorMuted) }
                        }
                    }
                }
            }
            if (doneCount > 0) item {
                TextButton(onClick = { IdeaStore.clearDone(ctx); editing = -1; reload() },
                    modifier = Modifier.fillMaxWidth()) { Text("Clear $doneCount done", color = ColorMuted) }
            }
        }
    }
}
