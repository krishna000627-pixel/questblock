package com.krishna.questblock.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.FunLimit
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Hw
import com.krishna.questblock.data.HwItem
import com.krishna.questblock.data.Player
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.QhApp
import com.krishna.questblock.data.QhApps
import com.krishna.questblock.data.QhState
import com.krishna.questblock.data.QhStore
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.service.BlockerAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Package changes and the Home button reach the launcher through this. */
const val EXTRA_QH_SETTINGS = "qh_open_settings"

object QuestHomeBus {
    var openSettings by mutableIntStateOf(0)
    var tick by mutableIntStateOf(0)
    var homePress by mutableIntStateOf(0)
}

/**
 * v212: Quest Home, the launcher theme. Only enabled (in the manifest, by [AppName.applyAll]) while the theme is on.
 * When the theme is turned off while this screen is open it hands over to the normal app, so it never traps you.
 */
@AndroidEntryPoint
class QuestHomeActivity : ComponentActivity() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { QuestHomeBus.tick = QuestHomeBus.tick + 1 }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemeMode(this)
        TermuxMode.load(this)
        QuestHomeMode.load(this)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_QH_SETTINGS, false) == true) QuestHomeBus.openSettings = QuestHomeBus.openSettings + 1
        setContent {
            QuestTheme {
                QuestHomeScreen(onLeave = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching { ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED) }
        QuestHomeBus.tick = QuestHomeBus.tick + 1
    }

    override fun onStop() {
        runCatching { unregisterReceiver(receiver) }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        QuestHomeBus.homePress = QuestHomeBus.homePress + 1    // Home pressed while already here: back to the first page
        if (intent.getBooleanExtra(EXTRA_QH_SETTINGS, false)) QuestHomeBus.openSettings = QuestHomeBus.openSettings + 1
    }
}

@HiltViewModel
class QuestHomeViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    val prefs: Prefs,
) : ViewModel() {
    val player = repo.player.stateIn(viewModelScope, SharingStarted.Eagerly, Player())
    val tasks = repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<TaskEntity>())
    val blocked = repo.blockedApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<BlockedApp>())

    var st by mutableStateOf(QhStore.load(ctx))
        private set
    var apps by mutableStateOf<List<QhApp>>(emptyList())
        private set
    var funLine by mutableStateOf("")
        private set
    var hw by mutableStateOf<List<HwItem>>(emptyList())
        private set
    var focusApps by mutableStateOf<Set<String>>(emptySet())
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                runCatching { refreshLive() }
                delay(5000L)
            }
        }
    }

    private suspend fun refreshLive() {
        now = System.currentTimeMillis()
        hw = Hw.dueSoon(ctx, now)
        focusApps = prefs.focusAllowed.split(",").filter { it.isNotBlank() }.toSet()
        val fl = FunLimit.state(ctx, prefs)
        funLine = when {
            prefs.funFreeToday -> "Fun: free day"
            fl.enabled -> {
                val left = ((fl.limitMs - fl.usedMs).coerceAtLeast(0L) / 60_000L)
                "Fun: " + (if (left >= 60) "${left / 60}h ${left % 60}m" else "${left}m") + " left"
            }
            else -> ""
        }
    }

    fun update(f: (QhState) -> QhState) { st = QhStore.update(ctx, f) }

    /** Reads the installed apps, seeds the first run, forgets uninstalled apps. */
    fun reloadApps() {
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { QhApps.load(ctx) }
            apps = l
            val installed = l.map { it.pkg }.toSet()
            withContext(Dispatchers.IO) { QhStore.seedIfNeeded(ctx, installed) }
            st = if (l.size > 3) QhStore.update(ctx) { it.cleanup(installed) } else QhStore.load(ctx)
        }
    }

    fun complete(t: TaskEntity) {
        viewModelScope.launch { repo.completeTaskUndoable(t) }
    }

    fun focusRunning(): Boolean = player.value.focusModeUntil > now
}

/** Icons are read once, then kept (about 160 of them). */
object QhIconCache {
    private val cache = LruCache<String, ImageBitmap>(160)
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun load(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return runCatching {
            val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
            cache.put(pkg, bmp)
            bmp
        }.getOrNull()
    }
}

internal fun qhLaunch(ctx: Context, pkg: String) {
    val i = if (pkg == ctx.packageName) Intent(ctx, MainActivity::class.java) else ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (i == null) { Toast.makeText(ctx, "Can't open that app", Toast.LENGTH_SHORT).show(); return }
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, "Can't open that app", Toast.LENGTH_SHORT).show() }
}

internal fun qhOpenQuestBlock(ctx: Context, route: String? = null) {
    val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (route != null) i.putExtra(EXTRA_OPEN_ROUTE, route)
    runCatching { ctx.startActivity(i) }
}

internal fun qhAppInfo(ctx: Context, pkg: String) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

internal fun qhUninstall(ctx: Context, pkg: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Opens Android's own "Home app" page, where QuestBlock can be picked as the default. */
internal fun qhPickHomeApp(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(ctx, "Open Settings > Apps > Default apps > Home app", Toast.LENGTH_LONG).show() }
}

internal fun qhLockScreen(ctx: Context) {
    if (!BlockerAccessibilityService.lockScreen()) {
        Toast.makeText(ctx, "Turn on QuestBlock accessibility to lock the screen with a double tap", Toast.LENGTH_SHORT).show()
    }
}

/** v213: opens the lock page straight away (no flash of the app first). The lock page opens the app itself after an unlock. */
internal fun qhLockPage(ctx: Context, pkg: String) {
    runCatching {
        ctx.startActivity(
            Intent(ctx, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }.onFailure { Toast.makeText(ctx, "Can't open the lock page", Toast.LENGTH_SHORT).show() }
}

/** v213: the picked picture is copied into the app (QhWallFile) and the wall is switched to it. */
internal fun qhImportWall(ctx: Context, uri: Uri): Boolean {
    val ok = com.krishna.questblock.data.QhWallFile.import(ctx, uri)
    if (!ok) Toast.makeText(ctx, "Could not read that picture", Toast.LENGTH_SHORT).show()
    return ok
}
