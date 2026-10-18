package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** v226: sub page of the SVG icons flag: switch each icon on or off. An icon that is off keeps its emoji. Terminal: icons list | on <name> | off <name> | reset. */
@Composable
fun SvgIconsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val byName = SvgIcons.EMOJI.entries.groupBy({ it.value }, { it.key })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("SVG icons", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    Text("${SvgIcons.ICONS.size - SvgIconsOff.size} of ${SvgIcons.ICONS.size} icons on. An icon that is off shows its emoji.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { SvgIconsMode.setAll(ctx, true) }, modifier = Modifier.weight(1f)) { Text("All on") }
                OutlinedButton(onClick = { SvgIconsMode.setAll(ctx, false) }, modifier = Modifier.weight(1f)) { Text("All off") }
            }
        }
        byName.forEach { (name, emojis) ->
            item(key = "svg_$name") {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp)) { SvgGlyph(name, ColorPrimary) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, fontWeight = FontWeight.SemiBold, color = ColorText)
                            androidx.compose.material3.Text(emojis.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Switch(checked = name !in SvgIconsOff, onCheckedChange = { SvgIconsMode.setIcon(ctx, name, it) })
                    }
                }
            }
        }
    }
}
