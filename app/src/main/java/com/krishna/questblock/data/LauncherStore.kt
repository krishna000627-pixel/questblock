package com.krishna.questblock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class QhApp(val pkg: String, val label: String)

class QhFolder(val id: String, val name: String, val apps: List<String>)

/** v214: an own theme. wall = SVG path data (100 x 220 canvas) drawn as one layer, empty for none. */
class QhCustomTheme(
    val id: String, val name: String, val top: Long, val bottom: Long, val accent: Long, val dark: Boolean,
    val shape: String, val wall: String,
)

/**
 * v212: Quest Home (the launcher theme). Everything the launcher remembers: theme, home pages, dock, folders, hidden
 * apps, icon shape and which widgets show. Stored on the phone in its own preferences file. Items on a page are
 * "a:<package>" for an app and "f:<id>" for a folder. The functions on [QhState] return a changed copy.
 */
data class QhState(
    val theme: String = "dungeon",
    val pages: List<List<String>> = listOf(emptyList()),
    val dock: List<String> = emptyList(),
    val folders: List<QhFolder> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val shape: String = "",
    val widgets: Set<String> = setOf("clock", "status", "focus", "quests"),
    val seeded: Boolean = false,
    // v213: wall, grid, icons, drawer, gestures, hiding and blocking mode
    val wall: String = "svg",                 // svg | phone | phonesvg | color | picture
    val dim: Int = 0,                         // 0..80 percent
    val blur: Int = 0,                        // 0..100 (Android 12+ only)
    val wallColor: Long = 0xFF101018,
    val cols: Int = 4,                        // 4..6
    val dockMax: Int = 4,                     // 4..6
    val iconSize: Int = 100,                  // percent, 70..130
    val labels: Boolean = true,
    val labelLines: Int = 1,                  // 1..2
    val tile: Boolean = true,
    val azStrip: Boolean = true,
    val searchHidden: Boolean = false,        // true = hidden apps also show up in drawer search
    val hiddenBadge: Boolean = false,
    val focusHome: Boolean = false,           // during Focus hide every app that was not picked
    val lockedHide: Boolean = false,          // locked apps are hidden until unlocked
    val block: String = "both",               // accessibility | launcher | both
    val gDouble: String = "lock",
    val gLong: String = "settings",
    val gUp: String = "drawer",
    val gDown: String = "search",
    val appLabels: Map<String, String> = emptyMap(),
    // v214
    val maxPages: Int = 6, val rows: Int = 0, val transition: String = "slide", val parallax: Boolean = true, val animate: Boolean = true,
    val iconSrc: String = "system",           // system | glyph | pack | none
    val iconPack: String = "",
    val appIcons: Map<String, String> = emptyMap(),     // emoji:X | letters:XY | image | pack
    val appShapes: Map<String, String> = emptyMap(),
    val appColors: Map<String, Long> = emptyMap(),
    val revealWord: String = "", val revealCost: Int = 0, val revealGesture: Boolean = false,
    val hideRules: Set<String> = emptySet(),            // fun-study | fun-quiet | fun-exam
    val pendingUnhide: Map<String, Long> = emptyMap(),
    val watcher: Boolean = false,
    val sheetUnlock: Boolean = true,
    val drawerList: Boolean = false, val sort: String = "az", val recentRow: Boolean = false, val mostRow: Boolean = false,
    val g2Up: String = "none", val g2Down: String = "none",
    val widgetOrder: List<String> = listOf("clock", "status", "focus", "quests", "homework", "quickadd"),
    val clockScale: Int = 100, val questsCount: Int = 3, val hwCount: Int = 3, val focusPreset: Int = 25,
    val nightTheme: String = "", val nightFrom: Int = 21, val nightTo: Int = 6,
    val customThemes: List<QhCustomTheme> = emptyList(),
    val dots: String = "dots", val folderStyle: String = "grid", val dockStyle: String = "bar",
    val usageBadge: Boolean = false, val studyFirst: Boolean = false, val notifDots: Boolean = false,
    val searchExtra: Boolean = true, val searchContacts: Boolean = false,
    val hostWidgets: List<Int> = emptyList(),
) {
    /** How many items one page holds (the first page also carries the widgets). */
    fun capacity(page: Int): Int = when {
        rows <= 0 -> if (page == 0) 12 else 20
        page == 0 -> cols * maxOf(rows - 2, 1)
        else -> cols * rows
    }

    /** Puts [item] before [target], on the target's page (when that page has room). */
    fun moveBefore(item: String, target: String): QhState {
        if (item == target) return this
        val at = pages.indexOfFirst { target in it }
        if (at < 0) return this
        val from = pages.indexOfFirst { item in it }
        if (from != at && pages[at].size >= capacity(at)) return this
        val pgs = withoutOnPages(item).map { it.toMutableList() }.toMutableList()
        val i = pgs[at].indexOf(target).coerceAtLeast(0)
        pgs[at].add(i, item)
        return copy(pages = trimEmptyPages(pgs))
    }

    /** Puts [item] (from a page, the dock or a folder) at the end of page [page], when there is room. */
    fun moveToPageEnd(item: String, page: Int): QhState {
        val to = page.coerceIn(0, maxPages - 1)
        val pgs = withoutOnPages(item).map { it.toMutableList() }.toMutableList()
        while (pgs.size <= to) pgs.add(mutableListOf())
        if (pgs[to].size >= capacity(to)) return this
        pgs[to].add(item)
        return copy(pages = trimEmptyPages(pgs))
    }

    /** Dropping app [a] on app [b] makes a folder with both (they leave pages and dock). */
    fun mergeApps(a: String, b: String): QhState {
        if (a == b) return this
        val base = copy(dock = dock.filter { it != a && it != b })
        return base.newFolder("Folder", listOf(b, a))
    }

    /** Dock from a page (or the dock stays as it is when it is full). */
    fun toDock(pkg: String): QhState = if (pkg in dock) this else if (dock.size >= dockMax) this else removeHome(pkg).addDock(pkg)

    /** Hidden apps whose waiting time is over come back. */
    fun applyPendingUnhide(now: Long): QhState {
        val due = pendingUnhide.filterValues { it <= now }.keys
        return if (due.isEmpty()) this else copy(hidden = hidden - due, pendingUnhide = pendingUnhide - due)
    }

    fun labelOf(pkg: String, fallback: String): String = appLabels[pkg]?.takeIf { it.isNotBlank() } ?: fallback
    fun onHome(pkg: String): Boolean = pages.any { "a:$pkg" in it }
    fun folderOf(pkg: String): QhFolder? = folders.firstOrNull { pkg in it.apps }
    fun placedAnywhere(pkg: String): Boolean = onHome(pkg) || pkg in dock || folderOf(pkg) != null
    fun folder(id: String): QhFolder? = folders.firstOrNull { it.id == id }
    fun folderByName(name: String): QhFolder? =
        folders.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: folders.firstOrNull { it.name.lowercase().startsWith(name.lowercase()) }

    private fun withoutOnPages(item: String): List<List<String>> = pages.map { p -> p.filter { it != item } }

    /** Puts an app on the first page that has room (a new page when all are full, up to [QhStore.MAX_PAGES]). */
    fun addHome(pkg: String): QhState {
        if (onHome(pkg) || folderOf(pkg) != null || pkg in hidden) return this
        return placeItem("a:$pkg")
    }

    private fun placeItem(item: String): QhState {
        val pgs = pages.map { it.toMutableList() }.toMutableList()
        for (i in pgs.indices) {
            if (pgs[i].size < capacity(i)) { pgs[i].add(item); return copy(pages = pgs) }
        }
        if (pgs.size < maxPages) { pgs.add(mutableListOf(item)); return copy(pages = pgs) }
        return this
    }

    fun removeHome(pkg: String): QhState = copy(pages = trimEmptyPages(withoutOnPages("a:$pkg")))

    private fun trimEmptyPages(p: List<List<String>>): List<List<String>> {
        val out = p.toMutableList()
        while (out.size > 1 && out.last().isEmpty()) out.removeAt(out.size - 1)
        return if (out.isEmpty()) listOf(emptyList()) else out
    }

    /** Swaps an item with its neighbour on the same page. delta -1 = earlier, +1 = later. */
    fun moveItem(item: String, delta: Int): QhState {
        val pgs = pages.map { it.toMutableList() }
        for (p in pgs) {
            val i = p.indexOf(item)
            if (i < 0) continue
            val j = i + delta
            if (j < 0 || j >= p.size) return this
            val t = p[i]; p[i] = p[j]; p[j] = t
            return copy(pages = pgs)
        }
        return this
    }

    /** Moves an item to the page before (-1) or after (+1) its own, when that page has room. */
    fun moveToPage(item: String, delta: Int): QhState {
        val at = pages.indexOfFirst { item in it }
        if (at < 0) return this
        val to = at + delta
        if (to < 0 || to >= maxPages) return this
        val pgs = withoutOnPages(item).map { it.toMutableList() }.toMutableList()
        while (pgs.size <= to) pgs.add(mutableListOf())
        if (pgs[to].size >= capacity(to)) return this
        pgs[to].add(item)
        return copy(pages = trimEmptyPages(pgs))
    }

    fun addDock(pkg: String): QhState = if (pkg in dock || dock.size >= dockMax) this else copy(dock = dock + pkg)
    fun removeDock(pkg: String): QhState = copy(dock = dock.filter { it != pkg })

    fun hide(pkg: String): QhState = copy(
        hidden = hidden + pkg, dock = dock.filter { it != pkg },
        pages = trimEmptyPages(withoutOnPages("a:$pkg")),
        folders = folders.map { QhFolder(it.id, it.name, it.apps.filter { a -> a != pkg }) },
    ).dropEmptyFolders()

    fun unhide(pkg: String): QhState = copy(hidden = hidden - pkg)

    private fun dropEmptyFolders(): QhState {
        val gone = folders.filter { it.apps.isEmpty() }.map { "f:${it.id}" }
        if (gone.isEmpty()) return this
        return copy(folders = folders.filter { it.apps.isNotEmpty() }, pages = trimEmptyPages(pages.map { p -> p.filter { it !in gone } }))
    }

    // ── folders ──────────────────────────────────────────────────────────
    fun newFolder(name: String, apps: List<String>): QhState {
        val id = "f" + java.lang.Long.toString(System.nanoTime() and 0xFFFFFFFL, 36)
        val f = QhFolder(id, name.trim().ifEmpty { "Folder" }, apps.distinct().filter { it !in hidden })
        val base = copy(
            folders = folders + f,
            pages = pages.map { p -> p.filter { !(it.startsWith("a:") && it.removePrefix("a:") in f.apps) } },
        )
        return base.placeItem("f:$id")
    }

    fun addToFolder(folderId: String, pkg: String): QhState {
        val f = folder(folderId) ?: return this
        if (pkg in f.apps) return this
        val cleaned = copy(folders = folders.map { fl -> if (fl.id == folderId) QhFolder(fl.id, fl.name, fl.apps + pkg) else QhFolder(fl.id, fl.name, fl.apps.filter { it != pkg }) },
            pages = withoutOnPages("a:$pkg"))
        return cleaned.dropEmptyFolders()
    }

    fun removeFromFolder(folderId: String, pkg: String): QhState {
        val f = folder(folderId) ?: return this
        if (pkg !in f.apps) return this
        val next = copy(folders = folders.map { fl -> if (fl.id == folderId) QhFolder(fl.id, fl.name, fl.apps.filter { it != pkg }) else fl })
            .dropEmptyFolders()
        return next.addHome(pkg)
    }

    fun renameFolder(folderId: String, name: String): QhState =
        copy(folders = folders.map { if (it.id == folderId) QhFolder(it.id, name.trim().ifEmpty { it.name }, it.apps) else it })

    /** Deleting a folder puts its apps back on the home pages. */
    fun deleteFolder(folderId: String): QhState {
        val f = folder(folderId) ?: return this
        var s = copy(folders = folders.filter { it.id != folderId }, pages = trimEmptyPages(withoutOnPages("f:$folderId")))
        f.apps.forEach { s = s.addHome(it) }
        return s
    }

    fun cleanup(installed: Set<String>): QhState {
        val keep: (String) -> Boolean = { it in installed }
        return copy(
            pages = trimEmptyPages(pages.map { p -> p.filter { !it.startsWith("a:") || keep(it.removePrefix("a:")) } }),
            dock = dock.filter(keep),
            folders = folders.map { QhFolder(it.id, it.name, it.apps.filter(keep)) },
            hidden = hidden.filter(keep).toSet(),
        ).dropEmptyFolders()
    }
}

