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

/** v272: Flags > Fonts. One font for the whole app. Each font's name is written in that font. Default = the normal font. */
@Composable
fun ThemeFontsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var msg by remember { mutableStateOf("") }
    val chosen = ThemeFonts.chosenId
    fun pick(id: String?) {
        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running" else { ThemeFonts.set(ctx, id); msg = "" }
    }
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
                (if (ThemeFonts.on) "" else "Fonts are off in Flags, so the normal font shows. ") +
                    "Tap a font to use it everywhere. Default is the normal font. Fonts only change the letters.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            )
        }
        if (msg.isNotEmpty()) item { Text(msg, style = MaterialTheme.typography.bodySmall, color = ColorDanger) }
        item { FontRow("Default", null, "normal font", chosen == null) { pick(null) } }
        items(APP_FONTS.filter { it.id != "system" }, key = { it.id }) { f ->
            FontRow(f.label, f.family, f.kind, chosen == f.id) { pick(f.id) }
        }
    }
}

/** The name is drawn in the font itself (Default uses the normal font). */
@Composable
private fun FontRow(name: String, family: FontFamily?, kind: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, ColorPrimary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = ColorText, fontFamily = family ?: FontFamily.Default)
                Text(SAMPLE, style = MaterialTheme.typography.bodySmall, color = ColorMuted, fontFamily = family ?: FontFamily.Default)
                Text(kind, style = MaterialTheme.typography.labelSmall, color = ColorMuted, fontFamily = FontFamily.Default)
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = ColorPrimary)
        }
    }
}
