package com.krishna.questblock.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.krishna.questblock.data.AppDatabase
import java.io.File

/** Whole-database backup: copy the SQLite file out / in. Settings (prefs) are not part of it. */
object Backup {
    private const val DB_NAME = "questblock.db"

    fun export(ctx: Context, db: AppDatabase, out: Uri): Boolean = runCatching {
        // flush the write-ahead log so the file is complete
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ctx.contentResolver.openOutputStream(out)!!.use { o ->
            ctx.getDatabasePath(DB_NAME).inputStream().use { it.copyTo(o) }
        }
        true
    }.getOrDefault(false)

    fun import(ctx: Context, db: AppDatabase, src: Uri): Boolean = runCatching {
        val bytes = ctx.contentResolver.openInputStream(src)!!.use { it.readBytes() }
        require(bytes.size > 100 && String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3")
        val f = ctx.getDatabasePath(DB_NAME)
        db.close()
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        f.writeBytes(bytes)
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
