package com.krishna.questblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home",    "Home",    Icons.Default.Home),
    NavItem("quests",  "Quests",  Icons.Default.Star),
    NavItem("focus",   "Focus",   Icons.Default.Bolt),
    NavItem("vault",   "Vault",   Icons.Default.Lock),
    NavItem("profile", "Profile", Icons.Default.Person),
)
private val TAB_ROUTES = NAV_ITEMS.map { it.route }

/**
 * One way to switch tabs, used by the bottom bar AND every shortcut.
 * v9 saved and restored each tab's whole stack, so tapping Quests while "Add Quest" had been open
 * brought Add Quest straight back. Now a tab always opens on its main page: sub-pages above the
 * start destination are dropped and nothing is restored.
 */
private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = false }
    launchSingleTop = true
    restoreState = false
}

private fun NavHostController.openPage(route: String) = navigate(route) { launchSingleTop = true }

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Sub-pages (Add Quest, Shop, Stats, Settings, …) highlight the tab they were opened from.
    var lastTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(route) { if (route != null && route in TAB_ROUTES) lastTab = route }
    val activeTab = if (route != null && route in TAB_ROUTES) route else lastTab

    Scaffold(
        containerColor = ColorBg,
        bottomBar = {
            NavigationBar(containerColor = ColorSurface) {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = activeTab == item.route,
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
                    onAddQuest = { navController.openPage("quest_types") },
                    onGoFocus  = { navController.goTab("focus") },
                    onOpenShop = { navController.openPage("shop") },
                    onOpenStats = { navController.openPage("stats") },
                )
            }
            composable("quests") {
                QuestsScreen(
                    onAddQuest = { navController.openPage("quest_types") },
                    onEditQuest = { id -> navController.openPage("create_quest?taskId=$id") },
                    onOpenFocus = { navController.goTab("focus") },
                )
            }
            composable("focus")   { FocusModeScreen() }
            composable("vault")   { VaultScreen() }
            composable("profile") {
                ProfileScreen(
                    onOpenShop = { navController.openPage("shop") },
                    onOpenStats = { navController.openPage("stats") },
                    onOpenSettings = { navController.openPage("settings") },
                    onOpenCoins = { navController.openPage("coins") },
                    onOpenAbout = { navController.openPage("about") },
                )
            }
            composable("quest_types") {
                QuestTypesScreen(
                    onBack = { navController.popBackStack() },
                    // replace the picker with the form, so Back from the form returns to where you started
                    onPick = { type ->
                        navController.navigate("create_quest?taskId=-1&type=$type") {
                            popUpTo("quest_types") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = "create_quest?taskId={taskId}&type={type}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("type") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { QuestCreatorScreen(onBack = { navController.popBackStack() }) }
            composable("stats")    { StatsScreen(onBack = { navController.popBackStack() }) }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReliability = { navController.openPage("reliability") },
                )
            }
            composable("reliability") { ReliabilityScreen(onBack = { navController.popBackStack() }) }
            composable("coins")        { CoinLogScreen(onBack = { navController.popBackStack() }) }
            composable("about")        { AboutMeScreen(onBack = { navController.popBackStack() }) }
            composable("shop")         { InventoryScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
