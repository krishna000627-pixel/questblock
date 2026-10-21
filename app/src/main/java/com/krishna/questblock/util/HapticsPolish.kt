package com.krishna.questblock.util

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * v281: one light, consistent tick for taps (filter pills, shortcut pills, the swipe hint). It uses the system tick of the phone,
 * follows the same Haptics switch as everything else (flag "haptics"), and needs no new permission.
 * Quest done and level up already buzz through Haptics.event("coin") and Haptics.event("level"), so they are not repeated here.
 */
object HapticsPolish {
    fun tap(view: View) {
        if (!Haptics.on || !com.krishna.questblock.data.Flags.get("ui_haptic_ticks")) return
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}
