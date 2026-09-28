package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
}

@Composable
fun AppsScreen(vm: AppsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BlockedApp?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Distraction apps", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Locked apps need coins to open.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows, key = { it.app.pkg }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.app.label)
                        row.rule?.let {
                            Text(
                                "${it.cost} 🪙 for ${it.unlockMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    row.rule?.let { rule ->
                        IconButton(onClick = { editing = rule }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit rule")
                        }
                    }
                    Switch(checked = row.rule != null, onCheckedChange = { vm.setBlocked(row.app, it) })
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
private fun RuleDialog(rule: BlockedApp, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    var cost by remember { mutableStateOf(rule.cost.toString()) }
    var minutes by remember { mutableStateOf(rule.unlockMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter(Char::isDigit) },
                    label = { Text("Coins per unlock") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Minutes per unlock") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    cost.toIntOrNull()?.coerceAtLeast(1) ?: rule.cost,
                    minutes.toIntOrNull()?.coerceIn(1, 240) ?: rule.unlockMinutes,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
