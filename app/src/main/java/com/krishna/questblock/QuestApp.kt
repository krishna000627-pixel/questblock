package com.krishna.questblock

import android.app.Application
import com.krishna.questblock.service.Reminders
import dagger.hilt.android.HiltAndroidApp

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
    }
}
