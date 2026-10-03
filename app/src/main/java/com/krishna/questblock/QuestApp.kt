package com.krishna.questblock

import android.app.Application
import com.krishna.questblock.service.Reminders
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.krishna.questblock.util.DebugLog.init(this)
        Reminders.ensureChannel(this)
        com.krishna.questblock.util.ScreenLog.start(this)
    }
}
