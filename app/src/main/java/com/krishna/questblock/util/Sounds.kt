package com.krishna.questblock.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.krishna.questblock.R

/** Sound effects (coin, level-up, chime, alarm) and the looping Focus background sound. Files are in res/raw. */
object Sounds {
    private var app: Context? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()
    private val loaded = HashSet<Int>()
    private var amb: MediaPlayer? = null
    private var ambKey = ""
    private val main = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopNow() }

    /** key -> label, for the pickers. "off" = no background sound. */
    val AMBIENTS = listOf("off" to "Off", "rain" to "\uD83C\uDF27 Rain", "brown" to "\uD83D\uDFE4 Brown", "pink" to "\uD83C\uDF38 Pink", "white" to "\u26AA White")

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Haptics.init(ctx)
        runCatching {
            val p = SoundPool.Builder().setMaxStreams(3)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded.add(id) } }
            intArrayOf(R.raw.sfx_coin, R.raw.sfx_levelup, R.raw.sfx_chime, R.raw.sfx_alarm).forEach { ids[it] = p.load(ctx.applicationContext, it, 1) }
            pool = p
        }
    }

    private fun sp() = app?.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    /** Sound effects on/off (default on). */
    var sfxOn: Boolean
        get() = sp()?.getBoolean("snd_sfx", true) ?: false
        set(v) { sp()?.edit()?.putBoolean("snd_sfx", v)?.apply() }

    /** The Focus background sound: off, rain, brown, pink or white. v204: never saved. Every session starts with it off; it plays only after you turn it on during the session. */
    @Volatile var ambient: String = "off"

    private fun play(res: Int) {
        if (!sfxOn) return
        runCatching {
            val id = ids[res] ?: return
            if (synchronized(loaded) { id in loaded }) pool?.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    /** v273: each theme has its own sound set (ThemeSounds.kt). Default and Termux keep the recorded sounds. */
    var themeOn: Boolean
        get() = sp()?.getBoolean("snd_theme", true) ?: false
        set(v) { sp()?.edit()?.putBoolean("snd_theme", v)?.apply() }

    /** v277: the Zen theme is silent, also with Theme sounds off (then the recorded sounds would play). */
    private fun zenSilent(): Boolean {
        val s = sp() ?: return false
        return !s.getBoolean("termux_theme", false) && ThemeSounds.isSilent(s.getString("shop_theme", "default") ?: "default")
    }

    private fun themed(kind: String): Boolean {
        if (zenSilent()) return true
        if (!sfxOn || !themeOn) return false
        val s = sp() ?: return false
        if (s.getBoolean("termux_theme", false)) return false
        return ThemeSounds.play(s.getString("shop_theme", "default") ?: "default", kind)
    }

    fun coin() { Haptics.event("coin"); if (!themed("coin")) play(R.raw.sfx_coin) }
    fun levelUp() { Haptics.event("level"); if (!themed("level")) play(R.raw.sfx_levelup) }
    fun chime() { Haptics.event("chime"); if (!themed("chime")) play(R.raw.sfx_chime) }
    fun alarm() { Haptics.event("alarm"); if (!themed("alarm")) play(R.raw.sfx_alarm) }

    /** A quest paid out: level-up wins over the coin sound. */
    fun questDone(coins: Boolean, levelUp: Boolean) { if (levelUp) levelUp() else if (coins) coin() }

    private fun ambRes(key: String): Int? = when (key) {
        "rain" -> R.raw.amb_rain; "brown" -> R.raw.amb_brown; "pink" -> R.raw.amb_pink; "white" -> R.raw.amb_white; else -> null
    }

    /** Play the chosen background sound until [untilMs] (end of the Focus session). Call on the main thread. */
    fun startAmbient(untilMs: Long) {
        val ctx = app ?: return
        val key = ambient
        val res = ambRes(key)
        val left = untilMs - System.currentTimeMillis()
        if (res == null || left <= 0L) { stopNow(); return }
        runCatching {
            val cur = amb
            if (cur == null || ambKey != key) {
                stopPlayer()
                amb = MediaPlayer.create(ctx, res)?.apply { isLooping = true; setVolume(0.6f, 0.6f); start() }
                ambKey = key
            } else if (!cur.isPlaying) cur.start()
            main.removeCallbacks(stopRunnable)
            main.postDelayed(stopRunnable, left)
        }
    }

    /** Stop the background sound (safe from any thread). */
    fun stopAmbient() { ambient = "off"; main.post { stopNow() } }

    private fun stopNow() { main.removeCallbacks(stopRunnable); stopPlayer() }

    private fun stopPlayer() {
        runCatching { amb?.stop() }
        runCatching { amb?.release() }
        amb = null; ambKey = ""
    }
}
