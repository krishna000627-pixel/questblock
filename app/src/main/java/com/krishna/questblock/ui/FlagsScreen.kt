package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.util.Sounds

/** v225: one row of the "More flags" list, shared by the flags page and the terminal. */
class FlagRow(val id: String, val label: String, val desc: String, val get: (android.content.Context) -> Boolean, val set: (android.content.Context, Boolean) -> Unit)

object FlagRows {
    val ALL: List<FlagRow> = listOf(
        FlagRow("icons", "SVG icons", "Emoji drawn as SVG icons on the normal pages", { SvgIconsOn }, { c, v -> SvgIconsMode.set(c, v) }),
        FlagRow("sounds", "Sound effects", "Coin, level up and chime sounds", { Sounds.sfxOn }, { _, v -> Sounds.sfxOn = v }),
        FlagRow("haptics", "Haptics", "Each theme buzzes like its sounds on coin, level up, Focus end and unlock end", { com.krishna.questblock.util.Haptics.on }, { _, v -> com.krishna.questblock.util.Haptics.on = v }),
        FlagRow("themesounds", "Theme sounds", "Every theme has its own coin, level up, chime and alarm sounds. Off = the normal sounds", { Sounds.themeOn }, { _, v -> Sounds.themeOn = v }),
    ) + com.krishna.questblock.data.Flags.EXTRA.map { d ->
        FlagRow(d.id, d.label, d.desc, { com.krishna.questblock.data.Flags.get(d.id) }, { c, v -> com.krishna.questblock.data.Flags.set(c, d.id, v) })
    }
    fun find(w: String?): FlagRow? = ALL.firstOrNull { it.id == w?.lowercase() }
}

private data class FlagGroup(val id: String, val title: String, val subtitle: String, val rows: List<FlagRow>)

private fun groupFlags(rows: List<FlagRow>): List<FlagGroup> {
    val anim = rows.filter { it.id.startsWith("anim_") }
    val audio = rows.filter { it.id in setOf("sounds", "themesounds", "haptics", "haptics_sync") || it.id.startsWith("snd_") }
    val ui = rows.filter { it.id.startsWith("ui_") || it.id in setOf("theme_ux_v2", "theme_strings", "icons", "themeicon", "a11y_bits") }
    val deckIds = setOf(
        "power_suite", "focus_presets", "urge_shield", "focus_mixer", "boss_battle", "loot_chest",
        "coin_vault", "hero_titles", "daily_fortune", "flashcards", "exam_countdown", "water_tracker",
        "scratchpad", "morning_night_routines", "audio_haptic_presets", "json_export",
    )
    val deck = rows.filter { it.id in deckIds }
    val used = (anim + audio + ui + deck).map { it.id }.toSet()
    val pages = rows.filter { it.id !in used }
    return listOf(
        FlagGroup("anim", "Animations & Motion", "Transitions, card cascade, counters, shimmer, combo", anim),
        FlagGroup("audio", "Sounds & Haptics", "UI sounds, hold ticks, ambient audio, synced haptics", audio),
        FlagGroup("ui", "UI, Themes & Layout", "Theme UI/UX v2, per-theme strings, card density, icons", ui),
        FlagGroup("deck", "Command Deck (55)", "Focus, Boss, Loot, Flashcards, Water, Routines, Export", deck),
        FlagGroup("pages", "Companion & App Pages", "Adopt companion, Mood, Leaderboard, Experiments", pages),
    ).filter { it.rows.isNotEmpty() }
}

