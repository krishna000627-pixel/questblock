package com.krishna.questblock.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** v239/v240: events the style themes react to (LEVEL UP, penalty look, per-theme quest effects). Set from GameRepository, read by ui/SystemStyle.kt and ui/ThemeExtras.kt. */
object SystemEvents {
    var levelUpStamp by mutableIntStateOf(0)
    var penaltyOn by mutableStateOf(false)
    var penaltyStamp by mutableIntStateOf(0)
    var questDoneStamp by mutableIntStateOf(0)
    var armyCount by mutableIntStateOf(0)
    var lastCoins = 0
    var lastXp = 0
    fun levelUp() { levelUpStamp++ }
    fun penalty() { penaltyOn = true; penaltyStamp++ }
    fun questDone(coins: Int, xp: Int) { lastCoins = coins; lastXp = xp; questDoneStamp++ }
}
