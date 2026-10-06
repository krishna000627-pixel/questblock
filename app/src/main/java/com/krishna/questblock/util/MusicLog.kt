package com.krishna.questblock.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.krishna.questblock.data.FunLimit
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Records when music/media is playing (any app), from Android's audio callback. It fires only when playback changes,
 * so it costs no battery. Used for "ghost time" (fun apps playing in the background) and to pause music at the limit.
 * Stored as "time:1" (playing) / "time:0" (stopped), newest last.
 */
object MusicLog {
    private const val KEY = "events"
    private const val MAX = 600
    private var sp: SharedPreferences? = null
    private var started = false
    private var ctx: Context? = null
    private var prefs: Prefs? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watch: Job? = null

    class Entry(val t: Long, val on: Boolean)

    @Synchronized
    fun start(app: Context, p: Prefs) {
        val c = app.applicationContext
        ctx = c; prefs = p
        sp = c.getSharedPreferences("qb_music", Context.MODE_PRIVATE)
        if (started || Build.VERSION.SDK_INT < 26) return
        started = true
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                val on = configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
                set(System.currentTimeMillis(), on)
            }
        }, Handler(Looper.getMainLooper()))
        set(System.currentTimeMillis(), am.isMusicActive)
    }

    @Synchronized
    private fun set(t: Long, on: Boolean) {
        val p = sp ?: return
        val list = parse(p.getString(KEY, "").orEmpty())
        if (list.isEmpty() || list.last().on != on) {
            list.add(Entry(t, on))
            val kept = if (list.size > MAX) list.subList(list.size - MAX, list.size) else list
            p.edit().putString(KEY, kept.joinToString(";") { "${it.t}:${if (it.on) 1 else 0}" }).apply()
        }
        if (on) startWatch() else { watch?.cancel(); watch = null }
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

    /** Playing periods inside [from, to] as [start, end] pairs. A period is capped at 6 h in case a stop was never seen. */
    @Synchronized
    fun intervals(c: Context, from: Long, to: Long): List<LongArray> {
        val p = sp ?: c.applicationContext.getSharedPreferences("qb_music", Context.MODE_PRIVATE)
        val all = parse(p.getString(KEY, "").orEmpty())
        val out = ArrayList<LongArray>()
        var start = -1L
        for (e in all) {
            if (e.on) { if (start < 0) start = e.t }
            else if (start >= 0) { add(out, start, e.t, from, to); start = -1L }
        }
        if (start >= 0) add(out, start, to, from, to)
        return out
    }

    private fun add(out: MutableList<LongArray>, s: Long, e: Long, from: Long, to: Long) {
        val a = maxOf(s, from)
        val b = minOf(minOf(e, s + 6 * 3_600_000L), to)
        if (b > a) out.add(longArrayOf(a, b))
    }

    fun isPlaying(): Boolean {
        val c = ctx ?: return false
        return (c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true
    }

    // ── Pause music from fun apps once the fun limit is reached (or fun apps are locked) ──
    private fun startWatch() {
        if (watch?.isActive == true) return
        watch = scope.launch {
            while (true) {
                runCatching { evaluate() }
                delay(60_000L)
            }
        }
    }

    private suspend fun evaluate() {
        val c = ctx ?: return
        val p = prefs ?: return
        if (!p.pauseMusicAtLimit || !isPlaying()) return
        val st = FunLimit.state(c, p, force = true)
        if (!(st.reached || p.funBlockedNow())) return
        if (UsageTracker.openFunServices(c, p).isEmpty()) return
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        DebugLog.d("music: paused (fun limit reached or fun apps locked)")
    }
}
