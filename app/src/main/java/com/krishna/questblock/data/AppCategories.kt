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
        "com.whatsapp" to "neutral",   // v288: was study
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
        // v102: from the phone's app list
        "notion.id" to "study",
        "org.readera" to "study",
        "pdf.pdfreader.pdfviewer.pdfeditor" to "study",
        "com.google.android.keep" to "study",
        "com.dudoo.app" to "study",
        "com.openai.chatgpt" to "coding",
        "com.github.android" to "coding",
        "ru.iiec.pydroid3" to "coding",
        "com.tomminosoftware.sqliteeditor" to "coding",
        "com.termux.api" to "coding",
        "com.JindoBlu.TwoPlayerGamesChallenge" to "fun",
        "com.supercell.clashofclans" to "fun",
        "com.ohbibi.fps" to "fun",
        "com.freefire.biocodes" to "fun",
        "echo.music.iad1tya" to "fun",
        "com.dywx.larkplayer" to "fun",
        "com.heytap.music" to "fun",
        "com.radio.pocketfm" to "fun",
        "in.startv.hotstar" to "fun",
        "com.pinterest" to "fun",
        "com.snaptube.premium" to "fun",
        "com.instantbits.cast.webvideo" to "fun",
        "org.torproject.torbrowser" to "fun",
        "com.google.android.contacts" to "neutral",
        "com.google.android.gm" to "neutral",
        "com.google.android.apps.messaging" to "neutral",
        "ch.pboos.android.SleepTimer" to "neutral",
        "com.truecaller" to "neutral",
        "com.krishna.snaptalk" to "neutral",
        "com.habitrpg.android.habitica" to "neutral",
        // v288: from the phone's app list (Krishna's own words)
        "arivihan.technologies.doubtbuzzter2" to "study",
        "com.scholar.krishna" to "study",              // GScholar
        "syco.panth.krishna" to "study",               // Notally
        "com.headway.books" to "study",
        "com.google.android.apps.labs.language.tailwind" to "study",   // Notebook
        "com.starry.myne" to "fun",                    // Myne, novel reading
        "hesoft.T2S" to "fun",                         // text to speech for listening to novels
        "com.bgbrowser" to "fun",                      // the browser for T2S
        "com.chess" to "fun",
        "com.vectorunit.purple.googleplay" to "fun",   // BB Racing
        "com.discord" to "fun",
        "com.garena.game.ffi" to "fun",                // Free Fire India
        "io.voodoo.paper2" to "fun",                   // Paper.io 2
        "com.nearme.gamecenter" to "fun",
        "com.oplus.games" to "fun",
        "com.coloros.video" to "fun",
        "com.android.fmradio" to "fun",
        "com.aria.reply" to "neutral",                 // AI WhatsApp responder
        "dev.km.android.chargemeter" to "neutral",     // Charge Meter
        "com.krishna.apphub" to "neutral",
        "com.krishna.videowall" to "neutral",
        "syco.panth.krishna.wanotifier" to "neutral",
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

    fun categoryOf(prefs: Prefs, pm: PackageManager, pkg: String): String = when (pkg) {
        YtSplit.FUN_KEY -> "fun"          // v173: the two parts of YouTube
        YtSplit.STUDY_KEY -> "study"
        else -> prefs.categoryOverride(pkg) ?: autoCategory(pm, pkg)
    }
}
