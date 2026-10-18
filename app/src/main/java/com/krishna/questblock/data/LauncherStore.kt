package com.krishna.questblock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

class QhApp(val pkg: String, val label: String)

class QhFolder(val id: String, val name: String, val apps: List<String>)

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
) {
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
            if (pgs[i].size < QhStore.capacity(i)) { pgs[i].add(item); return copy(pages = pgs) }
        }
        if (pgs.size < QhStore.MAX_PAGES) { pgs.add(mutableListOf(item)); return copy(pages = pgs) }
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
        if (to < 0 || to >= QhStore.MAX_PAGES) return this
        val pgs = withoutOnPages(item).map { it.toMutableList() }.toMutableList()
        while (pgs.size <= to) pgs.add(mutableListOf())
        if (pgs[to].size >= QhStore.capacity(to)) return this
        pgs[to].add(item)
        return copy(pages = trimEmptyPages(pgs))
    }

    fun addDock(pkg: String): QhState = if (pkg in dock || dock.size >= QhStore.DOCK_MAX) this else copy(dock = dock + pkg)
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
        )
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
        sp(c).edit().putString("state", o.toString()).apply()
    }

    fun update(c: Context, f: (QhState) -> QhState): QhState {
        val n = f(load(c))
        save(c, n)
        return n
    }

    fun reset(c: Context) { sp(c).edit().remove("state").apply() }

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
