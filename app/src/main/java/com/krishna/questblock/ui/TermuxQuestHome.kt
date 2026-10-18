package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.krishna.questblock.data.QhApps
import com.krishna.questblock.data.QhSet
import com.krishna.questblock.data.QhWallFile
import com.krishna.questblock.data.QhState
import com.krishna.questblock.data.QhStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v212: Quest Home (the launcher theme) as terminal commands (qhome). They change the layout and look only; nothing
 * here opens the launcher screen. Same store as the launcher: [QhStore]. Switching the theme itself is theme set questhome.
 */
class TermuxQuestHome(
    private val ctx: Context,
    private val pick: suspend (TermuxSession, String) -> InstalledApp?,
    private val confirm: (TermuxSession, String, suspend () -> Unit) -> Unit,
) {
    private val widgetIds = listOf("clock", "status", "focus", "quests", "homework")

    suspend fun run(s: TermuxSession, a: TermuxArgs) {
        when (a.pos.getOrNull(1)?.lowercase()) {
            null, "status" -> status(s)
            "themes" -> themes(s)
            "theme" -> theme(s, a)
            "icons" -> icons(s, a)
            "widget", "widgets" -> widget(s, a)
            "pages", "layout", "ls" -> pages(s)
            "add" -> add(s, a)
            "rm" -> rm(s, a)
            "dock" -> dock(s, a)
            "folder", "folders" -> folder(s, a)
            "hide" -> hide(s, a, true)
            "unhide" -> hide(s, a, false)
            "hidden" -> hidden(s)
            "apps" -> apps(s, a)
            "default" -> default(s)
            "reset" -> reset(s)
            "set" -> setting(s, a)
            "wall" -> wall(s, a)
            "grid" -> grid(s, a)
            "icon" -> icon(s, a)
            "block" -> block(s, a)
            "focushome" -> flag(s, a, "focushome", "Focus home")
            "export" -> exportLayout(s)
            "import" -> importLayout(s, a)
            else -> s.err("usage: qhome status|themes|theme|icons|icon|widget|pages|add|rm|dock|folder|hide|unhide|hidden|apps|set|wall|grid|block|focushome|export|import|default|reset  (man qhome)")
        }
    }

    private suspend fun labels(): Map<String, String> =
        withContext(Dispatchers.IO) { QhApps.load(ctx) }.associate { it.pkg to it.label }

    private fun isDefaultHome(): Boolean = runCatching {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
    }.getOrDefault(false)

    private suspend fun status(s: TermuxSession) {
        val st = QhStore.load(ctx)
        val t = qhTheme(st.theme)
        val lab = labels()
        s.head("quest home")
        s.out("theme   {c}${t.name}{/}  {d}${t.tagline.tm()}{/}")
        s.out("icons   ${if (st.shape.isEmpty()) "theme default (${t.shape})" else st.shape}")
        s.out("launcher ${if (QuestHomeOn) "{g}on{/}" else "{y}off{/} {d}(theme set questhome){/}"}")
        if (QuestHomeOn) {
            if (isDefaultHome()) s.ok("QuestBlock is your Home app")
            else s.warn("another launcher is your Home app: {c}qhome default{/}")
        }
        s.out("pages   {c}${st.pages.size}{/}  (${st.pages.joinToString(" + ") { it.size.toString() }} items)   dock {c}${st.dock.size}{/}/${st.dockMax}   folders ${st.folders.size}   hidden ${st.hidden.size}")
        s.out("dock    ${if (st.dock.isEmpty()) "-" else st.dock.joinToString(", ") { (lab[it] ?: it).tm() }}")
        s.out("widgets ${widgetIds.joinToString(" ") { if (it in st.widgets) "{g}$it{/}" else "{d}$it{/}" }}")
        s.out("wall    {c}${st.wall}{/}  dim ${st.dim}%  blur ${st.blur}   grid ${st.cols}x  icons ${st.iconSize}%  labels ${if (st.labels) "on" else "off"}")
        s.out("blocking {c}${st.block}{/}  {d}(qhome block accessibility|launcher|both){/}   focus home ${if (st.focusHome) "{g}on{/}" else "off"}")
        s.dim("qhome set ls \u00b7 qhome themes \u00b7 qhome pages \u00b7 qhome apps \u00b7 qhome folder \u00b7 qhome widget")
    }

    private fun themes(s: TermuxSession) {
        val cur = QhStore.load(ctx).theme
        s.head("quest home themes")
        QH_THEMES.forEachIndexed { i, t ->
            s.out("{c}${(i + 1).toString().padStart(2)}{/} ${t.name.padEnd(14)} {d}${t.shape.padEnd(8)} ${t.tagline.tm()}{/}" + if (t.id == cur) "  {g}\u2713 active{/}" else "")
        }
        s.dim("qhome theme <n|name>")
    }

    private fun theme(s: TermuxSession, a: TermuxArgs) {
        val q = a.rest(2).trim()
        if (q.isEmpty()) { s.err("usage: qhome theme <n|name>  (see: qhome themes)"); return }
        val t = qhThemeFor(q)
        if (t == null) { s.err("no theme '${q.tm()}'. see: qhome themes"); return }
        QhStore.update(ctx) { it.copy(theme = t.id) }
        s.ok("theme: {c}${t.name}{/}  {d}${t.tagline.tm()}{/}")
        if (!QuestHomeOn) s.dim("the launcher itself is off: theme set questhome")
    }

    private fun icons(s: TermuxSession, a: TermuxArgs) {
        val v = a.pos.getOrNull(2)?.lowercase()
        if (v == null) { s.out("icon shape: ${QhStore.load(ctx).shape.ifEmpty { "theme default" }}   {d}${QH_SHAPES.joinToString(" ")} theme{/}"); return }
        if (v == "theme" || v == "default") { QhStore.update(ctx) { it.copy(shape = "") }; s.ok("icon shape: theme default"); return }
        if (v !in QH_SHAPES) { s.err("shape: ${QH_SHAPES.joinToString(", ")} or theme"); return }
        QhStore.update(ctx) { it.copy(shape = v) }
        s.ok("icon shape: $v")
    }

    private fun widget(s: TermuxSession, a: TermuxArgs) {
        val name = a.pos.getOrNull(2)?.lowercase()
        val st = QhStore.load(ctx)
        if (name == null) {
            widgetIds.forEach { s.out("${it.padEnd(9)} ${if (it in st.widgets) "{g}on{/}" else "off"}") }
            s.dim("qhome widget <${widgetIds.joinToString("|")}> on|off")
            return
        }
        if (name !in widgetIds) { s.err("widget: ${widgetIds.joinToString(", ")}"); return }
        val on = when (a.pos.getOrNull(3)?.lowercase()) { "on", "true", "1", "yes" -> true; "off", "false", "0", "no" -> false; null -> name !in st.widgets; else -> null }
        if (on == null) { s.err("usage: qhome widget $name on|off"); return }
        QhStore.update(ctx) { it.copy(widgets = if (on) it.widgets + name else it.widgets - name) }
        s.ok("$name widget ${if (on) "on" else "off"}")
    }

    private suspend fun pages(s: TermuxSession) {
        val st = QhStore.load(ctx)
        val lab = labels()
        s.head("home pages")
        st.pages.forEachIndexed { i, p ->
            s.out("{c}page ${i + 1}{/} {d}${p.size}/${QhStore.capacity(i)}{/}" + if (i == 0) "  {d}(widgets on top){/}" else "")
            if (p.isEmpty()) s.dim("   empty")
            p.forEachIndexed { j, item ->
                val text = if (item.startsWith("a:")) (lab[item.removePrefix("a:")] ?: item.removePrefix("a:")) else "[folder] " + (st.folder(item.removePrefix("f:"))?.name ?: "?")
                s.out("   ${(j + 1).toString().padStart(2)} ${text.tm()}")
            }
        }
        s.out("{b}DOCK{/} ${if (st.dock.isEmpty()) "-" else st.dock.joinToString(", ") { (lab[it] ?: it).tm() }}")
        st.folders.forEach { f -> s.out("{b}FOLDER{/} ${f.name.tm()}: ${f.apps.joinToString(", ") { (lab[it] ?: it).tm() }}") }
    }

    private suspend fun add(s: TermuxSession, a: TermuxArgs) {
        val app = pick(s, a.rest(2)) ?: return
        val st = QhStore.load(ctx)
        if (app.pkg in st.hidden) { s.err("${app.label.tm()} is hidden: qhome unhide ${app.label.tm()}"); return }
        if (st.onHome(app.pkg)) { s.warn("${app.label.tm()} is on the home pages already"); return }
        if (st.folderOf(app.pkg) != null) { s.warn("${app.label.tm()} is in a folder: qhome folder take"); return }
        val n = QhStore.update(ctx) { it.addHome(app.pkg) }
        if (n.onHome(app.pkg)) s.ok("${app.label.tm()} added to home, page ${n.pages.indexOfFirst { "a:${app.pkg}" in it } + 1}") else s.err("all pages are full")
    }

    private suspend fun rm(s: TermuxSession, a: TermuxArgs) {
        val app = pick(s, a.rest(2)) ?: return
        if (!QhStore.load(ctx).onHome(app.pkg)) { s.warn("${app.label.tm()} is not on the home pages"); return }
        QhStore.update(ctx) { it.removeHome(app.pkg) }
        s.ok("${app.label.tm()} left the home pages (it stays in the drawer)")
    }

    private suspend fun dock(s: TermuxSession, a: TermuxArgs) {
        val sub = a.pos.getOrNull(2)?.lowercase()
        if (sub == null) {
            val lab = labels()
            val st = QhStore.load(ctx)
            s.out("dock {c}${st.dock.size}{/}/${st.dockMax}: ${if (st.dock.isEmpty()) "-" else st.dock.joinToString(", ") { (lab[it] ?: it).tm() }}")
            s.dim("qhome dock add|rm <app>")
            return
        }
        val app = pick(s, a.rest(3)) ?: return
        when (sub) {
            "add" -> {
                val st = QhStore.load(ctx)
                if (app.pkg in st.dock) { s.warn("already in the dock"); return }
                if (st.dock.size >= st.dockMax) { s.err("the dock holds ${st.dockMax} apps: qhome dock rm <app> first"); return }
                QhStore.update(ctx) { it.addDock(app.pkg) }
                s.ok("${app.label.tm()} is in the dock")
            }
            "rm" -> {
                if (app.pkg !in QhStore.load(ctx).dock) { s.warn("not in the dock"); return }
                QhStore.update(ctx) { it.removeDock(app.pkg) }
                s.ok("${app.label.tm()} left the dock")
            }
            else -> s.err("usage: qhome dock add|rm <app>")
        }
    }

    private suspend fun folder(s: TermuxSession, a: TermuxArgs) {
        val sub = a.pos.getOrNull(2)?.lowercase()
        val lab = labels()
        if (sub == null) {
            val st = QhStore.load(ctx)
            if (st.folders.isEmpty()) s.dim("no folders. qhome folder new <name> <app> [app...]")
            st.folders.forEach { f -> s.out("{c}${f.name.tm()}{/}: ${f.apps.joinToString(", ") { (lab[it] ?: it).tm() }}") }
            return
        }
        when (sub) {
            "new" -> {
                val name = a.pos.getOrNull(3)
                if (name.isNullOrBlank()) { s.err("usage: qhome folder new <name> <app> [app...]"); return }
                val pkgs = ArrayList<String>()
                for (q in a.pos.drop(4)) { val app = pick(s, q) ?: return; pkgs.add(app.pkg) }
                if (pkgs.isEmpty()) { s.err("give at least one app for the folder"); return }
                QhStore.update(ctx) { it.newFolder(name, pkgs) }
                s.ok("folder ${name.tm()} with ${pkgs.size} app${if (pkgs.size == 1) "" else "s"}")
            }
            "rm", "delete" -> {
                val f = QhStore.load(ctx).folderByName(a.pos.getOrNull(3) ?: "")
                if (f == null) { s.err("no such folder (qhome folder)"); return }
                confirm(s, "delete folder ${f.name.tm()}? its apps go back to the home pages") {
                    QhStore.update(ctx) { it.deleteFolder(f.id) }
                    s.ok("folder deleted")
                }
            }
            "add" -> {
                val f = QhStore.load(ctx).folderByName(a.pos.getOrNull(3) ?: "")
                if (f == null) { s.err("no such folder (qhome folder)"); return }
                val app = pick(s, a.rest(4)) ?: return
                QhStore.update(ctx) { it.addToFolder(f.id, app.pkg) }
                s.ok("${app.label.tm()} is in ${f.name.tm()}")
            }
            "take" -> {
                val f = QhStore.load(ctx).folderByName(a.pos.getOrNull(3) ?: "")
                if (f == null) { s.err("no such folder (qhome folder)"); return }
                val app = pick(s, a.rest(4)) ?: return
                if (app.pkg !in f.apps) { s.warn("${app.label.tm()} is not in ${f.name.tm()}"); return }
                QhStore.update(ctx) { it.removeFromFolder(f.id, app.pkg) }
                s.ok("${app.label.tm()} is back on the home pages")
            }
            "rename" -> {
                val f = QhStore.load(ctx).folderByName(a.pos.getOrNull(3) ?: "")
                val nn = a.pos.getOrNull(4)
                if (f == null || nn.isNullOrBlank()) { s.err("usage: qhome folder rename <name> <new name>"); return }
                QhStore.update(ctx) { it.renameFolder(f.id, nn) }
                s.ok("renamed to ${nn.tm()}")
            }
            else -> s.err("usage: qhome folder [new|rm|add|take|rename] ...")
        }
    }

    private suspend fun hide(s: TermuxSession, a: TermuxArgs, hide: Boolean) {
        val app = pick(s, a.rest(2)) ?: return
        if (app.pkg == ctx.packageName && hide) { s.err("QuestBlock can't be hidden"); return }
        val st = QhStore.load(ctx)
        if (hide == (app.pkg in st.hidden)) { s.warn("${app.label.tm()} is already ${if (hide) "hidden" else "visible"}"); return }
        QhStore.update(ctx) { if (hide) it.hide(app.pkg) else it.unhide(app.pkg) }
        s.ok("${app.label.tm()} ${if (hide) "hidden from the home pages and the drawer" else "visible again"}")
    }

    private suspend fun hidden(s: TermuxSession) {
        val st = QhStore.load(ctx)
        val lab = labels()
        s.head("hidden apps")
        if (st.hidden.isEmpty()) s.dim("none")
        st.hidden.forEach { s.out((lab[it] ?: it).tm()) }
    }

    private suspend fun apps(s: TermuxSession, a: TermuxArgs) {
        val q = a.rest(2).trim().lowercase()
        val st = QhStore.load(ctx)
        val all = withContext(Dispatchers.IO) { QhApps.load(ctx) }.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
        s.head("apps")
        all.take(50).forEach { app ->
            val where = when {
                app.pkg in st.hidden -> "{d}hidden{/}"
                st.folderOf(app.pkg) != null -> "folder ${st.folderOf(app.pkg)?.name?.tm()}"
                st.onHome(app.pkg) -> "{g}home p${st.pages.indexOfFirst { "a:${app.pkg}" in it } + 1}{/}"
                app.pkg in st.dock -> "{c}dock{/}"
                else -> "{d}drawer{/}"
            }
            s.out("${app.label.tm().padEnd(22).take(22)} $where")
        }
        if (all.size > 50) s.dim("\u2026 ${all.size - 50} more: qhome apps <text>")
    }

    private fun default(s: TermuxSession) {
        if (!QuestHomeOn) { s.warn("Quest Home is off: theme set questhome first"); return }
        s.step("opening Android's own Home app page. pick QuestBlock there")
        qhPickHomeApp(ctx)
    }

    // ── v213 ──
    private fun setting(s: TermuxSession, a: TermuxArgs) {
        val k = a.pos.getOrNull(2)?.lowercase()
        val st = QhStore.load(ctx)
        if (k == null || k == "ls") {
            s.head("quest home settings")
            QhSet.KEYS.forEach { key -> s.out("{c}${key.padEnd(13)}{/} ${(QhSet.get(st, key) ?: "").padEnd(14)} {d}${QhSet.help(key).tm()}{/}") }
            s.dim("qhome set <key> <value>")
            return
        }
        val v = a.pos.getOrNull(3)
        if (v == null) { s.out("$k = ${QhSet.get(st, k) ?: "?"}   {d}${if (k in QhSet.KEYS) QhSet.help(k).tm() else "see: qhome set ls"}{/}"); return }
        if (k == "wall" && v.lowercase() == "picture" && !QhWallFile.exists(ctx)) {
            s.err("no own picture yet. pick one once in the launcher settings page (Look > My picture), then: qhome wall picture"); return
        }
        var bad = false
        val n = QhSet.set(st, k, v) { s.err(it.tm()); bad = true }
        if (n == null || bad) return
        QhStore.save(ctx, n)
        s.ok("$k = ${QhSet.get(n, k)}")
    }

    private fun wall(s: TermuxSession, a: TermuxArgs) {
        val st = QhStore.load(ctx)
        val v = a.pos.getOrNull(2)
        if (v == null) {
            s.out("wall {c}${st.wall}{/}   dim ${st.dim}%   blur ${st.blur}   colour ${QhSet.get(st, "wallcolor")}   own picture ${if (QhWallFile.exists(ctx)) "set" else "none"}")
            s.dim("qhome wall <${QhSet.WALLS.joinToString("|")}>   qhome wall dim <0-80>   qhome wall blur <0-100>   qhome wall color <#RRGGBB>")
            return
        }
        when (v.lowercase()) {
            "dim", "blur", "color", "colour" -> {
                val key = if (v.lowercase().startsWith("colo")) "wallcolor" else v.lowercase()
                val x = a.pos.getOrNull(3)
                if (x == null) { s.err("usage: qhome wall $v <value>"); return }
                var bad = false
                val n = QhSet.set(st, key, x) { s.err(it.tm()); bad = true }
                if (n != null && !bad) { QhStore.save(ctx, n); s.ok("$key = ${QhSet.get(n, key)}") }
            }
            else -> {
                if (v.lowercase() == "picture" && !QhWallFile.exists(ctx)) { s.err("no own picture yet. pick one once in the launcher settings page (Look > My picture)"); return }
                var bad = false
                val n = QhSet.set(st, "wall", v) { s.err(it.tm()); bad = true }
                if (n != null && !bad) { QhStore.save(ctx, n); s.ok("wall = ${n.wall}") }
            }
        }
    }

    private fun grid(s: TermuxSession, a: TermuxArgs) {
        val st = QhStore.load(ctx)
        val v = a.pos.getOrNull(2)
        if (v == null) { s.out("grid {c}${st.cols}{/} columns   dock ${st.dockMax}   icons ${st.iconSize}%   labels ${if (st.labels) "on" else "off"} (${st.labelLines} line${if (st.labelLines == 1) "" else "s"})"); s.dim("qhome grid <4-6>   (qhome set dock|iconsize|labels|lines)"); return }
        val cols = v.substringBefore('x').toIntOrNull()
        if (cols == null || cols !in 4..6) { s.err("columns: 4 to 6 (qhome grid 5)"); return }
        QhStore.update(ctx) { it.copy(cols = cols) }
        s.ok("home and drawer grid: $cols columns")
    }

    private suspend fun icon(s: TermuxSession, a: TermuxArgs) {
        // qhome icon <app> label <text> | reset
        val sub = a.pos.getOrNull(2)
        if (sub == null) { s.err("usage: qhome icon <app> label <text> | reset   (other icon options: qhome icons, qhome grid, qhome set)"); return }
        val app = pick(s, sub) ?: return
        when (a.pos.getOrNull(3)?.lowercase()) {
            "label" -> {
                val t = a.rest(4).trim()
                if (t.isEmpty()) { s.err("usage: qhome icon ${app.label.tm()} label <text>"); return }
                QhStore.update(ctx) { it.copy(appLabels = it.appLabels + (app.pkg to t.take(24))) }
                s.ok("${app.label.tm()} is now called ${t.take(24).tm()} on the launcher")
            }
            "reset" -> {
                QhStore.update(ctx) { it.copy(appLabels = it.appLabels - app.pkg) }
                s.ok("${app.label.tm()} is back to its own name")
            }
            "image" -> s.warn("icon pictures and icon packs are not in this version yet")
            else -> s.err("usage: qhome icon <app> label <text> | reset")
        }
    }

    private fun block(s: TermuxSession, a: TermuxArgs) {
        val v = a.pos.getOrNull(2)?.lowercase()
        val st = QhStore.load(ctx)
        if (v == null) {
            s.out("blocking {c}${st.block}{/}")
            s.dim("accessibility: strong, catches every way of opening an app (needs QuestBlock accessibility)")
            s.dim("launcher: only taps inside Quest Home open the lock page first; notifications, recents, other launchers and links stay open")
            s.dim("both: the launcher goes straight to the lock page and accessibility still covers the rest")
            return
        }
        if (v !in QhSet.BLOCKS) { s.err("blocking: ${QhSet.BLOCKS.joinToString(", ")}"); return }
        QhStore.update(ctx) { it.copy(block = v) }
        s.ok("blocking mode: $v")
    }

    private fun flag(s: TermuxSession, a: TermuxArgs, key: String, name: String) {
        val st = QhStore.load(ctx)
        val v = a.pos.getOrNull(2)
        if (v == null) { s.out("$name ${QhSet.get(st, key)}"); return }
        var bad = false
        val n = QhSet.set(st, key, v) { s.err(it.tm()); bad = true }
        if (n != null && !bad) { QhStore.save(ctx, n); s.ok("$name ${QhSet.get(n, key)}") }
    }

    private fun exportLayout(s: TermuxSession) {
        s.head("quest home export")
        s.out(android.util.Base64.encodeToString(QhStore.exportJson(ctx).toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE))
        s.dim("copy this one line. restore it with: qhome import <the line>   (the own wall picture is a file and is not in it)")
    }

    private fun importLayout(s: TermuxSession, a: TermuxArgs) {
        val t = a.rest(2).trim()
        if (t.isEmpty()) { s.err("usage: qhome import <text from qhome export>"); return }
        confirm(s, "replace the whole launcher layout and settings with this text?") {
            val json = runCatching { String(android.util.Base64.decode(t, android.util.Base64.URL_SAFE)) }.getOrNull()
            if (json != null && QhStore.importJson(ctx, json)) s.ok("layout imported") else s.err("that is not a Quest Home export")
        }
    }

    private fun reset(s: TermuxSession) {
        confirm(s, "reset the launcher layout (pages, dock, folders, hidden apps, widgets, theme)?") {
            QhStore.reset(ctx)
            s.ok("layout reset. the dock is filled again next time the launcher opens")
        }
    }
}
