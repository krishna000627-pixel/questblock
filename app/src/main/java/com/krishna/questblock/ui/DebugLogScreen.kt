package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.util.DebugLog

@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var log by remember { mutableStateOf(DebugLog.read()) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            TitleIcon("Debug log", 24.dp, endPad = 8.dp)
            Text("Debug log", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { log = DebugLog.read() }, shape = RoundedCornerShape(10.dp)) { Text("Refresh") }
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock log", DebugLog.read()))
                android.widget.Toast.makeText(ctx, "Log copied", android.widget.Toast.LENGTH_SHORT).show()
            }, shape = RoundedCornerShape(10.dp)) { Text("Copy") }
            OutlinedButton(onClick = { DebugLog.clear(); log = "" }, shape = RoundedCornerShape(10.dp)) { Text("Clear") }
        }

        Text(
            if (log.isBlank()) "(empty — try an unlock, then tap Refresh)" else log,
            style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(8.dp))
    }
}
