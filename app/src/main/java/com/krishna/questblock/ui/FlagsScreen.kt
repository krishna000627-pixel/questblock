package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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

/** v225: one row of the "More flags" list, shared by the flags page and the terminal. */
class FlagRow(val id: String, val label: String, val desc: String, val get: (android.content.Context) -> Boolean, val set: (android.content.Context, Boolean) -> Unit)

object FlagRows {
    val ALL: List<FlagRow> = listOf(
        FlagRow("icons", "SVG icons", "Emoji drawn as SVG icons on the normal pages", { SvgIconsOn }, { c, v -> SvgIconsMode.set(c, v) }),
        FlagRow("sounds", "Sound effects", "Coin, level up and chime sounds", { com.krishna.questblock.util.Sounds.sfxOn }, { _, v -> com.krishna.questblock.util.Sounds.sfxOn = v }),
        FlagRow("haptics", "Haptics", "Each theme buzzes like its sounds on coin, level up, Focus end and unlock end, and a tick on the Handheld page wipe", { com.krishna.questblock.util.Haptics.on }, { _, v -> com.krishna.questblock.util.Haptics.on = v }),
        FlagRow("themesounds", "Theme sounds", "Every theme has its own coin, level up, chime and alarm sounds. Off = the normal sounds", { com.krishna.questblock.util.Sounds.themeOn }, { _, v -> com.krishna.questblock.util.Sounds.themeOn = v }),
    ) + com.krishna.questblock.data.Flags.EXTRA.map { d ->
        FlagRow(d.id, d.label, d.desc, { com.krishna.questblock.data.Flags.get(d.id) }, { c, v -> com.krishna.questblock.data.Flags.set(c, d.id, v) })
    }
    fun find(w: String?): FlagRow? = ALL.firstOrNull { it.id == w?.lowercase() }
}

/** v219: the flags page (7 taps on the newest entry in Version info). Terminal version: flags. */
@Composable
fun FlagsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val on = remember { mutableStateMapOf<String, Boolean>().also { m -> AppThemes.IDS.forEach { m[it] = AppThemes.enabled(ctx, it) } } }
    var msg by remember { mutableStateOf("") }
    var showIcons by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }
    var showFonts by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    if (showIcons) { SvgIconsPage(onBack = { showIcons = false }); return }
    if (showFx) { ThemeFxFlagsPage(onBack = { showFx = false }); return }
    if (showFonts) { ThemeFontsPage(onBack = { showFonts = false }); return }
    val desc = mapOf(
        "phone" to "The normal pages with the bottom bar",
        "termux" to "The whole app as one terminal",
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                TitleIcon("Flags", 24.dp, endPad = 8.dp)
                Column {
                    Text("Flags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
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
                        Text("Theme effects", fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text("Focus ring, XP bar, level up, done pop and lock screens of every theme. " + themeFxOnCount() + " of " + themeFxTotal() + " on", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    androidx.compose.material3.TextButton(onClick = { showFx = true }) { Text("Open") }
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
                    androidx.compose.material3.TextButton(onClick = { showFonts = true }) { Text("Choose") }
                    Switch(checked = fon, onCheckedChange = { nv ->
                        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                        else { ThemeFonts.setOn(ctx, nv); fon = nv; msg = "" }
                    })
                }
            }
        }
        item(key = "more_head") { Text("More flags", fontWeight = FontWeight.SemiBold, color = ColorMuted, modifier = Modifier.padding(top = 6.dp)) }
        FlagRows.ALL.filter { it.id != "themefx" && !it.id.startsWith("fx_") }.forEach { row ->
            item(key = "flag_" + row.id) {
                var v by remember { mutableStateOf(row.get(ctx)) }
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.label, fontWeight = FontWeight.SemiBold, color = ColorText)
                            Text(row.desc, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        if (row.id == "icons") androidx.compose.material3.TextButton(onClick = { showIcons = true }) { Text("Choose") }
                        Switch(checked = v, onCheckedChange = { nv ->
                            if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
                            else { row.set(ctx, nv); v = nv; msg = "" }
                        })
                    }
                }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
    }
}
