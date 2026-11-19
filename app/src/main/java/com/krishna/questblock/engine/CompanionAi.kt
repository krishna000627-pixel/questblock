package com.krishna.questblock.engine

import com.krishna.questblock.data.CompanionState
import com.krishna.questblock.data.CompanionStore
import com.krishna.questblock.data.Player
import java.util.Calendar

/**
 * v295: Lightweight, zero-bloat inbuilt Companion AI engine.
 * Synthesizes context-aware responses from player stats, time of day, pet mood, and species personality.
 */
object CompanionAi {
    fun greet(player: Player, comp: CompanionState): String {
        val spec = CompanionStore.specOf(comp.activeId)
        val name = comp.customName.ifBlank { spec.baseName }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeWord = when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
        if (comp.hunger < 30) return "$name: My stomach is growling. A quick snack will power me up!"
        return when (comp.activeId) {
            "wolf" -> "$name: $timeWord, Commander. Streak at ${player.streak}d. Lock in and conquer a quest."
            "owl" -> "$name: $timeWord, Scholar. You have ${player.xp} XP—ready for a focused study block?"
            "cat" -> "$name: $timeWord! Chain two quests back-to-back for a combo boost!"
            "dragon" -> "$name: $timeWord. True mastery is built one disciplined hour at a time."
            "frog" -> "$name: $timeWord. Breathe steady. One calm task at a time."
            else -> "$name: $timeWord! ${player.coins} coins in the pouch—let's keep the streak alive!"
        }
    }

    fun reply(prompt: String, player: Player, comp: CompanionState): String {
        val spec = CompanionStore.specOf(comp.activeId)
        val name = comp.customName.ifBlank { spec.baseName }
        val q = prompt.lowercase()
        return when {
            "focus" in q || "study" in q || "distract" in q -> when (comp.activeId) {
                "owl" -> "$name: Start a 25-min Focus timer with Library Hearth audio. Zero distractions."
                "wolf" -> "$name: Turn on Strict Focus. Let the Vault guard your time."
                "frog" -> "$name: Clear your desk, pick one chapter, and start a calm Focus block."
                else -> "$name: Hit Focus for 25 minutes—I'll guard your streak while you work!"
            }
            "motivat" in q || "tired" in q || "lazy" in q || "bored" in q -> when (comp.activeId) {
                "dragon" -> "$name: Even legends start with one small quest. Do just 5 minutes right now."
                "cat" -> "$name: Finish 1 easy quest to trigger a combo—momentum does the rest!"
                else -> "$name: You're on a ${player.streak}d streak with ${player.xp} XP. Don't break the chain!"
            }
            "how are you" in q || "mood" in q || "status" in q -> {
                "$name: Feeling ${comp.mood.lowercase()} at Lv.${comp.level} (${spec.stages[comp.stageIndex]}). Hunger ${comp.hunger}%, Bond ${comp.happiness}%."
            }
            "what" in q || "next" in q || "do" in q || "plan" in q -> {
                if (comp.hunger < 40) "$name: First, grab my daily snack, then tackle your hardest quest while fresh!"
                else if (player.coins < 10) "$name: Coin pouch is light (${player.coins}). Clear 2 quests to stack rewards!"
                else "$name: Knock out one Focus session or daily routine—you'll level us both up!"
            }
            else -> "$name: I'm right beside you (Lv.${comp.level}). Clear a quest or start Focus to grow stronger together!"
        }
    }
}
