package com.krishna.questblock.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * v273 -> v292: Full multi-voice animation & UI sound engine for every theme.
 * Synthesizes 18 sound events (coin, level, chime, alarm, tap, tab, toggle_on, toggle_off,
 * open, close, swipe, hold_tick, unlock, denied, undo, streak, combo, theme_switch)
 * plus 4 seamless Focus ambient soundscapes (cyber, nightforest, library, deepspace)
 * with zero extra audio asset bloat.
 */
internal class Voice(
    val wave: Int,
    val root: Double,
    val noteMs: Int,
    val decay: Double,
    val coin: IntArray,
    val level: IntArray,
    val chime: IntArray,
    val alarm: IntArray,
    val noiseMix: Double = 0.0,
    val subMix: Double = 0.0,
)

internal object ThemeSounds {
    private const val RATE = 22050
    private const val SINE = 0
    private const val SQUARE = 1
    private const val TRI = 2
    private const val SAW = 3
    private const val PULSE = 4
    private const val BELL = 5
    private const val PLUCK = 6

    private fun v(
        wave: Int,
        root: Double,
        ms: Int,
        decay: Double,
        coin: IntArray,
        level: IntArray,
        chime: IntArray,
        alarm: IntArray,
        noiseMix: Double = 0.0,
        subMix: Double = 0.0,
    ) = Voice(wave, root, ms, decay, coin, level, chime, alarm, noiseMix, subMix)

    private fun a(vararg n: Int) = n