object QhStore {
    const val COLS = 4
    const val MAX_PAGES = 6
    const val DOCK_MAX = 4

    /** How many items a page holds. The first page also carries the widgets. */
    fun capacity(page: Int): Int = if (page == 0) 12 else 20

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_launcher", Context.MODE_PRIVATE)

    /** Custom themes of the last loaded state (the theme code reads this to resolve ids like c1). */
    @Volatile var customCache: List<QhCustomTheme> = emptyList()

    private fun strMap(j: JSONObject?): Map<String, String> = if (j == null) emptyMap() else j.keys().asSequence().associateWith { k -> j.optString(k) }

    private fun parseThemes(a: JSONArray?): List<QhCustomTheme> {
        if (a == null) return emptyList()
        return List(a.length()) { i ->
            val t = a.getJSONObject(i)
            QhCustomTheme(
                t.getString("id"), t.optString("name", "Mine"), t.optString("top").toLongOrNull() ?: 0xFF101018, t.optString("bottom").toLongOrNull() ?: 0xFF000000,
                t.optString("accent").toLongOrNull() ?: 0xFF7C5CFF, t.optBoolean("dark", true), t.optString("shape", "squircle"), t.optString("wall", ""),
            )
        }.take(10)
    }

    private fun strList(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    fun load(c: Context): QhState = runCatching {
        val o = JSONObject(sp(c).getString("state", "{}") ?: "{}")
        val pj = o.optJSONArray("pages")
        val pages = ArrayList<List<String>>()
        if (pj != null) for (i in 0 until pj.length()) pages.add(strList(pj.getJSONArray(i)))
        if (pages.isEmpty()) pages.add(emptyList())
        val fj = o.optJSONArray("folders")
        val folders = ArrayList<QhFolder>()
        if (fj != null) for (i in 0 until fj.length()) {
            val f = fj.getJSONObject(i)
            folders.add(QhFolder(f.getString("id"), f.getString("name"), strList(f.optJSONArray("apps"))))
        }
        QhState(
            theme = o.optString("theme", "dungeon"), pages = pages, dock = strList(o.optJSONArray("dock")), folders = folders,
            hidden = strList(o.optJSONArray("hidden")).toSet(), shape = o.optString("shape", ""),
            widgets = if (o.has("widgets")) strList(o.optJSONArray("widgets")).toSet() else QhState().widgets,
            seeded = o.optBoolean("seeded"),
            wall = o.optString("wall", "svg"), dim = o.optInt("dim", 0).coerceIn(0, 80), blur = o.optInt("blur", 0).coerceIn(0, 100),
            wallColor = o.optLong("wallColor", 0xFF101018),
            cols = o.optInt("cols", 4).coerceIn(4, 6), dockMax = o.optInt("dockMax", 4).coerceIn(4, 6),
            iconSize = o.optInt("iconSize", 100).coerceIn(70, 130),
            labels = o.optBoolean("labels", true), labelLines = o.optInt("labelLines", 1).coerceIn(1, 2), tile = o.optBoolean("tile", true),
            azStrip = o.optBoolean("azStrip", true), searchHidden = o.optBoolean("searchHidden", false),
            hiddenBadge = o.optBoolean("hiddenBadge", false), focusHome = o.optBoolean("focusHome", false),
            lockedHide = o.optBoolean("lockedHide", false), block = o.optString("block", "both"),
            gDouble = o.optString("gDouble", "lock"), gLong = o.optString("gLong", "settings"),
            gUp = o.optString("gUp", "drawer"), gDown = o.optString("gDown", "search"),
            appLabels = o.optJSONObject("appLabels")?.let { j -> j.keys().asSequence().associateWith { k -> j.optString(k) } } ?: emptyMap(),
            maxPages = o.optInt("maxPages", 6).coerceIn(1, 8), rows = o.optInt("rows", 0).coerceIn(0, 8),
            transition = o.optString("transition", "slide"), parallax = o.optBoolean("parallax", true), animate = o.optBoolean("animate", true),
            iconSrc = o.optString("iconSrc", "system"), iconPack = o.optString("iconPack", ""),
            appIcons = strMap(o.optJSONObject("appIcons")), appShapes = strMap(o.optJSONObject("appShapes")),
            appColors = strMap(o.optJSONObject("appColors")).mapValues { it.value.toLongOrNull() ?: 0L },
            revealWord = o.optString("revealWord", ""), revealCost = o.optInt("revealCost", 0).coerceIn(0, 500), revealGesture = o.optBoolean("revealGesture", false),
            hideRules = strList(o.optJSONArray("hideRules")).toSet(),
            pendingUnhide = strMap(o.optJSONObject("pendingUnhide")).mapValues { it.value.toLongOrNull() ?: 0L },
            watcher = o.optBoolean("watcher", false), sheetUnlock = o.optBoolean("sheetUnlock", true),
            drawerList = o.optBoolean("drawerList", false), sort = o.optString("sort", "az"),
            recentRow = o.optBoolean("recentRow", false), mostRow = o.optBoolean("mostRow", false),
            g2Up = o.optString("g2Up", "none"), g2Down = o.optString("g2Down", "none"),
            widgetOrder = if (o.has("widgetOrder")) strList(o.optJSONArray("widgetOrder")) else QhState().widgetOrder,
            clockScale = o.optInt("clockScale", 100).coerceIn(60, 140), questsCount = o.optInt("questsCount", 3).coerceIn(1, 6),
            hwCount = o.optInt("hwCount", 3).coerceIn(1, 6), focusPreset = o.optInt("focusPreset", 25).coerceIn(5, 180),
            nightTheme = o.optString("nightTheme", ""), nightFrom = o.optInt("nightFrom", 21).coerceIn(0, 23), nightTo = o.optInt("nightTo", 6).coerceIn(0, 23),
            customThemes = parseThemes(o.optJSONArray("customThemes")),
            dots = o.optString("dots", "dots"), folderStyle = o.optString("folderStyle", "grid"), dockStyle = o.optString("dockStyle", "bar"),
            usageBadge = o.optBoolean("usageBadge", false), studyFirst = o.optBoolean("studyFirst", false), notifDots = o.optBoolean("notifDots", false),
            searchExtra = o.optBoolean("searchExtra", true), searchContacts = o.optBoolean("searchContacts", false),
            hostWidgets = o.optJSONArray("hostWidgets")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList(),
        ).also { customCache = it.customThemes }
    }.getOrDefault(QhState())

    fun save(c: Context, s: QhState) {
        val o = JSONObject()
        o.put("theme", s.theme)
        o.put("pages", JSONArray(s.pages.map { JSONArray(it) }))
        o.put("dock", JSONArray(s.dock))
        o.put("folders", JSONArray(s.folders.map { JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps)) }))
        o.put("hidden", JSONArray(s.hidden.toList()))
        o.put("shape", s.shape)
        o.put("widgets", JSONArray(s.widgets.toList()))
        o.put("seeded", s.seeded)
        o.put("wall", s.wall); o.put("dim", s.dim); o.put("blur", s.blur); o.put("wallColor", s.wallColor)
        o.put("cols", s.cols); o.put("dockMax", s.dockMax); o.put("iconSize", s.iconSize)
        o.put("labels", s.labels); o.put("labelLines", s.labelLines); o.put("tile", s.tile)
        o.put("azStrip", s.azStrip); o.put("searchHidden", s.searchHidden); o.put("hiddenBadge", s.hiddenBadge)
        o.put("focusHome", s.focusHome); o.put("lockedHide", s.lockedHide); o.put("block", s.block)
        o.put("gDouble", s.gDouble); o.put("gLong", s.gLong); o.put("gUp", s.gUp); o.put("gDown", s.gDown)
        o.put("appLabels", JSONObject().also { j -> s.appLabels.forEach { (k, v) -> j.put(k, v) } })
        o.put("maxPages", s.maxPages); o.put("rows", s.rows); o.put("transition", s.transition); o.put("parallax", s.parallax); o.put("animate", s.animate)
        o.put("iconSrc", s.iconSrc); o.put("iconPack", s.iconPack)
        o.put("appIcons", JSONObject().also { j -> s.appIcons.forEach { (k, v) -> j.put(k, v) } })
        o.put("appShapes", JSONObject().also { j -> s.appShapes.forEach { (k, v) -> j.put(k, v) } })
        o.put("appColors", JSONObject().also { j -> s.appColors.forEach { (k, v) -> j.put(k, v.toString()) } })
        o.put("revealWord", s.revealWord); o.put("revealCost", s.revealCost); o.put("revealGesture", s.revealGesture)
        o.put("hideRules", JSONArray(s.hideRules.toList()))
        o.put("pendingUnhide", JSONObject().also { j -> s.pendingUnhide.forEach { (k, v) -> j.put(k, v.toString()) } })
        o.put("watcher", s.watcher); o.put("sheetUnlock", s.sheetUnlock)
        o.put("drawerList", s.drawerList); o.put("sort", s.sort); o.put("recentRow", s.recentRow); o.put("mostRow", s.mostRow)
        o.put("g2Up", s.g2Up); o.put("g2Down", s.g2Down)
        o.put("widgetOrder", JSONArray(s.widgetOrder))
        o.put("clockScale", s.clockScale); o.put("questsCount", s.questsCount); o.put("hwCount", s.hwCount); o.put("focusPreset", s.focusPreset)
        o.put("nightTheme", s.nightTheme); o.put("nightFrom", s.nightFrom); o.put("nightTo", s.nightTo)
        o.put("customThemes", JSONArray(s.customThemes.map { t ->
            JSONObject().put("id", t.id).put("name", t.name).put("top", t.top.toString()).put("bottom", t.bottom.toString())
                .put("accent", t.accent.toString()).put("dark", t.dark).put("shape", t.shape).put("wall", t.wall)
        }))
        o.put("dots", s.dots); o.put("folderStyle", s.folderStyle); o.put("dockStyle", s.dockStyle)
        o.put("usageBadge", s.usageBadge); o.put("studyFirst", s.studyFirst); o.put("notifDots", s.notifDots)
        o.put("searchExtra", s.searchExtra); o.put("searchContacts", s.searchContacts)
        o.put("hostWidgets", JSONArray(s.hostWidgets))
        customCache = s.customThemes
        sp(c).edit().putString("state", o.toString()).apply()
    }

    fun update(c: Context, f: (QhState) -> QhState): QhState {
        val n = f(load(c))
        save(c, n)
        return n
    }

    fun reset(c: Context) { sp(c).edit().remove("state").apply() }

    /** v213: the whole layout and settings as text (for backup and qhome export). The own wall picture is a file and is not in it. */
    fun exportJson(c: Context): String = sp(c).getString("state", null) ?: JSONObject().toString()

    /** Returns false when the text is not a launcher layout. */
    fun importJson(c: Context, text: String): Boolean {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (!o.has("pages") && !o.has("theme")) return false
        sp(c).edit().putString("state", o.toString()).apply()
        return true
    }

    /** First run: the dock gets QuestBlock and the phone's own dialer, messages, browser and camera. */
    fun seedIfNeeded(c: Context, installed: Set<String>): QhState {
        val s = load(c)
        if (s.seeded) return s
        val dock = (listOf(c.packageName) + QhApps.defaultDock(c)).filter { it in installed || it == c.packageName }.distinct().take(DOCK_MAX)
        val n = s.copy(dock = dock, seeded = true)
        save(c, n)
        return n
    }
}

