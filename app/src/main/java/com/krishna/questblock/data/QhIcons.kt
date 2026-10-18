package com.krishna.questblock.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** v214: installed icon packs (ADW / Nova style, appfilter.xml) and own icon pictures per app. */
object QhIcons {
    val PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.fede.launcher.THEME_ICONPACK", "org.adw.launcher.icons.ACTION_PICK_ICON",
    )

    private val maps = HashMap<String, Map<String, String>>()

    /** (package, label) of every installed icon pack. */
    fun findPacks(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val seen = LinkedHashMap<String, String>()
        PACK_ACTIONS.forEach { a ->
            runCatching {
                pm.queryIntentActivities(Intent(a), 0).forEach { r ->
                    val p = r.activityInfo.packageName
                    if (p !in seen) seen[p] = r.loadLabel(pm).toString()
                }
            }
        }
        return seen.map { it.key to it.value }
    }

    /** "pkg/Class" and "pkg" to drawable name, read from the pack's appfilter.xml. */
    private fun map(c: Context, pack: String): Map<String, String> = maps.getOrPut(pack) {
        val out = HashMap<String, String>()
        runCatching {
            val res = c.packageManager.getResourcesForApplication(pack)
            val id = res.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser = if (id != 0) res.getXml(id) else {
                val f = android.util.Xml.newPullParser()
                f.setInput(res.assets.open("appfilter.xml"), null)
                f
            }
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                        val inner = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        val pkg = inner.substringBefore('/')
                        out[inner] = dr
                        if (pkg !in out) out[pkg] = dr
                    }
                }
                ev = parser.next()
            }
        }
        out
    }

    fun clearCache() { maps.clear() }

    fun drawable(c: Context, pack: String, pkg: String): Drawable? = runCatching {
        if (pack.isEmpty()) return null
        val pm = c.packageManager
        val comp = pm.getLaunchIntentForPackage(pkg)?.component
        val m = map(c, pack)
        val name = (comp?.let { m["${it.packageName}/${it.className}"] }) ?: m[pkg] ?: return null
        val res = pm.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null)
    }.getOrNull()

    fun imageFile(c: Context, pkg: String): File = File(File(c.filesDir, "qh_icons").also { it.mkdirs() }, pkg.replace(Regex("[^A-Za-z0-9._]"), "_") + ".png")
    fun hasImage(c: Context, pkg: String): Boolean = imageFile(c, pkg).let { it.exists() && it.length() > 0 }

    /** Copies a picked picture into the app, scaled to 192 px, as this app's icon. */
    fun importImage(c: Context, pkg: String, uri: Uri): Boolean = runCatching {
        val bmp = c.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: return false
        val k = 192f / maxOf(bmp.width, bmp.height).toFloat()
        val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
        imageFile(c, pkg).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun deleteImage(c: Context, pkg: String) { runCatching { imageFile(c, pkg).delete() } }

    /** Short kind of an app for the built-in glyph pack: call, chat, web, camera, music, video, game, mail, map, shop, tool or app. */
    fun kindOf(c: Context, pkg: String, label: String): String {
        val p = pkg.lowercase()
        val l = label.lowercase()
        fun has(vararg k: String) = k.any { p.contains(it) || l.contains(it) }
        return when {
            has("dialer", "phone", "contacts") -> "call"
            has("whatsapp", "telegram", "messag", "sms", "signal", "snap", "chat", "discord") -> "chat"
            has("chrome", "browser", "firefox", "opera", "brave", "edge") -> "web"
            has("camera") -> "camera"
            has("music", "spotify", "audio", "podcast", "radio") -> "music"
            has("youtube", "video", "netflix", "prime", "tv", "player") -> "video"
            has("mail", "gmail", "outlook") -> "mail"
            has("maps", "navig", "uber", "ola") -> "map"
            has("shop", "amazon", "flipkart", "pay", "bank") -> "shop"
            has("settings", "calculator", "clock", "files", "notes", "calendar") -> "tool"
            else -> runCatching {
                when (c.packageManager.getApplicationInfo(pkg, 0).category) {
                    android.content.pm.ApplicationInfo.CATEGORY_GAME -> "game"
                    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "chat"
                    android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "video"
                    android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "music"
                    android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "map"
                    else -> "app"
                }
            }.getOrDefault("app")
        }
    }

    /** 24 x 24 outline glyphs (SVG path data) for the built-in pack. */
    val GLYPHS: Map<String, String> = mapOf(
        "call" to "M6,3 L10,3 L12,8 L9.5,10 Q11,13.5 14,14.5 L16,12 L21,14 L21,18 Q21,21 18,21 Q6,20 3,6 Q3,3 6,3 Z",
        "chat" to "M4,4 L20,4 Q21,4 21,5 L21,15 Q21,16 20,16 L11,16 L6,20 L6,16 L4,16 Q3,16 3,15 L3,5 Q3,4 4,4 Z",
        "web" to "M12,3 A9,9 0 1,0 12.01,3 Z M3,12 L21,12 M12,3 Q7,12 12,21 M12,3 Q17,12 12,21",
        "camera" to "M4,7 L8,7 L9.5,5 L14.5,5 L16,7 L20,7 Q21,7 21,8 L21,18 Q21,19 20,19 L4,19 Q3,19 3,18 L3,8 Q3,7 4,7 Z M12,9 A3.5,3.5 0 1,0 12.01,9 Z",
        "music" to "M9,18 L9,6 L19,4 L19,16 M9,18 A2.5,2.5 0 1,1 8.99,18 Z M19,16 A2.5,2.5 0 1,1 18.99,16 Z",
        "video" to "M3,6 L21,6 L21,18 L3,18 Z M10,9 L15,12 L10,15 Z",
        "game" to "M6,9 L18,9 Q22,9 22,14 Q22,18 19,18 L16,15 L8,15 L5,18 Q2,18 2,14 Q2,9 6,9 Z M8,10.5 L8,13.5 M6.5,12 L9.5,12",
        "mail" to "M3,5 L21,5 L21,19 L3,19 Z M3,5 L12,13 L21,5",
        "map" to "M12,21 Q5,13 5,9 A7,7 0 1,1 19,9 Q19,13 12,21 Z M12,7 A2.5,2.5 0 1,0 12.01,7 Z",
        "shop" to "M5,8 L19,8 L20,20 L4,20 Z M9,8 Q9,4 12,4 Q15,4 15,8",
        "tool" to "M12,3 L14,6 L18,6 L18,10 L21,12 L18,14 L18,18 L14,18 L12,21 L10,18 L6,18 L6,14 L3,12 L6,10 L6,6 L10,6 Z",
        "app" to "M4,4 L10,4 L10,10 L4,10 Z M14,4 L20,4 L20,10 L14,10 Z M4,14 L10,14 L10,20 L4,20 Z M14,14 L20,14 L20,20 L14,20 Z",
    )
}
