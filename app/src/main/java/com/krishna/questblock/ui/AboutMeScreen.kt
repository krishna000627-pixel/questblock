@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.AboutMe

/** The developer's page. Text lives in data/AboutMe.kt. Works offline; only the link rows open other apps. */
@Composable
fun AboutMeScreen(onBack: () -> Unit, onOpenIdeas: () -> Unit = {}, onOpenVersions: () -> Unit = {}, onOpenFeatures: () -> Unit = {}) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            TitleIcon("About me", 24.dp, endPad = 8.dp)
            Text("About me", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        // ── Name + bio ──────────────────────────────────────────────────
        AboutCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👨‍💻", fontSize = 36.sp)
                Column {
                    Text(AboutMe.NAME, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text(AboutMe.TAGLINE, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
            Text(AboutMe.BIO, style = MaterialTheme.typography.bodyMedium, color = ColorText)
        }

        // ── Why ─────────────────────────────────────────────────────────
        AboutCard(onClick = onOpenFeatures) {
            Text("💡 Why I built QuestBlock", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
            Text(AboutMe.WHY, style = MaterialTheme.typography.bodyMedium, color = ColorText)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("See everything the app does", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = ColorPrimary)
                Text("›", color = ColorMuted, fontSize = 20.sp)
            }
        }

        // ── Update prompts & ideas widget ───────────────────────────────
        var ideas by remember { mutableStateOf(com.krishna.questblock.data.IdeaStore.all(ctx)) }
        AboutCard {
            Text("💡 Update prompts & ideas", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
            val openIdeas = ideas.filter { !it.done }
            if (openIdeas.isEmpty()) {
                Text("Write what you want in the next update, then copy it into a chat.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            openIdeas.take(2).forEach { idea ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(idea.text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ColorText, maxLines = 3)
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("QuestBlock idea", idea.text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
            }
            OutlinedButton(onClick = onOpenIdeas, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text(if (ideas.isEmpty()) "Write an idea" else "Open all (${openIdeas.size} open)")
            }
        }

        // ── Links ───────────────────────────────────────────────────────
        AboutCard {
            Text("🔗 Links", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
            AboutMe.LINKS.forEach { link ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val intent = Intent(if (link.url.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(link.url))
                        runCatching { ctx.startActivity(intent) }
                            .onFailure { Toast.makeText(ctx, "No app found to open this", Toast.LENGTH_SHORT).show() }
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(link.emoji, fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text(link.label, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(link.shown, style = MaterialTheme.typography.bodySmall, color = ColorPrimary)
                    }
                    Text("›", color = ColorMuted, fontSize = 20.sp)
                }
            }
        }

        // ── App ─────────────────────────────────────────────────────────
        AboutCard {
            Text("📱 QuestBlock", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
            Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = ColorText,
                modifier = Modifier.clickable { onOpenVersions() })
            Text(AboutMe.CREDITS, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AboutCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
        modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