    val VOICES: Map<String, Voice> = mapOf(
        // 9 Style Themes
        "system" to v(SQUARE, 880.0, 85, 5.0, a(0, 12), a(0, 4, 7, 12, 16), a(0, 7, 12), a(0, -5, 0, -5), subMix = 0.18),
        "ninja" to v(PLUCK, 440.0, 115, 4.0, a(0, 7), a(0, 2, 7, 9, 14), a(0, 5), a(0, -2, 0, -2), noiseMix = 0.14),
        "hashira" to v(BELL, 523.0, 125, 3.0, a(0, 7), a(0, 4, 7, 11, 14), a(0, 7, 4), a(7, 0, 7, 0), noiseMix = 0.08),
        "monarch" to v(SAW, 220.0, 105, 4.5, a(0, 7), a(0, 3, 7, 10, 15), a(0, 3, 7), a(0, -1, 0, -1), subMix = 0.35),
        "gothic" to v(BELL, 330.0, 145, 3.0, a(0, 3), a(0, 3, 7, 12, 15), a(0, 7), a(0, -3, 0, -3), subMix = 0.22),
        "guild" to v(TRI, 392.0, 110, 4.0, a(0, 4), a(0, 4, 7, 12, 16), a(0, 7, 4), a(0, 5, 0, 5), noiseMix = 0.10),
        "pixel" to v(PULSE, 988.0, 68, 5.2, a(0, 5), a(0, 4, 7, 12, 7, 12), a(0, 4, 7), a(0, 0, 12, 12)),
        "handheld" to v(SQUARE, 659.0, 78, 5.0, a(0, 12), a(0, 2, 4, 7, 12), a(0, 7), a(0, 0, -5, -5)),
        "blueprint" to v(SINE, 1047.0, 110, 5.5, a(0, 7), a(0, 5, 7, 12), a(0), a(0, 0, 0), noiseMix = 0.06),
        // 16 Colour, Paper & Dark Themes
        "sunset" to v(SINE, 392.0, 125, 3.5, a(0, 4), a(0, 4, 7, 12), a(0, 7), a(0, -3, 0, -3)),
        "ocean" to v(SINE, 330.0, 145, 3.0, a(0, 5), a(0, 5, 9, 12, 17), a(0, 7), a(0, -2, 0, -2), noiseMix = 0.07),
        "forest" to v(TRI, 294.0, 125, 3.5, a(0, 4), a(0, 4, 7, 9, 12), a(0, 5), a(0, -4, 0, -4), noiseMix = 0.08),
        "neon" to v(SAW, 440.0, 88, 5.0, a(0, 12), a(0, 3, 7, 10, 14, 19), a(0, 7, 12), a(0, 12, 0, 12), subMix = 0.25),
        "scifi" to v(BELL, 740.0, 95, 4.0, a(0, 7), a(0, 7, 12, 19), a(0, 12), a(0, 6, 0, 6), subMix = 0.15),
        "amoled" to v(SINE, 523.0, 105, 5.0, a(0, 7), a(0, 7, 12), a(0), a(0, 0)),
        "terminal" to v(SQUARE, 523.0, 58, 6.0, a(0, 0), a(0, 0, 0, 7), a(0, 0), a(0, 0, 0, 0), noiseMix = 0.08),
        "paper" to v(PLUCK, 349.0, 115, 4.2, a(0, 4), a(0, 2, 4, 7, 9), a(0, 4), a(0, -2, 0, -2), noiseMix = 0.16),
        "linen" to v(PLUCK, 311.0, 125, 4.0, a(0, 5), a(0, 3, 7, 10, 12), a(0, 7), a(0, -3, 0, -3), noiseMix = 0.12),
        "sage" to v(BELL, 392.0, 135, 3.0, a(0, 4), a(0, 4, 9, 12), a(0, 5), a(0, -5, 0, -5)),
        "sky" to v(BELL, 587.0, 115, 3.5, a(0, 7), a(0, 4, 7, 11, 16), a(0, 12), a(0, 5, 0, 5)),
        "lilac" to v(BELL, 466.0, 125, 3.5, a(0, 3), a(0, 3, 7, 10, 14), a(0, 7), a(0, -4, 0, -4)),
        "chalk" to v(PLUCK, 262.0, 95, 5.0, a(0, 2), a(0, 2, 4, 7, 9), a(0, 4), a(0, -1, 0, -1), noiseMix = 0.22),
        "clay" to v(TRI, 392.0, 105, 4.5, a(0, 7), a(0, 4, 7, 11, 12), a(0, 5), a(0, 3, 0, 3), subMix = 0.12),
        "rose" to v(BELL, 440.0, 135, 3.0, a(0, 4), a(0, 4, 7, 12, 16), a(0, 7), a(0, -2, 0, -2)),
        "indigo" to v(SINE, 277.0, 150, 3.0, a(0, 7), a(0, 5, 7, 12, 17), a(0, 12), a(0, -5, 0, -5), subMix = 0.18),
        // 3 Minimal & 3 Maximal Themes (Zen stays silent)
        "mono" to v(SINE, 880.0, 85, 6.0, a(0, 7), a(0, 7, 12), a(0), a(0, 0)),
        "whitespace" to v(SINE, 523.0, 130, 4.2, a(0, 7), a(0, 12), a(0), a(0, 0)),
        "carnival" to v(PULSE, 523.0, 75, 4.5, a(0, 4, 7), a(0, 4, 7, 12, 16, 19, 24), a(0, 7, 12), a(0, 12, 0, 12)),
        "baroque" to v(PLUCK, 392.0, 105, 4.0, a(0, 4), a(0, 4, 7, 12, 16), a(0, 7, 12), a(0, -5, 0, -5), subMix = 0.14),
        "vaporwave" to v(SINE, 330.0, 150, 3.0, a(0, 7, 11), a(0, 4, 7, 11, 14, 19), a(0, 7, 11), a(0, -7, 0, -7), subMix = 0.22),
        "habitica" to v(TRI, 523.0, 90, 4.0, a(0, 4, 7), a(0, 4, 7, 12, 16, 19), a(0, 7, 12), a(0, -3, 0, -3), subMix = 0.14),
    )

    private val DEFAULT_VOICE = v(BELL, 523.0, 100, 4.0, a(0, 7), a(0, 4, 7, 12), a(0, 7), a(0, -3, 0, -3))

    val SILENT: Set<String> = setOf("zen")
    fun isSilent(shopTheme: String): Boolean = shopTheme.removePrefix("theme_") in SILENT

    private val cache = HashMap<String, ShortArray>()
    private val lastPlayed = HashMap<String, Long>()
    private val main = Handler(Looper.getMainLooper())

    fun voiceFor(shopTheme: String): Voice? = VOICES[shopTheme.removePrefix("theme_")]

