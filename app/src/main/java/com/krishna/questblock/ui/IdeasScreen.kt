@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.IdeaStore

/** One kind of note: the text the line starts with, the short name shown on the pill, and its colour. */
internal class IdeaTag(val prefix: String, val label: String, val color: Color)

internal val IDEA_TAGS = listOf(
    IdeaTag("🐞 Bug: ", "🐞 Bug", Color(0xFFFF5C5C)),
    IdeaTag("✨ Feature: ", "✨ Feature", Color(0xFFFFC857)),
    IdeaTag("🎨 UI: ", "🎨 UI", Color(0xFFB45CFF)),
    IdeaTag("⌨️ Termux: ", "⌨️ Termux", Color(0xFF3DDC84)),
    IdeaTag("💡 Idea: ", "💡 Idea", Color(0xFF5C9EFF)),
)

/** v209: added to the update prompt so every change also gets a Termux theme version. */
internal const val TERMUX_RULE =
    "- The Termux theme (ui/Termux*.kt) must keep working: for every change above, add or extend its terminal command with help text, " +
        "suggestion chips and Focus/strict rules, and give any new page or lock screen a terminal version. Nothing in the terminal may open a normal page."

/** Where an idea sits in the update prompt: new tag order first, then the old emoji tags, then the rest. */
internal fun ideaRank(i: com.krishna.questblock.data.Idea): Int {
    val order = listOf("bug", "launcher", "ui", "theme", "other")
    if (i.tag.isNotEmpty()) return order.indexOf(i.tag).let { if (it < 0) 9 else it }
    val t = tagOf(i.text)
    return 10 + (if (t == null) 9 else IDEA_TAGS.indexOf(t))
}

internal fun tagOf(text: String): IdeaTag? =
    IDEA_TAGS.firstOrNull { text.startsWith(it.prefix.trim()) } ?:
        IDEA_TAGS.firstOrNull { text.startsWith(it.label.substringBefore(' ')) }

