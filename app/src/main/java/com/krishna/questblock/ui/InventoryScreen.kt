@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.InventoryItem
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.ShopCatalog
import com.krishna.questblock.data.ShopItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player    = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val inventory = repo.inventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var buyResult by mutableStateOf<String?>(null)
        private set

    fun buy(item: ShopItem) = viewModelScope.launch {
        val ok = repo.buyItem(item)
        buyResult = if (ok) "Bought ${item.emoji} ${item.name}!" else "Not enough coins 🪙"
    }

    fun equip(inv: InventoryItem) = viewModelScope.launch { repo.equipItem(inv) }

    fun clearResult() { buyResult = null }
}

@Composable
fun InventoryScreen(vm: InventoryViewModel = hiltViewModel()) {
    val player    by vm.player.collectAsStateWithLifecycle()
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val buyResult  = vm.buyResult

    var tab by remember { mutableIntStateOf(0) }

    // Show snackbar for buy result
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(buyResult) {
        if (buyResult != null) {
            snackState.showSnackbar(buyResult!!)
            vm.clearResult()
        }
    }

    Scaffold(
        containerColor = ColorBg,
        snackbarHost = { SnackbarHost(snackState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Inventory", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Text("🪙 ${player.coins}", style = MaterialTheme.typography.titleMedium,
                    color = ColorCoin, fontWeight = FontWeight.Bold)
            }

            // Active power-ups strip
            ActivePowerUpsRow(player)

            // Tab row
            TabRow(
                selectedTabIndex = tab,
                containerColor   = ColorSurface,
                contentColor     = ColorPrimary,
            ) {
                listOf("Shop", "My Items").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick  = { tab = i },
                        text     = { Text(label) },
                    )
                }
            }

            when (tab) {
                0 -> ShopTab(player, onBuy = { vm.buy(it) })
                1 -> MyItemsTab(inventory, onEquip = { vm.equip(it) })
            }
        }
    }
}

// ── Active power-ups banner ────────────────────────────────────────────────
@Composable
private fun ActivePowerUpsRow(player: Player) {
    val now = System.currentTimeMillis()
    val active = buildList {
        if (now < player.xpBoostUntil)        add("⚡ XP Boost" to player.xpBoostUntil)
        if (now < player.coinMultiplierUntil)  add("🧲 Coin Magnet" to player.coinMultiplierUntil)
        if (now < player.streakShieldUntil)    add("🛡️ Shield" to player.streakShieldUntil)
    }
    if (active.isEmpty()) return
    LazyRow(
        contentPadding         = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement  = Arrangement.spacedBy(8.dp),
    ) {
        items(active) { (label, expiry) ->
            val mins = ((expiry - now) / 60_000).coerceAtLeast(0)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ColorPrimary.copy(alpha = 0.18f),
            ) {
                Text(
                    "$label ${mins}m",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = ColorPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Shop tab ──────────────────────────────────────────────────────────────
@Composable
private fun ShopTab(player: Player, onBuy: (ShopItem) -> Unit) {
    var selectedCat by remember { mutableStateOf("All") }

    Column {
        // Category filter
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ShopCatalog.categories) { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick  = { selectedCat = cat },
                    label    = { Text(cat) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorPrimary.copy(alpha = 0.2f),
                        selectedLabelColor     = ColorPrimary,
                        containerColor         = ColorSurface,
                        labelColor             = ColorMuted,
                    ),
                )
            }
        }

        val filtered = ShopCatalog.filter(selectedCat)
        LazyVerticalGrid(
            columns               = GridCells.Fixed(2),
            contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { it.id }) { item ->
                ShopItemCard(item = item, canAfford = player.coins >= item.price, onBuy = { onBuy(item) })
            }
        }
    }
}

@Composable
private fun ShopItemCard(item: ShopItem, canAfford: Boolean, onBuy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape  = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.emoji, fontSize = 32.sp)
            Text(item.name, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center)
            Text(item.description, style = MaterialTheme.typography.bodySmall,
                color = ColorMuted, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = onBuy,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                enabled  = canAfford,
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = ColorPrimary,
                    contentColor           = ColorOnPrimary,
                    disabledContainerColor = ColorSurfaceHigh,
                    disabledContentColor   = ColorMuted,
                ),
                shape    = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("🪙 ${item.price}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── My Items tab ──────────────────────────────────────────────────────────
@Composable
private fun MyItemsTab(inventory: List<InventoryItem>, onEquip: (InventoryItem) -> Unit) {
    if (inventory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎒", style = MaterialTheme.typography.displayMedium)
                Text("Your inventory is empty", style = MaterialTheme.typography.titleMedium, color = ColorText)
                Text("Buy items from the shop!", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Group by category
        val groups = inventory.groupBy { it.category }
        val catLabels = mapOf(
            "powerup"        to "⚡ Power-ups",
            "cosmetic_frame" to "🖼️ Frames",
            "cosmetic_theme" to "🎨 Themes",
            "cosmetic_badge" to "🏅 Badges",
            "pass"           to "🎫 Passes",
        )
        catLabels.forEach { (cat, label) ->
            val items = groups[cat] ?: return@forEach
            item {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = ColorMuted, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(items, key = { it.id }) { inv ->
                InventoryItemCard(inv, onEquip = { onEquip(inv) })
            }
        }
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItem, onEquip: () -> Unit) {
    val isCosmetic = item.category.startsWith("cosmetic")
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
        shape  = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(ColorSurfaceHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(item.emoji, fontSize = 22.sp) }

            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                val statusText = when {
                    item.isEquipped            -> "✓ Equipped"
                    item.usedAt != null        -> "Used"
                    else                       -> "Not equipped"
                }
                Text(statusText, style = MaterialTheme.typography.labelSmall,
                    color = if (item.isEquipped) ColorPrimary else ColorMuted)
            }

            if (isCosmetic && !item.isEquipped) {
                Button(
                    onClick        = onEquip,
                    colors         = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape          = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("Equip", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
