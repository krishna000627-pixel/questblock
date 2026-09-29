package com.krishna.questblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home",      "Home",      Icons.Default.Home),
    NavItem("quests",    "Quests",    Icons.Default.Star),
    NavItem("vault",     "Vault",     Icons.Default.Lock),
    NavItem("inventory", "Inventory", Icons.Default.ShoppingCart),
    NavItem("profile",   "Profile",   Icons.Default.Person),
)

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
                        onClick  = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor       = ColorPrimary,
                            selectedTextColor       = ColorPrimary,
                            indicatorColor          = ColorPrimary.copy(alpha = 0.15f),
                            unselectedIconColor     = ColorMuted,
                            unselectedTextColor     = ColorMuted,
                        ),
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "home",
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable("home")      { HomeScreen() }
            composable("quests")    { QuestsScreen() }
            composable("vault")     { VaultScreen() }
            composable("inventory") { InventoryScreen() }
            composable("profile")   { ProfileScreen() }
        }
    }
}