/** The quest board: write update prompts and ideas, tick them off, and copy them into a chat as one ready prompt. */
@Composable
fun IdeasScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ideas by remember { mutableStateOf(IdeaStore.all(ctx)) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val draft = field.text
    val focus = remember { FocusRequester() }
    fun setDraft(t: String) { field = TextFieldValue(t, TextRange(t.length)) }

    // put the tag at the start of the line the cursor is on, then leave the cursor ready to type
    fun applyTag(tag: String) {
        val t = field.text
        val pos = field.selection.start.coerceIn(0, t.length)
        val ls = if (pos == 0) 0 else t.lastIndexOf('\n', pos - 1) + 1
        val le = t.indexOf('\n', pos).let { if (it < 0) t.length else it }
        var line = t.substring(ls, le)
        IDEA_TAGS.forEach { line = line.removePrefix(it.prefix) }
        val newLine = tag + line
        val caret = if (line.isBlank()) ls + tag.length else ls + newLine.length
        field = TextFieldValue(t.substring(0, ls) + newLine + t.substring(le), TextRange(caret))
        focus.requestFocus()
    }
    var editing by remember { mutableIntStateOf(-1) }
    var withLog by remember { mutableStateOf(false) }
    var withTermux by remember { mutableStateOf(true) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val open = ideas.filter { it.status != "done" }
    val doneCount = ideas.size - open.size

    fun copy(text: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock idea", text))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }
    fun reload() { ideas = IdeaStore.all(ctx) }
    var q by remember { mutableStateOf("") }
    var tagF by remember { mutableStateOf("") }
    var statusF by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    fun sendPrompt(list: List<com.krishna.questblock.data.Idea>, indices: List<Int>) {
                val cur = version.substringBefore('.').toIntOrNull()
                val head = if (cur != null)
                    "QuestBlock v$cur (latest zip attached). Bump to v${cur + 1} and make these changes. Short reply, give the Termux command."
                else "QuestBlock (latest zip attached). Make these changes and bump the version. Short reply, give the Termux command."
                val logPart = if (withLog) "\n\nDebug log (last 15 lines):\n" +
                    com.krishna.questblock.util.DebugLog.read().lines().filter { it.isNotBlank() }.takeLast(15).joinToString("\n") else ""
                // changes grouped: bugs, features, UI, ideas, then untagged; numbered straight through
                val ordered = list.sortedWith(compareByDescending<com.krishna.questblock.data.Idea> { it.prio }.thenBy { ideaRank(it) })
                val changes = ordered.mapIndexed { i, it -> "${i + 1}. " + (if (it.tag.isNotEmpty()) "[${it.tag}] " else "") + (if (it.prio > 0) "(priority ${it.prio}) " else "") + it.text }.joinToString("\n")
                val rules = "Rules:\n" +
                    "- Bump versionCode and versionName in app/build.gradle.kts.\n" +
                    "- Add a new entry at the top of VERSION_LOG in data/VersionLog.kt. Read VersionLog.kt first for more info: it holds the full history of past updates and what each one changed, so do not redo or undo them.\n" +
                    "- Change only what the list above needs.\n" +
                    "- Keep the reply short." + (if (withTermux) "\n" + TERMUX_RULE else "")
                val cmd = if (cur != null) {
                    val n = cur + 1
                    "\n\nEnd your reply with this Termux command:\n" +
                        "cd ~ && unzip -oq ~/storage/downloads/QuestBlock_v$n.zip -d ~/qb$n && cp -r ~/qb$n/QuestBlock_v$n/. ~/QuestBlock/ && rm -rf ~/qb$n && cd ~/QuestBlock && bash termux/release.sh \"v$n: short summary\""
                } else ""
                copy(head + "\n\n" + changes + "\n\n" + rules + cmd + logPart)
                // everything that was just copied is now sent: tick it all off
                val sent = indices
                sent.forEach { IdeaStore.setMeta(ctx, it, status = "done") }
                editing = -1; reload()
                Toast.makeText(ctx, "Copied. ${sent.size} scrolls ticked off", Toast.LENGTH_SHORT).show()
    }


    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                    Column(Modifier.weight(1f)) {
                        Text("📜 Quest board", style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black, color = ColorText, maxLines = 1)
                        Text("Update scrolls for the next version", style = MaterialTheme.typography.bodySmall,
                            color = ColorMuted, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = ColorCoin.copy(alpha = 0.15f)) {
                        Text("${open.size} open · $doneCount done", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium, color = ColorCoin, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        // ── Write ───────────────────────────────────────────────────────
        item {
            RpgPanel(if (editing >= 0) "✏️ Edit scroll" else "✍️ New scroll", accent = ColorCoin) {
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IDEA_TAGS.forEach { tag ->
                        AssistChip(
                            onClick = { applyTag(tag.prefix) },
                            label = { Text(tag.label.substringAfter(' '), fontWeight = FontWeight.SemiBold) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, tag.color.copy(alpha = 0.7f)),
                            colors = AssistChipDefaults.assistChipColors(labelColor = tag.color),
                        )
                    }
                }
                OutlinedTextField(
                    value = field, onValueChange = { field = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 3, maxLines = 8,
                    placeholder = { Text("What should the next update do? One line = one scroll.") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (editing >= 0) IdeaStore.update(ctx, editing, draft)
                        else {
                            // each non-empty line becomes its own idea, in the order written
                            val tagOnly = IDEA_TAGS.map { it.prefix.trim() }
                            draft.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in tagOnly }
                                .reversed().forEach { IdeaStore.add(ctx, it) }
                        }
                        setDraft(""); editing = -1; reload()
                    }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCoin, contentColor = Color(0xFF1A1200))) {
                        Text(if (editing >= 0) "Save edit" else "Save scroll", fontWeight = FontWeight.Bold)
                    }
                    if (editing >= 0) OutlinedButton(onClick = { setDraft(""); editing = -1 }, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                    else OutlinedButton(onClick = { copy(draft) }, enabled = draft.isNotBlank(), shape = RoundedCornerShape(10.dp)) { Text("Copy") }
                }
            }
        }

        // ── Ready prompt ────────────────────────────────────────────────
        if (open.isNotEmpty()) item {
            RpgPanel("🚀 Send to the forge", accent = ColorPrimary) {
                Button(
                    onClick = { sendPrompt(open, ideas.indices.filter { ideas[it].status != "done" }) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("📋 Copy as update prompt (${open.size})", fontWeight = FontWeight.Bold) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withLog, onCheckedChange = { withLog = it })
                    Text("Add the last 15 debug log lines", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withTermux, onCheckedChange = { withTermux = it })
                    Text("Also cover the Termux theme (a command and terminal page for each change)", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
        }

        // ── List ────────────────────────────────────────────────────────
        if (ideas.isEmpty()) item {
            Text("The board is empty. Write what you want changed, tick it off when it's done.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted, modifier = Modifier.padding(4.dp))
        }
        // ── Search and filters ──────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search scrolls") })
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tagF == "", onClick = { tagF = "" }, label = { Text("All tags") })
                    IdeaStore.TAGS.forEach { t -> FilterChip(selected = tagF == t, onClick = { tagF = if (tagF == t) "" else t }, label = { Text(t) }) }
                }
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = statusF == "", onClick = { statusF = "" }, label = { Text("Any status") })
                    IdeaStore.STATUSES.forEach { t -> FilterChip(selected = statusF == t, onClick = { statusF = if (statusF == t) "" else t }, label = { Text(t) }) }
                }
                if (selected.isNotEmpty()) {
                    Button(
                        onClick = {
                            val idx = selected.filter { it in ideas.indices }.sorted()
                            sendPrompt(idx.map { ideas[it] }, idx)
                            selected = emptySet()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text("\uD83D\uDCCB Copy ${selected.size} ticked as one update prompt", fontWeight = FontWeight.Bold) }
                }
            }
        }
        val shown = ideas.withIndex().filter { (_, it) ->
            (q.isBlank() || it.text.contains(q.trim(), ignoreCase = true)) && (tagF.isEmpty() || it.tag == tagF) && (statusF.isEmpty() || it.status == statusF)
        }.sortedWith(compareByDescending<IndexedValue<com.krishna.questblock.data.Idea>> { it.value.pin }.thenByDescending { it.value.prio })
        items(shown.size) { n ->
            val i = shown[n].index
            val idea = shown[n].value
            val tag = tagOf(idea.text)
            val tagColor = when (idea.tag) { "bug" -> Color(0xFFFF5C5C); "ui" -> Color(0xFFB45CFF); "launcher" -> Color(0xFF3DDC84); "theme" -> Color(0xFFFFC857); "other" -> Color(0xFF5C9EFF); else -> tag?.color ?: ColorPrimary }
            val done = idea.status == "done"
            val accent = if (done) ColorMuted else tagColor
            val body = tag?.let { t -> idea.text.removePrefix(t.prefix).removePrefix(t.prefix.trim()).trim().ifEmpty { idea.text } } ?: idea.text
            Surface(shape = RoundedCornerShape(14.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                    Column(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = i in selected, onCheckedChange = { c -> selected = if (c) selected + i else selected - i })
                            Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(6.dp), color = accent.copy(alpha = 0.15f), onClick = {
                                    val order = listOf("") + IdeaStore.TAGS
                                    IdeaStore.setMeta(ctx, i, tag = order[(order.indexOf(idea.tag) + 1) % order.size]); reload()
                                }) { Text(if (idea.tag.isEmpty()) (tag?.label ?: "+ tag") else idea.tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold) }
                                Surface(shape = RoundedCornerShape(6.dp), color = ColorMuted.copy(alpha = 0.18f), onClick = {
                                    IdeaStore.setMeta(ctx, i, status = IdeaStore.STATUSES[(IdeaStore.STATUSES.indexOf(idea.status) + 1) % IdeaStore.STATUSES.size]); reload()
                                }) { Text(idea.status, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = ColorText, fontWeight = FontWeight.Bold) }
                                Surface(shape = RoundedCornerShape(6.dp), color = ColorMuted.copy(alpha = 0.18f), onClick = { IdeaStore.setMeta(ctx, i, prio = (idea.prio + 1) % 4); reload() }) {
                                    Text(if (idea.prio == 0) "P-" else "P${idea.prio}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = ColorText, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(if (idea.pin) "\u2605" else "\u2606", color = if (idea.pin) ColorCoin else ColorMuted,
                                modifier = Modifier.clickable { IdeaStore.setMeta(ctx, i, pin = !idea.pin); reload() }.padding(8.dp))
                        }
                        Text(body, Modifier.padding(start = 14.dp, end = 10.dp, bottom = 2.dp),
                            color = if (done) ColorMuted else ColorText,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { copy(idea.text) }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.ContentCopy, "Copy", tint = ColorMuted, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { setDraft(idea.text); editing = i; focus.requestFocus() }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Edit, "Edit", tint = ColorMuted, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = {
                                IdeaStore.delete(ctx, i)
                                selected = emptySet()
                                if (editing == i) { editing = -1; setDraft("") } else if (editing > i) editing--
                                reload()
                            }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = ColorDanger.copy(alpha = 0.8f), modifier = Modifier.size(18.dp)) }
                        }
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
