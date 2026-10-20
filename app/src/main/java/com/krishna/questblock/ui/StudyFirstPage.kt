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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** v157: the YouTube study gate found a chapter you have not studied yet. No coins here: study it first, then tick it on the Chapters page. */
@Composable
fun StudyFirstPage(chapter: String, onHome: () -> Unit, onQuests: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(ColorBg).let { if (AnyFx.on) it.fxBackground() else it }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (AnyFx.on) FxLockEmblem { Text("\uD83D\uDCDA", style = MaterialTheme.typography.displayMedium) } else Text("\uD83D\uDCDA", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("Study first", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
        FxLockWord()
        Spacer(Modifier.height(8.dp))
        Text(chapter, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = ColorWarning, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("This chapter is not studied yet. Study it, tick it on the Chapters page, and its videos open free.",
            style = MaterialTheme.typography.bodyMedium, color = ColorMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onQuests,
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open QuestBlock", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onHome) { Text("Back to YouTube home", color = ColorMuted) }
    }
}