/** v219 -> v300: developer Flags page with dedicated sub-pages and back navigation. */
@Composable
fun FlagsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val on = remember { mutableStateMapOf<String, Boolean>().also { m -> AppThemes.IDS.forEach { m[it] = AppThemes.enabled(ctx, it) } } }
    var msg by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var showIcons by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }
    var showFonts by remember { mutableStateOf(false) }
    var showAppIcon by remember { mutableStateOf(false) }
    var openGroupId by remember { mutableStateOf<String?>(null) }
    val flagState = remember { mutableStateMapOf<String, Boolean>().also { m -> FlagRows.ALL.forEach { m[it.id] = it.get(ctx) } } }

    val baseRows = remember { FlagRows.ALL.filter { it.id != "themefx" && !it.id.startsWith("fx_") } }
    val groups = remember(baseRows) { groupFlags(baseRows) }

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            openGroupId != null -> openGroupId = null
            else -> onBack()
        }
    }
    if (showIcons) { SvgIconsPage(onBack = { showIcons = false }); return }
    if (showFx) { ThemeFxFlagsPage(onBack = { showFx = false }); return }
    if (showFonts) { ThemeFontsPage(onBack = { showFonts = false }); return }
    if (showAppIcon) { AppIconPage(onBack = { showAppIcon = false }); return }

    val activeGroup = groups.firstOrNull { it.id == openGroupId }
    if (activeGroup != null) {
        FlagGroupSubPage(
            group = activeGroup,
            flagState = flagState,
            onOpenIcons = { showIcons = true },
            onOpenAppIcon = { showAppIcon = true },
            onBack = { openGroupId = null },
        )
        return
    }

    val desc = mapOf(
        "phone" to "The normal pages with the bottom bar",
        "termux" to "The whole app as one terminal",
    )
    val q = query.trim().lowercase()
    val searchResults = remember(q) {
        if (q.isEmpty()) emptyList() else baseRows.filter { q in it.label.lowercase() || q in it.desc.lowercase() || q in it.id.lowercase() }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { Sounds.uiClose(); onBack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                TitleIcon("Flags", 24.dp, endPad = 8.dp)
                Column {
                    Text("Flags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Developer switches · Tap any category to open sub-page", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }
        }
        AppThemes.IDS.forEach { id ->
            item(key = id) {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(AppThemes.label(id) + if (AppThemes.active() == id) "  (in use)" else "", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Text(desc[id].orEmpty(), style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Switch(checked = on[id] == true, onCheckedChange = { v ->
                            val err = AppThemes.setEnabled(ctx, id, v)
                            msg = err ?: ""
                            on[id] = AppThemes.enabled(ctx, id)
                        })
                    }
                }
            }
        }
        item(key = "homework") {
            var hw by remember { mutableStateOf(com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) }
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Homework tracker", fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text("Widget, search, Home pill, notifications, lock page lines, terminal hw", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    Switch(checked = hw, onCheckedChange = { v ->
                        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                        else { com.krishna.questblock.data.Flags.setHomework(ctx, v); hw = v; msg = "Some screens pick it up after you reopen them" }
                    })
                }
            }
        }
        item(key = "theme_fx") {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Theme effects & Studio", fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text("Focus ring, XP bar, level up, done pop, Sound Studio. " + themeFxOnCount() + " of " + themeFxTotal() + " on", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    TextButton(onClick = { showFx = true }) { Text("Open") }
                }
            }
        }
        item(key = "theme_fonts") {
            var fon by remember { mutableStateOf(ThemeFonts.on) }
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fonts", fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text("Now: " + (ThemeFonts.chosenId?.let { ThemeFonts.find(it)?.label } ?: "Default") + ". Off = the normal font", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    TextButton(onClick = { showFonts = true }) { Text("Choose") }
                    Switch(checked = fon, onCheckedChange = { nv ->
                        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                        else { ThemeFonts.setOn(ctx, nv); fon = nv; msg = "" }
                    })
                }
            }
        }

        item(key = "search_flags") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Quick search across all flags") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (q.isNotEmpty()) {
            items(searchResults, key = { "srch_" + it.id }) { row ->
                FlagSwitchCard(
                    row = row,
                    checked = flagState[row.id] ?: row.get(ctx),
                    onOpenIcons = { showIcons = true },
                    onOpenAppIcon = { showAppIcon = true },
                    onToggle = { nv ->
                        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                        else { row.set(ctx, nv); flagState[row.id] = nv; Sounds.uiToggle(nv); msg = "" }
                    },
                )
            }
        } else {
            items(groups, key = { "page_" + it.id }) { grp ->
                val onCount = grp.rows.count { flagState[it.id] == true }
                Card(
                    onClick = { Sounds.uiOpen(); openGroupId = grp.id },
                    colors = CardDefaults.cardColors(containerColor = ColorCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(grp.title, fontWeight = FontWeight.Bold, color = ColorText)
                            Text("${grp.subtitle} · $onCount of ${grp.rows.size} on", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = ColorMuted)
                    }
                }
            }
        }

        if (msg.isNotEmpty()) item { Text(msg, color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FlagGroupSubPage(
    group: FlagGroup,
    flagState: MutableMap<String, Boolean>,
    onOpenIcons: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val onCount = group.rows.count { flagState[it.id] == true }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { Sounds.uiClose(); onBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText)
                }
                Column(Modifier.weight(1f)) {
                    Text("Flags", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    Text(group.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                }
                TextButton(onClick = {
                    if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                    else {
                        val target = onCount < group.rows.size
                        group.rows.forEach { r -> r.set(ctx, target); flagState[r.id] = target }
                        Sounds.uiToggle(target)
                    }
                }) { Text(if (onCount < group.rows.size) "All on" else "All off") }
            }
        }

        items(group.rows, key = { it.id }) { row ->
            FlagSwitchCard(
                row = row,
                checked = flagState[row.id] ?: row.get(ctx),
                onOpenIcons = onOpenIcons,
                onOpenAppIcon = onOpenAppIcon,
                onToggle = { nv ->
                    if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                    else { row.set(ctx, nv); flagState[row.id] = nv; Sounds.uiToggle(nv); msg = "" }
                },
            )
        }

        if (msg.isNotEmpty()) item { Text(msg, color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FlagSwitchCard(
    row: FlagRow,
    checked: Boolean,
    onOpenIcons: () -> Unit,
    onOpenAppIcon: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${row.label} (${row.id})", fontWeight = FontWeight.SemiBold, color = ColorText)
                Text(row.desc, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            if (row.id == "icons") TextButton(onClick = onOpenIcons) { Text("Choose") }
            if (row.id == "themeicon") TextButton(onClick = onOpenAppIcon) { Text("Choose") }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
