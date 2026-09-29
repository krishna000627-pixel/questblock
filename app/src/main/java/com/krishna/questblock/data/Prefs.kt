package com.krishna.questblock.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Prefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    var strictMode: Boolean
        get() = sp.getBoolean("strict", false)
        set(value) = sp.edit().putBoolean("strict", value).apply()

    var strictPausedUntil: Long
        get() = sp.getLong("strict_paused_until", 0L)
        set(value) = sp.edit().putLong("strict_paused_until", value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(value) = sp.edit().putBoolean("onboarding_done", value).apply()

    // Active study session package (persists across app kills)
    var activeStudyPkg: String
        get() = sp.getString("active_study_pkg", "") ?: ""
        set(value) = sp.edit().putString("active_study_pkg", value).apply()

    var activeStudySessionId: Long
        get() = sp.getLong("active_study_session_id", -1L)
        set(value) = sp.edit().putLong("active_study_session_id", value).apply()
}
