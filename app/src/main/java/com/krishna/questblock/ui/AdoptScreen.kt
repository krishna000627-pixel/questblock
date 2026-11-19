package com.krishna.questblock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.CompanionSpec
import com.krishna.questblock.data.CompanionStore
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.util.Sounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdoptViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())

    fun spendCoins(amount: Int, reason: String, detail: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.spendCoins(amount, reason, detail)
            onDone(ok)
        }
    }
}

/**
 * v292: Adopt & Care for an RPG study companion.
 */
@Composable
fun AdoptScreen(
    onBack: () -> Unit,
    vm: AdoptViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CompanionStore.load(ctx) }
    val player by vm.player.collectAsStateWithLifecycle()
    val st = CompanionStore.state
    val spec = CompanionStore.specOf(st.activeId)
    val accent = Color(spec.colorHex)
    val stageName = spec.stages[st.stageIndex]
    val stageEmoji = spec.emojis[st.stageIndex]
    val displayName = st.customName.ifBlank { spec.baseName }

    var showRename by remember { mutableStateOf(false) }
    var petBurst by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    LaunchedEffect(petBurst) {
        if (petBurst) {
            delay(700L)
            petBurst = false
        }
    }

    val floatY = if (AnimConfig.master) {
        rememberInfiniteTransition(label = "comp_float").animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "comp_y",
        ).value
    } else 0f

    if (showRename) {
        var draft by remember { mutableStateOf(st.customName.ifBlank { spec.baseName }) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            modifier = Modifier.grayWindow(),
            title = { Text("Name your companion", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(24) },
                    singleLine = true,
                    label = { Text("Companion name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    CompanionStore.rename(ctx, draft)
                    Sounds.uiToggle(true)
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { Sounds.uiClose(); onBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText)
                }
                Column(Modifier.weight(1f)) {
                    Text("Adopt Companion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Grows with your quests & Focus sessions", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                AnimatedCounter(
                    value = player.coins,
                    suffix = " GOLD",
                    color = ColorCoin,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        // Active Companion Stage Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().staggeredEntrance(0),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.05f))))
                            .border(2.dp, accent.copy(alpha = 0.65f), CircleShape)
                            .bounceClick {
                                CompanionStore.pet(ctx)
                                Sounds.coin()
                                petBurst = true
                                statusMsg = "$displayName loved that! (+6 XP)"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelCompanionSprite(
                            companionId = st.activeId,
                            stageIndex = st.stageIndex,
                            happy = petBurst,
                            modifier = Modifier.size(82.dp).graphicsLayer { translationY = floatY },
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = petBurst,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(350)),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        ) {
                            Text("+BOND", fontSize = 11.sp, fontWeight = FontWeight.Black, color = accent)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColorText)
                        IconButton(onClick = { Sounds.uiOpen(); showRename = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = ColorMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = "$stageName · Lv. ${st.level} · Mood: ${st.mood} · ${st.careStreak}d care streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(spec.perk, style = MaterialTheme.typography.bodySmall, color = ColorMuted)

                    // Companion XP bar with shimmer
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Evolution XP", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                            Text("${st.levelXp}/60 XP", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                        }
                        val frac = (st.levelXp / 60f).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(999.dp)).background(ColorSurfaceHigh)) {
                            Box(Modifier.fillMaxWidth(frac).height(9.dp).background(accent))
                            XpShimmerOverlay(frac, accent, Modifier.matchParentSize())
                        }
                    }

                    // Hunger & Happiness meters
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MeterTile("Hunger", st.hunger, ColorCoin, Modifier.weight(1f))
                        MeterTile("Bond", st.happiness, accent, Modifier.weight(1f))
                    }

                    // Care Actions
                    val freeSnack = CompanionStore.hasFreeSnackToday()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareActionChip(
                            label = "Pet",
                            sub = "Free · +6 XP",
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        ) {
                            CompanionStore.pet(ctx)
                            Sounds.coin()
                            petBurst = true
                            statusMsg = "$displayName purred happily!"
                        }
                        CareActionChip(
                            label = if (freeSnack) "Daily Snack" else "Treat (2 G)",
                            sub = "+25 Hunger",
                            accent = ColorCoin,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (freeSnack) {
                                CompanionStore.feed(ctx, freeDaily = true)
                                Sounds.combo(2)
                                statusMsg = "Fed $displayName today's free snack! (+18 XP)"
                            } else {
                                vm.spendCoins(2, "Companion treat", displayName) { ok ->
                                    if (ok) {
                                        CompanionStore.feed(ctx, freeDaily = false)
                                        Sounds.coin()
                                        statusMsg = "Gave $displayName a tasty treat! (+18 XP)"
                                    } else {
                                        Sounds.denied()
                                        statusMsg = "Need 2 coins for a treat!"
                                    }
                                }
                            }
                        }
                        CareActionChip(
                            label = "Train",
                            sub = "+24 XP",
                            accent = ColorPrimary,
                            modifier = Modifier.weight(1f),
                        ) {
                            CompanionStore.train(ctx)
                            Sounds.streak()
                            statusMsg = "$displayName trained hard! (+24 XP)"
                        }
                    }

                    if (statusMsg.isNotEmpty()) {
                        Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = ColorPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            Text(
                "Adoption Sanctuary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        itemsIndexed(CompanionStore.CATALOG, key = { _, it -> it.id }) { idx, item ->
            val owned = item.id in st.adoptedIds
            val active = item.id == st.activeId
            val itemAccent = Color(item.colorHex)
            Surface(
                modifier = Modifier.fillMaxWidth().staggeredEntrance(idx + 1),
                shape = RoundedCornerShape(14.dp),
                color = ColorCard,
                border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) itemAccent else ColorDivider),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelCompanionSprite(companionId = item.id, stageIndex = if (active) st.stageIndex else 0, modifier = Modifier.size(36.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.baseName, fontWeight = FontWeight.Bold, color = ColorText)
                        Text(item.stages.joinToString(" \u2192 "), style = MaterialTheme.typography.labelSmall, color = itemAccent)
                        Text(item.perk, style = MaterialTheme.typography.bodySmall, color = ColorMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) itemAccent.copy(alpha = 0.2f) else itemAccent)
                            .bounceClick(enabled = !active) {
                                if (owned) {
                                    CompanionStore.select(ctx, item.id)
                                    Sounds.uiToggle(true)
                                    statusMsg = "${item.baseName} is now your active companion!"
                                } else if (item.cost == 0) {
                                    CompanionStore.markAdopted(ctx, item.id)
                                    Sounds.levelUp()
                                    statusMsg = "Adopted ${item.baseName}!"
                                } else {
                                    vm.spendCoins(item.cost, "Adopted ${item.baseName}", "Companion") { ok ->
                                        if (ok) {
                                            CompanionStore.markAdopted(ctx, item.id)
                                            Sounds.levelUp()
                                            statusMsg = "Welcome ${item.baseName}!"
                                        } else {
                                            Sounds.denied()
                                            statusMsg = "Need ${item.cost} coins to adopt ${item.baseName}"
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = when {
                                active -> "Active"
                                owned -> "Equip"
                                item.cost == 0 -> "Adopt Free"
                                else -> "${item.cost} G"
                            },
                            color = if (active) itemAccent else Color.Black,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ColorSurface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            Text("$value%", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (value / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = ColorSurfaceHigh,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CareActionChip(label: String, sub: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .bounceClick(playSound = false, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
    }
}

/** Compact Home card showing the user's active adopted companion. */
@Composable
fun CompanionMiniCard(onOpenAdopt: () -> Unit, modifier: Modifier = Modifier) {
    if (!com.krishna.questblock.data.Flags.get("adopt")) return
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { if (CompanionStore.revision == 0) CompanionStore.load(ctx) }
    val st = CompanionStore.state
    val spec = CompanionStore.specOf(st.activeId)
    val accent = Color(spec.colorHex)
    val name = st.customName.ifBlank { spec.baseName }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().bounceClick(onClick = onOpenAdopt),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.5.dp, accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PixelCompanionSprite(companionId = st.activeId, stageIndex = st.stageIndex, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("$name · Lv. ${st.level}", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                Text("${spec.stages[st.stageIndex]} · ${st.mood} · Tap to visit", color = ColorMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("BOND ${st.happiness}%", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}
