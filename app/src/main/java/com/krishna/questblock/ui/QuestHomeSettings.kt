package com.krishna.questblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
) {
    val ctx = LocalContext.current
    BackHandler(enabled = true) { onClose() }
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
        "lock" to "Lock screen", "drawer" to "Open drawer", "search" to "Open search", "settings" to "Launcher settings",
        "questblock" to "Open QuestBlock", "focus" to "Start Focus", "none" to "Nothing",
    )

    Box(Modifier.fillMaxSize().background(Color(theme.top).copy(alpha = 1f)).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quest Home settings", color = txt, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done", color = acc, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {

                // ── Look ──
                Title("Look")
                Label("Theme")
                Pills(QH_THEMES.map { it.id }, st.theme, { id -> QH_THEMES.first { it.id == id }.name }) { id -> update { it.copy(theme = id) } }
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
                Label("Icon shape")
                Pills(listOf("") + QH_SHAPES, st.shape, { if (it.isEmpty()) "theme" else it }) { sh -> update { it.copy(shape = sh) } }
                Num("Icon size", st.iconSize, 70, 130, "%") { v -> update { it.copy(iconSize = v) } }
                SwitchRow("Show labels", st.labels) { b -> update { it.copy(labels = b) } }
                Label("Label lines")
                Pills(listOf("1", "2"), st.labelLines.toString()) { v -> update { it.copy(labelLines = v.toInt()) } }
                SwitchRow("Icon tile", st.tile, "The soft square or circle behind every icon") { b -> update { it.copy(tile = b) } }

                // ── Layout ──
                Title("Layout")
                Label("Columns", "On the home pages and in the drawer")
                Pills(listOf("4", "5", "6"), st.cols.toString()) { v -> update { it.copy(cols = v.toInt()) } }
                Label("Dock size", "Apps in the dock, plus the apps button")
                Pills(listOf("4", "5", "6"), st.dockMax.toString()) { v -> update { s -> s.copy(dockMax = v.toInt(), dock = s.dock.take(v.toInt())) } }

                // ── Drawer ──
                Title("Drawer")
                SwitchRow("A to Z strip", st.azStrip) { b -> update { it.copy(azStrip = b) } }
                SwitchRow("Hidden apps show in search", st.searchHidden, "Off: hidden apps are also hidden from drawer search") { b -> update { it.copy(searchHidden = b) } }

                // ── Gestures ──
                Title("Gestures")
                val acts = QhSet.ACTIONS
                Label("Double tap"); Pills(acts, st.gDouble, { actionNames[it] ?: it }) { a -> update { it.copy(gDouble = a) } }
                Label("Long press on empty space"); Pills(acts, st.gLong, { actionNames[it] ?: it }) { a -> update { it.copy(gLong = a) } }
                Label("Swipe up"); Pills(acts, st.gUp, { actionNames[it] ?: it }) { a -> update { it.copy(gUp = a) } }
                Label("Swipe down"); Pills(acts, st.gDown, { actionNames[it] ?: it }) { a -> update { it.copy(gDown = a) } }
                Text("Two-finger swipe is not in this version.", color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

                // ── Widgets ──
                Title("Widgets on the first page")
                Pills(listOf("clock", "status", "focus", "quests", "homework"), "", { n ->
                    (if (n in st.widgets) "\u2713 " else "") + n.replaceFirstChar { it.uppercase() }
                }) { n -> update { it.copy(widgets = if (n in it.widgets) it.widgets - n else it.widgets + n) } }

                // ── Hidden and locks ──
                Title("Hidden apps and locks")
                SwitchRow("Hidden count on the apps button", st.hiddenBadge) { b -> update { it.copy(hiddenBadge = b) } }
                SwitchRow("Focus home", st.focusHome, "During a Focus session, apps that were not picked are hidden instead of dimmed") { b -> update { it.copy(focusHome = b) } }
                SwitchRow("Hide locked apps", st.lockedHide, "Apps locked in the Vault disappear until they are unlocked") { b -> update { it.copy(lockedHide = b) } }
                if (st.hidden.isNotEmpty()) {
                    Label("Hidden (${st.hidden.size})", "Hiding is instant")
                    st.hidden.forEach { pkg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(apps[pkg]?.label ?: pkg, color = txt, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { update { it.unhide(pkg) } }) { Text("Unhide", color = acc) }
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
                Text(
                    "Accessibility: " + (if (acc1) "on" else "off") + "     Draw over apps: " + (if (ov) "on" else "off") +
                        (if (!acc1 && st.block != "launcher") "\nAccessibility is off, so only the launcher guards locked apps right now." else ""),
                    color = if (!acc1 && st.block != "launcher") Color(0xFFFFB84D) else mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )

                // ── Backup ──
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
                Title("More")
                Text("Make Quest Home my Home app", color = txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onHomeApp() }.padding(vertical = 10.dp))
                Text("Open QuestBlock", color = txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onOpenQuestBlock() }.padding(vertical = 10.dp))
                Text("Turn off Quest Home", color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { onTurnOff() }.padding(vertical = 10.dp))
                Text("Terminal: qhome set ls shows every setting here.", color = mut, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