/** v213: the picture chosen as a wall is copied here once, so it keeps working when the original goes away. */
object QhUse {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("qb_launcher", Context.MODE_PRIVATE)
    private fun read(c: Context): JSONObject = runCatching { JSONObject(sp(c).getString("use", "{}") ?: "{}") }.getOrDefault(JSONObject())

    fun bump(c: Context, pkg: String) {
        val o = read(c)
        val e = o.optJSONObject(pkg) ?: JSONObject()
        e.put("n", e.optInt("n") + 1).put("t", System.currentTimeMillis())
        o.put(pkg, e)
        sp(c).edit().putString("use", o.toString()).apply()
    }

    fun counts(c: Context): Map<String, Int> = read(c).let { o -> o.keys().asSequence().associateWith { o.getJSONObject(it).optInt("n") } }
    fun lastUsed(c: Context): Map<String, Long> = read(c).let { o -> o.keys().asSequence().associateWith { o.getJSONObject(it).optLong("t") } }
}

object QhWallFile {
    fun file(c: Context): java.io.File = java.io.File(c.filesDir, "qh_wall.jpg")
    fun exists(c: Context): Boolean = file(c).let { it.exists() && it.length() > 0 }
    /** Reads the picked image, scales it down to at most 1600 px and saves it. Returns true when it worked. */
    fun import(c: Context, uri: Uri): Boolean = runCatching {
        val bmp = c.contentResolver.openInputStream(uri).use { android.graphics.BitmapFactory.decodeStream(it) } ?: return false
        val k = 1600f / maxOf(bmp.width, bmp.height).toFloat()
        val out = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
        file(c).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        true
    }.getOrDefault(false)
}

