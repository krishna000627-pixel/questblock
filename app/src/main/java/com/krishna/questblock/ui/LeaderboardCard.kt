package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.Player

private class LbRow(val name: String, val xp: Int, val isYou: Boolean)

private val ADJ = listOf("Swift", "Calm", "Brave", "Lucky", "Silent", "Sharp", "Bright", "Wild",
    "Iron", "Clever", "Cosmic", "Noble", "Rapid", "Steady", "Sunny", "Frosty")
private val NOUN = listOf("Owl", "Fox", "Wolf", "Hawk", "Panda", "Tiger", "Raven", "Otter",
    "Lynx", "Falcon", "Koala", "Bison", "Heron", "Gecko", "Moose", "Cobra")

/** A well-mixed number in 0.0 until 1.0 from any seed (same seed, same number). */
private fun unit(seed: Long): Double {
    var z = seed + -7046029254386353131L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    z = z xor (z ushr 31)
    return (z ushr 11).toDouble() / (1L shl 53).toDouble()
}

private fun localDay(): Long {
    val n = System.currentTimeMillis()
    return (n + java.util.TimeZone.getDefault().getOffset(n)) / 86_400_000L
}

/** 80 random-looking names. A new set every day. */
private fun names(day: Long): List<String> {
    val rnd = java.util.Random((unit(day) * 1e15).toLong())
    return ADJ.flatMap { a -> NOUN.map { n -> "${a}_$n" } }
        .shuffled(rnd).take(80)
        .map { if (rnd.nextInt(3) == 0) it + (10 + rnd.nextInt(90)) else it }
}

/** The board for one half of a day (half 0 = midnight to noon, half 1 = noon to midnight). */
private fun board(you: String, yourXp: Int, day: Long, half: Int): List<LbRow> {
    val ref = maxOf(yourXp, 60)
    val slot = day * 2 + half
    val rivals = names(day).map { n ->
        val skill = 0.35 + unit(n.hashCode().toLong() + day * 131L) * 1.1      // how strong this player is today
        val roll = 0.6 + unit(n.hashCode().toLong() * 31L + slot * 977L) * 0.8  // how their half-day went
        LbRow(n, maxOf(3, (ref * skill * roll).toInt()), false)
    }
    return (rivals + LbRow(you, yourXp, true)).sortedByDescending { it.xp }
}

/** Leaderboard: 80 simulated players, you see the top 10. It changes twice a day (midnight and noon). */
@Composable
fun LeaderboardCard(player: Player, weeklyData: List<Pair<String, Int>>) {
    val day = remember { localDay() }
    val half = remember { if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) >= 12) 1 else 0 }
    val you = player.name.ifBlank { "You" }
    val yourXp = weeklyData.sumOf { it.second }
    val todayXp = weeklyData.lastOrNull()?.second ?: 0
    val now = remember(day, half, yourXp, you) { board(you, yourXp, day, half) }
    // rank movement is only known inside the same day (the names change every day)
    val before = remember(day, half, yourXp, you) { if (half == 1) board(you, yourXp, day, 0) else null }
    val maxXp = (now.maxOfOrNull { it.xp } ?: 1).coerceAtLeast(1)
    val myIdx = now.indexOfFirst { it.isYou }
    val top = now.take(10)

    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text("🏆 Leaderboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
                Text(
                    "Top 10 of ${now.size} players. Changes twice a day. Next change at ${if (half == 0) "12:00" else "midnight"}.",
                    style = MaterialTheme.typography.labelSmall, color = ColorMuted,
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ColorPrimary.copy(alpha = 0.12f)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("You are #${myIdx + 1} of ${now.size}", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (myIdx > 0) "${now[myIdx - 1].xp - yourXp + 1} XP to pass ${now[myIdx - 1].name}"
                        else if (now.size > 1) "You lead by ${yourXp - now[1].xp} XP" else "",
                        style = MaterialTheme.typography.labelMedium, color = ColorMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$yourXp XP", fontWeight = FontWeight.Black, color = ColorPrimary, style = MaterialTheme.typography.titleMedium)
                    Text("+$todayXp today", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                }
            }
            top.forEachIndexed { i, r -> LbLine(i, r, maxXp, movedOf(before, r, i)) }
            if (myIdx >= 10) {
                Text("•••", color = ColorMuted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                LbLine(myIdx, now[myIdx], maxXp, movedOf(before, now[myIdx], myIdx))
            }
        }
    }
}

private fun movedOf(before: List<LbRow>?, r: LbRow, i: Int): Int {
    if (before == null) return 0
    val prev = before.indexOfFirst { it.name == r.name }
    return if (prev < 0) 0 else prev - i
}

@Composable
private fun LbLine(i: Int, r: LbRow, maxXp: Int, moved: Int) {
    val avatar = Color.hsv((Math.abs(r.name.hashCode()) % 360).toFloat(), 0.45f, 0.85f)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (r.isYou) ColorPrimary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" },
            modifier = Modifier.width(32.dp), fontWeight = FontWeight.Black, color = ColorMuted,
        )
        Box(Modifier.size(32.dp).clip(CircleShape).background(avatar), contentAlignment = Alignment.Center) {
            Text(r.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (r.isYou) "${r.name} (you)" else r.name,
                color = if (r.isYou) ColorPrimary else ColorText,
                fontWeight = if (r.isYou) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ColorSurfaceHigh)) {
                Box(
                    Modifier.fillMaxWidth((r.xp.toFloat() / maxXp).coerceIn(0.02f, 1f)).fillMaxHeight()
                        .background(if (r.isYou) ColorPrimary else ColorMuted.copy(alpha = 0.6f)),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${r.xp}", fontWeight = FontWeight.Bold, color = if (r.isYou) ColorPrimary else ColorText, style = MaterialTheme.typography.labelLarge)
            Text(
                when { moved > 0 -> "▲$moved"; moved < 0 -> "▼${-moved}"; else -> "–" },
                style = MaterialTheme.typography.labelSmall,
                color = when { moved > 0 -> ColorPrimary; moved < 0 -> ColorDanger; else -> ColorMuted },
            )
        }
    }
}
