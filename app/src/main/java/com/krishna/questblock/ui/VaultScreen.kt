@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
) : ViewModel() {
    val blockedApps = repo.blockedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player      = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())

    private val _installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed = _installed.asStateFlow()

    var unlockResult by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _installed.value = pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }

    fun setBlocked(pkg: String, label: String, on: Boolean) =
        viewModelScope.launch { repo.setBlocked(pkg, label, on) }

    fun setStudy(pkg: String, label: String, on: Boolean) =
        viewModelScope.launch { repo.setStudyApp(pkg, label, on) }

    fun unlockApp(pkg: String, label: String, option: UnlockOption) = viewModelScope.launch {
        val ok = repo.unlockApp(pkg, label, option)
        unlockResult = if (ok) "✓ ${label} unlocked for ${option.label}" else "Not enough coins 🪙"
    }

    fun clearResult() { unlockResult = null }
}

@Composable
fun VaultScreen(vm: VaultViewModel = hiltViewModel()) {
    val blocked   by vm.blockedApps.collectAsStateWithLifecycle()
    val player    by vm.player.collectAsStateWithLifecycle()
    val installed by vm.installed.collectAsStateWithLifecycle()

    var search       by remember { mutableStateOf("") }
    var unlockTarget by remember { mutableStateOf<BlockedApp?>(null) }
    val snack = remember { SnackbarHostState() }
    val result = vm.unlockResult

    LaunchedEffect(result) {
        if (result != null) { snack.showSnackbar(result); vm.clearResult() }
    }

    val now = System.currentTimeMillis()
    val studyApps   = blocked.filter { it.isStudyApp }
    val lockedApps  = blocked.filter { !it.isStudyApp }
    val allFiltered = installed.filter {
        search.isBlank() || it.label.contains(search, ignoreCase = true)
    }
    val notInBlocked = allFiltered.filter { app -> blocked.none { it.packageName == app.pkg } }

    Scaffold(containerColor = ColorBg, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Header
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Vault", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, color = ColorText)
                        Text("${lockedApps.size} locked · ${studyApps.size} study · 🪙 ${player.coins}",
                            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    placeholder = { Text("Search apps…", color = ColorMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = ColorMuted, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorPrimary, unfocusedBorderColor = ColorDivider,
                        cursorColor = ColorPrimary, focusedTextColor = ColorText, unfocusedTextColor = ColorText,
                        focusedContainerColor = ColorSurface, unfocusedContainerColor = ColorSurface,
                    ),
                )
            }

            // Study apps section
            if (studyApps.isNotEmpty() && search.isBlank()) {
                item {
                    Text("📚 Study Apps · earn 1🪙/5min",
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorPrimary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(studyApps, key = { "study_${it.packageName}" }) { app ->
                    AppRow(
                        label = app.label, pkg = app.packageName,
                        subtitle = "Study app · earns coins while in use",
                        isOn = true, showUnlock = false, coins = player.coins, now = now, unlockedUntil = 0L,
                        onToggle = { vm.setStudy(app.packageName, app.label, false) },
                        onUnlock = {},
                    )
                }
            }

            // Blocked apps section
            if (lockedApps.isNotEmpty() && search.isBlank()) {
                item {
                    Text("🔒 Blocked Apps",
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorMuted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(lockedApps, key = { it.packageName }) { app ->
                    val isUnlocked = app.unlockedUntil > now
                    val remaining = if (isUnlocked) app.unlockedUntil - now else 0L
                    AppRow(
                        label = app.label, pkg = app.packageName,
                        subtitle = if (isUnlocked) "Unlocked · ${formatRemaining(remaining)}" else "Locked",
                        isOn = true, showUnlock = !isUnlocked, coins = player.coins, now = now,
                        unlockedUntil = app.unlockedUntil,
                        onToggle = { vm.setBlocked(app.packageName, app.label, false) },
                        onUnlock = { unlockTarget = app },
                    )
                }
            }

            // All apps
            if (search.isNotBlank() || lockedApps.isEmpty()) {
                item {
                    Text(if (search.isBlank()) "All Apps" else "Search results",
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorMuted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
            }
            items(notInBlocked, key = { "all_${it.pkg}" }) { app ->
                AppRow(
                    label = app.label, pkg = app.pkg,
                    subtitle = "Tap to block · long-press for study",
                    isOn = false, showUnlock = false, coins = player.coins, now = now, unlockedUntil = 0L,
                    onToggle = { vm.setBlocked(app.pkg, app.label, true) },
                    onUnlock = {},
                    onLongPress = { vm.setStudy(app.pkg, app.label, true) },
                )
            }
        }
    }

    // Unlock duration picker sheet
    unlockTarget?.let { app ->
        UnlockSheet(
            app = app, playerCoins = player.coins,
            onDismiss = { unlockTarget = null },
            onUnlock  = { option -> vm.unlockApp(app.packageName, app.label, option); unlockTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockSheet(
    app: BlockedApp, playerCoins: Int,
    onDismiss: () -> Unit, onUnlock: (UnlockOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ColorCard) {
        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Unlock ${app.label}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = ColorText)
            Text("🪙 You have $playerCoins coins", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            Spacer(Modifier.height(4.dp))
            UNLOCK_OPTIONS.forEach { option ->
                val canAfford = playerCoins >= option.cost
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (canAfford) ColorSurface else ColorSurfaceHigh, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(option.label, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = if (canAfford) ColorText else ColorMuted)
                    }
                    Button(
                        onClick = { onUnlock(option) }, enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary, contentColor = ColorOnPrimary,
                            disabledContainerColor = ColorSurfaceHigh, disabledContentColor = ColorMuted,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text("🪙 ${option.cost}", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    label: String, pkg: String, subtitle: String,
    isOn: Boolean, showUnlock: Boolean, coins: Int, now: Long, unlockedUntil: Long,
    onToggle: () -> Unit, onUnlock: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val isCurrentlyUnlocked = unlockedUntil > now
    Card(colors = CardDefaults.cardColors(
        containerColor = when {
            isCurrentlyUnlocked -> ColorPrimary.copy(0.08f)
            isOn -> ColorCard
            else -> ColorSurface
        }),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // App initial avatar
            Box(Modifier.size(36.dp).background(
                if (isOn) ColorPrimary.copy(0.15f) else ColorSurfaceHigh, CircleShape),
                contentAlignment = Alignment.Center) {
                Text(label.firstOrNull()?.uppercase() ?: "?", fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOn) ColorPrimary else ColorMuted)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = ColorText)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
            // Unlock + button
            if (showUnlock) {
                IconButton(onClick = onUnlock, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Unlock",
                        tint = ColorCoin, modifier = Modifier.size(18.dp))
                }
            }
            // Block toggle
            Switch(checked = isOn, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ColorOnPrimary, checkedTrackColor = ColorPrimary,
                    uncheckedThumbColor = ColorMuted, uncheckedTrackColor = ColorSurfaceHigh,
                ),
                modifier = Modifier.height(24.dp),
            )
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val h = ms / 3_600_000; val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h ${m}m left" else "${m}m left"
}
