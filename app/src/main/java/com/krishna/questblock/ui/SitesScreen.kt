@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.krishna.questblock.data.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SiteRow(val domain: String, val unblockPending: Boolean)

@HiltViewModel
class SitesViewModel @Inject constructor(private val prefs: Prefs) : ViewModel() {
    var rows by mutableStateOf(load()); private set
    var error by mutableStateOf(""); private set

    private fun load() = prefs.blockedSites().map { SiteRow(it, prefs.siteUnblockPending(it)) }

    fun add(raw: String): Boolean {
        val ok = prefs.addSite(raw)
        error = if (ok) "" else "Enter a site like instagram.com"
        rows = load()
        return ok
    }

    fun remove(d: String) { prefs.removeSite(d); rows = load() }
}

@Composable
fun SitesScreen(onBack: () -> Unit, vm: SitesViewModel = hiltViewModel()) {
    var text by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HubHeader("Website blocker", onBack) }
        item {
            HubCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        placeholder = { Text("instagram.com") }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Button(onClick = { if (vm.add(text)) text = "" }) { Text("Block") }
                }
                if (vm.error.isNotEmpty()) Text(vm.error, color = ColorWarning, style = MaterialTheme.typography.bodySmall)
                Text("Unlock with coins, like apps. Fully locked when Fun is used up. Unblocking waits until midnight. Works in browsers that show their address bar.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        items(vm.rows, key = { it.domain }) { r ->
            HubCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.domain, color = ColorText, fontWeight = FontWeight.SemiBold)
                        if (r.unblockPending) Text("Unblocks at midnight", style = MaterialTheme.typography.labelSmall, color = ColorWarning)
                    }
                    if (!r.unblockPending) TextButton(onClick = { vm.remove(r.domain) }) { Text("Remove") }
                }
            }
        }
    }
}
