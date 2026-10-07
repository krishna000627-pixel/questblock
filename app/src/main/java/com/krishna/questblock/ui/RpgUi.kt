package com.krishna.questblock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.krishna.questblock.data.StarterQuest
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.engine.Cultivation

/** A framed RPG panel: gradient border in the accent colour, bold title and a thin rule under it. */
@Composable
fun RpgPanel(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = ColorCoin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ColorSurface,
        border = BorderStroke(1.5.dp, Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.25f)))),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = accent)
            Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.3f)))
            content()
        }
    }
}

private val REALM_COLORS = listOf(
    Color(0xFF8FD6C2), Color(0xFF5C9EFF), Color(0xFFB45CFF), Color(0xFFFF8C5C),
    Color(0xFFFF5C7A), Color(0xFF7C6EF5), Color(0xFFFFC857),
)

private val BOSSES = listOf(
    StarterQuest("🐉", "Boss: finish a full past-year paper, timed, no breaks", "Weekly boss · 90 min", "Boards", 3),
    StarterQuest("👹", "Boss: 3 hours of deep study in one day, phone away", "Weekly boss · 180 min", "General", 3),
    StarterQuest("🦂", "Boss: solve 40 mixed PCM problems, review every mistake", "Weekly boss · 120 min", "General", 3),
    StarterQuest("🐲", "Boss: revise a whole chapter from memory, then test yourself", "Weekly boss · 90 min", "General", 3),
)
private val TRIBULATION = StarterQuest("⚡", "Tribulation: one 45-min focused block, then 10 questions with no help",
    "Breakthrough trial · 45 min", "General", 3)

/**
 * Cultivation realm, stage and progress to the next stage, worked out from total XP.
 * Hidden when Settings → Cultivation is off. [onAddQuest] (Home only) enables the tribulation and weekly boss buttons.
 */
@Composable
fun CultivationStrip(xp: Int, modifier: Modifier = Modifier, onAddQuest: ((StarterQuest) -> Unit)? = null) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    if (!sp.getBoolean("cultivation_on", true)) return
    val c = remember(xp) { Cultivation.of(xp) }
    val accent = REALM_COLORS[c.realmIndex % REALM_COLORS.size]
    val frac = if (c.atTop || c.need == 0) 1f else c.into / c.need.toFloat()
    val anim by animateFloatAsState(targetValue = frac, animationSpec = tween(700), label = "cultivation")

    // breakthrough popup: only when the realm number went up since the last time it was seen
    var celebrate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(c.realmIndex) {
        val last = sp.getInt("cult_last_realm", -1)
        if (last in 0 until c.realmIndex) celebrate = c.realm.name
        if (last != c.realmIndex) sp.edit().putInt("cult_last_realm", c.realmIndex).apply()
    }
    celebrate?.let { name ->
        AlertDialog(
            onDismissRequest = { celebrate = null },
            confirmButton = { TextButton(onClick = { celebrate = null }) { Text("Onward", fontWeight = FontWeight.Bold) } },
            title = { Text("⚡ Breakthrough!", fontWeight = FontWeight.Black) },
            text = { Text("The heavens part. You broke through into $name.") },
        )
    }

    val day = remember { System.currentTimeMillis() / 86_400_000L }
    var tribDone by remember { mutableStateOf(sp.getLong("cult_trib_day", -1L) == day) }
    var bossDone by remember { mutableStateOf(sp.getLong("cult_boss_week", -1L) == day / 7) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(c.realm.emoji, fontSize = 24.sp)
                Column(Modifier.weight(1f)) {
                    Text(c.realm.name, fontWeight = FontWeight.Black, color = accent, style = MaterialTheme.typography.titleSmall)
                    Text("${c.stageLabel} · Realm ${c.realmIndex + 1} of ${Cultivation.REALMS.size}",
                        color = ColorMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text(if (c.atTop) "MAX" else "${c.into}/${c.need}", color = accent,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(ColorSurfaceHigh)) {
                Box(Modifier.fillMaxWidth(anim).fillMaxHeight().background(accent, CircleShape))
            }
            Text(
                if (c.atTop) "You stand at the peak of the Dao."
                else if (c.isPeakStage) "Peak stage: ${c.toNextRealm} XP to break through into ${c.nextRealm}"
                else "${c.toNextRealm} XP to break through into ${c.nextRealm}",
                color = ColorMuted, style = MaterialTheme.typography.labelSmall,
            )
            if (onAddQuest != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.isPeakStage && !c.atTop) OutlinedButton(
                        onClick = { onAddQuest(TRIBULATION); sp.edit().putLong("cult_trib_day", day).apply(); tribDone = true },
                        enabled = !tribDone, shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(if (tribDone) "⚡ Tribulation set" else "⚡ Face the tribulation", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(
                        onClick = { onAddQuest(BOSSES[((day / 7) % BOSSES.size).toInt()]); sp.edit().putLong("cult_boss_week", day / 7).apply(); bossDone = true },
                        enabled = !bossDone, shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(if (bossDone) "🐉 Boss summoned" else "🐉 Weekly boss", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
