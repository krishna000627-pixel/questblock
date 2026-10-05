package com.krishna.questblock.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager

/**
 * QuestBlock's own record of the screen turning on/off (like Digital Wellbeing keeps).
 * The system broadcast only fires when the state changes, so it costs no battery.
 * Stored as "time:1" (on) / "time:0" (off), newest last, capped to the last ~10 days.
 */
object ScreenLog {
    private const val KEY = "events"
    private const val MAX = 800
    private var sp: SharedPreferences? = null
    private var registered = false

    class Entry(val t: Long, val on: Boolean)

    @Synchronized
    fun start(app: Context) {
        val ctx = app.applicationContext
        sp = ctx.getSharedPreferences("qb_screen", Context.MODE_PRIVATE)
        if (registered) return
        registered = true
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                when (i?.action) {
                    Intent.ACTION_SCREEN_OFF -> add(System.currentTimeMillis(), false)
                    Intent.ACTION_SCREEN_ON -> add(System.currentTimeMillis(), true)
                }
            }
        }
        ctx.registerReceiver(r, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        // Record the state we start in, in case the process was killed while the screen changed.
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null) add(System.currentTimeMillis(), pm.isInteractive)
    }

    @Synchronized
    private fun add(t: Long, on: Boolean) {
        val p = sp ?: return
        val list = parse(p.getString(KEY, "").orEmpty())
        // ignore a repeat of the same state (start-up check, duplicate broadcasts)
        if (list.isNotEmpty() && list.last().on == on) return
        list.add(Entry(t, on))
        val kept = if (list.size > MAX) list.subList(list.size - MAX, list.size) else list
        p.edit().putString(KEY, kept.joinToString(";") { "${it.t}:${if (it.on) 1 else 0}" }).apply()
    }

    private fun parse(s: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        if (s.isEmpty()) return out
        for (part in s.split(';')) {
            val a = part.split(':')
            val t = a.getOrNull(0)?.toLongOrNull() ?: continue
            out.add(Entry(t, a.getOrNull(1) == "1"))
        }
        return out
    }

    /** Entries between [from] and [to], oldest first. */
    @Synchronized
    fun between(ctx: Context, from: Long, to: Long): List<Entry> {
        val p = sp ?: ctx.applicationContext.getSharedPreferences("qb_screen", Context.MODE_PRIVATE)
        return parse(p.getString(KEY, "").orEmpty()).filter { it.t in from..to }
    }
}
