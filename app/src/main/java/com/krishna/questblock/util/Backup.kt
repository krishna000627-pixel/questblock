package com.krishna.questblock.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import com.krishna.questblock.data.AppDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup 2.0: one zip with the database, the settings (fun limit, categories, shields, theme...) and the Quest board / usage stats.
 * Old backups (a bare SQLite file) can still be restored; they just carry no settings.
 */
object Backup {
    private const val DB_NAME = "questblock.db"
    private const val PREFS_NAME = "questblock"
    private const val ENTRY_DB = "questblock.db"
    private const val ENTRY_PREFS = "prefs.txt"
    private const val AUTO_PREFIX = "QuestBlock_auto_"
    private const val KEEP_AUTO = 7
    private const val ENTRY_EXTRA = "prefs_"                       // prefs_<file>.txt, one per extra settings file below
    private const val SAFETY_PREFIX = "QuestBlock_before_restore_"
    private const val KEEP_SAFETY = 3
    // other on-phone data besides the main settings: Quest board scrolls and the usage stats
    private val EXTRA_PREFS = listOf("qb_ideas", "qb_shorts", "qb_screen", "qb_music")

    private val MAGIC = byteArrayOf('Q'.code.toByte(), 'B'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())

    // AES-256-GCM: the tag makes any edit to the file fail on restore. The key is built into the app (no password to remember).
    private fun key(): javax.crypto.spec.SecretKeySpec {
        val h = java.security.MessageDigest.getInstance("SHA-256")
            .digest("questblock-backup-key-v1|com.krishna.questblock".toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(h, "AES")
    }
    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return MAGIC + iv + c.doFinal(plain)
    }
    private fun isSealed(b: ByteArray) = b.size > 32 && b[0] == MAGIC[0] && b[1] == MAGIC[1] && b[2] == MAGIC[2] && b[3] == MAGIC[3]
    private fun decrypt(b: ByteArray): ByteArray {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, b.copyOfRange(4, 16)))
        return c.doFinal(b, 16, b.size - 16)
    }

    private fun zipBytes(ctx: Context, db: AppDatabase): ByteArray {
        // flush the write-ahead log so the file is complete
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry(ENTRY_DB))
            ctx.getDatabasePath(DB_NAME).inputStream().use { it.copyTo(z) }
            z.closeEntry()
            z.putNextEntry(ZipEntry(ENTRY_PREFS))
            z.write(prefsToText(ctx, PREFS_NAME).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            EXTRA_PREFS.forEach { name ->
                z.putNextEntry(ZipEntry("$ENTRY_EXTRA$name.txt"))
                z.write(prefsToText(ctx, name).toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** One line per setting: type|key|value (strings are Base64 so newlines and tabs are safe). */
    private fun prefsToText(ctx: Context, name: String): String {
        val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for ((k, v) in sp.all) {
            when (v) {
                is Boolean -> sb.append("b|$k|$v\n")
                is Int -> sb.append("i|$k|$v\n")
                is Long -> sb.append("l|$k|$v\n")
                is Float -> sb.append("f|$k|$v\n")
                is String -> sb.append("s|$k|").append(Base64.encodeToString(v.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).append('\n')
            }
        }
        return sb.toString()
    }

    private fun restorePrefs(ctx: Context, name: String, text: String) {
        val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        ed.clear()
        text.lineSequence().forEach { line ->
            val p = line.split('|', limit = 3)
            if (p.size == 3) runCatching {
                when (p[0]) {
                    "b" -> ed.putBoolean(p[1], p[2].toBoolean())
                    "i" -> ed.putInt(p[1], p[2].toInt())
                    "l" -> ed.putLong(p[1], p[2].toLong())
                    "f" -> ed.putFloat(p[1], p[2].toFloat())
                    "s" -> ed.putString(p[1], String(Base64.decode(p[2], Base64.NO_WRAP), Charsets.UTF_8))
                }
            }
        }
        ed.commit()
    }

    fun export(ctx: Context, db: AppDatabase, out: Uri): Boolean = runCatching {
        val bytes = encrypt(zipBytes(ctx, db))
        ctx.contentResolver.openOutputStream(out)!!.use { it.write(bytes) }
        true
    }.getOrDefault(false)

    fun import(ctx: Context, db: AppDatabase, src: Uri): Boolean = runCatching {
        val raw = ctx.contentResolver.openInputStream(src)!!.use { it.readBytes() }
        require(isSealed(raw))                                  // old plain .zip / .db files can be edited, so they are refused
        val bytes = decrypt(raw)                                // an edited or damaged encrypted file throws here and is refused
        var dbBytes: ByteArray? = null
        var prefsText: String? = null
        val extras = HashMap<String, String>()
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    when (e.name) {
                        ENTRY_DB -> dbBytes = z.readBytes()
                        ENTRY_PREFS -> prefsText = String(z.readBytes(), Charsets.UTF_8)
                        else -> if (e.name.startsWith(ENTRY_EXTRA) && e.name.endsWith(".txt")) {
                            val n = e.name.removePrefix(ENTRY_EXTRA).removeSuffix(".txt")
                            if (n in EXTRA_PREFS) extras[n] = String(z.readBytes(), Charsets.UTF_8)
                        }
                    }
                    e = z.nextEntry
                }
            }
        } else {
            dbBytes = bytes
        }
        val d = dbBytes
        require(d != null && d.size > 100 && String(d, 0, 15, Charsets.US_ASCII) == "SQLite format 3")
        require(dbIsHealthy(ctx, d))
        // coins on this phone right now. A restore keeps them (no doubling, no lowering) unless this phone has no coin history yet
        var keepCoins: Int? = null
        var keepLoginDay: Long = 0L
        runCatching {
            val cur = db.openHelper.readableDatabase
            val n = cur.query("SELECT COUNT(*) FROM coin_log").use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (n >= 3) cur.query("SELECT coins, lastLoginDay FROM player LIMIT 1").use {
                if (it.moveToFirst()) { keepCoins = it.getInt(0); keepLoginDay = it.getLong(1) }
            }
        }
        val claimedNow = ArrayList<String>()
        if (keepCoins != null) ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k.startsWith("claimed_") && v == true) claimedNow += k
        }
        // safety copy of what is on the phone now, so a wrong restore can be undone
        runCatching { safetyCopy(ctx, db) }
        val f = ctx.getDatabasePath(DB_NAME)
        db.close()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        f.writeBytes(d)
        keepCoins?.let { c ->
            runCatching {
                android.database.sqlite.SQLiteDatabase.openDatabase(f.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { x ->
                    x.execSQL("UPDATE player SET coins = ?, lastLoginDay = MAX(lastLoginDay, ?)", arrayOf<Any>(c, keepLoginDay))
                    x.execSQL("INSERT INTO coin_log (timestamp, amount, source, detail) VALUES (?, 0, 'Restore', ?)",
                        arrayOf<Any>(System.currentTimeMillis(), "Coins kept at $c"))
                }
            }
        }
        prefsText?.let { restorePrefs(ctx, PREFS_NAME, it) }
        if (claimedNow.isNotEmpty()) {
            val ed = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            claimedNow.forEach { ed.putBoolean(it, true) }
            ed.commit()
        }
        extras.forEach { (n, t) -> restorePrefs(ctx, n, t) }
        true
    }.getOrDefault(false)

    /** The database inside a backup must open and pass SQLite's own quick check before it replaces the live one. */
    private fun dbIsHealthy(ctx: Context, d: ByteArray): Boolean {
        val tmp = File(ctx.cacheDir, "restore_check.db")
        return try {
            tmp.writeBytes(d)
            android.database.sqlite.SQLiteDatabase.openDatabase(tmp.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { x ->
                x.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            }
        } catch (e: Exception) { false } finally { tmp.delete() }
    }

    /** Copy of the current data into Downloads/QuestBlock right before a restore. Keeps the newest 3. */
    private fun safetyCopy(ctx: Context, db: AppDatabase) {
        if (Build.VERSION.SDK_INT < 29) return
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = SAFETY_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".qbak"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/QuestBlock")
        }
        val uri = resolver.insert(collection, values) ?: return
        (resolver.openOutputStream(uri) ?: return).use { it.write(encrypt(zipBytes(ctx, db))) }
        val old = ArrayList<Long>()
        resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$SAFETY_PREFIX%"),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) { if (i >= KEEP_SAFETY) old += c.getLong(0); i++ }
        }
        old.forEach { id -> runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) } }
    }

    /** Daily copy into Downloads/QuestBlock (survives "clear data"). Keeps the newest 7. Returns true if a file was written. */
    fun autoBackup(ctx: Context, db: AppDatabase): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 29) return@runCatching false
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = AUTO_PREFIX + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".qbak"

        // today's copy already there?
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)?.use {
            if (it.moveToFirst()) return@runCatching false
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/QuestBlock")
        }
        val uri = resolver.insert(collection, values) ?: return@runCatching false
        val out: OutputStream = resolver.openOutputStream(uri) ?: return@runCatching false
        out.use { it.write(encrypt(zipBytes(ctx, db))) }

        // keep only the newest KEEP_AUTO
        val old = ArrayList<Long>()
        resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$AUTO_PREFIX%"),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) { if (i >= KEEP_AUTO) old += c.getLong(0); i++ }
        }
        old.forEach { id -> runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) } }
        true
    }.getOrDefault(false)

    /** Relaunch the app in a fresh process (used after restore / reset). */
    fun restart(ctx: Context) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) ctx.startActivity(launch)
        Handler(Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 400L)
    }
}
