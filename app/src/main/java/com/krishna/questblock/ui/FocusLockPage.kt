package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** v204: a Focus session is running and this app was not picked for it. No coins, no unlock: finish the session or go back. */
@Composable
fun FocusLockPage(pkg: String, onHome: () -> Unit, onQuests: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
    Column(
        Modifier.fillMaxSize().background(ColorBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\u26A1", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("Focus is on", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = ColorWarning, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("This app was not picked for this session. Finish the session to use it, or open QuestBlock to see the timer.",
            style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
        // v211: homework due in the next 24 hours
        val soon = remember { com.krishna.questblock.data.Hw.dueSoon(ctx) }
        if (soon.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("\uD83D\uDCDD Homework due soon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ColorWarning, textAlign = TextAlign.Center)
            soon.take(3).forEach {
                Text("${com.krishna.questblock.data.Hw.subjectName(it.subj)}: ${it.text} \u00b7 ${com.krishna.questblock.data.Hw.dueLabel(it.due)}",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onQuests,
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open QuestBlock", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onHome) { Text("Go home", color = ColorMuted) }
    }
}
