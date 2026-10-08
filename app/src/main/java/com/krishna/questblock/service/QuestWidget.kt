package com.krishna.questblock.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.krishna.questblock.R

/** Home-screen widget: the next routine step (or top quest) and your coins. The app writes the text whenever Home changes. */
class QuestWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    companion object {
        private const val SP = "quest_widget"

        fun update(ctx: Context, line: String, coins: Int, action: String = "", streak: Int = 0, xp: Int = -1) {
            val app = ctx.applicationContext
            val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
            if (sp.getString("line", null) == line && sp.getInt("coins", -1) == coins && sp.getString("action", "") == action && sp.getInt("streak", -1) == streak && sp.getInt("xp", -2) == xp) return
            sp.edit().putString("line", line).putInt("coins", coins).putString("action", action).putInt("streak", streak).putInt("xp", xp).apply()
            refresh(app)
        }

        /** Redraw every widget (also used when the Done-button switch changes in Settings). */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            runCatching {
                val mgr = AppWidgetManager.getInstance(app)
                mgr.getAppWidgetIds(ComponentName(app, QuestWidget::class.java)).forEach { render(app, mgr, it) }
            }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            val views = RemoteViews(ctx.packageName, R.layout.quest_widget)
            views.setTextViewText(R.id.widget_line, sp.getString("line", "Open QuestBlock"))
            val xp = sp.getInt("xp", -1)
            val cultOn = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("cultivation_on", false)
            val realm = if (xp >= 0 && cultOn) com.krishna.questblock.engine.Cultivation.of(xp).let {
                "  ·  ${it.realm.emoji} ${it.realm.name.substringBefore(' ')} ${if (it.stageName.startsWith("Layer")) "L" + it.stageName.removePrefix("Layer ") else it.stageName}"
            } else ""
            views.setTextViewText(R.id.widget_coins, "🪙 ${sp.getInt("coins", 0)}" + sp.getInt("streak", 0).let { if (it > 0) "  ·  🔥 $it" else "" } + realm)
            // optional Done button (Settings → Optional features)
            val action = sp.getString("action", "").orEmpty()
            val doneOn = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean("opt_widget_done", false)
            if (doneOn && action.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_done, android.view.View.VISIBLE)
                val di = Intent(ctx, WidgetDoneReceiver::class.java).putExtra("action", action)
                views.setOnClickPendingIntent(R.id.widget_done,
                    PendingIntent.getBroadcast(ctx, 11, di, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } else views.setViewVisibility(R.id.widget_done, android.view.View.GONE)
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (launch != null) {
                val pi = PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }
            mgr.updateAppWidget(id, views)
        }
    }
}
