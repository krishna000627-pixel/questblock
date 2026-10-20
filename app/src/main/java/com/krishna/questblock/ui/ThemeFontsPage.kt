package com.krishna.questblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val SAMPLE = "Read chapter 5 and solve 10 questions"

/** v270: Flags > Fonts. One row per theme; tap a theme to pick its font. Same data as the terminal command font. */
@Composable
fun ThemeFontsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var open by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    val k = open
    if (k != null) { ThemeFontPicker(key = k, onBack = { open = null }); return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("Flags", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    Text("Fonts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                }
            }
        }
        item {
            Text(
                "${APP_FONTS.size} fonts, one for each theme. Tap a theme to pick its font. The font of the theme you use now changes the whole app at once; the others show when you switch to them. Fonts only change the letters.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            )
        }
        if (msg.isNotEmpty()) item { Text(msg, style = MaterialTheme.typography.bodySmall, color = ColorDanger) }
        items(ThemeFonts.THEMES, key = { it.first }) { (key, name) ->
            val id = ThemeFonts.resolvedId(key)
            val own = ThemeFonts.chosen(key) != null
            val font = ThemeFonts.find(id)
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { open = key }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(SAMPLE, style = MaterialTheme.typography.bodyMedium, color = ColorText, fontFamily = font?.family ?: FontFamily.Default)
                        Text((font?.label ?: id) + if (own) " · your pick" else " · theme default", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                    Text("Change", color = ColorPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            TextButton(onClick = {
                if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running" else { ThemeFonts.resetAll(ctx); msg = "" }
            }) { Text("Reset every theme to its default font") }
        }
    }
}

@Composable
private fun ThemeFontPicker(key: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var msg by remember { mutableStateOf("") }
    val name = ThemeFonts.THEMES.firstOrNull { it.first == key }?.second ?: key
    val chosen = ThemeFonts.chosen(key)
    fun pick(id: String?) {
        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running" else { ThemeFonts.set(ctx, key, id); msg = "" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("Fonts", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                }
            }
        }
        item { Text("Tap a font to use it for this theme.", style = MaterialTheme.typography.bodySmall, color = ColorMuted) }
        if (msg.isNotEmpty()) item { Text(msg, style = MaterialTheme.typography.bodySmall, color = ColorDanger) }
        item {
            val d = ThemeFonts.find(ThemeFonts.defaultId(key))
            FontRow("Theme default: " + (d?.label ?: "System font"), d?.family, chosen == null) { pick(null) }
        }
        items(APP_FONTS, key = { it.id }) { f ->
            FontRow(f.label + "  ·  " + f.kind, f.family, chosen == f.id) { pick(f.id) }
        }
    }
}

@Composable
private fun FontRow(title: String, family: FontFamily?, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, ColorPrimary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                Text(SAMPLE, style = MaterialTheme.typography.titleMedium, color = ColorText, fontFamily = family ?: FontFamily.Default)
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = ColorPrimary)
        }
    }
}
