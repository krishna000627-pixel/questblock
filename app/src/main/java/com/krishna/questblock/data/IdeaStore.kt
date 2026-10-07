package com.krishna.questblock.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Idea(val text: String, val done: Boolean = false)

/** Notes for update prompts and ideas. Newest first. Stored on the phone only. */
object IdeaStore {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_ideas", Context.MODE_PRIVATE)

    fun all(c: Context): List<Idea> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        List(a.length()) {
            // older versions stored plain strings
            val o = a.opt(it)
            if (o is JSONObject) Idea(o.optString("t"), o.optBoolean("d")) else Idea(a.getString(it))
        }
    }.getOrDefault(emptyList())

    private fun save(c: Context, l: List<Idea>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.text).put("d", it.done)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    fun add(c: Context, text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) save(c, listOf(Idea(t)) + all(c))
    }

    fun update(c: Context, index: Int, text: String) {
        val l = all(c).toMutableList()
        if (index in l.indices && text.isNotBlank()) { l[index] = Idea(text.trim(), l[index].done); save(c, l) }
    }

    fun toggle(c: Context, index: Int) {
        val l = all(c).toMutableList()
        if (index in l.indices) { l[index] = Idea(l[index].text, !l[index].done); save(c, l) }
    }

    fun delete(c: Context, index: Int) {
        val l = all(c).toMutableList()
        if (index in l.indices) { l.removeAt(index); save(c, l) }
    }

    fun clearDone(c: Context) = save(c, all(c).filter { !it.done })
}
