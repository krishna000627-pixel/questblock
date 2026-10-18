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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/** v219: the flags page (7 taps on the newest entry in Version info). Terminal version: flags. */
@Composable
fun FlagsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val on = remember { mutableStateMapOf<String, Boolean>().also { m -> AppThemes.IDS.forEach { m[it] = AppThemes.enabled(ctx, it) } } }
    var msg by remember { mutableStateOf("") }
    val desc = mapOf(
        "phone" to "The normal pages with the bottom bar",
        "termux" to "The whole app as one terminal",
        "home" to "The launcher (home screen and drawer)",
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                TitleIcon("Flags", 24.dp, endPad = 8.dp)
                Column {
                    Text("Flags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Turn an app theme or the homework tracker on or off. A theme that is off can't be equipped.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
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
        if (msg.isNotEmpty()) item { Text(msg, color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
    }
}
