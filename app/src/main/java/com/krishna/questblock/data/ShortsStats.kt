package com.krishna.questblock.data

import android.content.Context

/** The two short-video screens we can count: YouTube Shorts ("yt") and Instagram Reels ("ig"). */
object ShortsStats {
    const val YT = "yt"
    const val IG = "ig"

    class Day(val day: Long, val ms: Long, val n: Int)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("qb_shorts", Context.MODE_PRIVATE)

    /** Add watch time and/or swiped videos to today's totals. Keeps the last 30 days. */
    @Synchronized
    fun add(ctx: Context, kind: String, ms: Long, n: Int) {
        val p = sp(ctx)
        val d = dayNumber(System.currentTimeMillis())
        val key = "d:$d:$kind"
        val cur = read(p.getString(key, null))
        val e = p.edit().putString(key, "${cur.first + ms},${cur.second + n}")
        // drop days older than 30
        p.all.keys.filter { it.startsWith("d:") }.forEach { k ->
            val dd = k.split(':').getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (dd < d - 30) e.remove(k)
        }
        e.apply()
    }

    private fun read(s: String?): Pair<Long, Int> {
        val a = s?.split(',') ?: return 0L to 0
        return (a.getOrNull(0)?.toLongOrNull() ?: 0L) to (a.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun forDay(ctx: Context, day: Long, kind: String): Day {
        val r = read(sp(ctx).getString("d:$day:$kind", null))
        return Day(day, r.first, r.second)
    }

    fun today(ctx: Context, kind: String): Day = forDay(ctx, dayNumber(System.currentTimeMillis()), kind)

    /** Newest first. */
    fun lastDays(ctx: Context, count: Int, kind: String): List<Day> {
        val t = dayNumber(System.currentTimeMillis())
        return (0 until count).map { forDay(ctx, t - it, kind) }
    }
}

/** Names of the Vault pseudo-apps (their lock key starts with "short:"). */
fun pseudoLabel(key: String): String? = when (key) {
    "short:yt" -> "YouTube Shorts"
    "short:ig" -> "Instagram Reels"
    else -> null
}

/** Key used for the unlock time of a "site:" or "short:" lock page target. */
fun lockKeyOf(target: String): String = if (target.startsWith("site:")) target.removePrefix("site:") else target


/** True when Instagram is installed (it has a launcher icon, so the manifest's launcher query makes it visible). */
fun isInstagramInstalled(ctx: android.content.Context): Boolean =
    try { ctx.packageManager.getPackageInfo("com.instagram.android", 0); true } catch (e: Exception) { false }

/** True when YouTube (or a YouTube mod) is installed and not disabled. */
fun isYouTubeInstalled(ctx: android.content.Context): Boolean =
    listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube").any {
        try { ctx.packageManager.getApplicationInfo(it, 0).enabled } catch (e: Exception) { false }
    }
