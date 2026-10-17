package com.krishna.questblock.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.service.KeepAlive
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Extra on the intent of a notification: the page (route) to open. */
const val EXTRA_OPEN_ROUTE = "open_route"

/** PendingIntent for a notification that opens QuestBlock on one page. Own request code per page, so the pages never share one intent. */
fun openRoutePending(ctx: Context, route: String): PendingIntent = PendingIntent.getActivity(
    ctx, 7000 + (route.hashCode() and 0xFFF),
    Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_ROUTE, route),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: Prefs

    // Reactive state — changing this triggers recompose without restart
    private var onboardingDone by mutableStateOf(false)
    private var openRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoute = intent.getStringExtra(EXTRA_OPEN_ROUTE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onboardingDone = prefs.onboardingDone
        openRoute = intent?.getStringExtra(EXTRA_OPEN_ROUTE)
        if (onboardingDone) KeepAlive.start(this)
        ColorPrimary = ComposeColor(prefs.accentArgb)
        ShopTheme = getSharedPreferences("questblock", MODE_PRIVATE).getString("shop_theme", "default") ?: "default"
        loadThemeMode(this)
        TermuxMode.load(this)   // v207: Termux theme (one terminal instead of the normal pages)
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            QuestTheme {
                if (TermuxOn) {
                    // v207: every page is a command in one terminal; notifications open it with the command typed in
                    TermuxShell(initialRoute = openRoute, onRouteHandled = { openRoute = null; intent?.removeExtra(EXTRA_OPEN_ROUTE) })
                } else if (onboardingDone) {
                    MainScreen(openRoute = openRoute, onRouteHandled = { openRoute = null; intent?.removeExtra(EXTRA_OPEN_ROUTE) })
                } else {
                    OnboardingScreen(onDone = {
                        onboardingDone = true   // triggers recompose → MainScreen instantly
                        KeepAlive.start(this@MainActivity)
                    })
                }
            }
        }
    }
}
