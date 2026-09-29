@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun VaultScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor   = ColorSurface,
            contentColor     = ColorPrimary,
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Blocked Apps") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Stats") })
        }
        when (tab) {
            0 -> AppsScreen()
            1 -> StatsScreen()
        }
    }
}
