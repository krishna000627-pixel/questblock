package com.krishna.questblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

/** Finished quests that can still be taken back (optional 10-second Undo). */
object UndoBus {
    val events = kotlinx.coroutines.flow.MutableSharedFlow<com.krishna.questblock.data.GameRepository.CompletionUndo>(extraBufferCapacity = 4)
}

@dagger.hilt.android.lifecycle.HiltViewModel
class UndoViewModel @javax.inject.Inject constructor(private val repo: com.krishna.questblock.data.GameRepository) : androidx.lifecycle.ViewModel() {
    fun undo(u: com.krishna.questblock.data.GameRepository.CompletionUndo) =
        viewModelScope.launch { repo.undoCompletion(u) }
    val theme = repo.player.map { it.equippedTheme }
}

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
fun MainScreen(openRoute: String? = null, onRouteHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    // v206: read the strict Focus state at once (not after a delay), so a strict session opens straight on the Focus tab with no Home flash
    val strictSp = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE)
    fun strictNow() = strictSp.getBoolean("focus_strict", false) && strictSp.getLong("focus_until_cache", 0L) > System.currentTimeMillis()
    val startDest = remember { if (strictNow()) "focus" else "home" }
    // A notification asked for a page: tabs open on their main page, other pages open on top of Home
    LaunchedEffect(openRoute) {
        val r = openRoute ?: return@LaunchedEffect
        if (strictNow()) { onRouteHandled(); return@LaunchedEffect }   // v206: strict Focus ignores notification pages
        if (r in TAB_ROUTES) navController.goTab(r) else { navController.goTab("home"); navController.openPage(r) }
        onRouteHandled()
    }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Sub-pages (Add Quest, Shop, Stats, Settings, …) highlight the tab they were opened from.
    var lastTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(route) { if (route != null && route in TAB_ROUTES) lastTab = route }
    val activeTab = if (route != null && route in TAB_ROUTES) route else lastTab

    val undoVm: UndoViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val themeCtx = androidx.compose.ui.platform.LocalContext.current
    val themeId by undoVm.theme.collectAsState(initial = ShopTheme)
    LaunchedEffect(themeId) {
        ShopTheme = themeId
        themeCtx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE).edit().putString("shop_theme", themeId).apply()
        AppName.applyAll(themeCtx)     // v250: the launcher icon follows the theme (Ninja, System, Paper, Gothic have their own colours)
    }
    // v205: while a strict Focus session runs QuestBlock only shows the Focus timer
    var strict by remember { mutableStateOf(strictNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            strict = strictNow()
            kotlinx.coroutines.delay(1000L)
        }
    }
    LaunchedEffect(strict, route) { if (strict && route != null && route != "focus") navController.goTab("focus") }
    androidx.activity.compose.BackHandler(enabled = strict) { }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        UndoBus.events.collect { u ->
            val r = snack.showSnackbar(ThemeExtras.word("done", "Quest done"), actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) undoVm.undo(u)
        }
    }

    Scaffold(
        containerColor = ColorBg,
        topBar = { if (FxOn) androidx.compose.foundation.layout.Column {
            FxBar()
            if (SystemOn) SystemHost(strict) { navController.goTab("quests") }
            if (ThemeExtras.on) ThemeExtrasHost()
        } },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (!strict) NavigationBar(
                containerColor = ColorSurface,
                modifier = Modifier.glowLine(),
            ) {
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
        NavHost(navController = navController, startDestination = startDest,
            modifier = Modifier.fillMaxSize().padding(innerPadding).fxBackground()) {
            composable("home") {
                HomeScreen(
                    onAddQuest = { navController.openPage("quest_types") },
                    onGoFocus  = { navController.goTab("focus") },
                    onOpenShop = { navController.openPage("shop") },
                    onOpenStats = { navController.openPage("stats") },
                    onOpenHub = { navController.openPage("hub") },
                    onOpenChapters = { navController.openPage("chapters") },
                    onOpenAttendance = { navController.openPage("attendance") },
                    onOpenHomework = { navController.openPage("homework") },
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
            composable("stats")    { StatsScreen(onBack = { navController.popBackStack() }, onOpenUsage = { navController.openPage("usage") }) }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReliability = { navController.openPage("reliability") },
                    onOpenCategories = { navController.openPage("categories") },
                    onOpenUsage = { navController.openPage("usage") },
                    onOpenDebug = { navController.openPage("debuglog") },
                    onOpenVersions = { navController.openPage("versions") },
                    onOpenWeekly = { navController.openPage("weekly") },
                    onOpenSelfTest = { navController.openPage("selftest") },
                    onOpenIdeas = { navController.openPage("ideas") },
                    onOpenAbout = { navController.openPage("about") },
                )
            }
            composable("versions") { VersionScreen(onBack = { navController.popBackStack() }) }
            composable("selftest") { SelfTestScreen(onBack = { navController.popBackStack() }) }
            composable("attendance") { AttendanceScreen(onBack = { navController.popBackStack() }) }
            if (com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) {
                composable("homework") { HomeworkScreen(onBack = { navController.popBackStack() }, onGoFocus = { navController.goTab("focus") }) }
                composable("homework_add") { HomeworkScreen(onBack = { navController.popBackStack() }, onGoFocus = { navController.goTab("focus") }) }
            }
            composable("chapters") { ChaptersScreen(onBack = { navController.popBackStack() },
                onOpenSubject = { id, school -> navController.openPage("chapter_subject/$id/$school") },
                onOpenMarks = { navController.openPage("marks") }) }
            composable(
                route = "chapter_subject/{id}/{school}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("school") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                ChapterSubjectScreen(
                    subjectId = e.arguments?.getString("id") ?: "",
                    startSchool = e.arguments?.getBoolean("school") ?: false,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("marks") { MarksScreen(onBack = { navController.popBackStack() }) }
            composable("weekly") { WeeklyReviewScreen(onBack = { navController.popBackStack() }) }
            composable("debuglog") { DebugLogScreen(onBack = { navController.popBackStack() }) }
            composable("ideas") { IdeasScreen(onBack = { navController.popBackStack() }) }
            composable("unlock_timers") { UnlockTimersScreen(onBack = { navController.popBackStack() }) }
            composable("usage") { UsageScreen(onBack = { navController.popBackStack() }, onOpenCategories = { navController.openPage("categories") }) }
            composable("categories") { CategoriesScreen(onBack = { navController.popBackStack() }) }
            composable("reliability") { ReliabilityScreen(onBack = { navController.popBackStack() }) }
            composable("coins")        { CoinLogScreen(onBack = { navController.popBackStack() }) }
            composable("about")        { AboutMeScreen(onBack = { navController.popBackStack() }, onOpenIdeas = { navController.openPage("ideas") },
                onOpenVersions = { navController.openPage("versions") }) }
            composable("shop")         { InventoryScreen(onBack = { navController.popBackStack() }) }
            composable("hub")          { PlayerHubScreen(
                onBack = { navController.popBackStack() },
                onOpenGoals = { navController.openPage("hub_goals") },
                onOpenReports = { navController.openPage("weekly") },
                onOpenBadges = { navController.openPage("hub_badges") },
                onOpenRules = { navController.openPage("hub_rules") },
                onOpenExperiments = { navController.openPage("hub_experiments") },
            ) }
            composable("hub_goals")    { GoalsScreen(onBack = { navController.popBackStack() }) }
            composable("hub_badges")   { BadgesScreen(onBack = { navController.popBackStack() }) }
            composable("hub_rules")    { RulesScreen(onBack = { navController.popBackStack() }, onOpenSites = { navController.openPage("hub_sites") }) }
            composable("hub_experiments") { ExperimentsScreen(onBack = { navController.popBackStack() }) }
            composable("hub_sites")    { SitesScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

/** A thin glowing line along the top edge of the bottom bar (concept style). */
private fun Modifier.glowLine(): Modifier = this.then(
    Modifier.drawBehind {
        drawLine(
            color = ColorPrimary.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    }
)
