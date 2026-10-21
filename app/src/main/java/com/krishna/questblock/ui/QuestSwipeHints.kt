package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.util.HapticsPolish

/**
 * v281: a one-time hint on the Quests page that explains the swipes (right = complete, left = delete).
 * "Got it" saves swipe_hint_seen, so it never comes back. Shows nothing once seen, so it costs nothing after that.
 */
@Composable
fun QuestSwipeHint(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var seen by remember { mutableStateOf(sp.getBoolean("swipe_hint_seen", false)) }
    if (seen) return
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorSurfaceHigh).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "\u2194 Swipe right to complete, left to delete",
            style = MaterialTheme.typography.labelMedium, color = ColorText, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            HapticsPolish.tap(view)
            sp.edit().putBoolean("swipe_hint_seen", true).apply()
            seen = true
        }) { Text("Got it", color = ColorPrimary, fontWeight = FontWeight.Bold) }
    }
}