    /**
     * Plays a theme-matched sound effect.
     * [kind] supports: coin, level, chime, alarm, tap, tab, toggle_on, toggle_off,
     * open, close, swipe, hold_tick, unlock, denied, undo, streak, combo, theme_switch.
     * [pitchStep] shifts pitch in semitones (used by hold_tick and combo).
     */
    fun play(shopTheme: String, kind: String, pitchStep: Int = 0, useFallbackVoice: Boolean = false): Boolean {
        val key = shopTheme.removePrefix("theme_")
        if (key in SILENT) return true
        val voice = VOICES[key] ?: if (useFallbackVoice) DEFAULT_VOICE else return false

        // Anti-spam rate limiter for rapid UI interactions
        val now = System.currentTimeMillis()
        val cooldown = when (kind) {
            "tap", "swipe", "hold_tick" -> 45L
            "tab", "toggle_on", "toggle_off" -> 70L
            else -> 25L
        }
        synchronized(lastPlayed) {
            val prev = lastPlayed[kind] ?: 0L
            if (now - prev < cooldown) return true
            lastPlayed[kind] = now
        }

        return runCatching {
            val cacheKey = "$key/$kind/$pitchStep"
            val pcm = synchronized(cache) {
                if (cache.size > 160) cache.clear()
                cache.getOrPut(cacheKey) { render(voice, kind, pitchStep) }
            }
            val at = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            at.write(pcm, 0, pcm.size)
            at.play()
            main.postDelayed({
                runCatching { at.stop() }
                runCatching { at.release() }
            }, pcm.size * 1000L / RATE + 250L)
            true
        }.getOrDefault(false)
    }

    private fun osc(wave: Int, ph: Double, f: Double, t: Double): Double = when (wave) {
        SQUARE -> if (sin(ph) >= 0.0) 0.58 else -0.58
        TRI -> 2.0 / PI * asin(sin(ph))
        SAW -> 2.0 * ((f * t) % 1.0) - 1.0
        PULSE -> if ((f * t) % 1.0 < 0.25) 0.6 else -0.6
        BELL -> 0.68 * sin(ph) + 0.24 * sin(ph * 2.76) * exp(-6.0 * t) + 0.08 * sin(ph * 5.4) * exp(-11.0 * t)
        PLUCK -> (2.0 / PI * asin(sin(ph))) * (0.55 + 0.45 * exp(-10.0 * t))
        else -> sin(ph)
    }

    private fun render(v: Voice, kind: String, pitchStep: Int): ShortArray {
        val isUi = kind in setOf("tap", "tab", "toggle_on", "toggle_off", "open", "close", "swipe", "hold_tick")
        val notes: IntArray = when (kind) {
            "coin" -> v.coin
            "level" -> v.level
            "chime" -> v.chime
            "alarm" -> v.alarm
            "tap" -> a(0)
            "tab" -> a(0, 5)
            "toggle_on" -> a(0, 7)
            "toggle_off" -> a(7, 0)
            "open" -> a(0, 4, 7)
            "close" -> a(7, 4, 0)
            "swipe" -> a(0, 3)
            "hold_tick" -> a(pitchStep.coerceIn(0, 16))
            "unlock" -> a(0, 4, 7, 12)
            "denied" -> a(0, -6)
            "undo" -> a(7, 4, 0, -5)
            "streak" -> a(0, 4, 7, 12, 16)
            "combo" -> a(0 + pitchStep, 4 + pitchStep, 7 + pitchStep, 12 + pitchStep)
            "theme_switch" -> a(0, 4, 7, 11, 14)
            else -> v.chime
        }

        val stepMs = when (kind) {
            "tap" -> 26
            "hold_tick" -> 32
            "tab", "swipe" -> 42
            "toggle_on", "toggle_off" -> 48
            "open", "close" -> 52
            "unlock", "streak", "theme_switch" -> maxOf(55, v.noteMs - 20)
            "combo" -> 60
            "denied" -> 95
            else -> v.noteMs
        }

        val step = RATE * stepMs / 1000
        val len = when (kind) {
            "tap", "hold_tick" -> step * 2
            "tab", "toggle_on", "toggle_off", "swipe" -> (step * 2.2).toInt()
            else -> step * 2
        }
        val tail = if (isUi) RATE / 25 else RATE / 10
        val total = step * (notes.size - 1) + len + tail
        val buf = DoubleArray(total)

        val vol = when (kind) {
            "alarm" -> 0.55
            "tap", "swipe" -> 0.22
            "tab", "toggle_on", "toggle_off", "open", "close" -> 0.28
            "hold_tick" -> 0.26
            "unlock", "streak", "combo" -> 0.48
            else -> 0.45
        }
        val decayRate = if (isUi) v.decay * 1.45 else v.decay
        val rng = Random(42)

        notes.forEachIndexed { k, semi ->
            val f = v.root * 2.0.pow(semi / 12.0)
            val start = k * step
            for (i in 0 until len) {
                if (start + i >= total) break
                val t = i.toDouble() / RATE
                val ph = 2.0 * PI * f * t
                var x = osc(v.wave, ph, f, t)
                // Add subtle harmonic richness for celebratory sounds
                if (kind in setOf("level", "unlock", "streak", "combo", "theme_switch")) {
                    x = x * 0.78 + 0.22 * sin(ph * 2.0) * exp(-4.0 * t)
                }
                if (v.subMix > 0.0 && !isUi) {
                    x += v.subMix * sin(ph * 0.5) * exp(-3.5 * t)
                }
                if (v.noiseMix > 0.0) {
                    val noise = (rng.nextDouble() * 2.0 - 1.0) * exp(-28.0 * t)
                    x += v.noiseMix * noise
                }
                val attackSamples = if (isUi) 45 else 90
                val attack = if (i < attackSamples) i / attackSamples.toDouble() else 1.0
                val env = attack * exp(-decayRate * i.toDouble() / len)
                buf[start + i] += x * env * vol
            }
        }

        val out = ShortArray(total)
        for (i in 0 until total) {
            out[i] = (buf[i].coerceIn(-1.0, 1.0) * 32000.0).toInt().toShort()
        }
        return out
    }

