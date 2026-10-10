package com.krishna.questblock.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** One-time 2 hour test window: unlimited coins and no fun limit. Runs on the phone's own monotonic clock. */
object TestMode {
    const val DURATION_MS = 2 * 60 * 60 * 1000L
    const val COIN_BONUS = 9_999_999
    private const val FILE = "qb_cache_1.dat"
    private const val SP = "questblock"

    @Volatile private var loaded = false
    @Volatile private var used = false
    @Volatile private var startAt = 0L
    @Volatile private var startBoot = -1
    @Volatile private var curBoot = -1

    fun isLoaded(): Boolean = loaded
    fun used(): Boolean = used

    fun active(): Boolean {
        if (!loaded || !used || startAt <= 0L || startBoot == -2 || startBoot != curBoot) return false
        val d = SystemClock.elapsedRealtime() - startAt
        return d in 0L until DURATION_MS
    }

    fun remainingMs(): Long = if (active()) DURATION_MS - (SystemClock.elapsedRealtime() - startAt) else 0L

    private fun bootCount(ctx: Context): Int =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun sig(body: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec("questblock-tm-v1|com.krishna.questblock".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return m.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun findUri(ctx: Context): Uri? {
        val col = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        ctx.contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(FILE), null)?.use {
            if (it.moveToFirst()) return ContentUris.withAppendedId(col, it.getLong(0))
        }
        return null
    }

    private fun writeMarker(ctx: Context, s: Long, b: Int): Boolean {
        val body = "v1|$s|$b"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/QuestBlock")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        (ctx.contentResolver.openOutputStream(uri) ?: return false).use { it.write("$body|${sig(body)}".toByteArray(Charsets.UTF_8)) }
        return true
    }

    /** Reads the saved state. Call off the main thread. */
    @Synchronized fun load(ctx: Context) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
        var u = false; var s = 0L; var b = -1
        if (sp.getBoolean("tm_used", false)) { u = true; s = sp.getLong("tm_start", 0L); b = sp.getInt("tm_boot", -1) }
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            val uri = findUri(app)
            if (uri != null) {
                u = true
                val t = app.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
                val p = t.split('|')
                val ok = p.size == 4 && p[0] == "v1" && sig("${p[0]}|${p[1]}|${p[2]}") == p[3]
                if (ok) { s = p[1].toLong(); b = p[2].toInt() } else { s = 0L; b = -2 }
            }
        }
        used = u; startAt = s; startBoot = b; curBoot = bootCount(app); loaded = true
    }

    /** Starts the window once. False when it was used before or could not be saved. Call off the main thread. */
    @Synchronized fun start(ctx: Context): Boolean {
        val app = ctx.applicationContext
        load(app)
        if (used) return false
        val s = SystemClock.elapsedRealtime()
        val b = bootCount(app)
        if (Build.VERSION.SDK_INT >= 29 && !runCatching { writeMarker(app, s, b) }.getOrDefault(false)) return false
        app.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putBoolean("tm_used", true).putLong("tm_start", s).putInt("tm_boot", b).commit()
        used = true; startAt = s; startBoot = b; curBoot = b
        return true
    }
}
