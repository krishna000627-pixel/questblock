@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.level
import com.krishna.questblock.data.xpIntoLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: GameRepository,
    val prefs: Prefs,
) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
}

@Composable
fun ProfileScreen(onOpenShop: () -> Unit, onOpenStats: () -> Unit, onOpenSettings: () -> Unit, onOpenCoins: () -> Unit, onOpenAbout: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val player by vm.player.collectAsStateWithLifecycle()


    val snack = remember { SnackbarHostState() }
    var pickAvatar by remember { mutableStateOf(false) }
    if (pickAvatar) AvatarPickerDialog { pickAvatar = false }

    Scaffold(containerColor = ColorBg, contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Avatar section ─────────────────────────────────────────────
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val frameColor = when (player.equippedFrame) {
                    "frame_fire"   -> androidx.compose.ui.graphics.Color(0xFFFF6B35)
                    "frame_ice"    -> androidx.compose.ui.graphics.Color(0xFF64B5F6)
                    "frame_gold"   -> ColorCoin
                    "frame_galaxy" -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
                    else           -> ColorPrimary
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clickable { pickAvatar = true }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(80.dp).background(
                            Brush.radialGradient(listOf(frameColor.copy(0.5f), frameColor.copy(0.1f))), CircleShape))
                        Box(
                            Modifier.size(72.dp).clip(HexShape)
                                .background(Brush.verticalGradient(listOf(frameColor.copy(0.55f), frameColor.copy(0.12f))))
                                .border(2.dp, frameColor, HexShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            LevelInHex(player.level())
                        }
                        // Badge overlay
                        val shownBadge = com.krishna.questblock.data.shownBadgeOf(player)
                        if (shownBadge.isNotBlank()) {
                            val emoji = when (shownBadge) {
                                "badge_beginner" -> "\uD83C\uDF31"
                                "badge_star"   -> "⭐"; "badge_crown" -> "👑"
                                "badge_sword"  -> "⚔️"; "badge_dragon" -> "🐉"; else -> ""
                            }
                            if (emoji.isNotBlank()) {
                                Box(Modifier.align(Alignment.BottomEnd).size(28.dp)
                                    .background(ColorSurface, CircleShape), contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    Text(player.name.ifBlank { "Hero" }, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    Text("Level ${player.level()} · ${player.xpIntoLevel()}/100 XP",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    Text("Tap the badge to change your avatar", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                }
            }

            CultivationStrip(player.xp)

            // ── Hero info (read-only; edit it from Settings) ────────────────
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hero info", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = ColorText)
                    ProfileInfoRow("👤 Name",  player.name.ifBlank { "—" })
                    ProfileInfoRow("🎯 Goal",  player.goal.ifBlank { "—" })
                    ProfileInfoRow("🎂 DOB",   player.dob.ifBlank  { "—" })
                }
            }

            // ── Stats row ───────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileStatChip(Modifier.weight(1f), "🪙", "${player.coins}", "Coins")
                ProfileStatChip(Modifier.weight(1f), "🔥", "${player.streak}d", "Streak")
                ProfileStatChip(Modifier.weight(1f), "⚡", "${player.xp}", "Total XP")
            }

            // ── Shop ────────────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenShop() }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🛍️", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Shop & Inventory", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text("Spend coins · power-ups · cosmetics · your own rewards",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text("🪙 ${player.coins}", color = ColorCoin, fontWeight = FontWeight.Bold)
                }
            }

            // ── Coin log ────────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenCoins() }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🧾", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Coin log", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text("Every earn and spend, and why an app was free",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text("›", color = ColorMuted, fontSize = 20.sp)
                }
            }

            // ── Stats + Settings ─────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).clickable { onOpenStats() }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📊", fontSize = 20.sp)
                        Text("Stats", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).clickable { onOpenSettings() }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚙️", fontSize = 20.sp)
                        Text("Settings", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                    }
                }
            }


            // ── About me ────────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = ColorSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { onOpenAbout() }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("👨‍💻", fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text("About me", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text(com.krishna.questblock.data.AboutMe.TAGLINE,
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Text("›", color = ColorMuted, fontSize = 20.sp)
                }
            }

            // ── Equipped cosmetics ──────────────────────────────────────────
            if (true) {
                Card(colors = CardDefaults.cardColors(containerColor = ColorSurface),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Equipped", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        if (player.equippedFrame.isNotBlank())
                            ProfileInfoRow("🖼️ Frame", player.equippedFrame.replace("_", " ").replaceFirstChar { it.uppercase() })
                        if (com.krishna.questblock.data.shownBadgeOf(player).isNotBlank())
                            ProfileInfoRow("🏅 Badge", com.krishna.questblock.data.shownBadgeOf(player).removePrefix("badge_").replaceFirstChar { it.uppercase() })
                        if (player.equippedTheme != "default")
                            ProfileInfoRow("🎨 Theme", player.equippedTheme.replace("_", " ").replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProfileStatChip(modifier: Modifier, emoji: String, value: String, label: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(emoji, fontSize = 18.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = ColorText)
    }
}
