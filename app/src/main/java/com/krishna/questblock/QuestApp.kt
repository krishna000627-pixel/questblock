package com.krishna.questblock

import android.app.Application
import com.krishna.questblock.service.Reminders
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuestApp : Application() {
    @javax.inject.Inject lateinit var prefs: com.krishna.questblock.data.Prefs
    @javax.inject.Inject lateinit var repo: com.krishna.questblock.data.GameRepository

    override fun onCreate() {
        super.onCreate()
        com.krishna.questblock.util.DebugLog.init(this)
        Reminders.ensureChannel(this)
        com.krishna.questblock.util.ScreenLog.start(this)
        com.krishna.questblock.util.MusicLog.start(this, prefs)
        com.krishna.questblock.service.WidgetFeed.start(this, repo)
        val sp = getSharedPreferences("questblock", MODE_PRIVATE)
        if (!sp.getBoolean("starter_vault_v102", false)) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    val pm = packageManager
                    val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    val labels = pm.queryIntentActivities(i, 0).associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    repo.seedStarterVault(labels)
                    sp.edit().putBoolean("starter_vault_v102", true).apply()
                }
            }
        }
    }
}
