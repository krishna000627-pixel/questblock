package com.krishna.questblock.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.krishna.questblock.R

/** Home-screen widget: the next routine step (or top quest) and your coins. The app writes the text whenever Home changes. */
class QuestWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    companion object {
        private const val SP = "quest_widget"

        fun update(ctx: Context, line: String, coins: Int) {
            val app = ctx.applicationContext
            val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
            if (sp.getString("line", null) == line && sp.getInt("coins", -1) == coins) return
            sp.edit().putString("line", line).putInt("coins", coins).apply()
            runCatching {
                val mgr = AppWidgetManager.getInstance(app)
                mgr.getAppWidgetIds(ComponentName(app, QuestWidget::class.java)).forEach { render(app, mgr, it) }
            }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            val views = RemoteViews(ctx.packageName, R.layout.quest_widget)
            views.setTextViewText(R.id.widget_line, sp.getString("line", "Open QuestBlock"))
            views.setTextViewText(R.id.widget_coins, "🪙 ${sp.getInt("coins", 0)}")
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (launch != null) {
                val pi = PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }
            mgr.updateAppWidget(id, views)
        }
    }
}