/** v213: every launcher setting by key, so the terminal (qhome set) and the settings page share one table. */
object QhSet {
    val WALLS = listOf("svg", "phone", "phonesvg", "color", "picture")
    val ACTIONS = listOf("lock", "drawer", "search", "settings", "questblock", "focus", "notifications", "none")
    val BLOCKS = listOf("accessibility", "launcher", "both")
    val KEYS = listOf("wall", "dim", "blur", "wallcolor", "cols", "dock", "iconsize", "labels", "lines", "tile", "az",
        "searchhidden", "hiddenbadge", "focushome", "lockedhide", "block", "g.double", "g.long", "g.up", "g.down",
        "g2.up", "g2.down", "pages", "rows", "transition", "parallax", "animate", "iconsrc", "list", "sort", "recentrow", "mostrow",
        "revealword", "revealcost", "revealgesture", "watcher", "sheet", "clockscale", "questscount", "hwcount", "focuspreset",
        "nighttheme", "nightfrom", "nightto", "dots", "folderstyle", "dockstyle", "usagebadge", "studyfirst", "notifdots", "searchextra", "searchcontacts")
    val SORTS = listOf("az", "za", "recent", "most")
    val TRANSITIONS = listOf("slide", "fade", "zoom", "cube")
    val ICON_SRC = listOf("system", "glyph", "pack", "none")
    val DOTS = listOf("dots", "bar", "number")
    val FOLDERS = listOf("grid", "stack")
    val DOCKS = listOf("bar", "floating", "none")
    val HIDE_RULES = listOf("fun-study", "fun-quiet", "fun-exam")

