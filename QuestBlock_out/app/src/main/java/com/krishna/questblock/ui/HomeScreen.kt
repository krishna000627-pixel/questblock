package com.krishna.questblock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.level
import com.krishna.questblock.data.xpIntoLevel
import com.krishna.questblock.util.Perms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedApps = repo.blockedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun HomeScreen(
    onGoToQuests: () -> Unit,
    onGoToVault: () -> Unit,
    onGoToProfile: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val player by vm.player.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val blockedApps by vm.blockedApps.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    val accessibility = Perms.accessibilityEnabled(ctx)
    val overlay = Perms.overlayGranted(ctx)
    val anyIssue = !accessibility || !overlay

    val now = System.currentTimeMillis()
    val activeLocks = blockedApps.filter { it.unlockedUntil > now }
    val todayTasks = tasks.filter { !it.done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Health banner
        if (anyIssue) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ColorDanger.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ColorDanger, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Setup needed", style = MaterialTheme.typography.labelLarge, color = ColorDanger)
                            Text(
                                buildString {
                                    if (!accessibility) append("Accessibility service is off. ")
                                    if (!overlay) append("Overlay permission missing.")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorMuted,
                            )
                        }
                        Button(
                            onClick = onGoToProfile,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDanger),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text("Fix", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }

        // Level ring + stats
        item { PlayerHeroCard(player) }

        // Active unlocks
        if (activeLocks.isNotEmpty()) {
            item {
                Text("Active unlocks", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
            }
            items(activeLocks, key = { it.packageName }) { app ->
                ActiveUnlockCard(app, onGoToVault)
            }
        }

        // Today's quests summary
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Today's quests", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
                Text(
                    "${tasks.count { it.done }} / ${tasks.size} done",
                    style = MaterialTheme.typography.labelMedium,
                    color = ColorPrimary,
                )
            }
        }
        if (todayTasks.isEmpty() && tasks.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorPrimary)
                        Text("All done! Earn more coins by adding quests.", color = ColorMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (tasks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No quests yet", style = MaterialTheme.typography.titleMedium, color = ColorText)
                        Text("Add your first quest to start earning coins.", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = onGoToQuests,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                        ) { Text("Add a quest") }
                    }
                }
            }
        }
        items(todayTasks.take(3), key = { it.id }) { task ->
            HomeQuestRow(task)
        }
        if (todayTasks.size > 3) {
            item {
                Button(
                    onClick = onGoToQuests,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceHigh, contentColor = ColorPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("See all ${todayTasks.size} quests") }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PlayerHeroCard(player: Player) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(ColorPrimary.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Level ring
                LevelRing(level = player.level(), progress = player.xpIntoLevel() / 100f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Level ${player.level()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip(label = "🪙", value = player.coins.toString(), color = ColorCoin)
                        StatChip(label = "🔥", value = "${player.streak}d", color = Color(0xFFFF8C42))
                        StatChip(label = "⚡", value = "${player.xpIntoLevel()}/100", color = ColorPrimary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { player.xpIntoLevel() / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = ColorPrimary,
                trackColor = ColorSurfaceHigh,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${player.xpIntoLevel()} / 100 XP to level ${player.level() + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = ColorMuted,
            )
        }
    }
}

@Composable
private fun LevelRing(level: Int, progress: Float) {
    var animProg by remember { mutableFloatStateOf(0f) }
    val animatedProg by animateFloatAsState(
        targetValue = animProg,
        animationSpec = tween(durationMillis = 900),
        label = "xp_ring",
    )
    LaunchedEffect(progress) { animProg = progress }

    Box(
        modifier = Modifier
            .size(64.dp)
            .background(ColorSurfaceHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
            val stroke = 5.dp.toPx()
            val radius = (size.minDimension - stroke) / 2
            drawArc(
                color = ColorSurfaceHigh,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
            drawArc(
                color = ColorPrimary,
                startAngle = -90f,
                sweepAngle = 360f * animatedProg,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
        Text(
            text = level.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ColorPrimary,
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 13.sp)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveUnlockCard(app: BlockedApp, onTap: () -> Unit) {
    val now = System.currentTimeMillis()
    val remaining = ((app.unlockedUntil - now) / 1000).coerceAtLeast(0)
    val mins = remaining / 60
    val secs = remaining % 60

    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = ColorSurfaceHigh),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = ColorCoin, modifier = Modifier.size(18.dp))
            Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ColorText)
            Text(
                "%02d:%02d".format(mins, secs),
                style = MaterialTheme.typography.labelLarge,
                color = ColorCoin,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HomeQuestRow(task: TaskEntity) {
    val diffColors = listOf(ColorPrimary, ColorCoin, ColorDanger)
    val diffLabels = listOf("Easy", "Medium", "Hard")
    val color = diffColors.getOrElse(task.difficulty - 1) { ColorPrimary }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ColorText)
            Text(
                diffLabels.getOrElse(task.difficulty - 1) { "Easy" },
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
