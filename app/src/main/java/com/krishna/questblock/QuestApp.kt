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
        com.krishna.questblock.data.Flags.load(this)
        com.krishna.questblock.util.DebugLog.init(this)
        com.krishna.questblock.util.Sounds.init(this)
        Reminders.ensureChannel(this)
        com.krishna.questblock.util.ScreenLog.start(this)
        com.krishna.questblock.util.MusicLog.start(this, prefs)
        com.krishna.questblock.service.WidgetFeed.start(this, repo)
        val sp = getSharedPreferences("questblock", MODE_PRIVATE)
        val mig = getSharedPreferences("qb_migrations", MODE_PRIVATE)   // survives the reset below
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                var studyFirstPending = false
                // v103: one-time full reset (database + settings), then balanced starting rules
                if (!mig.getBoolean("reset_v103", false)) {
                    repo.resetAll()
                    sp.edit()
                        .putInt("fun_limit_min", 0).putInt("weekend_fun_limit", 0)
                        .putBoolean("strict", false).putBoolean("cultivation_on", false)
                        .putBoolean("opt_counter", false).putBoolean("opt_undo", true)
                        .putBoolean("opt_warn80", false).putBoolean("opt_focus_notif", true)
                        .apply()
                    mig.edit().putBoolean("reset_v103", true).apply()
                }
                // v104 (v286: now off): YouTube Shorts lock is not picked by default; turn it on in the Vault
                if (!mig.getBoolean("yt_shorts_v104", false)) {
                    sp.edit().putBoolean("short_yt", false).apply()
                    mig.edit().putBoolean("yt_shorts_v104", true).apply()
                }
                // v106 study first, then coding, far less fun (one time; you can change everything after)
                if (!mig.getBoolean("study_first_v106", false)) {
                    sp.edit().putInt("fun_limit_min", 0).putInt("weekend_fun_limit", 0).putInt("study_min", 3).apply()
                    mig.edit().putBoolean("study_first_v106", true).apply()
                    studyFirstPending = true
                }
                if (!sp.getBoolean("starter_vault_v103", false)) {
                    val pm = packageManager
                    val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    val labels = pm.queryIntentActivities(i, 0).associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    repo.seedStarterVault(labels)
                    sp.edit().putBoolean("starter_vault_v103", true).apply()
                }
                if (studyFirstPending || !mig.getBoolean("fun_price_v106", false)) {
                    repo.makeFunExpensive()
                    mig.edit().putBoolean("fun_price_v106", true).apply()
                }
            }
        }
    }
}
