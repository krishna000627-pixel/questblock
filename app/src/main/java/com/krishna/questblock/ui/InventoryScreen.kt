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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player          = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val inventory       = repo.inventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customItems     = repo.customShopItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customPurchases = repo.customPurchases.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var snackMsg by mutableStateOf<String?>(null); private set

    fun buyBuiltIn(item: ShopItem) = viewModelScope.launch {
        snackMsg = if (repo.buyBuiltInItem(item)) "Bought ${item.emoji} ${item.name}!" else "Not enough 🪙"
    }
    fun equip(inv: InventoryItem) = viewModelScope.launch { repo.equipItem(inv) }
    fun addCustom(name: String, emoji: String, desc: String, price: Int) = viewModelScope.launch {
        repo.addCustomItem(name, emoji, desc, price)
        snackMsg = "Custom reward added!"
    }
    fun deleteCustom(item: CustomShopItem) = viewModelScope.launch { repo.deleteCustomItem(item) }
    fun buyCustom(item: CustomShopItem) = viewModelScope.launch {
        snackMsg = if (repo.buyCustomItem(item)) "Redeemed ${item.emoji} ${item.name}!" else "Not enough 🪙"
    }
    fun redeemPurchase(p: CustomPurchase) = viewModelScope.launch { repo.redeemCustomPurchase(p) }
    fun clearSnack() { snackMsg = null }
}

private val SHOP_TABS = listOf("Shop", "My Rewards", "Inventory")

@Composable
fun InventoryScreen(vm: InventoryViewModel = hiltViewModel()) {
    val player          by vm.player.collectAsStateWithLifecycle()
    val inventory       by vm.inventory.collectAsStateWithLifecycle()
    val customItems     by vm.customItems.collectAsStateWithLifecycle()
    val customPurchases by vm.customPurchases.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.snackMsg) {
        if (vm.snackMsg != null) { snack.showSnackbar(vm.snackMsg!!); vm.clearSnack() }
    }

    var tab by remember { mutableIntStateOf(0) }
    var selectedCat by remember { mutableStateOf("All") }
    var showAddCustom by remember { mutableStateOf(false) }

    Scaffold(containerColor = ColorBg, snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(onClick = { showAddCustom = true },
                    containerColor = ColorPrimary, contentColor = ColorOnPrimary, shape = androidx.compose.foundation.shape.CircleShape) {
                    Icon(Icons.Default.Add, "Add custom reward")
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Header
            Row(Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Shop", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Text("🪙 ${player.coins}", style = MaterialTheme.typography.titleMedium,
                    color = ColorCoin, fontWeight = FontWeight.Bold)
            }

            // Active power-ups
            ActivePowerUpsRow(player)

            // Tabs
            TabRow(selectedTabIndex = tab, containerColor = ColorSurface, contentColor = ColorPrimary) {
                SHOP_TABS.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, style = MaterialTheme.typography.labelMedium) })
                }
            }

            when (tab) {
                0 -> ShopTab(player, selectedCat, onCatChange = { selectedCat = it }, onBuy = { vm.buyBuiltIn(it) })
                1 -> MyRewardsTab(customItems, customPurchases, player.coins,
                    onBuy = { vm.buyCustom(it) }, onDelete = { vm.deleteCustom(it) },
                    onRedeem = { vm.redeemPurchase(it) })
                2 -> InventoryTab(inventory, onEquip = { vm.equip(it) })
            }
        }
    }

    if (showAddCustom) {
        AddCustomRewardDialog(
            onDismiss = { showAddCustom = false },
            onAdd = { name, emoji, desc, price -> vm.addCustom(name, emoji, desc, price); showAddCustom = false },
        )
    }
}

@Composable
private fun ActivePowerUpsRow(player: Player) {
    val now = System.currentTimeMillis()
    val active = buildList {
        if (now < player.xpBoostUntil)       add("⚡ XP Boost ${(player.xpBoostUntil - now)/60_000}m")
        if (now < player.coinMultiplierUntil) add("🧲 Coins ×1.5 ${(player.coinMultiplierUntil - now)/60_000}m")
        if (now < player.streakShieldUntil)   add("🛡️ Shield ${(player.streakShieldUntil - now)/60_000}m")
    }
    if (active.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(active) { label ->
            Surface(shape = RoundedCornerShape(20.dp), color = ColorPrimary.copy(0.15f)) {
                Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = ColorPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShopTab(player: Player, selectedCat: String, onCatChange: (String) -> Unit, onBuy: (ShopItem) -> Unit) {
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShopCatalog.categories) { cat ->
                FilterChip(selected = selectedCat == cat, onClick = { onCatChange(cat) },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorPrimary.copy(0.2f), selectedLabelColor = ColorPrimary,
                        containerColor = ColorSurface, labelColor = ColorMuted))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ShopCatalog.filter(selectedCat), key = { it.id }) { item ->
                ShopCard(item, player.coins >= item.price) { onBuy(item) }
            }
        }
    }
}

