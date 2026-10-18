package com.krishna.questblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.QhApp
import com.krishna.questblock.data.QhSet
import com.krishna.questblock.data.QhState
import com.krishna.questblock.data.QhStore
import com.krishna.questblock.data.QhWallFile
import com.krishna.questblock.util.Perms

/**
 * v213: the launcher settings page. A full screen over the launcher, not a dialog. Every row here is also a
 * terminal command (qhome set <key> <value>, qhome wall, qhome grid, qhome block ...), so nothing lives only here.
 */
@Composable
fun QhSettingsPage(
    theme: QhTheme,
    st: QhState,
    apps: Map<String, QhApp>,
    update: ((QhState) -> QhState) -> Unit,
    pickPicture: () -> Unit,
    onOpenQuestBlock: () -> Unit,
    onHomeApp: () -> Unit,
    onTurnOff: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onUnhide: (String) -> Unit,
    askContacts: () -> Unit,
    vm: QuestHomeViewModel,
) {
    val ctx = LocalContext.current
    var sp by remember { mutableStateOf("") }     // v218: "" = the list of pages
    var find by remember { mutableStateOf("") }
    val pageScroll = rememberScrollState()
    LaunchedEffect(sp) { pageScroll.scrollTo(0) }
    BackHandler(enabled = true) { if (sp.isNotEmpty()) sp = "" else onClose() }
    // id, name, one line, words the finder also matches
    val pages = listOf(
        Quad("look", "Theme and wall", "Theme, night theme, own themes, wall, dim, blur", "colour night picture wallpaper animation parallax"),
        Quad("icons", "Icons", "Shape, size, labels, tile, icon pack", "icon shape size label tile pack glyph letters"),
        Quad("layout", "Layout", "Columns, dock, rows, pages, page change, folders", "columns dock rows pages dots folder transition"),
        Quad("drawer", "Drawer", "List or grid, sort, A to Z, rows, search", "sort recent most used az search contacts"),
        Quad("gestures", "Gestures", "Double tap, long press, swipes", "double tap long press swipe two finger"),
        Quad("widgets", "Widgets", "Cards on the first page and Android widgets", "clock status focus quests quick add widget"),
        Quad("hidden", "Hidden apps and locks", "Hidden space, locked apps, blocking, rules", "hide lock block unlock sheet study first notification dots usage"),
        Quad("backup", "Backup", "Save and restore the launcher, reset", "backup restore reset export"),
        Quad("more", "More", "Home app, QuestBlock, turn off", "default home app turn off"),
    )
    val pageNames = pages.associate { it.first to it.second }
    val txt = Color(theme.text)
    val mut = Color(theme.muted)
    val acc = Color(theme.accent)

    @Composable
    fun Title(t: String) = Text(t.uppercase(), color = acc, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

    @Composable
    fun Label(t: String, hint: String? = null) {
        Text(t, color = txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        if (hint != null) Text(hint, color = mut, fontSize = 11.sp)
    }

    @Composable
    fun Pills(options: List<String>, current: String, names: (String) -> String = { it }, onPick: (String) -> Unit) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val sel = o == current
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (sel) acc.copy(alpha = 0.28f) else txt.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { onPick(o) },
                ) { Text(names(o), modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (sel) acc else mut) }
            }
        }
    }

    @Composable
    fun SwitchRow(t: String, on: Boolean, hint: String? = null, onSet: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t, color = txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (hint != null) Text(hint, color = mut, fontSize = 11.sp)
            }
            Switch(checked = on, onCheckedChange = onSet, colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color(theme.top)))
        }
    }

    @Composable
    fun Num(t: String, value: Int, lo: Int, hi: Int, unit: String = "", onSet: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t, color = txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$value$unit", color = mut, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(), onValueChange = { onSet(it.toInt().coerceIn(lo, hi)) }, valueRange = lo.toFloat()..hi.toFloat(),
            colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc, inactiveTrackColor = txt.copy(alpha = 0.15f)),
        )
    }

    val actionNames = mapOf(
        "lock" to "Lock screen", "menu" to "Home menu", "drawer" to "Open drawer", "search" to "Open search", "settings" to "Launcher settings",
        "questblock" to "Open QuestBlock", "focus" to "Start Focus", "notifications" to "Notifications", "none" to "Nothing",
    )

    Box(Modifier.fillMaxSize().background(Color(theme.top).copy(alpha = 1f)).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (sp.isEmpty()) "Quest Home settings" else "\u2039  " + (pageNames[sp] ?: ""), color = txt, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable(enabled = sp.isNotEmpty()) { sp = "" })
                TextButton(onClick = onClose) { Text("Done", color = acc, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(pageScroll).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {

                // ── Look ──
                if (sp.isEmpty()) {
                    androidx.compose.material3.OutlinedTextField(value = find, onValueChange = { find = it }, singleLine = true, label = { Text("Find a setting") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    val f = find.trim().lowercase()
                    pages.filter { f.isEmpty() || (it.second + " " + it.third + " " + it.fourth).lowercase().contains(f) }.forEach { (id, name, hint, _) ->
                        Column(Modifier.fillMaxWidth().clickable { sp = id }.padding(vertical = 14.dp)) {
                            Text(name, color = txt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(hint, color = mut, fontSize = 12.sp)
                        }
                    }
                }
                if (sp == "look") {
                Title("Look")
                Label("Theme")
                val allThemes = remember(st.customThemes) { qhAllThemes(ctx) }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    allThemes.forEach { t ->
                        val sel = t.id == st.theme
                        Column(Modifier.width(86.dp).clickable { update { it.copy(theme = t.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(86.dp, 150.dp).clip(RoundedCornerShape(14.dp)).border(if (sel) 2.dp else 0.dp, acc, RoundedCornerShape(14.dp))) {
                                QhBackground(t, Modifier.fillMaxSize(), animate = false)
                                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) { Box(Modifier.size(14.dp).background(Color(t.tile or 0x66000000L), qhShape(t.shape))) }
                                }
                            }
                            Text(t.name, color = if (sel) acc else mut, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Label("Night theme", "Used between the hours below")
                Pills(listOf("off") + allThemes.map { it.id }, st.nightTheme.ifEmpty { "off" }, { id -> allThemes.firstOrNull { it.id == id }?.name ?: id }) { id -> update { it.copy(nightTheme = if (id == "off") "" else id) } }
                if (st.nightTheme.isNotEmpty()) {
                    Num("Night from", st.nightFrom, 0, 23, ":00") { v -> update { it.copy(nightFrom = v) } }
                    Num("Night until", st.nightTo, 0, 23, ":00") { v -> update { it.copy(nightTo = v) } }
                }
                SwitchRow("Animations", st.animate, "Off saves battery and is the reduce-motion switch for the wall") { b -> update { it.copy(animate = b) } }
                SwitchRow("Wall moves with the page", st.parallax) { b -> update { it.copy(parallax = b) } }

                // my own theme
                var mkOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { mkOpen = !mkOpen }) { Text(if (mkOpen) "Close theme maker" else "Make my own theme (${st.customThemes.size}/10)", color = acc, fontWeight = FontWeight.Bold) }
                if (mkOpen) {
                    var name by remember { mutableStateOf("") }
                    var top by remember { mutableStateOf("101018") }
                    var bottom by remember { mutableStateOf("000000") }
                    var accent by remember { mutableStateOf("7C5CFF") }
                    var dark by remember { mutableStateOf(true) }
                    var shape by remember { mutableStateOf("squircle") }
                    var wall by remember { mutableStateOf("") }
                    val cols = listOf("101018", "000000", "1B2A41", "2D1B3D", "1F3D2B", "3D1F1F", "F4F1EA", "FFFFFF", "0F2027", "3A1C71")
                    val accs = listOf("7C5CFF", "2D9CDB", "27AE60", "F2994A", "EB5757", "BB6BD9", "56CCF2", "F2C94C")
                    androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, singleLine = true, label = { androidx.compose.material3.Text("Name") })
                    Label("Top colour"); Pills(cols, top, { "#$it" }) { top = it }
                    Label("Bottom colour"); Pills(cols, bottom, { "#$it" }) { bottom = it }
                    Label("Accent"); Pills(accs, accent, { "#$it" }) { accent = it }
                    SwitchRow("Light text (dark theme)", dark) { dark = it }
                    Label("Icon shape"); Pills(QH_SHAPES, shape) { shape = it }
                    Label("Wall picture", "Paste SVG path data (M L Q T C A Z) drawn on a 100 x 220 canvas. Leave empty for none.")
                    androidx.compose.material3.OutlinedTextField(value = wall, onValueChange = { wall = it.take(1500) }, label = { androidx.compose.material3.Text("Path data") }, maxLines = 4)
                    TextButton(onClick = {
                        if (name.isBlank()) { Toast.makeText(ctx, "Give it a name", Toast.LENGTH_SHORT).show(); return@TextButton }
                        if (st.customThemes.size >= 10) { Toast.makeText(ctx, "At most 10 own themes", Toast.LENGTH_SHORT).show(); return@TextButton }
                        val ok = wall.isBlank() || runCatching { androidx.compose.ui.graphics.vector.PathParser().parsePathString(wall); true }.getOrDefault(false)
                        if (!ok) { Toast.makeText(ctx, "That path data does not parse", Toast.LENGTH_SHORT).show(); return@TextButton }
                        val id = "c" + (System.currentTimeMillis() % 100000000L).toString(36)
                        update { it.copy(customThemes = it.customThemes + com.krishna.questblock.data.QhCustomTheme(id, name.trim(), 0xFF000000L or top.toLong(16), 0xFF000000L or bottom.toLong(16), 0xFF000000L or accent.toLong(16), dark, shape, wall.trim()), theme = id) }
                        mkOpen = false
                    }) { Text("Save and use", color = acc, fontWeight = FontWeight.Bold) }
                }
                st.customThemes.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name + "  (" + c.id + ")", color = txt, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { update { it.copy(customThemes = it.customThemes - c, theme = if (it.theme == c.id) "" .ifEmpty { QH_THEMES.first().id } else it.theme, nightTheme = if (it.nightTheme == c.id) "" else it.nightTheme) } }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                    }
                }
                Label("Wall", "svg: the theme picture. phone: your phone wallpaper. phone + svg: both. colour. my picture.")
                Pills(QhSet.WALLS, st.wall, { when (it) { "svg" -> "Theme picture"; "phone" -> "Phone wallpaper"; "phonesvg" -> "Phone + picture"; "color" -> "Colour"; else -> "My picture" } }) { w ->
                    if (w == "picture" && !QhWallFile.exists(ctx)) pickPicture() else update { it.copy(wall = w) }
                }
                if (st.wall == "color") {
                    Label("Wall colour")
                    Pills(listOf("101018", "000000", "1B2A41", "2D1B3D", "1F3D2B", "3D1F1F", "F4F1EA"), String.format("%06X", st.wallColor and 0xFFFFFF), { "#$it" }) { c ->
                        update { it.copy(wallColor = 0xFF000000L or c.toLong(16)) }
                    }
                }
                Label("My picture", if (QhWallFile.exists(ctx)) "A picture is saved inside the app." else "Pick one image once (Android's own picker). It is copied into the app.")
                Row { TextButton(onClick = pickPicture) { Text(if (QhWallFile.exists(ctx)) "Change picture" else "Pick a picture", color = acc, fontWeight = FontWeight.Bold) } }
                Num("Dim", st.dim, 0, 80, "%") { v -> update { it.copy(dim = v) } }
                Num("Blur", st.blur, 0, 100, "") { v -> update { it.copy(blur = v) } }
                Text("Blur works on Android 12 and newer; older phones only dim. Phone and picture walls always get at least a light dim so text stays readable (the text turns light there).", color = mut, fontSize = 11.sp)
                }
                if (sp == "icons") {
                Label("Icon shape")
                Pills(listOf("") + QH_SHAPES, st.shape, { if (it.isEmpty()) "theme" else it }) { sh -> update { it.copy(shape = sh) } }
                Num("Icon size", st.iconSize, 70, 130, "%") { v -> update { it.copy(iconSize = v) } }
                SwitchRow("Show labels", st.labels) { b -> update { it.copy(labels = b) } }
                Label("Label lines")
                Pills(listOf("1", "2"), st.labelLines.toString()) { v -> update { it.copy(labelLines = v.toInt()) } }
                SwitchRow("Icon tile", st.tile, "The soft square or circle behind every icon") { b -> update { it.copy(tile = b) } }
                Label("Icons come from", "system: each app's own icon. glyph: simple pictures from the theme colours. pack: an installed icon pack. none: letter tiles.")
                Pills(QhSet.ICON_SRC, st.iconSrc) { v -> update { it.copy(iconSrc = v) }; qhIconsChanged() }
                if (st.iconSrc == "pack") {
                    val packs = remember { com.krishna.questblock.data.QhIcons.findPacks(ctx) }
                    if (packs.isEmpty()) Text("No icon pack app found. Install an ADW or Nova style pack, then come back.", color = mut, fontSize = 12.sp)
                    else Pills(packs.map { it.first }, st.iconPack, { p -> packs.first { it.first == p }.second }) { p -> com.krishna.questblock.data.QhIcons.clearCache(); update { it.copy(iconPack = p) }; qhIconsChanged() }
                }
                Text("Long-press an app, then Edit name and icon, to change one app (name, picture, emoji, letters, shape, colour).", color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                // ── Layout ──
                }
                if (sp == "layout") {
                Title("Layout")
                Label("Columns", "On the home pages and in the drawer")
                Pills(listOf("4", "5", "6"), st.cols.toString()) { v -> update { it.copy(cols = v.toInt()) } }
                Label("Dock size", "Apps in the dock, plus the apps button")
                Pills(listOf("4", "5", "6"), st.dockMax.toString()) { v -> update { s -> s.copy(dockMax = v.toInt(), dock = s.dock.take(v.toInt())) } }
                Label("Rows per page", "Auto fits as before (the first page also carries the widgets)")
                Pills(listOf("0", "3", "4", "5", "6", "7", "8"), st.rows.toString(), { if (it == "0") "auto" else it }) { v -> update { it.copy(rows = v.toInt()) } }
                Num("Most home pages", st.maxPages, 1, 8) { v -> update { it.copy(maxPages = v) } }
                Label("Page change")
                Pills(QhSet.TRANSITIONS, st.transition) { v -> update { it.copy(transition = v) } }
                Label("Page indicator"); Pills(QhSet.DOTS, st.dots) { v -> update { it.copy(dots = v) } }
                Label("Dock style"); Pills(QhSet.DOCKS, st.dockStyle) { v -> update { it.copy(dockStyle = v) } }
                Label("Folder style"); Pills(QhSet.FOLDERS, st.folderStyle) { v -> update { it.copy(folderStyle = v) } }

                // ── Drawer ──
                }
                if (sp == "drawer") {
                Title("Drawer")
                SwitchRow("List instead of grid", st.drawerList) { b -> update { it.copy(drawerList = b) } }
                Label("Sort"); Pills(QhSet.SORTS, st.sort, { when (it) { "az" -> "A to Z"; "za" -> "Z to A"; "recent" -> "Recent"; else -> "Most used" } }) { v -> update { it.copy(sort = v) } }
                SwitchRow("A to Z strip", st.azStrip) { b -> update { it.copy(azStrip = b) } }
                SwitchRow("Recent apps row", st.recentRow) { b -> update { it.copy(recentRow = b) } }
                SwitchRow("Most used row", st.mostRow) { b -> update { it.copy(mostRow = b) } }
                SwitchRow("Search also finds quests, homework and sums", st.searchExtra) { b -> update { it.copy(searchExtra = b) } }
                SwitchRow("Search also finds contacts", st.searchContacts, "Asks for the contacts permission") { b ->
                    if (b && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) askContacts()
                    else update { it.copy(searchContacts = b) }
                }
                SwitchRow("Usage time on icons", st.usageBadge, if (qhUsageGranted(ctx)) "Today's minutes in the corner of each icon" else "Needs Usage access (Settings > QuestBlock > Usage access)") { b -> update { it.copy(usageBadge = b) } }
                SwitchRow("Notification dots", st.notifDots, if (Perms.notificationAccess(ctx)) "A dot on apps that have a notification" else "Needs notification access for QuestBlock") { b -> update { it.copy(notifDots = b) } }
                SwitchRow("Hidden apps show in search", st.searchHidden, "Off: hidden apps are also hidden from drawer search") { b -> update { it.copy(searchHidden = b) } }

                // ── Gestures ──
                }
                if (sp == "gestures") {
                Title("Gestures")
                val acts = QhSet.ACTIONS
                Label("Double tap"); Pills(acts, st.gDouble, { actionNames[it] ?: it }) { a -> update { it.copy(gDouble = a) } }
                Label("Long press on empty space"); Pills(acts, st.gLong, { actionNames[it] ?: it }) { a -> update { it.copy(gLong = a) } }
                Label("Swipe up"); Pills(acts, st.gUp, { actionNames[it] ?: it }) { a -> update { it.copy(gUp = a) } }
                Label("Swipe down"); Pills(acts, st.gDown, { actionNames[it] ?: it }) { a -> update { it.copy(gDown = a) } }
                Label("Two fingers up"); Pills(acts, st.g2Up, { actionNames[it] ?: it }) { a -> update { it.copy(g2Up = a) } }
                Label("Two fingers down"); Pills(acts, st.g2Down, { actionNames[it] ?: it }) { a -> update { it.copy(g2Down = a) } }

                // ── Widgets ──
                }
                if (sp == "widgets") {
                Title("Widgets on the first page")
                Text("Tap to show or hide. Arrows change the order.", color = mut, fontSize = 11.sp)
                st.widgetOrder.forEachIndexed { i, n ->
                    if (n != "homework" || com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = n in st.widgets, onCheckedChange = { b -> update { it.copy(widgets = if (b) it.widgets + n else it.widgets - n) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = acc, checkedThumbColor = Color(theme.top)))
                        Text(when (n) { "quickadd" -> "Quick add"; else -> n.replaceFirstChar { it.uppercase() } }, color = txt, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        TextButton(onClick = { update { s -> val l = s.widgetOrder.toMutableList(); if (i > 0) { l.removeAt(i); l.add(i - 1, n) }; s.copy(widgetOrder = l) } }) { Text("\u25B2", color = acc) }
                        TextButton(onClick = { update { s -> val l = s.widgetOrder.toMutableList(); if (i < l.lastIndex) { l.removeAt(i); l.add(i + 1, n) }; s.copy(widgetOrder = l) } }) { Text("\u25BC", color = acc) }
                    }
                }
                Num("Clock size", st.clockScale, 60, 140, "%") { v -> update { it.copy(clockScale = v) } }
                Num("Quest lines", st.questsCount, 1, 6) { v -> update { it.copy(questsCount = v) } }
                if (com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) Num("Homework lines", st.hwCount, 1, 6) { v -> update { it.copy(hwCount = v) } }
                Num("One-tap Focus length", st.focusPreset, 5, 180, " min") { v -> update { it.copy(focusPreset = v) } }
                Label("Android widgets", "Real widgets from other apps, shown on the first page below the cards")
                st.hostWidgets.forEach { id ->
                    val nm = remember(id) { runCatching { android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.loadLabel(ctx.packageManager) }.getOrNull() ?: "Widget $id" }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(nm, color = txt, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveWidget(id) }) { Text("Remove", color = Color(0xFFFF6B6B)) }
                    }
                }
                TextButton(onClick = onAddWidget) { Text("Add an Android widget", color = acc, fontWeight = FontWeight.Bold) }

                // ── Hidden and locks ──
                }
                if (sp == "hidden") {
                Title("Hidden apps and locks")
                SwitchRow("Hidden count on the apps button", st.hiddenBadge) { b -> update { it.copy(hiddenBadge = b) } }
                SwitchRow("Focus home", st.focusHome, "During a Focus session, apps that were not picked are hidden instead of dimmed") { b -> update { it.copy(focusHome = b) } }
                SwitchRow("Hide locked apps", st.lockedHide, "Apps locked in the Vault disappear until they are unlocked") { b -> update { it.copy(lockedHide = b) } }
                Label("Hide Fun apps by rule", "While the rule is on, every Fun app disappears (not from the Vault)")
                Pills(QhSet.HIDE_RULES, "", { r -> (if (r in st.hideRules) "\u2713 " else "") + when (r) { "fun-study" -> "Study hours"; "fun-quiet" -> "Quiet hours"; else -> "Exam mode" } }) { r ->
                    update { it.copy(hideRules = if (r in it.hideRules) it.hideRules - r else it.hideRules + r) }
                }
                Text("Hiding is instant. Unhiding a Fun app waits until midnight while any fun rule is on.", color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                SwitchRow("Study first", st.studyFirst, "Fun apps stay locked until one quest is done today") { b -> update { it.copy(studyFirst = b) } }
                Label("Hidden space", "Hidden apps live here. Open it from drawer search with this word, by a long-press on the search icon, or for coins.")
                androidx.compose.material3.OutlinedTextField(
                    value = st.revealWord, onValueChange = { v -> update { it.copy(revealWord = v.lowercase().filter { c -> c.isLetterOrDigit() }.take(20)) } },
                    singleLine = true, label = { androidx.compose.material3.Text("Search word (3+ letters, empty = off)") },
                )
                Num("Coins to open", st.revealCost, 0, 500) { v -> update { it.copy(revealCost = v) } }
                SwitchRow("Long-press on the search icon opens it", st.revealGesture) { b -> update { it.copy(revealGesture = b) } }
                if (st.hidden.isNotEmpty()) {
                    Label("Hidden (${st.hidden.size})")
                    st.hidden.forEach { pkg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val waiting = st.pendingUnhide[pkg]
                            Text((apps[pkg]?.label ?: pkg) + if (waiting != null) "  (back at midnight)" else "", color = txt, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (waiting == null) TextButton(onClick = { onUnhide(pkg) }) { Text("Unhide", color = acc) }
                        }
                    }
                }
                Label("What blocks an app")
                Pills(QhSet.BLOCKS, st.block, { when (it) { "accessibility" -> "Accessibility (strong)"; "launcher" -> "Launcher only (light)"; else -> "Both" } }) { b -> update { it.copy(block = b) } }
                val acc1 = Perms.accessibilityEnabled(ctx)
                val ov = Perms.overlayGranted(ctx)
                Text(
                    when (st.block) {
                        "accessibility" -> "Strong: every way of opening an app is caught, but the app flashes before the lock page. Needs QuestBlock accessibility."
                        "launcher" -> "Light: tapping a locked app inside Quest Home opens the lock page first, with no flash. Apps opened from notifications, recents, another launcher or a link are NOT blocked. Apps with a daily limit or a time window are also not guarded here."
                        else -> "The launcher opens the lock page first and accessibility still covers everything else."
                    },
                    color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                )
                SwitchRow("Light watcher", st.watcher, "A check every second of the app on screen, with the lock page on top. Weaker than accessibility (a short delay, a few apps resist). Needs Usage access and Draw over apps.") { b -> update { it.copy(watcher = b) } }
                SwitchRow("Unlock sheet in the launcher", st.sheetUnlock, "Tapping a locked app shows the price and your coins right there") { b -> update { it.copy(sheetUnlock = b) } }
                Text(
                    "Usage access: " + (if (qhUsageGranted(ctx)) "on" else "off") + "     ",
                    color = mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Accessibility: " + (if (acc1) "on" else "off") + "     Draw over apps: " + (if (ov) "on" else "off") +
                        (if (!acc1 && st.block != "launcher") "\nAccessibility is off, so only the launcher guards locked apps right now." else ""),
                    color = if (!acc1 && st.block != "launcher") Color(0xFFFFB84D) else mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )

                // ── Backup ──
                }
                if (sp == "backup") {
                Title("Backup")
                Text("The layout (pages, dock, folders, hidden apps, labels and every setting above) is also in QuestBlock's own backup. The own wall picture is a file and is kept only on this phone.", color = mut, fontSize = 11.sp)
                Row {
                    TextButton(onClick = {
                        val code = android.util.Base64.encodeToString(QhStore.exportJson(ctx).toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Quest Home layout", code))
                        Toast.makeText(ctx, "Layout copied. Paste it somewhere safe.", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy layout", color = acc, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = {
                        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
                        val json = runCatching { String(android.util.Base64.decode(clip, android.util.Base64.URL_SAFE)) }.getOrNull()
                        if (json != null && QhStore.importJson(ctx, json)) {
                            update { QhStore.load(ctx) }
                            Toast.makeText(ctx, "Layout imported", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(ctx, "The clipboard has no Quest Home layout", Toast.LENGTH_SHORT).show()
                    }) { Text("Paste layout", color = acc, fontWeight = FontWeight.Bold) }
                }
                var confirmReset by remember { mutableStateOf(false) }
                TextButton(onClick = { if (confirmReset) { confirmReset = false; onReset() } else confirmReset = true }) {
                    Text(if (confirmReset) "Tap again to reset everything" else "Reset layout and settings", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }

                // ── About ──
                }
                if (sp == "more") {
                Title("More")
                Text("Make Quest Home my Home app", color = txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onHomeApp() }.padding(vertical = 10.dp))
                Text("Open QuestBlock", color = txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onOpenQuestBlock() }.padding(vertical = 10.dp))
                Text("Turn off Quest Home", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onTurnOff() }.padding(vertical = 10.dp))
                Text("Terminal: qhome set ls shows every setting here.", color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** v214: is Usage access on for QuestBlock? */
internal fun qhUsageGranted(ctx: Context): Boolean = runCatching {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

/** v218: one row of the settings list (id, name, line, finder words). */
private data class Quad(val first: String, val second: String, val third: String, val fourth: String)
