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
 * Backup 2.0: one zip with the database AND the settings (fun limit, categories, shields, theme...).
 * Old backups (a bare SQLite file) can still be restored; they just carry no settings.
 */
object Backup {
    private const val DB_NAME = "questblock.db"
    private const val PREFS_NAME = "questblock"
    private const val ENTRY_DB = "questblock.db"
    private const val ENTRY_PREFS = "prefs.txt"
    private const val AUTO_PREFIX = "QuestBlock_auto_"
    private const val KEEP_AUTO = 3

    private fun zipBytes(ctx: Context, db: AppDatabase): ByteArray {
        // flush the write-ahead log so the file is complete
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry(ENTRY_DB))
            ctx.getDatabasePath(DB_NAME).inputStream().use { it.copyTo(z) }
            z.closeEntry()
            z.putNextEntry(ZipEntry(ENTRY_PREFS))
            z.write(prefsToText(ctx).toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    /** One line per setting: type|key|value (strings are Base64 so newlines and tabs are safe). */
    private fun prefsToText(ctx: Context): String {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    private fun restorePrefs(ctx: Context, text: String) {
        val ed = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
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
        val bytes = zipBytes(ctx, db)
        ctx.contentResolver.openOutputStream(out)!!.use { it.write(bytes) }
        true
    }.getOrDefault(false)

    fun import(ctx: Context, db: AppDatabase, src: Uri): Boolean = runCatching {
        val bytes = ctx.contentResolver.openInputStream(src)!!.use { it.readBytes() }
        var dbBytes: ByteArray? = null
        var prefsText: String? = null
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    when (e.name) {
                        ENTRY_DB -> dbBytes = z.readBytes()
                        ENTRY_PREFS -> prefsText = String(z.readBytes(), Charsets.UTF_8)
                    }
                    e = z.nextEntry
                }
            }
        } else {
            dbBytes = bytes
        }
        val d = dbBytes
        require(d != null && d.size > 100 && String(d, 0, 15, Charsets.US_ASCII) == "SQLite format 3")
        val f = ctx.getDatabasePath(DB_NAME)
        db.close()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        f.writeBytes(d)
        prefsText?.let { restorePrefs(ctx, it) }
        true
    }.getOrDefault(false)

    /** Daily copy into Downloads/QuestBlock (survives "clear data"). Keeps the newest 3. Returns true if a file was written. */
    fun autoBackup(ctx: Context, db: AppDatabase): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 29) return@runCatching false
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = AUTO_PREFIX + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".zip"

        // today's copy already there?
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)?.use {
            if (it.moveToFirst()) return@runCatching false
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/QuestBlock")
        }
        val uri = resolver.insert(collection, values) ?: return@runCatching false
        val out: OutputStream = resolver.openOutputStream(uri) ?: return@runCatching false
        out.use { it.write(zipBytes(ctx, db)) }

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
