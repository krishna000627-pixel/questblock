package com.krishna.questblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home",    "Home",    Icons.Default.Home),
    NavItem("quests",  "Quests",  Icons.Default.Star),
    NavItem("focus",   "Focus",   Icons.Default.Bolt),
    NavItem("vault",   "Vault",   Icons.Default.Lock),
    NavItem("profile", "Profile", Icons.Default.Person),
)

/** One way to switch tabs, used by the bottom bar AND every shortcut, so a tab is never pushed twice. */
private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        containerColor = ColorBg,
        bottomBar = {
            NavigationBar(containerColor = ColorSurface) {
                val backStack by navController.currentBackStackEntryAsState()
                val current   = backStack?.destination
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == item.route } == true,
                        onClick  = { navController.goTab(item.route) },
                        icon  = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = ColorPrimary, selectedTextColor   = ColorPrimary,
                            indicatorColor      = ColorPrimary.copy(0.15f),
                            unselectedIconColor = ColorMuted,  unselectedTextColor = ColorMuted,
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            composable("home") {
                HomeScreen(
                    onAddQuest = { navController.navigate("create_quest") { launchSingleTop = true } },
                    onGoFocus  = { navController.goTab("focus") },
                    onOpenShop = { navController.navigate("shop") { launchSingleTop = true } },
                )
            }
            composable("quests") {
                QuestsScreen(
                    onAddQuest = { navController.navigate("create_quest") { launchSingleTop = true } },
                    onOpenFocus = { navController.goTab("focus") },
                )
            }
            composable("focus")   { FocusModeScreen() }
            composable("vault")   { VaultScreen() }
            composable("profile") {
                ProfileScreen(onOpenShop = { navController.navigate("shop") { launchSingleTop = true } })
            }
            composable("create_quest") { QuestCreatorScreen(onBack = { navController.popBackStack() }) }
            composable("shop")         { InventoryScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
