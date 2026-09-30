package com.krishna.questblock

import android.app.Application
import com.krishna.questblock.service.Reminders
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
    }
}
