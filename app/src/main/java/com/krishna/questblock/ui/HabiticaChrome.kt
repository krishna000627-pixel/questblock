package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.CompanionStore
import com.krishna.questblock.data.Player
import com.krishna.questblock.util.Sounds

private val HAB_PURPLE_DARK = Color(0xFF432874)
private val HAB_PURPLE = Color(0xFF6133B4)
private val HAB_HP = Color(0xFFFF6165)
private val HAB_XP = Color(0xFFFFBE5D)
private val HAB_MP = Color(0xFF50B5E9)
private val HAB_GOLD = Color(0xFFFFA623)

data class HabiticaDrawerLink(val route: String, val label: String, val icon: ImageVector)

val HABITICA_DRAWER_LINKS = listOf(
    HabiticaDrawerLink("quests", "Tasks & Quests", Icons.Default.CheckBox),
    HabiticaDrawerLink("home", "Tavern Home", Icons.Default.Home),
    HabiticaDrawerLink("focus", "Focus Quest", Icons.Default.Bolt),
    HabiticaDrawerLink("vault", "App Vault", Icons.Default.Lock),
    HabiticaDrawerLink("adopt", "Adopt & Stables", Icons.Default.Favorite),
    HabiticaDrawerLink("shop", "Market & Shop", Icons.Default.ShoppingBag),
    HabiticaDrawerLink("profile", "Hero Profile", Icons.Default.Person),
    HabiticaDrawerLink("stats", "Stats & Ranks", Icons.Default.Star),
    HabiticaDrawerLink("hub", "Player Hub", Icons.Default.Star),
    HabiticaDrawerLink("settings", "Settings", Icons.Default.Settings),
    HabiticaDrawerLink("versions", "Release Notes", Icons.Default.Info),
)

/**
 * v292: Habitica-style top RPG HUD with 3-line hamburger menu trigger, animated pixel companion,
 * HP/XP/MP bars, and Gold counter. Zero emojis.
 */
@Composable
fun HabiticaTopHud(
    player: Player,
    onOpenMenu: () -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { if (CompanionStore.revision == 0) CompanionStore.load(ctx) }
    val comp = CompanionStore.state
    val level = (player.xp / 100) + 1
    val xpInLevel = player.xp % 100
    val hpFrac = (comp.happiness / 100f).coerceIn(0.2f, 1f)
    val xpFrac = (xpInLevel / 100f).coerceIn(0f, 1f)
    val mpFrac = ((player.streak.coerceAtLeast(1) * 15).coerceAtMost(100) / 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HAB_PURPLE_DARK)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { Sounds.uiOpen(); onOpenMenu() }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HAB_PURPLE)
                    .border(1.5.dp, HAB_GOLD, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PixelCompanionSprite(
                    companionId = comp.activeId,
                    stageIndex = comp.stageIndex,
                    modifier = Modifier.size(32.dp),
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HudBarRow("HP", hpFrac, HAB_HP)
                HudBarRow("XP", xpFrac, HAB_XP)
                HudBarRow("MP", mpFrac, HAB_MP)
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
                Text("LV $level", color = HAB_XP, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("${player.coins} G", color = HAB_GOLD, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HudBarRow(label: String, frac: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFFD8CCF0), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceIn(0.03f, 1f))
                    .height(6.dp)
                    .background(color),
            )
        }
    }
}

/**
 * v292: 3-line side navigation drawer styled after Habitica Android.
 */
@Composable
fun HabiticaDrawerSheet(
    player: Player,
    activeRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val comp = CompanionStore.state
    val spec = CompanionStore.specOf(comp.activeId)
    val level = (player.xp / 100) + 1

    ModalDrawerSheet(
        drawerContainerColor = ColorSurface,
        drawerContentColor = ColorText,
        modifier = Modifier.width(290.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HAB_PURPLE_DARK)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HAB_PURPLE)
                            .border(2.dp, HAB_GOLD, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PixelCompanionSprite(
                            companionId = comp.activeId,
                            stageIndex = comp.stageIndex,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Column {
                        Text(player.name.ifBlank { "QuestBlock Hero" }, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text("Level $level · ${spec.stages[comp.stageIndex]}", color = HAB_XP, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("${player.coins} Gold · ${player.streak}d Streak", color = Color(0xFFD8CCF0), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            HABITICA_DRAWER_LINKS.forEach { link ->
                val selected = activeRoute == link.route
                NavigationDrawerItem(
                    label = { Text(link.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                    selected = selected,
                    onClick = {
                        Sounds.uiTab()
                        onClose()
                        onNavigate(link.route)
                    },
                    icon = { Icon(link.icon, contentDescription = link.label) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = HAB_PURPLE.copy(alpha = 0.14f),
                        selectedIconColor = HAB_PURPLE,
                        selectedTextColor = HAB_PURPLE,
                        unselectedIconColor = ColorMuted,
                        unselectedTextColor = ColorText,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ColorDivider)
            Text(
                text = "Habitica RPG Mode · Open Source Layout",
                style = MaterialTheme.typography.labelSmall,
                color = ColorMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}
