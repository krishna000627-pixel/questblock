package com.krishna.questblock.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** v214: tag (ui, launcher, bug, theme, other or empty), status (idea, next, doing, done), pin and priority (0 to 3). */
class Idea(
    val text: String, val done: Boolean = false,
    val tag: String = "", val status: String = if (done) "done" else "idea", val pin: Boolean = false, val prio: Int = 0,
)

/** Notes for update prompts and ideas. Newest first. Stored on the phone only. */
object IdeaStore {
    val TAGS = listOf("ui", "launcher", "bug", "theme", "other")
    val STATUSES = listOf("idea", "next", "doing", "done")

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_ideas", Context.MODE_PRIVATE)

    fun all(c: Context): List<Idea> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        List(a.length()) {
            // older versions stored plain strings
            val o = a.opt(it)
            if (o is JSONObject) {
                val d = o.optBoolean("d")
                val st = o.optString("s", if (d) "done" else "idea").let { s -> if (s in STATUSES) s else "idea" }
                Idea(o.optString("t"), st == "done", o.optString("g"), st, o.optBoolean("p"), o.optInt("r").coerceIn(0, 3))
            } else Idea(a.getString(it))
        }
    }.getOrDefault(emptyList())

    private fun save(c: Context, l: List<Idea>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.text).put("d", it.status == "done").put("g", it.tag).put("s", it.status).put("p", it.pin).put("r", it.prio)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    private fun copyOf(i: Idea, text: String = i.text, tag: String = i.tag, status: String = i.status, pin: Boolean = i.pin, prio: Int = i.prio) =
        Idea(text, status == "done", tag, status, pin, prio)

    fun add(c: Context, text: String, tag: String = "") {
        val t = text.trim()
        if (t.isNotEmpty()) save(c, listOf(Idea(t, tag = tag)) + all(c))
    }

    fun update(c: Context, index: Int, text: String) {
        val l = all(c).toMutableList()
        if (index in l.indices && text.isNotBlank()) { l[index] = copyOf(l[index], text = text.trim()); save(c, l) }
    }

    fun toggle(c: Context, index: Int) {
        val l = all(c).toMutableList()
        if (index in l.indices) { l[index] = copyOf(l[index], status = if (l[index].status == "done") "idea" else "done"); save(c, l) }
    }

    /** Changes only what is given; null leaves it as it is. */
    fun setMeta(c: Context, index: Int, tag: String? = null, status: String? = null, pin: Boolean? = null, prio: Int? = null) {
        val l = all(c).toMutableList()
        if (index !in l.indices) return
        val i = l[index]
        l[index] = copyOf(i, tag = tag ?: i.tag, status = status ?: i.status, pin = pin ?: i.pin, prio = prio ?: i.prio)
        save(c, l)
    }

    fun delete(c: Context, index: Int) {
        val l = all(c).toMutableList()
        if (index in l.indices) { l.removeAt(index); save(c, l) }
    }

    fun clearDone(c: Context) = save(c, all(c).filter { it.status != "done" })
}
