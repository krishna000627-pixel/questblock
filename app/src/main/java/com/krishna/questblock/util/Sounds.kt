package com.krishna.questblock.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.krishna.questblock.R
import com.krishna.questblock.data.Flags
import kotlinx.coroutines.flow.MutableSharedFlow

/** Sound effects, UI/animation sounds, combo tracking, and looping Focus background soundscapes. */
object Sounds {
    private var app: Context? = null
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()
    private val loaded = HashSet<Int>()
    private var amb: MediaPlayer? = null
    private var synthAmb: AudioTrack? = null
    private var ambKey = ""
    private val main = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopNow() }

    /** Emits combo counts (2, 3, 4...) when quests are completed within 25 seconds of each other. */
    val comboEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    private var lastQuestDoneMs = 0L
    private var comboStreak = 0

    private val BASE_AMBIENTS = listOf(
        "off" to "Off",
        "rain" to "\uD83C\uDF27 Rain",
        "brown" to "\uD83D\uDFE4 Brown",
        "pink" to "\uD83C\uDF38 Pink",
        "white" to "\u26AA White",
    )

    private val THEME_AMBIENTS = listOf(
        "cyber" to "\u26A1 Cyber Hum",
        "nightforest" to "\uD83C\uDF19 Night Forest",
        "library" to "\uD83D\uDD25 Library Hearth",
        "deepspace" to "\uD83C\uDF0C Deep Space",
    )

    /** key -> label, for the pickers. "off" = no background sound. */
    val AMBIENTS: List<Pair<String, String>>
        get() = if (Flags.get("snd_ambient_theme")) BASE_AMBIENTS + THEME_AMBIENTS else BASE_AMBIENTS

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Haptics.init(ctx)
        runCatching {
            val p = SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded.add(id) } }
            intArrayOf(R.raw.sfx_coin, R.raw.sfx_levelup, R.raw.sfx_chime, R.raw.sfx_alarm).forEach {
                ids[it] = p.load(ctx.applicationContext, it, 1)
            }
            pool = p
        }
    }

    private fun sp() = app?.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    /** Sound effects on/off (default on). */
    var sfxOn: Boolean
        get() = sp()?.getBoolean("snd_sfx", true) ?: false
        set(v) { sp()?.edit()?.putBoolean("snd_sfx", v)?.apply() }

    /** The Focus background sound: off, rain, brown, pink, white, cyber, nightforest, library, or deepspace. */
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

    private fun currentTheme(): String = sp()?.getString("shop_theme", "default") ?: "default"

    /** v277: the Zen theme is silent, also with Theme sounds off (then the recorded sounds would play). */
    private fun zenSilent(): Boolean {
        val s = sp() ?: return false
        return !s.getBoolean("termux_theme", false) && ThemeSounds.isSilent(s.getString("shop_theme", "default") ?: "default")
    }

    private fun themed(kind: String, pitchStep: Int = 0, allowFallbackVoice: Boolean = false): Boolean {
        if (zenSilent()) return true
        if (!sfxOn) return false
        val s = sp() ?: return false
        if (!themeOn || s.getBoolean("termux_theme", false)) {
            return if (allowFallbackVoice) ThemeSounds.play("default", kind, pitchStep, useFallbackVoice = true) else false
        }
        return ThemeSounds.play(s.getString("shop_theme", "default") ?: "default", kind, pitchStep, useFallbackVoice = allowFallbackVoice)
    }

    fun coin() { Haptics.event("coin"); if (!themed("coin")) play(R.raw.sfx_coin) }
    fun levelUp() { Haptics.event("level"); if (!themed("level")) play(R.raw.sfx_levelup) }
    fun chime() { Haptics.event("chime"); if (!themed("chime")) play(R.raw.sfx_chime) }
    fun alarm() { Haptics.event("alarm"); if (!themed("alarm")) play(R.raw.sfx_alarm) }

    // ── v292: UI & Animation Sound Events (all flag-gated) ──────────────────
    fun uiTap() {
        if (!Flags.get("snd_ui")) return
        Haptics.uiTick()
        themed("tap", allowFallbackVoice = true)
    }

    fun uiTab() {
        if (!Flags.get("snd_ui")) return
        Haptics.tab()
        themed("tab", allowFallbackVoice = true)
    }

    fun uiToggle(on: Boolean) {
        if (!Flags.get("snd_ui")) return
        Haptics.toggle(on)
        themed(if (on) "toggle_on" else "toggle_off", allowFallbackVoice = true)
    }

    fun uiOpen() {
        if (!Flags.get("snd_ui")) return
        themed("open", allowFallbackVoice = true)
    }

    fun uiClose() {
        if (!Flags.get("snd_ui")) return
        themed("close", allowFallbackVoice = true)
    }

    fun uiSwipe() {
        if (!Flags.get("snd_ui")) return
        themed("swipe", allowFallbackVoice = true)
    }

    fun holdTick(progress: Float) {
        Haptics.holdTick(progress)
        if (!Flags.get("snd_hold")) return
        val step = (progress.coerceIn(0f, 1f) * 14f).toInt()
        themed("hold_tick", pitchStep = step, allowFallbackVoice = true)
    }

    fun unlock() {
        Haptics.unlockBurst()
        if (!themed("unlock", allowFallbackVoice = true)) play(R.raw.sfx_chime)
    }

    fun denied() {
        Haptics.denied()
        themed("denied", allowFallbackVoice = true)
    }

    fun undo() {
        Haptics.uiTick()
        themed("undo", allowFallbackVoice = true)
    }

    fun streak() {
        Haptics.combo(3)
        if (!themed("streak", allowFallbackVoice = true)) play(R.raw.sfx_levelup)
    }

    fun combo(count: Int) {
        Haptics.combo(count)
        themed("combo", pitchStep = ((count - 2) * 2).coerceIn(0, 12), allowFallbackVoice = true)
    }

    fun themeSwitch(themeId: String = currentTheme()) {
        if (!Flags.get("snd_ui") || !sfxOn) return
        if (ThemeSounds.isSilent(themeId)) { Haptics.uiTick(); return }
        Haptics.toggle(true)
        ThemeSounds.play(themeId, "theme_switch", useFallbackVoice = true)
    }

    /** A quest paid out: level-up wins over the coin sound, and back-to-back clears trigger combos. */
    fun questDone(coins: Boolean, levelUp: Boolean) {
        com.krishna.questblock.data.CompanionStore.grantXp(app, 15)

        val now = System.currentTimeMillis()
        if (now - lastQuestDoneMs <= 25_000L) {
            comboStreak += 1
        } else {
            comboStreak = 1
        }
        lastQuestDoneMs = now

        if (levelUp) {
            levelUp()
        } else if (comboStreak >= 2 && Flags.get("anim_combo")) {
            combo(comboStreak)
            comboEvents.tryEmit(comboStreak)
        } else if (coins) {
            coin()
        }
    }

    private fun ambRes(key: String): Int? = when (key) {
        "rain" -> R.raw.amb_rain
        "brown" -> R.raw.amb_brown
        "pink" -> R.raw.amb_pink
        "white" -> R.raw.amb_white
        else -> null
    }

    private fun isSynthAmbient(key: String): Boolean = key in setOf("cyber", "nightforest", "library", "deepspace")

    /** Play the chosen background sound until [untilMs] (end of the Focus session). Call on the main thread. */
    fun startAmbient(untilMs: Long) {
        val ctx = app ?: return
        val key = ambient
        val left = untilMs - System.currentTimeMillis()
        if (key == "off" || left <= 0L) { stopNow(); return }

        if (isSynthAmbient(key) && Flags.get("snd_ambient_theme")) {
            runCatching {
                if (synthAmb == null || ambKey != key) {
                    stopPlayer()
                    val pcm = ThemeSounds.renderAmbientLoop(key)
                    val at = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(22050)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(pcm.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    at.write(pcm, 0, pcm.size)
                    at.setLoopPoints(0, pcm.size, -1)
                    at.setVolume(0.55f)
                    at.play()
                    synthAmb = at
                    ambKey = key
                }
                main.removeCallbacks(stopRunnable)
                main.postDelayed(stopRunnable, left)
            }
            return
        }

        val res = ambRes(key)
        if (res == null) { stopNow(); return }
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
        amb = null
        runCatching { synthAmb?.stop() }
        runCatching { synthAmb?.release() }
        synthAmb = null
        ambKey = ""
    }
}
