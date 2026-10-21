package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

private const val CAL_WEEKS = 5
private const val CAL_DAYS = CAL_WEEKS * 7

/** Midnight (local time) of the day that [ms] falls in. */
private fun dayStartOf(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** How many quests were finished on each day: key = midnight of the day. */
fun questCountsByDay(completedAt: List<Long>): Map<Long, Int> =
    completedAt.groupingBy { dayStartOf(it) }.eachCount()

/**
 * v281: a small heatmap of the last 5 weeks on Profile. One square per day, darker = more quests finished that day,
 * today has an outline. Static (no animation), 35 tiny boxes, so it costs almost nothing to draw.
 */
@Composable
fun StreakCalendar(counts: Map<Long, Int>, streak: Int, modifier: Modifier = Modifier) {
    val today = remember { dayStartOf(System.currentTimeMillis()) }
    val days = remember(today) {
        val c = Calendar.getInstance()
        List(CAL_DAYS) { i ->
            c.timeInMillis = today
            c.add(Calendar.DAY_OF_YEAR, -(CAL_DAYS - 1 - i))
            c.timeInMillis
        }
    }
    val total = days.sumOf { counts[it] ?: 0 }
    androidx.compose.material3.Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "$total quests finished in the last $CAL_DAYS days, streak $streak days" },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Last $CAL_WEEKS weeks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ColorText)
                Text("\uD83D\uDD25 ${streak}d \u00B7 $total done", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            }
            for (w in 0 until CAL_WEEKS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 0 until 7) {
                        val day = days[w * 7 + d]
                        val n = counts[day] ?: 0
                        val fill = when {
                            n <= 0 -> ColorMuted.copy(alpha = 0.12f)
                            n == 1 -> ColorPrimary.copy(alpha = 0.35f)
                            n == 2 -> ColorPrimary.copy(alpha = 0.6f)
                            else -> ColorPrimary.copy(alpha = 0.9f)
                        }
                        var m = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp)).background(fill)
                        if (day == today) m = m.border(1.dp, ColorText.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        Box(m)
                    }
                }
            }
        }
    }
}
