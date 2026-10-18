package com.krishna.questblock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.NEXT_UP
import com.krishna.questblock.data.VERSION_LOG

@Composable
fun VersionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    // v219: 7 quick taps on the newest entry open the flags page
    var showFlags by remember { mutableStateOf(false) }
    var taps by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    if (showFlags) { FlagsScreen(onBack = { showFlags = false }); return }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("Version info", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Installed: v$current", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🔭 Coming next", fontWeight = FontWeight.Bold, color = ColorPrimary)
                    NEXT_UP.trim().lines().forEach { line ->
                        if (line.isNotBlank()) Text(line.trim(), style = MaterialTheme.typography.bodySmall, color = ColorText)
                    }
                }
            }
        }
        items(VERSION_LOG) { entry ->
            val latest = entry === VERSION_LOG.first()
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().then(if (!latest) Modifier else Modifier.clickable {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap > 1500L) 1 else taps + 1
                    lastTap = now
                    if (taps >= 7) { taps = 0; showFlags = true }
                })) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("v${entry.version}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (entry.version == current) ColorPrimary else ColorMuted)
                        if (entry.version == current)
                            Text("installed", fontSize = 11.sp, color = ColorPrimary, fontWeight = FontWeight.Bold)
                    }
                    Text(entry.headline, fontWeight = FontWeight.SemiBold, color = ColorText)
                    entry.items.forEach { item ->
                        Text("• $item", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
