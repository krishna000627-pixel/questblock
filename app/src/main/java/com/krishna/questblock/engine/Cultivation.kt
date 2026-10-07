package com.krishna.questblock.engine

/** One cultivation realm: a name, its stages and the XP each stage costs. */
class Realm(val name: String, val emoji: String, val stages: List<String>, val perStage: Int)

class CultivationInfo(
    val realm: Realm,
    val realmIndex: Int,
    val stageIndex: Int,
    val stageName: String,
    /** XP gathered inside the current stage and the XP the stage needs. */
    val into: Int,
    val need: Int,
    /** XP left until the next realm (null at the very top). */
    val toNextRealm: Int?,
    val nextRealm: String?,
    val atTop: Boolean,
) {
    val stageLabel: String get() = if (stageName.startsWith("Layer")) stageName else "$stageName stage"
    val isPeakStage: Boolean get() = stageIndex == realm.stages.lastIndex
}

/**
 * Cultivation-style levelling. Total XP carries you through realms; each realm has stages,
 * and the last stage of a realm is the gate to a breakthrough into the next one.
 */
object Cultivation {
    private val LAYERS = (1..9).map { "Layer $it" }
    private val FOUR = listOf("Early", "Middle", "Late", "Peak")

    val REALMS = listOf(
        Realm("Qi Condensation", "🌬️", LAYERS, 100),
        Realm("Foundation Establishment", "🏛️", FOUR, 300),
        Realm("Core Formation", "🔮", FOUR, 600),
        Realm("Nascent Soul", "🕊️", FOUR, 1000),
        Realm("Soul Transformation", "🦋", FOUR, 1600),
        Realm("Void Refinement", "🌌", FOUR, 2400),
        Realm("Immortal Ascension", "⚡", FOUR, 3500),
    )

    fun of(xp: Int): CultivationInfo {
        var left = xp.coerceAtLeast(0)
        REALMS.forEachIndexed { ri, r ->
            val total = r.stages.size * r.perStage
            if (left < total) {
                val si = left / r.perStage
                return CultivationInfo(
                    r, ri, si, r.stages[si], left % r.perStage, r.perStage,
                    total - left, REALMS.getOrNull(ri + 1)?.name ?: "Dao Sage", false,
                )
            }
            left -= total
        }
        val last = REALMS.last()
        return CultivationInfo(last, REALMS.lastIndex, last.stages.lastIndex, last.stages.last(), 0, 0, null, null, true)
    }
}
