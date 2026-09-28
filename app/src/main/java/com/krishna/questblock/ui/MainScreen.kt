package com.krishna.questblock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private data class NavDest(val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavDest("Home",   Icons.Default.Home),
    NavDest("Quests", Icons.Default.Star),
    NavDest("Vault",  Icons.Default.Lock),
    NavDest("Stats",  Icons.Default.BarChart),
    NavDest("Profile",Icons.Default.Person),
)

@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = ColorBg,
        bottomBar = {
            NavigationBar(
                containerColor = ColorSurface,
                tonalElevation = androidx.compose.ui.unit.Dp(0f),
            ) {
                TABS.forEachIndexed { i, dest ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorPrimary,
                            selectedTextColor = ColorPrimary,
                            indicatorColor = ColorPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = ColorMuted,
                            unselectedTextColor = ColorMuted,
                        ),
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(onGoToQuests = { tab = 1 }, onGoToVault = { tab = 2 }, onGoToProfile = { tab = 4 })
                1 -> QuestsScreen()
                2 -> AppsScreen()
                3 -> StatsScreen()
                else -> ProfileScreen()
            }
        }
    }
}
