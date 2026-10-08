@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.krishna.questblock.engine.Challenge
import com.krishna.questblock.engine.Goal
import com.krishna.questblock.engine.HubStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin wrapper so every Hub page reads the same in-memory store. */
@HiltViewModel
class HubViewModel @Inject constructor(val store: HubStore) : ViewModel()

@Composable
internal fun HubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
    }
}

/** Clean main page: today's numbers, rank, and four doors to the details. */
@Composable
fun PlayerHubScreen(
    onBack: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBadges: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenExperiments: () -> Unit = {},
    vm: HubViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.store.refresh() }
    val st = vm.store.state
    val snap = st.snap
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HubHeader("Player hub", onBack) }
        if (snap != null && !snap.usageAccess) item {
            HubCard { Text("Usage access is off",
                color = ColorWarning, style = MaterialTheme.typography.bodySmall) }
        }
        if (st.autoDone > 0) item {
            HubCard { Text("✅ ${st.autoDone} quest${if (st.autoDone > 1) "s" else ""} auto-completed",
                color = ColorPrimary, fontWeight = FontWeight.SemiBold) }
        }

        if (snap != null) {
            // ── Today ───────────────────────────────────────────────────
            item {
                val t = snap.today
                HubCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Glance("⚡", "Lv ${snap.level}"); Glance("🪙", "${st.coins}"); Glance("🔥", "${snap.streak} d")
                        Glance("✅", "${t.questsDone}/${t.questsDone + snap.openToday.size}"); Glance("🎯", "${t.focusMin} m")
                    }
                    val total = t.screenMin.coerceAtLeast(1)
                    Text("📱 ${fmtMin(t.screenMin)}", color = ColorText, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorSurfaceHigh)) {
                        if (t.screenMin > 0) {
                            Box(Modifier.weight(maxOf(0.001f, t.studyMin.toFloat() / total)).fillMaxHeight().background(ColorPrimary))
                            Box(Modifier.weight(maxOf(0.001f, t.codingMin.toFloat() / total)).fillMaxHeight().background(ColorAccent))
                            Box(Modifier.weight(maxOf(0.001f, t.neutralMin.toFloat() / total)).fillMaxHeight().background(ColorMuted))
                            Box(Modifier.weight(maxOf(0.001f, t.funMin.toFloat() / total)).fillMaxHeight().background(ColorWarning))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📚 ${fmtMin(t.studyMin)}", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                        Text("💻 ${fmtMin(t.codingMin)}", style = MaterialTheme.typography.labelMedium, color = ColorAccent)
                        Text("😐 ${fmtMin(t.neutralMin)}", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                        Text("🎮 ${fmtMin(t.funMin)}", style = MaterialTheme.typography.labelMedium, color = ColorWarning)
                    }
                }
            }

            // ── Rank ────────────────────────────────────────────────────
            item {
                HubCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(st.rank, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ColorPrimary)
                            Text("Level ${snap.level}" + (st.toNext?.let { " • $it to next" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${st.score}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = ColorText)
                            Text("score", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                        }
                    }
                    st.attrs.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${a.emoji} ${a.name}", color = ColorText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(112.dp))
                            Bar(a.into.toFloat() / a.per, Modifier.weight(1f))
                            Text("Lv ${a.level}", color = ColorMuted, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }

            // ── Doors ───────────────────────────────────────────────────
            val season = st.season
            item {
                NavCard("🌌 Season & challenges",
                    (if (season != null) "Season ${season.number} • day ${season.dayIn}/${season.length}" else "") +
                        (if (st.readyToClaim > 0) " • ${st.readyToClaim} to claim" else ""), onOpenGoals)
            }
            item { NavCard("📈 Weekly review", "Report, coins and one change", onOpenReports) }
            item { NavCard("🏆 Badges", "${st.badges.count { it.unlocked }} of ${st.badges.size} unlocked", onOpenBadges) }
            item { NavCard("⚙️ Rules", "Limits and locks", onOpenRules) }
            item { NavCard("🧪 Experiments", "Optional features, all switches are yours", onOpenExperiments) }
        } else {
            item { Spacer(Modifier.height(1.dp)) }
        }
    }
}

internal fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun Glance(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Text(value, color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun GoalRow(g: Goal, claimed: Boolean, onClaim: () -> Unit) {
    val c: Challenge = g.c
    val done = c.progress >= c.target
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, color = ColorText, fontWeight = FontWeight.SemiBold)
                Text("${c.progress}/${c.target}  ·  " + (if (c.xp > 0) "+${c.xp} XP " else "") + "+${c.coins}🪙",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            if (done && !claimed) Button(onClick = onClaim) { Text("Claim") }
            else if (claimed) Text("Claimed", color = ColorMuted, style = MaterialTheme.typography.labelMedium)
        }
        Bar(c.progress.toFloat() / c.target, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun Bar(frac: Float, modifier: Modifier) {
    val f = frac.coerceIn(0f, 1f)
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorSurfaceHigh)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(ColorPrimary))
    }
}

@Composable
private fun NavCard(title: String, sub: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            Text("›", fontSize = 24.sp, color = ColorMuted)
        }
    }
}
