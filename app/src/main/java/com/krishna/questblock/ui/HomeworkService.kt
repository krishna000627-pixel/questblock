package com.krishna.questblock.ui

import android.content.Context
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Hw
import com.krishna.questblock.data.HwItem
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.service.KeepAlive
import com.krishna.questblock.util.Sounds
import kotlinx.coroutines.flow.first

/** v211: what handing in or focusing on homework does, shared by the Homework page and the terminal commands. */
object HwService {
    class Done(val item: HwItem, val lines: List<String>)

    /** Hand in one item: coins when on time (once per item), tick its chapter, learn how long it took. */
    suspend fun complete(ctx: Context, repo: GameRepository, id: Long): Done? {
        val before = Hw.get(ctx, id) ?: return null
        if (before.done) return Done(before, listOf("already handed in"))
        val item = Hw.complete(ctx, id) ?: return null
        val lines = ArrayList<String>()
        if (item.onTime) {
            if (!item.paid) {
                repo.grantBonus(Hw.ON_TIME_COINS, 0, "Homework", item.text.take(40))
                Hw.edit(ctx, id) { it.copy(paid = true) }
                lines.add("handed in on time: +${Hw.ON_TIME_COINS} coins")
            } else lines.add("handed in on time (its coins were paid before)")
        } else {
            lines.add("handed in late: no coins")
        }
        if (item.ch > 0 && item.kind.isNotEmpty()) {
            var pay = 0
            var detail = ""
            val msg = termuxChapterEnsure(ctx, item.subj, item.ch, item.kind) { coins, d -> pay += coins; detail = d }
            if (pay > 0) repo.grantBonus(pay, 0, "Chapter", detail)
            lines.add(msg)
        }
        if (item.minutes >= 5) lines.add("took ${item.minutes} min of Focus: the estimate for ${Hw.subjectName(item.subj)} learns from it")
        return Done(Hw.get(ctx, id) ?: item, lines)
    }

    /** Starts a timed Focus session for one item. Returns an error line, or null when it started. */
    suspend fun startFocus(ctx: Context, repo: GameRepository, item: HwItem, minutes: Int, strict: Boolean): String? {
        if (repo.player.first().focusModeUntil > System.currentTimeMillis()) return "a Focus session is already running"
        if (minutes < 5 || minutes > 120) return "a session is 5 to 120 minutes"
        repo.setFocusStrict(strict)
        Sounds.ambient = "off"
        val ok = repo.startFocus(minutes * 60_000L, null)
        if (!ok) return "a Focus session is already running"
        Hw.setPending(ctx, item.id, System.currentTimeMillis(), minutes * 60_000L)
        KeepAlive.start(ctx)
        return null
    }

    /** Adds finished Focus minutes to their items. Returns a line to show, or null. */
    suspend fun sync(ctx: Context, repo: GameRepository): String? = Hw.syncFocus(ctx, repo.focusLogs.first())

    /** The chapter link as text: "P 6 Electromagnetic Induction (study)". */
    fun linkText(ctx: Context, item: HwItem): String {
        if (item.ch <= 0) return ""
        val t = termuxChapterTitle(ctx, item.subj, item.ch)?.take(28) ?: ""
        return "ch ${item.subj} ${item.ch}${if (t.isNotEmpty()) " $t" else ""} (${if (item.kind == "copy") "copy" else "study"})"
    }
}