    /** Generates a 4-second seamless looping PCM buffer for synthesized Focus ambient soundscapes. */
    fun renderAmbientLoop(key: String): ShortArray {
        val seconds = 4
        val total = RATE * seconds
        val buf = DoubleArray(total)
        val rng = Random(key.hashCode())
        var lp = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val loopEnv = 0.5 - 0.5 * kotlin.math.cos(2.0 * PI * t / seconds) // seamless harmonic breathing
            val sample = when (key) {
                "cyber" -> {
                    val pad = 0.35 * sin(2.0 * PI * 110.0 * t) +
                        0.25 * sin(2.0 * PI * 165.0 * t) +
                        0.15 * sin(2.0 * PI * 220.0 * t) * (0.4 + 0.6 * loopEnv)
                    val pulse = 0.06 * sin(2.0 * PI * 440.0 * t) * (0.5 + 0.5 * sin(2.0 * PI * 1.5 * t))
                    (pad + pulse) * 0.38
                }
                "nightforest" -> {
                    val wind = (rng.nextDouble() * 2.0 - 1.0)
                    lp = lp * 0.97 + wind * 0.03
                    val chirpGate = if ((t * 2.0) % 1.0 < 0.18) sin(2.0 * PI * 28.0 * t).coerceAtLeast(0.0) else 0.0
                    val cricket = 0.10 * sin(2.0 * PI * 4186.0 * t) * chirpGate
                    lp * 2.2 + cricket
                }
                "library" -> {
                    val room = (rng.nextDouble() * 2.0 - 1.0)
                    lp = lp * 0.985 + room * 0.015
                    val crackle = if (rng.nextDouble() < 0.0022) (rng.nextDouble() * 2.0 - 1.0) * 0.35 else 0.0
                    val warmth = 0.14 * sin(2.0 * PI * 65.4 * t)
                    lp * 2.6 + crackle + warmth
                }
                else -> { // deepspace
                    val d1 = 0.40 * sin(2.0 * PI * 55.0 * t)
                    val d2 = 0.25 * sin(2.0 * PI * 82.4 * t) * (0.5 + 0.5 * loopEnv)
                    val d3 = 0.14 * sin(2.0 * PI * 110.0 * t)
                    (d1 + d2 + d3) * 0.42
                }
            }
            buf[i] = sample
        }
        val out = ShortArray(total)
        for (i in 0 until total) {
            out[i] = (buf[i].coerceIn(-0.95, 0.95) * 28000.0).toInt().toShort()
        }
        return out
    }
}
