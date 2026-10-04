package com.krishna.questblock.util

import android.content.Context
import android.provider.Settings
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.UsageTracker

/** What must stay switched on for blocking and the fun-limit commitment to work. */
object Health {
    class Problem(val id: String, val title: String, val text: String, val action: String)

    fun problems(ctx: Context, prefs: Prefs): List<Problem> {
        val out = ArrayList<Problem>()
        if (!Perms.accessibilityEnabled(ctx)) {
            out += Problem("acc", "Blocking is off", "Turn QuestBlock back on in Accessibility.", Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        if (prefs.funRulesActive) {
            if (!UsageTracker.hasAccess(ctx)) {
                out += Problem("usage", "Fun limit can't be counted", "Turn Usage access back on for QuestBlock.", Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            if (!Perms.batteryExempt(ctx)) {
                out += Problem("battery", "Battery saver may stop QuestBlock", "Set QuestBlock to Unrestricted so the fun limit keeps working.", Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
        }
        return out
    }
}
