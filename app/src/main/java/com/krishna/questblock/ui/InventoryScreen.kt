@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val player    = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())
    val inventory = repo.inventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rewards   = repo.customRewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var buyResult by mutableStateOf<String?>(null)
        private set

    fun buy(item: ShopItem) = viewModelScope.launch {
        val ok = repo.buyItem(item)
        buyResult = if (ok) "Bought ${item.emoji} ${item.name}!" else "Not enough coins 🪙"
    }

    fun buyReward(r: CustomReward) = viewModelScope.launch {
        val ok = repo.buyCustomReward(r)
        buyResult = when {
            !ok -> "Not enough coins 🪙"
            r.type == "reward" -> "Enjoy your ${r.emoji} ${r.name}! -${r.price}🪙"
            else -> "${r.emoji} ${r.name} saved in My Items"
        }
    }
    fun addReward(name: String, emoji: String, price: Int, desc: String, type: String) =
        viewModelScope.launch { repo.addCustomReward(name, emoji, price, desc, type) }
    fun deleteReward(r: CustomReward) = viewModelScope.launch { repo.deleteCustomReward(r) }
    fun redeem(inv: InventoryItem) = viewModelScope.launch {
        repo.redeemItem(inv); buyResult = "Redeemed ${inv.emoji} ${inv.name}"
    }
    fun equip(inv: InventoryItem) = viewModelScope.launch { repo.equipItem(inv) }
    fun clearResult() { buyResult = null }
}

private const val MY_REWARDS = "My Rewards"

@Composable
fun InventoryScreen(onBack: () -> Unit, vm: InventoryViewModel = hiltViewModel()) {
    val player    by vm.player.collectAsStateWithLifecycle()
    val inventory by vm.inventory.collectAsStateWithLifecycle()
    val rewards   by vm.rewards.collectAsStateWithLifecycle()
    val buyResult  = vm.buyResult

    var tab by remember { mutableIntStateOf(0) }

    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(buyResult) {
        if (buyResult != null) {
            snackState.showSnackbar(buyResult)
            vm.clearResult()
        }
    }

    Scaffold(
        containerColor = ColorBg,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Text("Shop", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = ColorText, modifier = Modifier.weight(1f))
                Text("🪙 ${player.coins}", style = MaterialTheme.typography.titleMedium,
                    color = ColorCoin, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
            }

            ActivePowerUpsRow(player)

            TabRow(selectedTabIndex = tab, containerColor = ColorSurface, contentColor = ColorPrimary) {
                listOf("Shop", "My Items").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }

            when (tab) {
                0 -> ShopTab(
                    player = player, rewards = rewards,
                    onBuy = { vm.buy(it) }, onBuyReward = { vm.buyReward(it) },
                    onAddReward = { n, e, p, d, t -> vm.addReward(n, e, p, d, t) },
                    onDeleteReward = { vm.deleteReward(it) },
                )
                1 -> MyItemsTab(inventory, onEquip = { vm.equip(it) }, onRedeem = { vm.redeem(it) })
            }
        }
    }
}

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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(active) { (label, expiry) ->
            val mins = ((expiry - now) / 60_000).coerceAtLeast(0)
            Surface(shape = RoundedCornerShape(20.dp), color = ColorPrimary.copy(alpha = 0.18f)) {
                Text("$label ${mins}m", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall, color = ColorPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shop tab ──────────────────────────────────────────────────────────────
@Composable
private fun ShopTab(
    player: Player, rewards: List<CustomReward>,
    onBuy: (ShopItem) -> Unit, onBuyReward: (CustomReward) -> Unit,
    onAddReward: (String, String, Int, String, String) -> Unit, onDeleteReward: (CustomReward) -> Unit,
) {
    var selectedCat by remember { mutableStateOf("All") }
    var showCreate by remember { mutableStateOf(false) }
    val cats = ShopCatalog.categories + MY_REWARDS

    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cats) { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ColorPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = ColorPrimary,
                        containerColor = ColorSurface, labelColor = ColorMuted,
                    ),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedCat == MY_REWARDS) {
                item(key = "add_reward") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorPrimary.copy(0.08f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable { showCreate = true },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(28.dp))
                            Text("New reward", style = MaterialTheme.typography.labelLarge,
                                color = ColorPrimary, fontWeight = FontWeight.Bold)
                            Text("Ice cream, gaming hour, movie night…", style = MaterialTheme.typography.bodySmall,
                                color = ColorMuted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                items(rewards, key = { "r_${it.id}" }) { r ->
                    RewardCard(r, canAfford = player.coins >= r.price, onBuy = { onBuyReward(r) }, onDelete = { onDeleteReward(r) })
                }
            } else {
                items(ShopCatalog.filter(selectedCat), key = { it.id }) { item ->
                    ShopItemCard(item, canAfford = player.coins >= item.price, onBuy = { onBuy(item) })
                }
            }
        }
    }

    if (showCreate) {
        CreateRewardDialog(
            onDismiss = { showCreate = false },
            onCreate = { n, e, p, d, t -> onAddReward(n, e, p, d, t); showCreate = false },
        )
    }
}