    fun get(s: QhState, k: String): String? = when (k) {
        "wall" -> s.wall; "dim" -> s.dim.toString(); "blur" -> s.blur.toString()
        "wallcolor" -> String.format("#%06X", s.wallColor and 0xFFFFFF)
        "cols" -> s.cols.toString(); "dock" -> s.dockMax.toString(); "iconsize" -> s.iconSize.toString()
        "labels" -> onOff(s.labels); "lines" -> s.labelLines.toString(); "tile" -> onOff(s.tile); "az" -> onOff(s.azStrip)
        "searchhidden" -> onOff(s.searchHidden); "hiddenbadge" -> onOff(s.hiddenBadge)
        "focushome" -> onOff(s.focusHome); "lockedhide" -> onOff(s.lockedHide); "block" -> s.block
        "g.double" -> s.gDouble; "g.long" -> s.gLong; "g.up" -> s.gUp; "g.down" -> s.gDown
        "g2.up" -> s.g2Up; "g2.down" -> s.g2Down; "pages" -> s.maxPages.toString(); "rows" -> s.rows.toString()
        "transition" -> s.transition; "parallax" -> onOff(s.parallax); "animate" -> onOff(s.animate); "iconsrc" -> s.iconSrc
        "list" -> onOff(s.drawerList); "sort" -> s.sort; "recentrow" -> onOff(s.recentRow); "mostrow" -> onOff(s.mostRow)
        "revealword" -> s.revealWord.ifEmpty { "off" }; "revealcost" -> s.revealCost.toString(); "revealgesture" -> onOff(s.revealGesture)
        "watcher" -> onOff(s.watcher); "sheet" -> onOff(s.sheetUnlock)
        "clockscale" -> s.clockScale.toString(); "questscount" -> s.questsCount.toString(); "hwcount" -> s.hwCount.toString(); "focuspreset" -> s.focusPreset.toString()
        "nighttheme" -> s.nightTheme.ifEmpty { "off" }; "nightfrom" -> s.nightFrom.toString(); "nightto" -> s.nightTo.toString()
        "dots" -> s.dots; "folderstyle" -> s.folderStyle; "dockstyle" -> s.dockStyle
        "usagebadge" -> onOff(s.usageBadge); "studyfirst" -> onOff(s.studyFirst); "notifdots" -> onOff(s.notifDots)
        "searchextra" -> onOff(s.searchExtra); "searchcontacts" -> onOff(s.searchContacts)
        else -> null
    }

