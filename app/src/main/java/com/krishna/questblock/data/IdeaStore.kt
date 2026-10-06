package com.krishna.questblock.data

import android.content.Context
import org.json.JSONArray

/** Notes for update prompts and ideas. Newest first. Stored on the phone only. */
object IdeaStore {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_ideas", Context.MODE_PRIVATE)

    fun all(c: Context): List<String> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun save(c: Context, l: List<String>) {
        sp(c).edit().putString("list", JSONArray(l).toString()).apply()
    }

    fun add(c: Context, text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) save(c, listOf(t) + all(c))
    }

    fun update(c: Context, index: Int, text: String) {
        val l = all(c).toMutableList()
        if (index in l.indices && text.isNotBlank()) { l[index] = text.trim(); save(c, l) }
    }

    fun delete(c: Context, index: Int) {
        val l = all(c).toMutableList()
        if (index in l.indices) { l.removeAt(index); save(c, l) }
    }
}
