package com.krishna.questblock.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.krishna.questblock.data.FUN_EXTEND_COST
import com.krishna.questblock.data.FUN_EXTEND_MIN
import com.krishna.questblock.data.FunLimit
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.isAvailableNow
import com.krishna.questblock.data.nextRunDayStart
import com.krishna.questblock.data.startOfToday
import com.krishna.questblock.ui.MainActivity
import com.krishna.questblock.ui.fmtDur
import com.krishna.questblock.ui.isForToday
import com.krishna.questblock.util.DebugLog
import com.krishna.questblock.util.MusicLog
import com.krishna.questblock.util.Perms
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Small notifications used by the v73 optional features. */
object OptionalAlerts {
    const val ID_FUN80 = 4301
    const val ID_MUSIC = 4302
    const val ID_EVENING = 4303
    const val ID_SUMMARY = 4304

    fun post(ctx: Context, id: Int, title: String, text: String, extend: Boolean = false) {
        if (!Perms.notificationsOk(ctx)) return
        KeepAlive.ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, KeepAlive.CH_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (extend) {
            val pi = PendingIntent.getBroadcast(
                ctx, 7, Intent(ctx, FunExtendReceiver::class.java).putExtra("nid", id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, "+$FUN_EXTEND_MIN min · $FUN_EXTEND_COST🪙", pi)
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}

/** "+5 min" button on the fun-time warnings: costs coins, adds fun time for today only. */
@AndroidEntryPoint
class FunExtendReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: GameRepository

    override fun onReceive(ctx: Context, intent: Intent) {
        val nid = intent.getIntExtra("nid", 0)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = repo.buyFunExtension()
                FunLimit.last = null
                ctx.getSystemService(NotificationManager::class.java).cancel(nid)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(ctx, if (ok) "+$FUN_EXTEND_MIN min of fun time" else "Not enough coins", Toast.LENGTH_SHORT).show()
                }
            } finally { result.finish() }
        }
    }
}

/** The "Done" button on the home-screen widget: ticks the next routine step, or finishes the quest. */
@AndroidEntryPoint
class WidgetDoneReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: GameRepository

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.getStringExtra("action").orEmpty()
        val id = action.substringAfter(':', "").toLongOrNull() ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action.startsWith("step:")) {
                    val subs = repo.allSubTasks.first()
                    val sub = subs.firstOrNull { it.id == id } ?: return@launch
                    val task = repo.tasks.first().firstOrNull { it.id == sub.taskId } ?: return@launch
                    repo.completeStep(task, sub)
                    if (subs.filter { it.taskId == task.id }.all { it.done || it.id == sub.id }) repo.completeTask(task)
                } else if (action.startsWith("task:")) {
                    repo.tasks.first().firstOrNull { it.id == id }?.let { repo.completeTask(it) }
                }
            } finally { result.finish() }
        }
    }
}

/** Optional small overlay with the fun time left, shown over a fun app (needs "Draw over other apps"). */
object FunBubble {
    private var view: TextView? = null

    fun show(ctx: Context, text: String) {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        val v = view ?: TextView(app).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(26, 10, 26, 10)
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = 60f }
        }.also { tv ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.END; x = 24; y = 140 }
            val ok = runCatching { app.getSystemService(WindowManager::class.java).addView(tv, lp) }.isSuccess
            if (ok) view = tv
        }
        v.text = text
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        view = null
        runCatching { ctx.applicationContext.getSystemService(WindowManager::class.java).removeView(v) }
    }
}

/**
 * Live Shorts / Reels counter over the app (top left).
 * Drawn as an accessibility overlay when the blocker service shows it: that needs no "Draw over other apps" permission.
 */
object CounterBubble {
    private var view: TextView? = null
    private var wm: WindowManager? = null
    private var lastFail = 0L

    fun show(ctx: Context, text: String) {
        val isA11y = ctx is android.accessibilityservice.AccessibilityService
        val host: Context = if (isA11y) ctx else ctx.applicationContext
        if (!isA11y && !Settings.canDrawOverlays(host)) { fail("counter: no overlay permission"); return }
        val existing = view
        if (existing != null) { existing.text = text; return }
        val tv = TextView(host).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setPadding(26, 10, 26, 10)
            background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = 60f }
            this.text = text
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (isA11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 140 }
        val manager = host.getSystemService(WindowManager::class.java)
        val r = runCatching { manager.addView(tv, lp) }
        if (r.isSuccess) { view = tv; wm = manager; DebugLog.d("counter: shown") }
        else fail("counter: addView failed (${r.exceptionOrNull()?.javaClass?.simpleName})")
    }

    private fun fail(msg: String) {
        val now = System.currentTimeMillis()
        if (now - lastFail > 30_000L) { lastFail = now; DebugLog.d(msg) }
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        view = null
        runCatching { (wm ?: ctx.applicationContext.getSystemService(WindowManager::class.java)).removeView(v) }
        wm = null
    }
}

