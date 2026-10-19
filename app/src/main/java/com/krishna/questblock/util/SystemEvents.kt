package com.krishna.questblock.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** v239: events the System theme reacts to (full-screen LEVEL UP, red penalty frame). Set from GameRepository, read by ui/SystemStyle.kt. */
object SystemEvents {
    var levelUpStamp by mutableIntStateOf(0)
    var penaltyOn by mutableStateOf(false)
    fun levelUp() { levelUpStamp++ }
    fun penalty() { penaltyOn = true }
}