@Composable
private fun ShopItemCard(item: ShopItem, canAfford: Boolean, onBuy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.emoji, fontSize = 28.sp)
            Text(item.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = ColorText, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            BuyButton(item.price, canAfford, onBuy)
        }
    }
}

@Composable
private fun RewardCard(r: CustomReward, canAfford: Boolean, onBuy: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(14.dp)) {
        Box {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.emoji, fontSize = 28.sp)
                Text(r.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = ColorText, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (r.description.isBlank()) (if (r.type == "reward") "Redeem instantly" else "Keep & redeem later")
                     else r.description,
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BuyButton(r.price, canAfford, onBuy)
            }
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete reward", tint = ColorMuted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun BuyButton(price: Int, canAfford: Boolean, onBuy: () -> Unit) {
    Button(
        onClick = onBuy,
        modifier = Modifier.fillMaxWidth().height(34.dp),
        enabled = canAfford,
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorPrimary, contentColor = ColorOnPrimary,
            disabledContainerColor = ColorSurfaceHigh, disabledContentColor = ColorMuted),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) { Text("🪙 $price", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CreateRewardDialog(onDismiss: () -> Unit, onCreate: (String, String, Int, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎁") }
    var price by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("reward") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ColorPrimary, unfocusedBorderColor = ColorSurfaceHigh, cursorColor = ColorPrimary,
        focusedTextColor = ColorText, unfocusedTextColor = ColorText)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text("New reward", color = ColorText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Emoji", color = ColorMuted) },
                        singleLine = true, modifier = Modifier.width(84.dp), colors = fieldColors)
                    OutlinedTextField(name, { name = it }, label = { Text("Name", color = ColorMuted) },
                        singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors)
                }
                OutlinedTextField(price, { price = it.filter(Char::isDigit).take(5) },
                    label = { Text("Price (🪙)", color = ColorMuted) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                OutlinedTextField(desc, { desc = it }, label = { Text("Description (optional)", color = ColorMuted) },
                    maxLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "reward", onClick = { type = "reward" }, label = { Text("Reward") })
                    FilterChip(selected = type == "consumable", onClick = { type = "consumable" }, label = { Text("Consumable") })
                }
                Text(
                    if (type == "reward") "Reward: coins are spent and it's logged as redeemed — go enjoy it."
                    else "Consumable: goes to My Items so you can redeem it later.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (price.toIntOrNull() ?: 0) > 0,
                onClick = { onCreate(name, emoji, price.toIntOrNull() ?: 1, desc, type) },
            ) { Text("Create", color = ColorPrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}

// ── My Items tab ──────────────────────────────────────────────────────────
@Composable
private fun MyItemsTab(inventory: List<InventoryItem>, onEquip: (InventoryItem) -> Unit, onRedeem: (InventoryItem) -> Unit) {
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

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val groups = inventory.groupBy { it.category }
        val catLabels = mapOf(
            "custom"         to "🎁 My Rewards",
            "powerup"        to "⚡ Power-ups",
            "cosmetic_frame" to "🖼️ Frames",
            "cosmetic_theme" to "🎨 Themes",
            "cosmetic_badge" to "🏅 Badges",
        )
        catLabels.forEach { (cat, label) ->
            val list = groups[cat] ?: return@forEach
            item(key = "h_$cat") {
                Text(label, style = MaterialTheme.typography.labelLarge, color = ColorMuted)
            }
            items(list, key = { it.id }) { inv ->
                InventoryItemCard(inv, onEquip = { onEquip(inv) }, onRedeem = { onRedeem(inv) })
            }
        }
    }
}

@Composable
private fun InventoryItemCard(item: InventoryItem, onEquip: () -> Unit, onRedeem: () -> Unit) {
    val isCosmetic = item.category.startsWith("cosmetic")
    val isCustom = item.category == "custom"
    Card(colors = CardDefaults.cardColors(containerColor = ColorSurface), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(ColorSurfaceHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = 20.sp) }

            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = ColorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val statusText = when {
                    item.isEquipped     -> "✓ Equipped"
                    isCustom && item.usedAt == null -> "Ready to redeem"
                    item.usedAt != null -> "Used"
                    else                -> "Not equipped"
                }
                Text(statusText, style = MaterialTheme.typography.labelSmall,
                    color = if (item.isEquipped || (isCustom && item.usedAt == null)) ColorPrimary else ColorMuted)
            }

            if (isCosmetic && !item.isEquipped) {
                Button(onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("Equip", style = MaterialTheme.typography.labelSmall) }
            }
            if (isCustom && item.usedAt == null) {
                Button(onClick = onRedeem,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorCoin, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("Redeem", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
