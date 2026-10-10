package com.krishna.questblock.data

import android.content.Context

/**
 * v173: YouTube time split in two. Videos the YouTube study gate judged FUN (and Shorts) count as "YouTube (fun)",
 * videos it judged FREE (a chapter) count as "YouTube (study)". Used by the fun limit and the App usage page.
 * The gate counts its own playing time per day in ShortsStats (kinds [KIND_FUN] and [KIND_STUDY]); the part is taken out of YouTube's screen time.
 */
object YtSplit {
    const val FUN_KEY = "yt:fun"
    const val STUDY_KEY = "yt:study"
    const val KIND_FUN = "ytfun"
    const val KIND_STUDY = "ytstudy"
    val PKGS = listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube")

    fun isPseudo(key: String) = key == FUN_KEY || key == STUDY_KEY

    /** [per] is the screen time per app for [from]..[to]. Returns it with YouTube's time moved into the two parts. */
    fun apply(ctx: Context, prefs: Prefs, per: Map<String, Long>, from: Long, to: Long): Map<String, Long> {
        val pkg = PKGS.firstOrNull { (per[it] ?: 0L) > 0L } ?: return per
        val total = per[pkg] ?: 0L
        val d0 = dayNumber(from)
        val d1 = dayNumber((to - 1L).coerceAtLeast(from))
        var funMs = 0L
        var studyMs = 0L
        for (d in d0..d1) {
            funMs += ShortsStats.forDay(ctx, d, KIND_FUN).ms + ShortsStats.forDay(ctx, d, ShortsStats.YT).ms    // Shorts are fun too
            studyMs += ShortsStats.forDay(ctx, d, KIND_STUDY).ms
        }
        val out = LinkedHashMap(per)
        if (AppCategorizer.categoryOf(prefs, ctx.packageManager, pkg) == "fun") {
            // YouTube is a Fun app: its whole time is fun except the study videos
            val s = minOf(studyMs, total)
            if (s > 0L) { out[pkg] = total - s; out[STUDY_KEY] = s }
        } else {
            val f = minOf(funMs, total)
            if (f > 0L) { out[pkg] = total - f; out[FUN_KEY] = f }
        }
        return out
    }
}