@Composable
private fun ShopCard(item: ShopItem, canAfford: Boolean, onBuy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.emoji, fontSize = 30.sp)
            Text(item.name, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = ColorText, textAlign = TextAlign.Center, maxLines = 1)
            Text(item.description, style = MaterialTheme.typography.labelSmall,
                color = ColorMuted, textAlign = TextAlign.Center, maxLines = 2)
            Button(onClick = onBuy, enabled = canAfford,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary,
                    disabledContainerColor = ColorSurfaceHigh, disabledContentColor = ColorMuted),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("🪙 ${item.price}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MyRewardsTab(
    items: List<CustomShopItem>, purchases: List<CustomPurchase>, coins: Int,
    onBuy: (CustomShopItem) -> Unit, onDelete: (CustomShopItem) -> Unit,
    onRedeem: (CustomPurchase) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛍️", fontSize = 40.sp)
                        Text("No custom rewards yet", style = MaterialTheme.typography.titleSmall, color = ColorText)
                        Text("Tap + to create your own real-world rewards",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.emoji, fontSize = 28.sp)
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text(item.description, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { onBuy(item) }, enabled = coins >= item.price,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorCoin, contentColor = ColorBg,
                                disabledContainerColor = ColorSurfaceHigh, disabledContentColor = ColorMuted),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("🪙 ${item.price}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, tint = ColorMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // Pending redeems
        val pending = purchases.filter { !it.redeemed }
        if (pending.isNotEmpty()) {
            item {
                Text("Pending redemption", style = MaterialTheme.typography.labelMedium,
                    color = ColorWarning, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(pending, key = { "p_${it.id}" }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorWarning.copy(0.1f)),
                    shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🛍️", fontSize = 20.sp)
                        Text(p.itemName, style = MaterialTheme.typography.bodyMedium,
                            color = ColorText, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRedeem(p) },
                            colors = ButtonDefaults.textButtonColors(contentColor = ColorPrimary)) {
                            Text("Mark redeemed", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryTab(inventory: List<InventoryItem>, onEquip: (InventoryItem) -> Unit) {
    if (inventory.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎒", fontSize = 40.sp)
                Text("Inventory empty", style = MaterialTheme.typography.titleSmall, color = ColorText)
                Text("Buy items from the shop!", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val catLabels = mapOf("powerup" to "⚡ Power-ups", "cosmetic_frame" to "🖼️ Frames",
            "cosmetic_theme" to "🎨 Themes", "cosmetic_badge" to "🏅 Badges", "pass" to "🎫 Passes")
        catLabels.forEach { (cat, label) ->
            val catItems = inventory.filter { it.category == cat }
            if (catItems.isEmpty()) return@forEach
            item { Text(label, style = MaterialTheme.typography.labelMedium, color = ColorMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)) }
            items(catItems, key = { it.id }) { inv ->
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(ColorSurfaceHigh, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center) { Text(inv.emoji, fontSize = 20.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(inv.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ColorText)
                            Text(if (inv.isEquipped) "✓ Equipped" else if (inv.usedAt != null) "Used" else "Not equipped",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inv.isEquipped) ColorPrimary else ColorMuted)
                        }
                        if (inv.category.startsWith("cosmetic") && !inv.isEquipped) {
                            Button(onClick = { onEquip(inv) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("Equip", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomRewardDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Int) -> Unit) {
    var name  by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎁") }
    var desc  by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorCard,
        title = { Text("New Custom Reward", color = ColorText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = { if (it.length <= 2) emoji = it },
                        label = { Text("Emoji", color = ColorMuted) }, modifier = Modifier.width(80.dp),
                        singleLine = true, colors = dialogFieldColors())
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Name *", color = ColorMuted) }, modifier = Modifier.weight(1f),
                        singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = dialogFieldColors())
                }
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("Description", color = ColorMuted) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, colors = dialogFieldColors())
                OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() } },
                    label = { Text("Price 🪙", color = ColorMuted) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = dialogFieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toIntOrNull() ?: 100
                if (name.isNotBlank()) onAdd(name, emoji, desc, p)
            }) { Text("Add", color = ColorPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ColorPrimary, unfocusedBorderColor = ColorDivider,
    cursorColor = ColorPrimary, focusedTextColor = ColorText, unfocusedTextColor = ColorText,
)