    fun help(k: String): String = when (k) {
        "wall" -> "svg | phone | phonesvg | color | picture"
        "dim" -> "0-80 (percent, dark layer over the wall)"; "blur" -> "0-100 (Android 12+, needs phone wall)"
        "wallcolor" -> "#RRGGBB (for wall color)"; "cols" -> "4-6 columns"; "dock" -> "4-6 dock apps"
        "iconsize" -> "70-130 percent"; "labels" -> "on|off"; "lines" -> "1|2 label lines"; "tile" -> "on|off icon tile"
        "az" -> "on|off drawer A to Z strip"; "searchhidden" -> "on|off hidden apps show in drawer search"
        "hiddenbadge" -> "on|off hidden count on the apps button"; "focushome" -> "on|off hide unpicked apps during Focus"
        "lockedhide" -> "on|off hide locked apps until unlocked"; "block" -> "accessibility | launcher | both"
        "g.double", "g.long", "g.up", "g.down", "g2.up", "g2.down" -> QhSet.ACTIONS.joinToString(" | ")
        "pages" -> "1-8 home pages at most"; "rows" -> "0 (auto) or 3-8 rows"; "transition" -> TRANSITIONS.joinToString(" | ")
        "parallax", "animate", "list", "recentrow", "mostrow", "revealgesture", "watcher", "sheet", "usagebadge", "studyfirst", "notifdots", "searchextra", "searchcontacts" -> "on|off"
        "iconsrc" -> ICON_SRC.joinToString(" | ") + "  (pick the pack app: qhome pack)"; "sort" -> SORTS.joinToString(" | ")
        "revealword" -> "a word typed in drawer search that opens the hidden space, or off"; "revealcost" -> "0-500 coins to open the hidden space (0 = free)"
        "clockscale" -> "60-140 percent"; "questscount", "hwcount" -> "1-6 lines"; "focuspreset" -> "5-180 minutes for the one-tap Focus"
        "nighttheme" -> "a theme id (qhome themes) or off"; "nightfrom", "nightto" -> "hour 0-23"
        "dots" -> DOTS.joinToString(" | "); "folderstyle" -> FOLDERS.joinToString(" | "); "dockstyle" -> DOCKS.joinToString(" | ")
        else -> "?"
    }

