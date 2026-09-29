package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InstalledApp(val pkg: String, val label: String)
data class AppRow(val app: InstalledApp, val rule: BlockedApp?)

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val query = MutableStateFlow("")

    val rows = combine(installed, repo.blockedApps, query) { apps, blocked, q ->
        val rules = blocked.associateBy { it.packageName }
        apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
            .map { AppRow(it, rules[it.pkg]) }
            .sortedWith(compareBy<AppRow>({ it.rule == null }, { it.app.label.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            installed.value = pm.queryIntentActivities(launcherIntent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setBlocked(app: InstalledApp, on: Boolean) =
        viewModelScope.launch { repo.setBlocked(app.pkg, app.label, on) }
    fun updateRule(rule: BlockedApp, cost: Int, minutes: Int) =
        viewModelScope.launch { repo.updateRule(rule, cost, minutes) }
    fun setStudyApp(app: InstalledApp, isStudy: Boolean) =
        viewModelScope.launch { repo.setStudyApp(app.pkg, app.label, isStudy) }
}

@Composable
fun AppsScreen(vm: AppsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BlockedApp?>(null) }

    val now = System.currentTimeMillis()
    val lockedRows = rows.filter { it.rule != null }
    val unlockedRows = rows.filter { it.rule == null }

    Column(Modifier.fillMaxSize()) {
        // Header
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                "Vault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ColorText,
            )
            Text(
                "${lockedRows.size} app${if (lockedRows.size != 1) "s" else ""} locked",
                style = MaterialTheme.typography.bodySmall,
                color = ColorMuted,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search apps", color = ColorMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ColorMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorPrimary,
                    unfocusedBorderColor = ColorSurfaceHigh,
                    cursorColor = ColorPrimary,
                    focusedTextColor = ColorText,
                    unfocusedTextColor = ColorText,
                    focusedContainerColor = ColorSurface,
                    unfocusedContainerColor = ColorSurface,
                ),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val studyRows  = rows.filter { it.rule?.isStudyApp == true }
            val blockedRows2 = rows.filter { it.rule != null && it.rule.isStudyApp == false }

            if (studyRows.isNotEmpty()) {
                item { Text("📚 Study apps (earn 1🪙/5min)", style = MaterialTheme.typography.labelLarge, color = ColorPrimary) }
                items(studyRows, key = { "s_${it.app.pkg}" }) { row ->
                    AppCard(row, now, onEditRule = {}, onToggle = { vm.setStudyApp(row.app, it) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (blockedRows2.isNotEmpty()) {
                item { Text("🔒 Blocked apps", style = MaterialTheme.typography.labelLarge, color = ColorMuted) }
                items(blockedRows2, key = { it.app.pkg }) { row ->
                    AppCard(row, now, onEditRule = { editing = row.rule }, onToggle = { vm.setBlocked(row.app, it) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (unlockedRows.isNotEmpty()) {
                item { Text("All apps", style = MaterialTheme.typography.labelLarge, color = ColorMuted) }
                items(unlockedRows, key = { "u_${it.app.pkg}" }) { row ->
                    // Long-press to mark as study; single toggle to block
                    AppCard(row, now, onEditRule = {}, onToggle = { vm.setBlocked(row.app, it) })
                }
            }

            if (rows.isEmpty() && query.isBlank()) {
                item {
                    Spacer(Modifier.height(40.dp))
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🔒", style = MaterialTheme.typography.displayMedium)
                        Text("No apps found", style = MaterialTheme.typography.titleMedium, color = ColorText)
                        Text("Toggle an app to lock it.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                    }
                }
            }
        }
    }

    editing?.let { rule ->
        RuleDialog(
            rule = rule,
            onDismiss = { editing = null },
            onSave = { cost, minutes ->
                vm.updateRule(rule, cost, minutes)
                editing = null
            },
        )
    }
}

@Composable
private fun AppCard(
    row: AppRow,
    now: Long,
    onEditRule: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val isLocked = row.rule != null
    val isActivelyUnlocked = (row.rule?.unlockedUntil ?: 0L) > now
    val remaining = if (isActivelyUnlocked) ((row.rule!!.unlockedUntil - now) / 1000) else 0L

    Card(
        onClick = { if (isLocked) onEditRule() },
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) ColorSurface else ColorSurface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // App icon placeholder (letter avatar)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isLocked) ColorPrimary.copy(alpha = 0.15f) else ColorSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (isLocked) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ColorPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        row.app.label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = ColorMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Text(row.app.label, style = MaterialTheme.typography.bodyMedium, color = ColorText, fontWeight = if (isLocked) FontWeight.Medium else FontWeight.Normal)
                if (row.rule != null) {
                    if (row.rule.isStudyApp) {
                        Text("📚 Study app · earns 1🪙/5min", style = MaterialTheme.typography.labelSmall, color = ColorPrimary)
                    } else if (isActivelyUnlocked) {
                        val m = remaining / 60; val s = remaining % 60
                        Text("Unlocked · %02d:%02d left".format(m, s), style = MaterialTheme.typography.labelSmall, color = ColorCoin)
                    } else {
                        Text("${row.rule.cost} 🪙 · ${row.rule.unlockMinutes} min", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                }
            }

            Switch(
                checked = isLocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ColorOnPrimary,
                    checkedTrackColor = ColorPrimary,
                    uncheckedThumbColor = ColorMuted,
                    uncheckedTrackColor = ColorSurfaceHigh,
                ),
            )
        }
    }
}

@Composable
private fun RuleDialog(rule: BlockedApp, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var cost by remember { mutableStateOf(rule.cost.toString()) }
    var minutes by remember { mutableStateOf(rule.unlockMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = { Text(rule.label, color = ColorText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter(Char::isDigit) },
                    label = { Text("Coins per unlock", color = ColorMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorPrimary,
                        unfocusedBorderColor = ColorSurfaceHigh,
                        focusedTextColor = ColorText,
                        unfocusedTextColor = ColorText,
                        cursorColor = ColorPrimary,
                    ),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutes per unlock", color = ColorMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorPrimary,
                        unfocusedBorderColor = ColorSurfaceHigh,
                        focusedTextColor = ColorText,
                        unfocusedTextColor = ColorText,
                        cursorColor = ColorPrimary,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    cost.toIntOrNull()?.coerceAtLeast(1) ?: rule.cost,
                    minutes.toIntOrNull()?.coerceIn(1, 240) ?: rule.unlockMinutes,
                )
            }) { Text("Save", color = ColorPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ColorMuted) } },
    )
}