/**
 * Runs inside the keep-alive service every second or few. Cheap checks run each time;
 * the heavier ones (fun warnings, evening nudge, daily summary) at most every 30 s.
 */
object OptionalTick {
    private var lastSlow = 0L

    suspend fun run(ctx: Context, repo: GameRepository, prefs: Prefs) {
        val now = System.currentTimeMillis()
        focusEffects(ctx, prefs, now)
        breakVibrate(ctx, prefs, now)
        if (now - lastSlow < 30_000L) return
        lastSlow = now
        runCatching { slow(ctx, repo, prefs, now) }.onFailure { DebugLog.d("optional: ${it.javaClass.simpleName}") }
    }

    // Focus: Do Not Disturb and music pause on the way in, restore on the way out
    private fun focusEffects(ctx: Context, prefs: Prefs, now: Long) {
        val until = prefs.focusUntilCache
        val active = until > now
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (active && prefs.focusAppliedUntil != until) {
            prefs.focusAppliedUntil = until
            if (prefs.optFocusDnd && nm.isNotificationPolicyAccessGranted && prefs.dndPrevFilter == -1) {
                prefs.dndPrevFilter = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                DebugLog.d("optional: Do Not Disturb on for Focus")
            }
            MusicLog.pauseForFocus()
        } else if (!active && prefs.focusAppliedUntil != 0L) {
            prefs.focusAppliedUntil = 0L
        }
        if (!active && prefs.dndPrevFilter != -1) {
            if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(prefs.dndPrevFilter)
            prefs.dndPrevFilter = -1
            DebugLog.d("optional: Do Not Disturb restored")
        }
    }

    private fun breakVibrate(ctx: Context, prefs: Prefs, now: Long) {
        val end = prefs.breakUntil
        if (!prefs.optBreakVibrate || end <= 0L || now < end || prefs.lastBreakVibe == end || now - end > 120_000L) return
        prefs.lastBreakVibe = end
        val vib = ctx.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        else { @Suppress("DEPRECATION") vib.vibrate(500) }
    }

    private suspend fun slow(ctx: Context, repo: GameRepository, prefs: Prefs, now: Long) {
        val cal = java.util.Calendar.getInstance()
        val minuteOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        // 80% of today's fun time used
        if (prefs.optWarn80 && !prefs.doneToday("warn80")) {
            val st = FunLimit.state(ctx, prefs)
            if (st.enabled && !st.reached && st.usedMs * 100 >= st.limitMs * 80) {
                prefs.markToday("warn80")
                OptionalAlerts.post(ctx, OptionalAlerts.ID_FUN80, "⏳ 80% of fun time used",
                    "${fmtDur((st.limitMs - st.usedMs).coerceAtLeast(0L))} left today.")
            }
        }

        // Evening nudge: quests still open today
        if (prefs.optEvening && !prefs.doneToday("evening") && minuteOfDay >= prefs.optEveningHour * 60) {
            prefs.markToday("evening")
            val dayEnd = nextRunDayStart(0, now) - 1
            val open = repo.tasks.first().count { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }
            if (open > 0) OptionalAlerts.post(ctx, OptionalAlerts.ID_EVENING, "🎯 $open quest${if (open > 1) "s" else ""} still open today",
                "There is still time to finish ${if (open > 1) "them" else "it"} before midnight.")
        }

        // Daily summary at the chosen time
        if (prefs.optSummary && !prefs.doneToday("summary") && minuteOfDay >= prefs.optSummaryMin) {
            prefs.markToday("summary")
            val tasks = repo.tasks.first()
            val player = repo.player.first()
            val done = tasks.count { it.done && (it.completedAt ?: 0L) >= startOfToday(now) }
            val st = FunLimit.state(ctx, prefs)
            val fun0 = if (st.enabled) " · 🎮 ${fmtDur(st.usedMs)} of ${fmtDur(st.limitMs)}" else ""
            OptionalAlerts.post(ctx, OptionalAlerts.ID_SUMMARY, "📊 Today in QuestBlock",
                "✅ $done quest${if (done == 1) "" else "s"} done · 🪙 ${player.coins} · 🔥 ${player.streak}d$fun0")
        }
    }
}
