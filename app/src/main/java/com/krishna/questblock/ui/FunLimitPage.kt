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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.FunLimit

/** Shown instead of the unlock screen when the daily Fun & Games limit is used up. No unlocking here. */
@Composable
fun FunLimitPage(pkg: String, onHome: () -> Unit, onQuests: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) {
        if (pkg.startsWith("site:")) pkg.removePrefix("site:") else if (pkg.startsWith("short:") || pkg.startsWith("yt:")) (com.krishna.questblock.data.pseudoLabel(pkg) ?: pkg) else runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
    val st = FunLimit.last
    Column(
        Modifier.fillMaxSize().background(ColorBg).let { if (AnyFx.on) it.fxBackground() else it }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (AnyFx.on) FxLockEmblem { AppIcon(pkg, label, 84.dp) } else AppIcon(pkg, label, 84.dp)
        Spacer(Modifier.height(16.dp))
        Text("$label is locked", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
        FxLockWord()
        Spacer(Modifier.height(8.dp))
        Text(if (com.krishna.questblock.data.FunSignal.quiet) "\uD83D\uDD12 Fun apps are locked right now" else "\uD83C\uDFAE Fun limit reached", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = ColorWarning)
        Spacer(Modifier.height(8.dp))
        Text(
            if (com.krishna.questblock.data.FunSignal.quiet)
                "Quiet hours, study hours or exam mode are on. Fun apps open again when they end."
            else if (st != null && st.enabled)
                "You've had ${fmtDur(st.usedMs)} of ${fmtDur(st.limitMs)} fun time today. It resets at midnight."
            else "You've used today's fun time. It resets at midnight.",
            style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onQuests,
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Do a quest instead", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onHome) { Text("Go home", color = ColorMuted) }
    }
}
