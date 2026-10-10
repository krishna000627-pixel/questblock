package com.krishna.questblock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * v177: what YouTube itself says about a video (category, tags, description), looked up in the background the way video downloaders do it:
 * YouTube's own web API (the one its website uses), no account and no key of ours. The app only knows the title and channel, so the video is
 * found by a search for that title and accepted only when a result has the same title. Any failure (no signal, YouTube changed something,
 * no matching result) returns null and the title-only verdict stays as it was.
 */
object YtInfo {
    class Info(val videoId: String, val category: String, val keywords: List<String>, val description: String, val author: String)

    private class Entry(val info: Info?, val at: Long)
    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 80
    }

    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Already looked up (success only), no network. */
    @Synchronized fun peek(title: String): Info? = cache[norm(title)]?.info

    /** Lookup with a cache: a success is kept, a failure is not tried again for 60 s. Runs on the IO thread. */
    suspend fun lookup(title: String, channel: String): Info? {
        val k = norm(title)
        if (k.isEmpty()) return null
        synchronized(this) {
            val e = cache[k]
            if (e != null && (e.info != null || System.currentTimeMillis() - e.at < 60_000L)) return e.info
        }
        val t0 = System.currentTimeMillis()
        val info = withContext(Dispatchers.IO) { runCatching { fetch(title, channel) }.onFailure { DebugLog.d("yt: info lookup failed (${it.javaClass.simpleName})") }.getOrNull() }
        DebugLog.d("yt: info lookup ${if (info != null) "ok, category='${info.category}'" else "found nothing"} in ${System.currentTimeMillis() - t0} ms")
        synchronized(this) { cache[k] = Entry(info, System.currentTimeMillis()) }
        return info
    }

    private fun fetch(title: String, channel: String): Info? {
        val id = search(title, channel) ?: return null
        player(id)?.let { return it }
        return watchPage(id)
    }

    private fun client(): JSONObject = JSONObject().put("client",
        JSONObject().put("clientName", "WEB").put("clientVersion", "2.20250101.00.00").put("hl", "en").put("gl", "IN"))

    /** The id of the search result that has the same title. */
    private fun search(title: String, channel: String): String? {
        val body = JSONObject().put("context", client()).put("query", "$title $channel".trim())
        val res = post("https://www.youtube.com/youtubei/v1/search?key=$KEY&prettyPrint=false", body) ?: return null
        val found = ArrayList<JSONObject>()
        collect(JSONObject(res), found)
        val want = norm(title)
        for (v in found) {
            val id = v.optString("videoId")
            if (id.isEmpty()) continue
            val runs = v.optJSONObject("title")?.optJSONArray("runs") ?: continue
            val sb = StringBuilder()
            for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
            val got = norm(sb.toString())
            if (got == want || (want.length >= 15 && got.length >= 15 && (got.startsWith(want) || want.startsWith(got)))) return id
        }
        return null
    }

    private fun collect(o: Any?, out: MutableList<JSONObject>) {
        if (out.size >= 12) return
        when (o) {
            is JSONObject -> {
                o.optJSONObject("videoRenderer")?.let { out += it }
                val ks = o.keys()
                while (ks.hasNext()) collect(o.opt(ks.next()), out)
            }
            is JSONArray -> for (i in 0 until o.length()) collect(o.opt(i), out)
        }
    }

    /** YouTube's player data for the video: category from the microformat, tags and description from the video details. */
    private fun player(id: String): Info? {
        val body = JSONObject().put("context", client()).put("videoId", id).put("contentCheckOk", true).put("racyCheckOk", true)
        val res = post("https://www.youtube.com/youtubei/v1/player?key=$KEY&prettyPrint=false", body) ?: return null
        val j = JSONObject(res)
        val vd = j.optJSONObject("videoDetails") ?: return null
        val cat = j.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")?.optString("category").orEmpty()
        val kw = vd.optJSONArray("keywords")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return Info(id, cat, kw, vd.optString("shortDescription"), vd.optString("author"))
    }

    /** Fallback: the watch page itself carries the same data. */
    private fun watchPage(id: String): Info? {
        val html = get("https://www.youtube.com/watch?v=$id&hl=en") ?: return null
        val cat = Regex("\"category\":\"([^\"]{3,40})\"").find(html)?.groupValues?.get(1).orEmpty()
        if (cat.isEmpty()) return null
        val kw = Regex("\"keywords\":\\[(.*?)\\]").find(html)?.groupValues?.get(1)
            ?.let { runCatching { JSONArray("[$it]") }.getOrNull() }?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val desc = Regex("\"shortDescription\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)?.groupValues?.get(1)
            ?.let { runCatching { JSONObject("{\"s\":\"$it\"}").getString("s") }.getOrNull() }.orEmpty()
        return Info(id, cat, kw, desc, "")
    }

    private fun post(url: String, body: JSONObject): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 4_000; c.readTimeout = 5_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Origin", "https://www.youtube.com"); c.setRequestProperty("Accept-Language", "en")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return null
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4_000; c.readTimeout = 6_000
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept-Language", "en"); c.setRequestProperty("Cookie", "CONSENT=YES+1")
            if (c.responseCode != 200) return null
            return c.inputStream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                while (out.size() < 700_000) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                out.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }
}
