package com.krishna.questblock.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny persistent trace (survives the lock screen closing). Viewable in Settings > Debug log. */
object DebugLog {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) { sp = ctx.applicationContext.getSharedPreferences("qb_debug", Context.MODE_PRIVATE) }

    @Synchronized
    fun d(msg: String) {
        val p = sp ?: return
        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val next = ((p.getString("log", "") ?: "") + "$t $msg\n").takeLast(9000)
        p.edit().putString("log", next).apply()
    }

    fun read(): String = sp?.getString("log", "").orEmpty()
    fun clear() { sp?.edit()?.remove("log")?.apply() }
}
