package com.krishna.questblock.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * v273: a sound set for every theme (coin, level up, chime, alarm), made on the phone from a few notes, so no audio files are added.
 * The default theme and the Termux theme keep the original recorded sounds. Switch: flag "themesounds" (Flags > More flags, terminal: flags on|off themesounds).
 * New theme: add one line to VOICES (key = the shop theme id without "theme_").
 */
internal class Voice(
    val wave: Int, val root: Double, val noteMs: Int, val decay: Double,
    val coin: IntArray, val level: IntArray, val chime: IntArray, val alarm: IntArray,
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

    private fun v(wave: Int, root: Double, ms: Int, decay: Double, coin: IntArray, level: IntArray, chime: IntArray, alarm: IntArray) =
        Voice(wave, root, ms, decay, coin, level, chime, alarm)

    private fun a(vararg n: Int) = n

    val VOICES: Map<String, Voice> = mapOf(
        // the nine style themes
        "system" to v(SQUARE, 880.0, 90, 5.0, a(0, 12), a(0, 4, 7, 12, 16), a(0, 7, 12), a(0, -5, 0, -5)),
        "ninja" to v(PLUCK, 440.0, 120, 4.0, a(0, 7), a(0, 2, 7, 9, 14), a(0, 5), a(0, -2, 0, -2)),
        "hashira" to v(BELL, 523.0, 130, 3.0, a(0, 7), a(0, 4, 7, 11, 14), a(0, 7, 4), a(7, 0, 7, 0)),
        "monarch" to v(SAW, 220.0, 110, 4.5, a(0, 7), a(0, 3, 7, 10, 15), a(0, 3, 7), a(0, -1, 0, -1)),
        "gothic" to v(BELL, 330.0, 150, 3.0, a(0, 3), a(0, 3, 7, 12, 15), a(0, 7), a(0, -3, 0, -3)),
        "guild" to v(TRI, 392.0, 110, 4.0, a(0, 4), a(0, 4, 7, 12, 16), a(0, 7, 4), a(0, 5, 0, 5)),
        "pixel" to v(PULSE, 988.0, 70, 5.0, a(0, 5), a(0, 4, 7, 12, 7, 12), a(0, 4, 7), a(0, 0, 12, 12)),
        "handheld" to v(SQUARE, 659.0, 80, 5.0, a(0, 12), a(0, 2, 4, 7, 12), a(0, 7), a(0, 0, -5, -5)),
        "blueprint" to v(SINE, 1047.0, 120, 6.0, a(0, 7), a(0, 5, 7, 12), a(0), a(0, 0, 0)),
        // the sixteen newer themes
        "sunset" to v(SINE, 392.0, 130, 3.5, a(0, 4), a(0, 4, 7, 12), a(0, 7), a(0, -3, 0, -3)),
        "ocean" to v(SINE, 330.0, 150, 3.0, a(0, 5), a(0, 5, 9, 12, 17), a(0, 7), a(0, -2, 0, -2)),
        "forest" to v(TRI, 294.0, 130, 3.5, a(0, 4), a(0, 4, 7, 9, 12), a(0, 5), a(0, -4, 0, -4)),
        "neon" to v(SAW, 440.0, 90, 5.0, a(0, 12), a(0, 3, 7, 10, 14, 19), a(0, 7, 12), a(0, 12, 0, 12)),
        "scifi" to v(BELL, 740.0, 100, 4.0, a(0, 7), a(0, 7, 12, 19), a(0, 12), a(0, 6, 0, 6)),
        "amoled" to v(SINE, 523.0, 110, 5.0, a(0, 7), a(0, 7, 12), a(0), a(0, 0)),
        "terminal" to v(SQUARE, 523.0, 60, 6.0, a(0, 0), a(0, 0, 0, 7), a(0, 0), a(0, 0, 0, 0)),
        "paper" to v(PLUCK, 349.0, 120, 4.0, a(0, 4), a(0, 2, 4, 7, 9), a(0, 4), a(0, -2, 0, -2)),
        "linen" to v(PLUCK, 311.0, 130, 4.0, a(0, 5), a(0, 3, 7, 10, 12), a(0, 7), a(0, -3, 0, -3)),
        "sage" to v(BELL, 392.0, 140, 3.0, a(0, 4), a(0, 4, 9, 12), a(0, 5), a(0, -5, 0, -5)),
        "sky" to v(BELL, 587.0, 120, 3.5, a(0, 7), a(0, 4, 7, 11, 16), a(0, 12), a(0, 5, 0, 5)),
        "lilac" to v(BELL, 466.0, 130, 3.5, a(0, 3), a(0, 3, 7, 10, 14), a(0, 7), a(0, -4, 0, -4)),
        "chalk" to v(PLUCK, 262.0, 100, 5.0, a(0, 2), a(0, 2, 4, 7, 9), a(0, 4), a(0, -1, 0, -1)),
        "clay" to v(TRI, 392.0, 110, 4.5, a(0, 7), a(0, 4, 7, 11, 12), a(0, 5), a(0, 3, 0, 3)),
        "rose" to v(BELL, 440.0, 140, 3.0, a(0, 4), a(0, 4, 7, 12, 16), a(0, 7), a(0, -2, 0, -2)),
        "indigo" to v(SINE, 277.0, 160, 3.0, a(0, 7), a(0, 5, 7, 12, 17), a(0, 12), a(0, -5, 0, -5)),
    )

    private val cache = HashMap<String, ShortArray>()
    private val main = Handler(Looper.getMainLooper())

    fun voiceFor(shopTheme: String): Voice? = VOICES[shopTheme.removePrefix("theme_")]

    /** [kind] is coin, level, chime or alarm. */
    fun play(shopTheme: String, kind: String): Boolean {
        val key = shopTheme.removePrefix("theme_")
        val voice = VOICES[key] ?: return false
        return runCatching {
            val pcm = synchronized(cache) { cache.getOrPut("$key/$kind") { render(voice, kind) } }
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            at.write(pcm, 0, pcm.size)
            at.play()
            main.postDelayed({ runCatching { at.stop() }; runCatching { at.release() } }, pcm.size * 1000L / RATE + 300L)
            true
        }.getOrDefault(false)
    }

    private fun render(v: Voice, kind: String): ShortArray {
        val notes = when (kind) { "coin" -> v.coin; "level" -> v.level; "chime" -> v.chime; else -> v.alarm }
        val step = RATE * v.noteMs / 1000
        val len = step * 2
        val total = step * (notes.size - 1) + len + RATE / 10
        val buf = DoubleArray(total)
        val vol = if (kind == "alarm") 0.55 else 0.45
        notes.forEachIndexed { k, semi ->
            val f = v.root * 2.0.pow(semi / 12.0)
            val start = k * step
            for (i in 0 until len) {
                if (start + i >= total) break
                val t = i.toDouble() / RATE
                val ph = 2.0 * PI * f * t
                val x = when (v.wave) {
                    1 -> if (sin(ph) >= 0.0) 0.6 else -0.6
                    2 -> 2.0 / PI * kotlin.math.asin(sin(ph))
                    3 -> 2.0 * ((f * t) % 1.0) - 1.0
                    4 -> if ((f * t) % 1.0 < 0.25) 0.6 else -0.6
                    5 -> 0.7 * sin(ph) + 0.3 * sin(ph * 2.76) * exp(-6.0 * t)
                    6 -> 2.0 / PI * kotlin.math.asin(sin(ph)) * (0.6 + 0.4 * exp(-12.0 * t))
                    else -> sin(ph)
                }
                val attack = if (i < 90) i / 90.0 else 1.0
                val env = attack * exp(-v.decay * i.toDouble() / len)
                buf[start + i] += x * env * vol
            }
        }
        val out = ShortArray(total)
        for (i in 0 until total) out[i] = (buf[i].coerceIn(-1.0, 1.0) * 32000.0).toInt().toShort()
        return out
    }
}
