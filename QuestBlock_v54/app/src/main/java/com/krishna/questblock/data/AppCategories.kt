package com.krishna.questblock.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** The four buckets used by the App usage page. */
data class UsageCategory(val id: String, val label: String, val short: String, val emoji: String)

val USAGE_CATEGORIES: List<UsageCategory> = listOf(
    UsageCategory("study",   "Study",        "Study",   "\uD83D\uDCDA"),
    UsageCategory("coding",  "Coding",       "Coding",  "\uD83D\uDCBB"),
    UsageCategory("neutral", "Neutral",      "Neutral", "\uD83D\uDE10"),
    UsageCategory("fun",     "Fun & Games",  "Fun",     "\uD83C\uDFAE"),
)

fun usageCategory(id: String): UsageCategory = USAGE_CATEGORIES.firstOrNull { it.id == id } ?: USAGE_CATEGORIES[2]

object AppCategorizer {
    /** Starting point for the apps Krishna named. Anything here can still be changed by hand. */
    private val DEFAULTS: Map<String, String> = mapOf(
        // study
        "xyz.penpencil.physicswala" to "study",
        "com.whatsapp" to "study",
        "com.whatsapp.w4b" to "study",
        "com.google.android.youtube" to "study",
        // coding
        "com.anthropic.claude" to "coding",
        "com.termux" to "coding",
        "ru.zdevs.zarchiver" to "coding",
        "com.haibison.apksigner" to "coding",
        // neutral: apps built by the user
        "com.krishna.questblock" to "neutral",
        "neth.iecal.questphone" to "neutral",
        // fun & games
        "com.radioly.pocketfm" to "fun",
        "org.telegram.messenger" to "fun",
        "org.telegram.messenger.web" to "fun",
        "com.instagram.android" to "fun",
        "com.dts.freefireth" to "fun",
        "com.dts.freefiremax" to "fun",
    )

    /** Category with no manual choice: the list above, then Android's own app category, else Neutral. */
    fun autoCategory(pm: PackageManager, pkg: String): String {
        DEFAULTS[pkg]?.let { return it }
        val cat = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return when (cat) {
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_AUDIO -> "fun"
            else -> "neutral"
        }
    }

    fun categoryOf(prefs: Prefs, pm: PackageManager, pkg: String): String =
        prefs.categoryOverride(pkg) ?: autoCategory(pm, pkg)
}
