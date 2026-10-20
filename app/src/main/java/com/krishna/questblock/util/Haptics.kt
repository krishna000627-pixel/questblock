package com.krishna.questblock.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * v274: haptics that go with the sounds. Every theme feels like its sound set (ThemeSounds.kt): the number of buzzes follows the notes,
 * sharp square/pulse sounds give short hard buzzes, saw sounds long strong ones, bell and sine sounds soft ones. Default and Termux get a plain pattern.
 * Events: coin, level (level up), chime (Focus ended), alarm (unlock ran out), plus [tick] for the Handheld page wipe.
 * Switch: flag "haptics" (Flags > More flags, terminal: flags on|off haptics). Needs only the VIBRATE permission the app already has.
 */
object Haptics {
    private var app: Context? = null
    fun init(ctx: Context) { app = ctx.applicationContext }
    private fun sp() = app?.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    var on: Boolean
        get() = sp()?.getBoolean("haptics_on", true) ?: false
        set(v) { sp()?.edit()?.putBoolean("haptics_on", v)?.apply() }

    /** [kind] is coin, level, chime or alarm. */
    fun event(kind: String) {
        if (!on) return
        runCatching {
            val s = sp() ?: return
            val theme = if (s.getBoolean("termux_theme", false)) "default" else (s.getString("shop_theme", "default") ?: "default")
            val v = ThemeSounds.voiceFor(theme)
            val n = if (v != null) when (kind) { "coin" -> v.coin.size; "level" -> v.level.size; "chime" -> v.chime.size; else -> v.alarm.size }
                else when (kind) { "coin" -> 2; "level" -> 4; "chime" -> 1; else -> 3 }
            val noteMs = v?.noteMs ?: 100
            // wave numbers as in ThemeSounds: 0 sine, 1 square, 2 triangle, 3 saw, 4 pulse, 5 bell, 6 pluck
            val pulse: Int
            val amp: Int
            when (v?.wave) {
                1, 4 -> { pulse = 14; amp = 220 }
                3 -> { pulse = 38; amp = 255 }
                0, 5 -> { pulse = 30; amp = 100 }
                else -> { pulse = 24; amp = 170 }
            }
            val p = if (kind == "alarm") pulse * 4 else pulse
            val gap = maxOf(noteMs - pulse, 30)
            val t = LongArray(n * 2 + 1)
            val a = IntArray(n * 2 + 1)
            for (i in 0 until n) {
                val scale = when (kind) { "level" -> 0.5 + 0.5 * (i + 1) / n; "coin" -> 0.8; "chime" -> 0.6; else -> 1.0 }
                t[1 + i * 2] = p.toLong()
                t[2 + i * 2] = gap.toLong()
                a[1 + i * 2] = (amp * scale).toInt().coerceIn(1, 255)
            }
            val vib = app?.getSystemService(Vibrator::class.java) ?: return
            if (!vib.hasVibrator()) return
            val eff = if (vib.hasAmplitudeControl()) VibrationEffect.createWaveform(t, a, -1) else VibrationEffect.createWaveform(t, -1)
            vib.vibrate(eff)
        }
    }

    /** One short tick (the Handheld page wipe). */
    fun tick() {
        if (!on) return
        runCatching {
            val vib = app?.getSystemService(Vibrator::class.java) ?: return
            if (vib.hasVibrator()) vib.vibrate(VibrationEffect.createOneShot(12, 120))
        }
    }
}