    /** Returns the changed state, or null with an error text in [err]. */
    fun set(s: QhState, k: String, v: String, err: (String) -> Unit): QhState? {
        val low = v.trim().lowercase()
        fun bool(): Boolean? = when (low) { "on", "true", "1", "yes" -> true; "off", "false", "0", "no" -> false; else -> null }
        fun int(lo: Int, hi: Int): Int? = low.toIntOrNull()?.takeIf { it in lo..hi }
        fun bad(): QhState? { err("$k: ${help(k)}"); return null }
        return when (k) {
            "wall" -> if (low in WALLS) s.copy(wall = low) else bad()
            "dim" -> int(0, 80)?.let { s.copy(dim = it) } ?: bad()
            "blur" -> int(0, 100)?.let { s.copy(blur = it) } ?: bad()
            "wallcolor" -> runCatching { 0xFF000000L or low.removePrefix("#").toLong(16) }.getOrNull()?.takeIf { low.removePrefix("#").length == 6 }?.let { s.copy(wallColor = it) } ?: bad()
            "cols" -> int(4, 6)?.let { s.copy(cols = it) } ?: bad()
            "dock" -> int(4, 6)?.let { s.copy(dockMax = it) } ?: bad()
            "iconsize" -> int(70, 130)?.let { s.copy(iconSize = it) } ?: bad()
            "labels" -> bool()?.let { s.copy(labels = it) } ?: bad()
            "lines" -> int(1, 2)?.let { s.copy(labelLines = it) } ?: bad()
            "tile" -> bool()?.let { s.copy(tile = it) } ?: bad()
            "az" -> bool()?.let { s.copy(azStrip = it) } ?: bad()
            "searchhidden" -> bool()?.let { s.copy(searchHidden = it) } ?: bad()
            "hiddenbadge" -> bool()?.let { s.copy(hiddenBadge = it) } ?: bad()
            "focushome" -> bool()?.let { s.copy(focusHome = it) } ?: bad()
            "lockedhide" -> bool()?.let { s.copy(lockedHide = it) } ?: bad()
            "block" -> if (low in BLOCKS) s.copy(block = low) else bad()
            "g2.up" -> if (low in ACTIONS) s.copy(g2Up = low) else bad()
            "g2.down" -> if (low in ACTIONS) s.copy(g2Down = low) else bad()
            "pages" -> int(1, 8)?.let { s.copy(maxPages = it) } ?: bad()
            "rows" -> int(0, 8)?.takeIf { it == 0 || it >= 3 }?.let { s.copy(rows = it) } ?: bad()
            "transition" -> if (low in TRANSITIONS) s.copy(transition = low) else bad()
            "parallax" -> bool()?.let { s.copy(parallax = it) } ?: bad()
            "animate" -> bool()?.let { s.copy(animate = it) } ?: bad()
            "iconsrc" -> if (low in ICON_SRC) s.copy(iconSrc = low) else bad()
            "list" -> bool()?.let { s.copy(drawerList = it) } ?: bad()
            "sort" -> if (low in SORTS) s.copy(sort = low) else bad()
            "recentrow" -> bool()?.let { s.copy(recentRow = it) } ?: bad()
            "mostrow" -> bool()?.let { s.copy(mostRow = it) } ?: bad()
            "revealword" -> if (low == "off" || low.isEmpty()) s.copy(revealWord = "") else if (low.length in 3..20) s.copy(revealWord = low) else bad()
            "revealcost" -> int(0, 500)?.let { s.copy(revealCost = it) } ?: bad()
            "revealgesture" -> bool()?.let { s.copy(revealGesture = it) } ?: bad()
            "watcher" -> bool()?.let { s.copy(watcher = it) } ?: bad()
            "sheet" -> bool()?.let { s.copy(sheetUnlock = it) } ?: bad()
            "clockscale" -> int(60, 140)?.let { s.copy(clockScale = it) } ?: bad()
            "questscount" -> int(1, 6)?.let { s.copy(questsCount = it) } ?: bad()
            "hwcount" -> int(1, 6)?.let { s.copy(hwCount = it) } ?: bad()
            "focuspreset" -> int(5, 180)?.let { s.copy(focusPreset = it) } ?: bad()
            "nighttheme" -> if (low == "off") s.copy(nightTheme = "") else s.copy(nightTheme = low)
            "nightfrom" -> int(0, 23)?.let { s.copy(nightFrom = it) } ?: bad()
            "nightto" -> int(0, 23)?.let { s.copy(nightTo = it) } ?: bad()
            "dots" -> if (low in DOTS) s.copy(dots = low) else bad()
            "folderstyle" -> if (low in FOLDERS) s.copy(folderStyle = low) else bad()
            "dockstyle" -> if (low in DOCKS) s.copy(dockStyle = low) else bad()
            "usagebadge" -> bool()?.let { s.copy(usageBadge = it) } ?: bad()
            "studyfirst" -> bool()?.let { s.copy(studyFirst = it) } ?: bad()
            "notifdots" -> bool()?.let { s.copy(notifDots = it) } ?: bad()
            "searchextra" -> bool()?.let { s.copy(searchExtra = it) } ?: bad()
            "searchcontacts" -> bool()?.let { s.copy(searchContacts = it) } ?: bad()
            "g.double" -> if (low in ACTIONS) s.copy(gDouble = low) else bad()
            "g.long" -> if (low in ACTIONS) s.copy(gLong = low) else bad()
            "g.up" -> if (low in ACTIONS) s.copy(gUp = low) else bad()
            "g.down" -> if (low in ACTIONS) s.copy(gDown = low) else bad()
            else -> { err("no setting '$k'. see: qhome set ls"); null }
        }
    }

    private fun onOff(b: Boolean) = if (b) "on" else "off"
}

object QhApps {
    /** Every app with a launcher icon, A to Z, QuestBlock included. */
    fun load(c: Context): List<QhApp> {
        val pm = c.packageManager
        val own = c.packageName
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { QhApp(it.activityInfo.packageName, if (it.activityInfo.packageName == own) "QuestBlock" else it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
        val all = if (list.any { it.pkg == own }) list else list + QhApp(own, "QuestBlock")
        return all.sortedBy { it.label.lowercase() }
    }

    /** The phone's default dialer, messages, browser and camera (when it can tell). */
    fun defaultDock(c: Context): List<String> {
        val pm = c.packageManager
        fun pkgFor(i: Intent): String? = runCatching {
            pm.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()?.takeIf { it != "android" && it != c.packageName }
        return listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://"))),
            pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        ).distinct()
    }
}
