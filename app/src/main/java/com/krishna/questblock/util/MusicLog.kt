package com.krishna.questblock.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.krishna.questblock.data.AppCategorizer
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
 * Records when music/media is playing (any app) and which app it most likely came from.
 * Android's audio callback fires only when playback changes, so it costs no battery.
 * The source is the app you were using (or used last) when the music started; Android does not name the player.
 * Stored as "time:1:package" (started) / "time:0:" (stopped), newest last.
 */
object MusicLog {
    private const val KEY = "events"
    private const val MAX = 600
    private var sp: SharedPreferences? = null
    private var started = false
    private var ctx: Context? = null
    private var prefs: Prefs? = null
    private var lastOn = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watch: Job? = null

    class Entry(val t: Long, val on: Boolean, val pkg: String)
    class Period(val pkg: String, val start: Long, val end: Long)

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
                change(configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA })
            }
        }, Handler(Looper.getMainLooper()))
        change(am.isMusicActive)
    }

    @Synchronized
    private fun change(on: Boolean) {
        if (on == lastOn) return
        lastOn = on
        val t = System.currentTimeMillis()
        set(t, on, "")
        if (on) {
            startWatch()
            // find the source in the background, then tag the entry
            scope.launch { runCatching { findSource()?.let { tag(t, it) } } }
        } else {
            watch?.cancel(); watch = null
        }
    }

    private fun findSource(): String? {
        val c = ctx ?: return null
        runCatching {
            val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val cn = android.content.ComponentName(c, com.krishna.questblock.service.MediaListenerService::class.java)
            msm.getActiveSessions(cn)
                .firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
                ?.packageName?.let { if (it != c.packageName) return it }
        }
        val now = System.currentTimeMillis()
        val hidden = UsageTracker.hiddenPackages(c)
        val open = UsageTracker.foreground(c, now - 30 * 60_000L, now).openPkg
        if (open != null && open !in hidden && open != c.packageName) return open
        return UsageTracker.recentApp(c, 30 * 60_000L)
    }

    @Synchronized
    private fun set(t: Long, on: Boolean, pkg: String) {
        val p = sp ?: return
        val list = parse(p.getString(KEY, "").orEmpty())
        list.add(Entry(t, on, pkg))
        save(p, list)
    }

    @Synchronized
    private fun tag(t: Long, pkg: String) {
        val p = sp ?: return
        val list = parse(p.getString(KEY, "").orEmpty())
        val i = list.indexOfLast { it.t == t && it.on }
        if (i >= 0) { list[i] = Entry(t, true, pkg); save(p, list) }
    }

    private fun save(p: SharedPreferences, list: MutableList<Entry>) {
        val kept = if (list.size > MAX) list.subList(list.size - MAX, list.size) else list
        p.edit().putString(KEY, kept.joinToString(";") { "${it.t}:${if (it.on) 1 else 0}:${it.pkg}" }).apply()
    }

    private fun parse(s: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        if (s.isEmpty()) return out
        for (part in s.split(';')) {
            val a = part.split(':')
            val t = a.getOrNull(0)?.toLongOrNull() ?: continue
            out.add(Entry(t, a.getOrNull(1) == "1", a.getOrNull(2).orEmpty()))
        }
        return out
    }

    /** Playing periods inside [from, to]. A period is capped at 6 h in case a stop was never seen. */
    @Synchronized
    fun periods(c: Context, from: Long, to: Long): List<Period> {
        val p = sp ?: c.applicationContext.getSharedPreferences("qb_music", Context.MODE_PRIVATE)
        val all = parse(p.getString(KEY, "").orEmpty())
        val out = ArrayList<Period>()
        var start = -1L
        var pkg = ""
        fun add(s: Long, e: Long) {
            val a = maxOf(s, from)
            val b = minOf(minOf(e, s + 6 * 3_600_000L), to)
            if (b > a) out.add(Period(pkg, a, b))
        }
        for (e in all) {
            if (e.on) { if (start < 0) { start = e.t; pkg = e.pkg } else if (pkg.isEmpty()) pkg = e.pkg }
            else if (start >= 0) { add(start, e.t); start = -1L; pkg = "" }
        }
        if (start >= 0) add(start, to)
        return out
    }

    fun intervals(c: Context, from: Long, to: Long): List<LongArray> =
        periods(c, from, to).map { longArrayOf(it.start, it.end) }

    fun isPlaying(): Boolean {
        val c = ctx ?: return false
        return (c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true
    }

    // ── Pause music once the fun limit is reached (or fun apps are locked) ──
    private fun startWatch() {
        if (watch?.isActive == true) return
        watch = scope.launch {
            while (true) {
                runCatching { evaluate() }
                delay(30_000L)
            }
        }
    }

    private suspend fun evaluate() {
        val c = ctx ?: return
        val p = prefs ?: return
        if (!p.pauseMusicAtLimit || !isPlaying()) return
        val st = FunLimit.state(c, p, force = true)
        if (!(st.reached || p.funBlockedNow())) return
        // Study and coding apps may play their own audio, so leave those alone.
        val now = System.currentTimeMillis()
        val fg = UsageTracker.foreground(c, now - 3_600_000L, now).openPkg
        val cat = fg?.let { AppCategorizer.categoryOf(p, c.packageManager, it) }
        if (cat == "study" || cat == "coding") {
            DebugLog.d("music: playing but $fg ($cat) is on screen, not pausing")
            return
        }
        DebugLog.d("music: pausing (limit reached=${st.reached}, locked=${p.funBlockedNow()}, on screen=$fg)")
        pauseNow(c)
    }

    private suspend fun pauseNow(c: Context) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        delay(1_500L)
        if (!am.isMusicActive) return
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener { }
                .build()
            am.requestAudioFocus(req)
            delay(2_000L)
            am.abandonAudioFocusRequest(req)
        }
        DebugLog.d("music: pause tried, still playing=${am.isMusicActive}")
    }
}
