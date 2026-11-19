package com.krishna.questblock.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.krishna.questblock.data.Flags

/**
 * v274 -> v292: Haptics synced to theme sounds and animations.
 * Includes rising hold-to-unlock ramp, multi-beat combo/level-up bursts, and theme-tuned UI ticks.
 */
object Haptics {
    private var app: Context? = null
    fun init(ctx: Context) { app = ctx.applicationContext }
    private fun sp() = app?.getSharedPreferences("questblock", Context.MODE_PRIVATE)

    var on: Boolean
        get() = sp()?.getBoolean("haptics_on", true) ?: false
        set(v) { sp()?.edit()?.putBoolean("haptics_on", v)?.apply() }

    private fun vib(): Vibrator? = app?.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }

    private fun isZen(): Boolean {
        val s = sp() ?: return false
        return !s.getBoolean("termux_theme", false) &&
            ThemeSounds.isSilent(s.getString("shop_theme", "default") ?: "default")
    }

    /** [kind] is coin, level, chime or alarm. */
    fun event(kind: String) {
        if (!on) return
        runCatching {
            val s = sp() ?: return
            val theme = if (s.getBoolean("termux_theme", false)) "default" else (s.getString("shop_theme", "default") ?: "default")
            if (ThemeSounds.isSilent(theme)) {
                vib()?.vibrate(VibrationEffect.createOneShot(35, 60))
                return
            }
            val v = ThemeSounds.voiceFor(theme)
            val n = if (v != null) when (kind) { "coin" -> v.coin.size; "level" -> v.level.size; "chime" -> v.chime.size; else -> v.alarm.size }
                else when (kind) { "coin" -> 2; "level" -> 4; "chime" -> 1; else -> 3 }
            val noteMs = v?.noteMs ?: 100
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
            val vib = vib() ?: return
            val eff = if (vib.hasAmplitudeControl()) VibrationEffect.createWaveform(t, a, -1) else VibrationEffect.createWaveform(t, -1)
            vib.vibrate(eff)
        }
    }

    /** One short tick (the Handheld page wipe). */
    fun tick() {
        if (!on) return
        runCatching { vib()?.vibrate(VibrationEffect.createOneShot(12, 120)) }
    }

    /** Subtle UI tap tick tuned to current theme. */
    fun uiTick() {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val amp = if (isZen()) 45 else 85
            vib()?.vibrate(VibrationEffect.createOneShot(10, amp))
        }
    }

    fun tab() {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching { vib()?.vibrate(VibrationEffect.createOneShot(14, if (isZen()) 50 else 105)) }
    }

    fun toggle(checked: Boolean) {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val ms = if (checked) 18L else 12L
            val amp = if (isZen()) 55 else if (checked) 140 else 90
            vib()?.vibrate(VibrationEffect.createOneShot(ms, amp))
        }
    }

    /** Rising haptic pulse while holding to unlock an app. */
    fun holdTick(progress: Float) {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val p = progress.coerceIn(0f, 1f)
            val amp = (50 + (p * 190)).toInt().coerceIn(40, 255)
            val ms = (10 + (p * 14)).toLong()
            vib()?.vibrate(VibrationEffect.createOneShot(ms, amp))
        }
    }

    fun unlockBurst() {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val vib = vib() ?: return
            if (isZen()) { vib.vibrate(VibrationEffect.createOneShot(40, 70)); return }
            val t = longArrayOf(0, 25, 45, 55)
            val a = intArrayOf(0, 140, 0, 245)
            vib.vibrate(if (vib.hasAmplitudeControl()) VibrationEffect.createWaveform(t, a, -1) else VibrationEffect.createWaveform(t, -1))
        }
    }

    fun denied() {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val vib = vib() ?: return
            val t = longArrayOf(0, 35, 50, 35)
            val a = intArrayOf(0, 200, 0, 180)
            vib.vibrate(if (vib.hasAmplitudeControl()) VibrationEffect.createWaveform(t, a, -1) else VibrationEffect.createWaveform(t, -1))
        }
    }

    fun combo(count: Int) {
        if (!on || !Flags.get("haptics_sync")) return
        runCatching {
            val vib = vib() ?: return
            if (isZen()) { vib.vibrate(VibrationEffect.createOneShot(35, 65)); return }
            val beats = count.coerceIn(2, 5)
            val t = LongArray(beats * 2 + 1)
            val a = IntArray(beats * 2 + 1)
            for (i in 0 until beats) {
                t[1 + i * 2] = 22L
                t[2 + i * 2] = 45L
                a[1 + i * 2] = (130 + i * 30).coerceAtMost(255)
            }
            vib.vibrate(if (vib.hasAmplitudeControl()) VibrationEffect.createWaveform(t, a, -1) else VibrationEffect.createWaveform(t, -1))
        }
    }
}
