@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
package com.krishna.questblock.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.SideEffect
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.krishna.questblock.data.QhUse
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.krishna.questblock.data.QhWallFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krishna.questblock.data.BlockedApp
import com.krishna.questblock.data.QhApp
import com.krishna.questblock.data.QhFolder
import com.krishna.questblock.data.QhState
import com.krishna.questblock.data.QhStore
import com.krishna.questblock.data.TaskEntity
import com.krishna.questblock.data.fmtLeft
import com.krishna.questblock.data.isAvailableNow
import com.krishna.questblock.data.level
import com.krishna.questblock.data.nextRunDayStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WIDGET_NAMES get() = listOf("clock" to "Clock", "status" to "Status", "focus" to "Focus", "quests" to "Quests", "homework" to "Homework").filter { it.first != "homework" || com.krishna.questblock.data.Flags.HOMEWORK_ENABLED }

/** v213: accents are stripped (Ä, É count as A, E); digits, symbols and anything else go under "#". */
private fun letterOf(label: String): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    val base = java.text.Normalizer.normalize(first.toString(), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (base in 'A'..'Z') base else '#'
}

private val LocalQh = compositionLocalOf { QhState() }

/** v217: text-only app rows (Niagara drawer). */
private val LocalQhTextOnly = compositionLocalOf { false }

/** v217: the Smart shortcut row sets the tab the drawer opens on. */
private var qhPendingCat by mutableStateOf("All")

private val WALL_PHOTO = setOf("phone", "phonesvg", "picture")

// ═══════════════════════ small building blocks ═══════════════════════
/** Bumped when an own icon picture or the icon pack changes, so icons are read again. */
private var qhIconStamp by mutableIntStateOf(0)
internal fun qhIconsChanged() { QhIconCache.clear(); qhIconStamp += 1 }

@Composable
private fun QhIcon(pkg: String, size: androidx.compose.ui.unit.Dp, label: String = "", tint: Color = Color.White) {
    val ctx = LocalContext.current
    val look = LocalQh.current
    val ov = look.appIcons[pkg]
    when {
        ov != null && ov.startsWith("emoji:") ->
            Text(ov.removePrefix("emoji:"), fontSize = (size.value * 0.78f).sp, textAlign = TextAlign.Center)
        (ov != null && ov.startsWith("letters:")) || (ov == null && look.iconSrc == "none") -> {
            val t = if (ov != null) ov.removePrefix("letters:") else label.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "?" }
            Text(t, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Black, color = tint, textAlign = TextAlign.Center)
        }
        ov == null && look.iconSrc == "glyph" -> {
            val kind = remember(pkg, label) { com.krishna.questblock.data.QhIcons.kindOf(ctx, pkg, label) }
            QhSvgIcon(com.krishna.questblock.data.QhIcons.GLYPHS[kind] ?: com.krishna.questblock.data.QhIcons.GLYPHS.getValue("app"), tint, (size.value * 0.78f).dp)
        }
        else -> {
            val mode = when { ov == "image" -> "image"; ov == "pack" || look.iconSrc == "pack" -> "pack"; else -> "system" }
            val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg, mode, look.iconPack, qhIconStamp) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { QhIconCache.load(ctx, pkg, mode, look.iconPack) }
            }
            val b = bmp
            if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
            else Spacer(Modifier.size(size))
        }
    }
}

/** v215: system, pack and own-picture icons already come with their own shape and background, so they are drawn full size with no second tile behind them. Emoji, letters, glyphs keep the tile. */
private fun qhBleed(look: QhState, pkg: String): Boolean {
    val ov = look.appIcons[pkg]
    return when {
        ov != null && (ov.startsWith("emoji:") || ov.startsWith("letters:")) -> false
        ov != null -> true
        else -> look.iconSrc == "system" || look.iconSrc == "pack"
    }
}

/** Cell size of an icon: the system launcher size (about 52dp) for full-size icons, the old 56dp tile otherwise. */
private fun qhBoxDp(look: QhState, bleed: Boolean) = ((if (bleed) 52f else 56f) * look.iconSize / 100f).dp

@Composable
private fun QhPill(text: String, selected: Boolean, theme: QhTheme, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(theme.accent).copy(alpha = 0.28f) else Color(theme.text).copy(alpha = 0.08f),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color(theme.accent) else Color(theme.muted))
    }
}

/** v214: drag and drop of home items. The tiles report where they are; the screen decides what a drop means. */
class QhDrag {
    var key by mutableStateOf<String?>(null)          // "p:<page item>" or "d:<pkg>" (dock)
    var pos by mutableStateOf(Offset.Zero)
    var moved by mutableStateOf(0f)
    val bounds = HashMap<String, Rect>()
    var dockRect: Rect? = null
    var onDrop: ((String, Offset, Float) -> Unit)? = null
    fun end() { val k = key; key = null; if (k != null) onDrop?.invoke(k, pos, moved) }
    fun cancel() { key = null }
}

private val LocalQhDrag = compositionLocalOf<QhDrag?> { null }

class QhUseInfo(val usage: Map<String, Long> = emptyMap(), val notif: Set<String> = emptySet())
private val LocalQhUse = compositionLocalOf { QhUseInfo() }

@Composable
private fun QhTile(
    theme: QhTheme, shapeId: String, label: String, locked: Boolean, dim: Boolean, showLabel: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier,
    tint: Long? = null, dragKey: String? = null, pkg: String? = null, bleed: Boolean = false, icon: @Composable () -> Unit,
) {
    val look = LocalQh.current
    val use = LocalQhUse.current
    val drag = LocalQhDrag.current
    val boxSize = qhBoxDp(look, bleed)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dragMod = if (dragKey != null && drag != null) {
        Modifier
            .onGloballyPositioned { drag.bounds[dragKey] = it.boundsInRoot(); origin = it.positionInRoot() }
            .pointerInput(dragKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { off -> drag.key = dragKey; drag.pos = origin + off; drag.moved = 0f },
                    onDrag = { change, amount -> change.consume(); drag.pos += amount; drag.moved += amount.getDistance() },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.cancel() },
                )
            }
            .combinedClickable(onClick = onClick)
    } else Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)
    val beingDragged = drag != null && dragKey != null && drag.key == dragKey && drag.moved > 24f
    Column(
        modifier.then(dragMod).padding(vertical = 6.dp, horizontal = 2.dp).alpha(if (dim) 0.35f else if (beingDragged) 0.25f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(boxSize).background(if (look.tile && !bleed) Color(tint ?: theme.tile) else Color.Transparent, qhShape(shapeId)), contentAlignment = Alignment.Center) {
            icon()
            if (locked) {
                // the badge sits on the corner of any icon shape, a little outside the tile so it is never cut
                Box(Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(18.dp).background(Color(theme.top).copy(alpha = 1f), CircleShape), contentAlignment = Alignment.Center) {
                    QhSvgIcon(QhSvg.LOCK, Color(theme.accent), 12.dp)
                }
            }
            if (look.notifDots && pkg != null && pkg in use.notif) {
                Box(Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).size(11.dp).background(Color(theme.accent), CircleShape))
            }
            val ms = if (look.usageBadge && pkg != null) use.usage[pkg] ?: 0L else 0L
            if (ms >= 60_000L) {
                val m = ms / 60_000L
                Text(if (m >= 60) "${m / 60}h${m % 60}m" else "${m}m", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(theme.text),
                    modifier = Modifier.align(Alignment.TopStart).offset((-2).dp, (-2).dp).background(Color(theme.top).copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(horizontal = 3.dp))
            }
        }
        if (showLabel && look.labels) {
            Text(label, fontSize = 11.sp, lineHeight = 13.sp, color = Color(theme.text), maxLines = look.labelLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun QhAppTile(
    app: QhApp, theme: QhTheme, shapeId: String, locked: Boolean, dim: Boolean,
    showLabel: Boolean = true, onClick: () -> Unit, onLong: () -> Unit, dragKey: String? = null,
) {
    val look = LocalQh.current
    val name = look.labelOf(app.pkg, app.label)
    val bleed = qhBleed(look, app.pkg)
    QhTile(theme, look.appShapes[app.pkg] ?: shapeId, name, locked, dim, showLabel, onClick, onLong,
        tint = look.appColors[app.pkg], dragKey = dragKey, pkg = app.pkg, bleed = bleed) {
        QhIcon(app.pkg, if (bleed) qhBoxDp(look, true) else (36f * look.iconSize / 100f).dp, name, Color(theme.accent))
    }
}

@Composable
private fun QhFolderTile(folder: QhFolder, theme: QhTheme, shapeId: String, onClick: () -> Unit, onLong: () -> Unit, dragKey: String? = null) {
    val look = LocalQh.current
    QhTile(theme, shapeId, folder.name, false, false, true, onClick, onLong, dragKey = dragKey) {
        val first = folder.apps.take(4)
        if (look.folderStyle == "stack") {
            Box(Modifier.size(40.dp)) {
                first.take(3).forEachIndexed { i, pk -> Box(Modifier.align(Alignment.TopStart).offset((i * 7).dp, (i * 5).dp)) { QhIcon(pk, 24.dp) } }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { first.take(2).forEach { QhIcon(it, 20.dp) } }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { first.drop(2).forEach { QhIcon(it, 20.dp) } }
            }
        }
    }
}

@Composable
private fun QhCard(theme: QhTheme, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = Color(theme.dock),
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it },
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun QhMenuItem(theme: QhTheme, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        color = if (danger) Color(0xFFFF6B6B) else Color(theme.text),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun QhDialog(theme: QhTheme, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(theme.bottom),
        title = { Text(title, color = Color(theme.text), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color(theme.accent), fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun QhNameDialog(theme: QhTheme, title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(theme.bottom),
        title = { Text(title, color = Color(theme.text), fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onOk(name) }) { Text("OK", color = Color(theme.accent), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(theme.muted)) } },
    )
}

// ═══════════════════════ widgets on the first page ═══════════════════════
@Composable
private fun QhWidgets(theme: QhTheme, vm: QuestHomeViewModel, st: QhState, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val player by vm.player.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val now = vm.now
    val widgets = st.widgets
    val clock by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(10_000L) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        st.widgetOrder.filter { it in widgets && (it != "homework" || com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) }.forEach { w ->
            when (w) {
                "clock" -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clock)), fontSize = (52f * st.clockScale / 100f).sp, fontWeight = FontWeight.Light, color = Color(theme.text))
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(clock)), fontSize = 14.sp, color = Color(theme.muted))
                    }
                    Box(Modifier.size(44.dp).background(Color(theme.tile), CircleShape).clickable { onSearch() }, contentAlignment = Alignment.Center) {
                        QhSvgIcon(QhSvg.SEARCH, Color(theme.text), 22.dp)
                    }
                }
                "status" -> {
                    val dayEnd = nextRunDayStart(0, now) - 1
                    val open = tasks.count { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }
                    QhCard(theme, onClick = { qhOpenQuestBlock(ctx) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Lv ${player.level()}  \u00b7  ${player.coins} coins  \u00b7  streak ${player.streak}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(theme.accent))
                            val parts = ArrayList<String>()
                            parts.add("$open quest${if (open == 1) "" else "s"} today")
                            if (vm.funLine.isNotEmpty()) parts.add(vm.funLine)
                            if (vm.hw.isNotEmpty()) parts.add("${vm.hw.size} homework due")
                            Text(parts.joinToString("  \u00b7  "), fontSize = 12.sp, color = Color(theme.muted))
                        }
                    }
                }
                "focus" -> {
                    val running = player.focusModeUntil > now
                    QhCard(theme, onClick = { qhOpenQuestBlock(ctx, "focus") }) {
                        if (running) {
                            val left = player.focusModeUntil - now
                            val total = player.focusTotalMs.coerceAtLeast(1L)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("\u26A1 Focus  ${fmtLeft(left)} left" + if (vm.prefs.focusStrict) "  \uD83D\uDD12 strict" else "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(theme.accent))
                                LinearProgressIndicator(progress = { (1f - left.toFloat() / total.toFloat()).coerceIn(0f, 1f) }, color = Color(theme.accent), trackColor = Color(theme.text).copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth())
                                Text("Only the apps you picked stay bright", fontSize = 12.sp, color = Color(theme.muted))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("\u26A1 Start a Focus session", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(theme.text))
                                    Text("or one tap with your last picked apps", fontSize = 11.sp, color = Color(theme.muted))
                                }
                                Surface(shape = RoundedCornerShape(20.dp), color = Color(theme.accent).copy(alpha = 0.25f), modifier = Modifier.clickable { vm.startFocusPreset(st.focusPreset) }) {
                                    Text("\u25B6 ${st.focusPreset}m", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(theme.accent))
                                }
                            }
                        }
                    }
                }
                "quests" -> {
                    val dayEnd = nextRunDayStart(0, now) - 1
                    val open: List<TaskEntity> = tasks.filter { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }.take(st.questsCount)
                    QhCard(theme) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Quests today", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(theme.muted),
                                modifier = Modifier.clickable { qhOpenQuestBlock(ctx, "quests") })
                            if (open.isEmpty()) Text("Nothing open. Nice.", fontSize = 13.sp, color = Color(theme.text))
                            open.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.complete(t) }) {
                                    Box(Modifier.size(18.dp).background(Color(theme.text).copy(alpha = 0.12f), CircleShape))
                                    Text(t.title, fontSize = 14.sp, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                                }
                            }
                            if (open.isNotEmpty()) Text("Tap one to finish it", fontSize = 11.sp, color = Color(theme.muted))
                        }
                    }
                }
                "homework" -> QhCard(theme, onClick = { qhOpenQuestBlock(ctx, "homework") }) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("\uD83D\uDCDD Homework due soon", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(theme.muted))
                        if (vm.hw.isEmpty()) Text("Nothing in the next 24 hours", fontSize = 13.sp, color = Color(theme.text))
                        vm.hw.take(st.hwCount).forEach {
                            Text("${com.krishna.questblock.data.Hw.subjectName(it.subj)}: ${it.text}  \u00b7  ${com.krishna.questblock.data.Hw.dueLabel(it.due, now)}",
                                fontSize = 13.sp, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                "quickadd" -> {
                    var text by remember { mutableStateOf("") }
                    var kind by remember { mutableStateOf("quest") }
                    QhCard(theme) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color(theme.accent).copy(alpha = 0.25f), modifier = Modifier.clickable { kind = when (kind) { "quest" -> if (com.krishna.questblock.data.Flags.HOMEWORK_ENABLED) "homework" else "idea"; "homework" -> "idea"; else -> "quest" } }) {
                                Text(kind.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(theme.accent))
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = text, onValueChange = { text = it.take(80) }, singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(theme.text), fontSize = 14.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(theme.accent)),
                                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                                decorationBox = { inner -> if (text.isEmpty()) Text("Add a $kind\u2026", fontSize = 14.sp, color = Color(theme.muted)); inner() },
                            )
                            Text("Add", color = Color(theme.accent), fontWeight = FontWeight.Black, fontSize = 14.sp,
                                modifier = Modifier.clickable { vm.quickAdd(kind, text); text = "" }.padding(6.dp))
                        }
                    }
                }
            }
        }
        // real Android widgets
        st.hostWidgets.forEach { id -> QhHostWidget(id) { vm.update { s0 -> s0.copy(hostWidgets = s0.hostWidgets - id) } } }
    }
}

@Composable
private fun QhHostWidget(id: Int, onGone: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? QuestHomeActivity ?: return
    val info = remember(id) { runCatching { android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) }.getOrNull() }
    if (info == null) { LaunchedEffect(id) { onGone() }; return }
    val h = info.minHeight.coerceIn(60, 400)
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c -> act.widgetHost.createView(c, id, info) },
        modifier = Modifier.fillMaxWidth().height(h.dp),
    )
}

// ═══════════════════════ app drawer ═══════════════════════
private class QhSection(val title: String, val apps: List<QhApp>)
private class QhExtra(val title: String, val sub: String, val onClick: () -> Unit)

/** A small calculator for the drawer search: + - * / ( ) and decimals. Null when the text is not a calculation. */
private fun qhCalc(text: String): Double? {
    val t = text.replace('x', '*').replace('\u00d7', '*').replace('\u00f7', '/').replace(" ", "").replace(',', '.')
    if (t.isEmpty() || !t.any { it in "+-*/" } || !t.all { it.isDigit() || it in "+-*/().%" }) return null
    var i = 0
    fun peek(): Char = if (i < t.length) t[i] else '\u0000'
    fun expr(): Double {
        fun factor(): Double {
            if (peek() == '-') { i++; return -factor() }
            if (peek() == '(') { i++; val v = expr(); if (peek() == ')') i++; return v }
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            if (st == i) throw IllegalStateException()
            return t.substring(st, i).toDouble()
        }
        fun term(): Double {
            var v = factor()
            while (peek() == '*' || peek() == '/') { val o = peek(); i++; val r = factor(); v = if (o == '*') v * r else v / r }
            return v
        }
        var v = term()
        while (peek() == '+' || peek() == '-') { val o = peek(); i++; val r = term(); v = if (o == '+') v + r else v - r }
        return v
    }
    return runCatching { expr().takeIf { i == t.length && it.isFinite() } }.getOrNull()
}

@Composable
private fun QhAppRow(app: QhApp, theme: QhTheme, shapeId: String, locked: Boolean, dim: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val look = LocalQh.current
    val name = look.labelOf(app.pkg, app.label)
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 10.dp, vertical = 6.dp).alpha(if (dim) 0.35f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textOnly = LocalQhTextOnly.current
        val bleed = qhBleed(look, app.pkg)
        if (!textOnly) Box(Modifier.size(44.dp).background(if (look.tile && !bleed) Color(look.appColors[app.pkg] ?: theme.tile) else Color.Transparent, qhShape(look.appShapes[app.pkg] ?: shapeId)), contentAlignment = Alignment.Center) {
            QhIcon(app.pkg, if (bleed) 44.dp else 30.dp, name, Color(theme.accent))
        }
        Text(name, fontSize = if (textOnly) 22.sp else 15.sp, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = if (textOnly) 6.dp else 14.dp, top = if (textOnly) 4.dp else 0.dp, bottom = if (textOnly) 4.dp else 0.dp))
        if (locked) QhSvgIcon(QhSvg.LOCK, Color(theme.accent), 16.dp)
    }
}

@Composable
private fun QhDrawer(
    theme: QhTheme, shapeId: String, st: QhState, apps: List<QhApp>, query: String, focusSearch: Boolean, vm: QuestHomeViewModel,
    isLocked: (String) -> Boolean, isDim: (String) -> Boolean, isShown: (String) -> Boolean,
    onQuery: (String) -> Unit, onClose: () -> Unit, onLaunch: (String) -> Unit, onLong: (QhApp) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) { if (focusSearch) { runCatching { fr.requestFocus() }; kb?.show() } }

    // hidden space: opened by a search word, by long-pressing the search icon, or by coins
    var spaceOpen by remember { mutableStateOf(false) }
    var askReveal by remember { mutableStateOf(false) }
    fun requestReveal() { if (st.revealCost > 0) askReveal = true else spaceOpen = true }
    val word = st.revealWord
    val wordHit = word.isNotEmpty() && query.trim().lowercase() == word
    LaunchedEffect(wordHit) { if (wordHit && !spaceOpen) { requestReveal(); onQuery("") } }

    val counts = remember(apps) { QhUse.counts(ctx) }
    val last = remember(apps) { QhUse.lastUsed(ctx) }

    val q = query.trim().lowercase()
    val dStyle = qhStyleOf(theme)
    var cat by remember { mutableStateOf(qhPendingCat) }
    LaunchedEffect(Unit) { qhPendingCat = "All" }
    val visible = remember(apps, st.hidden, st.searchHidden, st.appLabels, st.sort, query, isShown, spaceOpen, cat, dStyle) {
        val base = apps.filter { a ->
            val name = st.labelOf(a.pkg, a.label)
            val showHidden = q.isNotEmpty() && st.searchHidden
            (a.pkg !in st.hidden || showHidden) && isShown(a.pkg) && (q.isEmpty() || name.lowercase().contains(q)) &&
                (dStyle != "smart" || cat == "All" || q.isNotEmpty() || qhCategoryOf(ctx, a.pkg, name) == cat)
        }
        fun nm(a: QhApp) = st.labelOf(a.pkg, a.label).lowercase()
        when (st.sort) {
            "za" -> base.sortedWith(compareBy<QhApp>({ letterOf(st.labelOf(it.pkg, it.label)) == '#' }, { nm(it) })).reversed()
            "recent" -> base.sortedWith(compareByDescending<QhApp> { last[it.pkg] ?: 0L }.thenBy { nm(it) })
            "most" -> base.sortedWith(compareByDescending<QhApp> { counts[it.pkg] ?: 0 }.thenBy { nm(it) })
            else -> base.sortedWith(compareBy<QhApp>({ letterOf(st.labelOf(it.pkg, it.label)) != '#' }, { nm(it) }))
        }
    }
    val hiddenApps = remember(apps, st.hidden, spaceOpen) { if (spaceOpen) apps.filter { it.pkg in st.hidden } else emptyList() }

    // search extras: calculation, quests, homework, contacts
    val tasks by vm.tasks.collectAsState()
    val extras: List<QhExtra> = remember(query, tasks, vm.hw, st.searchExtra) {
        if (q.isEmpty() || !st.searchExtra) emptyList() else buildList {
            qhCalc(query)?.let { v ->
                val txt = if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString() else String.format(java.util.Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
                add(QhExtra("= $txt", "tap to copy") {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("result", txt))
                    Toast.makeText(ctx, "Copied $txt", Toast.LENGTH_SHORT).show()
                })
            }
            tasks.filter { !it.done && it.title.lowercase().contains(q) }.take(3).forEach { t -> add(QhExtra(t.title, "quest \u00b7 tap to finish") { vm.complete(t) }) }
            vm.hw.filter { it.text.lowercase().contains(q) }.take(3).forEach { h -> add(QhExtra(h.text, "homework") { qhOpenQuestBlock(ctx, "homework") }) }
        }
    }
    val contacts by produceState(emptyList<Pair<String, String>>(), query, st.searchContacts) {
        value = if (q.length < 2 || !st.searchContacts || androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val out = ArrayList<Pair<String, String>>()
                ctx.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$q%"), null,
                )?.use { c -> while (c.moveToNext() && out.size < 3) out += (c.getString(0) ?: "") to (c.getString(1) ?: "") }
                out.toList()
            }.getOrDefault(emptyList())
        }
    }

    val entries: List<Any> = remember(visible, query, extras, contacts, hiddenApps, st.recentRow, st.mostRow, st.sort) {
        buildList<Any> {
            if (q.isNotEmpty()) {
                addAll(extras)
                contacts.forEach { (n, num) -> add(QhExtra(n, num) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) }
                addAll(visible)
            } else {
                if (hiddenApps.isNotEmpty()) add(QhSection("Hidden space", hiddenApps))
                if (st.recentRow) {
                    val r = visible.filter { (last[it.pkg] ?: 0L) > 0L }.sortedByDescending { last[it.pkg] ?: 0L }.take(st.cols)
                    if (r.isNotEmpty()) add(QhSection("Recent", r))
                }
                if (st.mostRow) {
                    val m = visible.filter { (counts[it.pkg] ?: 0) > 0 }.sortedByDescending { counts[it.pkg] ?: 0 }.take(st.cols)
                    if (m.isNotEmpty()) add(QhSection("Most used", m))
                }
                val letterMode = st.sort == "az" || st.sort == "za"
                var lastL = ' '
                visible.forEach { a ->
                    if (letterMode) {
                        val l = letterOf(st.labelOf(a.pkg, a.label))
                        if (l != lastL) { add(l); lastL = l }
                    }
                    add(a)
                }
            }
        }
    }
    val letters = remember(entries) { entries.filterIsInstance<Char>() }
    val listMode = st.drawerList || dStyle == "niagara"
    val span = if (listMode) 1 else st.cols

    CompositionLocalProvider(LocalQh provides st.copy(labelLines = 2, labels = true), LocalQhTextOnly provides (dStyle == "niagara")) {
    // fully opaque (the theme colour had a see-through alpha), and it swallows taps so nothing behind it reacts
    Box(Modifier.fillMaxSize().background(Color(theme.top).copy(alpha = 1f)).pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc > 120f) onClose() }, onDragCancel = { acc = 0f }, onVerticalDrag = { _, dy -> acc += dy })
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(theme.text).copy(alpha = 0.10f), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.combinedClickable(onClick = {}, onLongClick = { if (st.revealGesture && st.hidden.isNotEmpty()) requestReveal() })) {
                            QhSvgIcon(QhSvg.SEARCH, Color(theme.muted), 20.dp)
                        }
                        Box(Modifier.padding(start = 10.dp).weight(1f)) {
                            if (query.isEmpty()) Text("Search apps", color = Color(theme.muted), fontSize = 15.sp)
                            BasicTextField(
                                value = query, onValueChange = onQuery, singleLine = true,
                                textStyle = TextStyle(color = Color(theme.text), fontSize = 15.sp),
                                cursorBrush = SolidColor(Color(theme.accent)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { (entries.firstOrNull { it is QhApp } as? QhApp)?.let { onLaunch(it.pkg) } }),
                                modifier = Modifier.fillMaxWidth().focusRequester(fr),
                            )
                        }
                    }
                }
                Text("\u2715", color = Color(theme.muted), fontSize = 20.sp, modifier = Modifier.clickable { onClose() }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp))
            }
            if (dStyle == "smart" && query.isBlank()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QH_CATS.forEach { c -> QhPill(c, cat == c, theme) { cat = c } }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(span), state = grid,
                    contentPadding = PaddingValues(start = 12.dp, end = 26.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries.size, span = { i -> if (entries[i] is QhApp) GridItemSpan(1) else GridItemSpan(span) }) { i ->
                        val e = entries[i]
                        when (e) {
                            is Char -> Text(e.toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(theme.accent), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
                            is QhSection -> Column {
                                Text(e.title, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(theme.accent), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    e.apps.forEach { a -> Box(Modifier.weight(1f)) { QhAppTile(a, theme, shapeId, isLocked(a.pkg), isDim(a.pkg), true, { onLaunch(a.pkg) }, { onLong(a) }) } }
                                    repeat((st.cols - e.apps.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            is QhExtra -> Surface(shape = RoundedCornerShape(14.dp), color = Color(theme.text).copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp).clickable { e.onClick() }) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(e.sub, fontSize = 11.sp, color = Color(theme.muted), maxLines = 1)
                                }
                            }
                            is QhApp ->
                                if (listMode) QhAppRow(e, theme, shapeId, isLocked(e.pkg), isDim(e.pkg), { onLaunch(e.pkg) }, { onLong(e) })
                                else QhAppTile(e, theme, shapeId, isLocked(e.pkg), isDim(e.pkg), true, { onLaunch(e.pkg) }, { onLong(e) })
                        }
                    }
                }
                if (query.isBlank() && letters.size > 1 && st.azStrip) {
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).heightIn(max = 520.dp).fillMaxHeight(0.85f)
                            .pointerInput(letters) {
                                detectVerticalDragGestures(onDragStart = {}, onDragEnd = {}, onDragCancel = {}, onVerticalDrag = { change, _ ->
                                    val idx = ((change.position.y / size.height.toFloat()) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                                    val at = entries.indexOfFirst { it is Char && it == letters[idx] }
                                    if (at >= 0) scope.launch { grid.scrollToItem(at) }
                                })
                            },
                        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        letters.forEach { c ->
                            Text(c.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(theme.muted),
                                modifier = Modifier.clickable {
                                    val at = entries.indexOfFirst { it is Char && it == c }
                                    if (at >= 0) scope.launch { grid.scrollToItem(at) }
                                }.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }
        if (askReveal) {
            QhDialog(theme, "Open the hidden space", { askReveal = false }) {
                Text("It costs ${st.revealCost} coins each time.", color = Color(theme.text), fontSize = 14.sp)
                QhMenuItem(theme, "Pay ${st.revealCost} coins and open") {
                    askReveal = false
                    vm.spend(st.revealCost) { ok -> if (ok) spaceOpen = true else Toast.makeText(ctx, "Not enough coins", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
    }
}

// ═══════════════════════ the wall ═══════════════════════
/** v213: svg (theme picture), phone wallpaper (the window is transparent), both, a plain colour or an own picture, then a dim layer. */
@Composable
private fun QhWall(st: QhState, theme: QhTheme, stamp: Int, frac: Float, pages: Int) {
    val ctx = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val shift = st.parallax && pages > 1
    val sysWall = st.wall == "phone" || st.wall == "phonesvg"
    // v214: the phone wallpaper moves a little with the page (the launcher tells the system), own walls are shifted here
    LaunchedEffect(frac, shift, st.wall) {
        if (sysWall) runCatching { android.app.WallpaperManager.getInstance(ctx).setWallpaperOffsets(view.windowToken, if (shift) (frac / (pages - 1)).coerceIn(0f, 1f) else 0.5f, 0.5f) }
    }
    val photoWall = st.wall in WALL_PHOTO
    val dim = if (photoWall) maxOf(st.dim, 15) else st.dim     // photo walls always get a light dim so the text stays readable
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().graphicsLayer {
          if (shift && !sysWall) { translationX = -(frac - (pages - 1) / 2f) * 28f; scaleX = 1.12f; scaleY = 1.12f }
      }) {
        when (st.wall) {
            "phone" -> Unit
            "phonesvg" -> QhBackground(theme, fill = false, animate = st.animate)
            "color" -> Box(Modifier.fillMaxSize().background(Color(st.wallColor)))
            "picture" -> {
                val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, stamp) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { android.graphics.BitmapFactory.decodeFile(QhWallFile.file(ctx).absolutePath)?.asImageBitmap() }.getOrNull()
                    }
                }
                val b = bmp
                if (b != null) {
                    val m = if (Build.VERSION.SDK_INT >= 31 && st.blur > 0) Modifier.fillMaxSize().blur((st.blur / 4).dp) else Modifier.fillMaxSize()
                    Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = m)
                } else QhBackground(theme, animate = st.animate)
            }
            else -> QhBackground(theme, animate = st.animate)
        }
      }
        if (dim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim / 100f)))
    }
}

// ═══════════════════════ the launcher ═══════════════════════
@Composable
fun QuestHomeScreen(onLeave: () -> Unit, vm: QuestHomeViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val own = ctx.packageName
    val st = vm.st
    val theme = qhThemeForState(ctx, st)
    val homeTheme = if (st.wall in WALL_PHOTO) qhReadable(theme) else theme
    val hStyle = qhStyleOf(theme)    // light text on a photo wall; dialogs and the drawer keep the real theme
    val shapeId = st.shape.ifEmpty { theme.shape }
    val appMap = remember(vm.apps) { vm.apps.associateBy { it.pkg } }
    val player by vm.player.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val now = vm.now
    val running = player.focusModeUntil > now

    LaunchedEffect(QuestHomeBus.tick) { vm.reloadApps() }
    LaunchedEffect(QuestHomeOn) { if (!QuestHomeOn) onLeave() }

    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var focusSearch by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var homeMenu by remember { mutableStateOf("") }   // v216: "" closed | main | wall | theme | icons
    var wallStamp by remember { mutableStateOf(0) }
    val pickWall = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && qhImportWall(ctx, uri)) { wallStamp += 1; vm.update { it.copy(wall = "picture") } }
    }
    // Android 12+: blur what is behind the window (the phone wallpaper)
    LaunchedEffect(st.wall, st.blur) {
        if (Build.VERSION.SDK_INT >= 31) {
            val px = if (st.wall == "phone" || st.wall == "phonesvg") st.blur * 2 else 0
            runCatching { (ctx as? Activity)?.window?.setBackgroundBlurRadius(px) }
        }
    }
    var menuApp by remember { mutableStateOf<QhApp?>(null) }
    var menuFolder by remember { mutableStateOf<String?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var folderPick by remember { mutableStateOf<QhApp?>(null) }
    var newFolderFor by remember { mutableStateOf<QhApp?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }

    val pageCount = st.pages.size.coerceAtLeast(1)
    val pager = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val drag = remember { QhDrag() }
    val use = QhUseInfo(vm.usageToday, vm.notifPkgs)
    val dragging = drag.key != null && drag.moved > 24f
    val screenW = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var sheetPkg by remember { mutableStateOf<String?>(null) }
    var editApp by remember { mutableStateOf<QhApp?>(null) }
    var iconPickPkg by remember { mutableStateOf<String?>(null) }
    val pickIcon = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pk = iconPickPkg
        if (uri != null && pk != null && com.krishna.questblock.data.QhIcons.importImage(ctx, pk, uri)) {
            qhIconsChanged(); vm.update { it.copy(appIcons = it.appIcons + (pk to "image")) }
        }
    }
    // real Android widgets: pick, bind (asks once), configure, then show on the first page
    val awm = remember { android.appwidget.AppWidgetManager.getInstance(ctx) }
    var pickWidget by remember { mutableStateOf(false) }
    var pendingWidget by remember { mutableStateOf(-1) }
    fun dropWidgetId(id: Int) { runCatching { (ctx as QuestHomeActivity).widgetHost.deleteAppWidgetId(id) }; pendingWidget = -1 }
    fun finishWidget(id: Int) { vm.update { it.copy(hostWidgets = it.hostWidgets + id) }; pendingWidget = -1 }
    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget
        if (id >= 0) { if (r.resultCode == Activity.RESULT_OK) finishWidget(id) else dropWidgetId(id) }
    }
    fun configureOrFinish(id: Int) {
        val cfg = awm.getAppWidgetInfo(id)?.configure
        if (cfg != null) configLauncher.launch(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        else finishWidget(id)
    }
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidget
        if (id >= 0) { if (r.resultCode == Activity.RESULT_OK) configureOrFinish(id) else dropWidgetId(id) }
    }
    fun startWidget(info: android.appwidget.AppWidgetProviderInfo) {
        val id = (ctx as QuestHomeActivity).widgetHost.allocateAppWidgetId()
        pendingWidget = id
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrFinish(id)
        else bindLauncher.launch(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
    }
    // contacts permission for drawer search
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.update { it.copy(searchContacts = ok) }
    }

    LaunchedEffect(QuestHomeBus.homePress) {
        drawer = false; query = ""; settings = false; openFolder = null; menuApp = null; menuFolder = null; homeMenu = ""
        if (pager.currentPage != 0) pager.animateScrollToPage(0)
    }
    LaunchedEffect(QuestHomeBus.openSettings) { if (QuestHomeBus.openSettings > 0) settings = true }
    BackHandler(enabled = true) {
        when {
            homeMenu.isNotEmpty() -> homeMenu = ""
            menuApp != null -> menuApp = null
            folderPick != null -> folderPick = null
            newFolderFor != null -> newFolderFor = null
            renameFolderId != null -> renameFolderId = null
            menuFolder != null -> menuFolder = null
            openFolder != null -> openFolder = null
            settings -> settings = false
            drawer -> { drawer = false; query = "" }
            pager.currentPage != 0 -> scope.launch { pager.animateScrollToPage(0) }
        }
    }

    LaunchedEffect(dragging) {
        while (dragging) {
            val x = drag.pos.x
            if (x < 48f && pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
            else if (x > screenW - 48f && pager.currentPage < pageCount - 1) pager.animateScrollToPage(pager.currentPage + 1)
            delay(700)
        }
    }

    fun openDrawer(search: Boolean) { query = ""; focusSearch = search; drawer = true }
    val studyBlocked: (String) -> Boolean = { pkg -> st.studyFirst && !vm.studyDone && pkg in vm.funPkgs && pkg != own }
    val isLocked: (String) -> Boolean = { pkg ->
        val r: BlockedApp? = blocked.firstOrNull { it.packageName == pkg }
        (r != null && !r.isStudyApp && r.unlockedUntil <= now) || studyBlocked(pkg)
    }
    val isDim: (String) -> Boolean = { pkg -> running && pkg != own && pkg !in vm.focusApps && !st.focusHome }
    // v213: Focus home and "hide locked apps" take apps out of sight (not out of the Vault)
    val isShown: (String) -> Boolean = { pkg ->
        pkg == own || !((st.focusHome && running && pkg !in vm.focusApps) || (st.lockedHide && isLocked(pkg)) || vm.ruleHides(pkg))
    }
    // v213: launcher-only lock. Only apps that are simply locked go straight to the lock page; apps with a daily limit or a
    // time window are left to accessibility (the launcher does not know their state).
    val plainLocked: (String) -> Boolean = { pkg ->
        val r: BlockedApp? = blocked.firstOrNull { it.packageName == pkg }
        r != null && !r.isStudyApp && r.unlockedUntil <= now && r.dailyLimitMin == 0 && r.schedStart < 0
    }
    val launch: (String) -> Unit = { pkg ->
        if (studyBlocked(pkg)) { Toast.makeText(ctx, "Finish one quest today first", Toast.LENGTH_SHORT).show(); qhOpenQuestBlock(ctx, "quests") }
        else if (pkg != own && st.block != "accessibility" && plainLocked(pkg)) { if (st.sheetUnlock) sheetPkg = pkg else qhLockPage(ctx, pkg) }
        else if (running && st.focusHome && pkg != own && pkg !in vm.focusApps) qhOpenQuestBlock(ctx, "focus")
        else { QhUse.bump(ctx, pkg); qhLaunch(ctx, pkg) }
        drawer = false; query = ""; openFolder = null
    }
    val act: (String) -> Unit = { a ->
        when (a) {
            "lock" -> qhLockScreen(ctx)
            "drawer" -> openDrawer(false)
            "search" -> openDrawer(true)
            "menu" -> homeMenu = "main"
            "settings" -> settings = true
            "questblock" -> qhOpenQuestBlock(ctx)
            "focus" -> qhOpenQuestBlock(ctx, "focus")
            "notifications" -> qhNotifications(ctx)
        }
    }
    // dropping a home item: on the dock, on another tile (middle = folder, edge = place before), on the page, or past the edge
    SideEffect {
        drag.onDrop = { key, pos, moved ->
            val kind = key.substringBefore(':')
            val id = key.substringAfter(':')
            if (moved < 30f) {
                if (kind == "d") appMap[id]?.let { menuApp = it }
                else if (id.startsWith("a:")) appMap[id.removePrefix("a:")]?.let { menuApp = it }
                else if (id.startsWith("f:")) menuFolder = id.removePrefix("f:")
            } else {
                val pageNow = pager.currentPage
                val toPage = if (pos.x > screenW - 48f && pageNow == pageCount - 1) pageCount else pageNow
                val dockHit = drag.dockRect?.contains(pos) == true
                val hit = drag.bounds.entries.firstOrNull { (k, r) -> k != key && k.startsWith("p:") && r.contains(pos) }
                val target = hit?.key?.removePrefix("p:")
                val centre = hit?.value?.let { it.deflate(minOf(it.width, it.height) * 0.28f).contains(pos) } ?: false
                when {
                    dockHit && kind == "p" && id.startsWith("a:") -> vm.update { it.toDock(id.removePrefix("a:")) }
                    dockHit -> Unit
                    target != null && kind == "d" -> vm.update { it.removeDock(id).moveToPageEnd("a:$id", pageNow) }
                    target != null -> vm.update { s0 ->
                        when {
                            centre && target.startsWith("f:") && id.startsWith("a:") -> s0.addToFolder(target.removePrefix("f:"), id.removePrefix("a:"))
                            centre && target.startsWith("a:") && id.startsWith("a:") -> s0.mergeApps(id.removePrefix("a:"), target.removePrefix("a:"))
                            else -> s0.moveBefore(id, target)
                        }
                    }
                    kind == "d" -> vm.update { it.removeDock(id).moveToPageEnd("a:$id", toPage) }
                    else -> vm.update { it.moveToPageEnd(id, toPage) }
                }
            }
        }
    }

    CompositionLocalProvider(LocalQh provides st, LocalQhDrag provides drag, LocalQhUse provides use) {
    Box(Modifier.fillMaxSize()) {
        QhWall(st, theme, wallStamp, pager.currentPage + pager.currentPageOffsetFraction, pageCount)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state = pager, userScrollEnabled = !dragging, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val entries = st.pages.getOrNull(page).orEmpty()
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val signed = pager.currentPage - page + pager.currentPageOffsetFraction
                            val off = kotlin.math.abs(signed).coerceIn(0f, 1f)
                            when (st.transition) {
                                "fade" -> alpha = 1f - off * 0.85f
                                "zoom" -> { scaleX = 1f - 0.18f * off; scaleY = 1f - 0.18f * off; alpha = 1f - 0.6f * off }
                                "cube" -> { rotationY = signed * -45f; alpha = 1f - 0.5f * off }
                            }
                        }
                        .pointerInput(Unit) {
                            // v214: two fingers swiping up or down
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var start: Float? = null
                                var lastY = 0f
                                do {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = ev.changes.filter { it.pressed }
                                    if (down.size >= 2) {
                                        val avg = down.map { it.position.y }.average().toFloat()
                                        if (start == null) start = avg
                                        lastY = avg
                                    }
                                } while (ev.changes.any { it.pressed })
                                val s0 = start
                                if (s0 != null) { val d = lastY - s0; if (d < -120f) act(st.g2Up) else if (d > 120f) act(st.g2Down) }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { act(st.gDouble) }, onLongPress = { act(st.gLong) }) }
                        .pointerInput(Unit) {
                            var acc = 0f
                            detectVerticalDragGestures(
                                onDragStart = { acc = 0f },
                                onDragEnd = { if (acc < -120f) act(st.gUp) else if (acc > 120f) act(st.gDown) },
                                onDragCancel = { acc = 0f },
                                onVerticalDrag = { _, dy -> acc += dy },
                            )
                        },
                ) {
                    if (hStyle == "niagara") {
                        // v217: Niagara. The dock apps and this page's apps as one big bottom-aligned text list
                        val dockSet = st.dock.toSet()
                        val niItems = if (page == 0) st.dock.map { "a:$it" } + entries.filter { !(it.startsWith("a:") && it.removePrefix("a:") in dockSet) } else entries
                        Column(Modifier.fillMaxSize()) {
                            if (page == 0) QhWidgets(homeTheme, vm, st) { openDrawer(true) }
                            androidx.compose.foundation.lazy.LazyColumn(
                                Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom,
                                contentPadding = PaddingValues(horizontal = 30.dp, vertical = 8.dp),
                            ) {
                                items(niItems.size) { idx ->
                                    val item = niItems[idx]
                                    if (item.startsWith("a:")) {
                                        val app = appMap[item.removePrefix("a:")]
                                        if (app != null && isShown(app.pkg)) {
                                            val nm = st.labelOf(app.pkg, app.label) + if (isLocked(app.pkg)) "  \uD83D\uDD12" else ""
                                            Text(nm, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color(homeTheme.text), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth().alpha(if (isDim(app.pkg)) 0.35f else 1f)
                                                    .combinedClickable(onClick = { launch(app.pkg) }, onLongClick = { menuApp = app }).padding(vertical = 9.dp))
                                        }
                                    } else {
                                        val f = st.folder(item.removePrefix("f:"))
                                        if (f != null) Text("\u25B8 " + f.name, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color(homeTheme.accent), maxLines = 1,
                                            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { openFolder = f.id }, onLongClick = { menuFolder = f.id }).padding(vertical = 9.dp))
                                    }
                                }
                            }
                        }
                    } else
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(st.cols), userScrollEnabled = page == 0,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        if (page == 0 && hStyle == "lawn") item(span = { GridItemSpan(st.cols) }) {
                            val day = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }
                            val date = remember { SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date()) }
                            Column(Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, bottom = 6.dp)) {
                                Text(day, fontSize = 34.sp, fontWeight = FontWeight.Light, color = Color(homeTheme.text))
                                Text(date, fontSize = 15.sp, color = Color(homeTheme.muted))
                            }
                        }
                        if (page == 0) item(span = { GridItemSpan(st.cols) }) { QhWidgets(homeTheme, vm, st) { openDrawer(true) } }
                        items(entries) { item ->
                            if (item.startsWith("a:")) {
                                val app = appMap[item.removePrefix("a:")]
                                if (app != null && isShown(app.pkg)) QhAppTile(app, homeTheme, shapeId, isLocked(app.pkg), isDim(app.pkg), true, { launch(app.pkg) }, { menuApp = app }, dragKey = "p:$item")
                            } else {
                                val f = st.folder(item.removePrefix("f:"))
                                if (f != null) QhFolderTile(f, homeTheme, shapeId, { openFolder = f.id }, { menuFolder = f.id }, dragKey = "p:$item")
                            }
                        }
                    }
                }
            }
            if (pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    when (st.dots) {
                        "number" -> Text("${pager.currentPage + 1} / $pageCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(homeTheme.muted))
                        "bar" -> repeat(pageCount) { i ->
                            Box(Modifier.padding(horizontal = 2.dp).height(3.dp).width(if (i == pager.currentPage) 26.dp else 12.dp)
                                .background(if (i == pager.currentPage) Color(homeTheme.accent) else Color(homeTheme.muted).copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                        }
                        else -> repeat(pageCount) { i ->
                            Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp)
                                .background(if (i == pager.currentPage) Color(homeTheme.accent) else Color(homeTheme.muted).copy(alpha = 0.5f), CircleShape))
                        }
                    }
                }
            }
            if (hStyle == "smart") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Social", "Media", "Games", "Tools").forEach { c -> QhPill(c, false, homeTheme) { qhPendingCat = c; openDrawer(false) } }
                }
            }
            // swipe-up handle
            Box(
                Modifier.fillMaxWidth().height(28.dp).clickable { openDrawer(false) }
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc < -40f) openDrawer(false) }, onDragCancel = { acc = 0f }, onVerticalDrag = { _, dy -> acc += dy })
                    },
                contentAlignment = Alignment.Center,
            ) { QhSvgIcon(QhSvg.HANDLE, Color(homeTheme.muted), 28.dp) }
            // dock: up to four apps and the drawer button (Niagara has none; its apps are the list)
            if (hStyle != "niagara") Surface(
                shape = RoundedCornerShape(if (st.dockStyle == "floating" || hStyle == "lawn") 40.dp else 28.dp),
                color = if (st.dockStyle == "none") Color.Transparent else Color(homeTheme.dock),
                border = if (st.dockStyle == "floating") androidx.compose.foundation.BorderStroke(1.dp, Color(homeTheme.muted).copy(alpha = 0.35f)) else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (st.dockStyle == "floating") 22.dp else 10.dp, vertical = 6.dp)
                    .onGloballyPositioned { drag.dockRect = it.boundsInRoot() },
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    st.dock.forEach { pkg ->
                        val app = appMap[pkg]
                        if (app != null && isShown(pkg)) QhAppTile(app, homeTheme, shapeId, isLocked(pkg), isDim(pkg), false, { launch(pkg) }, { menuApp = app }, dragKey = "d:$pkg")
                    }
                    QhTile(homeTheme, shapeId, "Apps", false, false, false, { openDrawer(false) }, { act(st.gLong) }) {
                        Box(contentAlignment = Alignment.Center) {
                            QhSvgIcon(QhSvg.APPS, Color(homeTheme.accent), 26.dp, filled = true)
                            if (st.hiddenBadge && st.hidden.isNotEmpty()) {
                                Box(Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp).size(16.dp).background(Color(homeTheme.accent), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(st.hidden.size.coerceAtMost(99).toString(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(homeTheme.top))
                                }
                            }
                        }
                    }
                }
            }
            if (hStyle == "lawn") {
                Surface(
                    shape = RoundedCornerShape(30.dp), color = Color(homeTheme.text).copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp).clickable { openDrawer(true) },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        QhSvgIcon(QhSvg.SEARCH, Color(homeTheme.muted), 20.dp)
                        Text("Search apps", color = Color(homeTheme.muted), fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }

        if (drawer) {
            QhDrawer(
                theme, shapeId, st, vm.apps, query, focusSearch, vm, isLocked, isDim, isShown,
                onQuery = { query = it }, onClose = { drawer = false; query = "" },
                onLaunch = launch, onLong = { menuApp = it },
            )
        }
        // v213: the settings page is a full screen over the launcher
        if (settings) {
            QhSettingsPage(
                theme = theme, st = st, apps = appMap,
                update = { f -> vm.update(f) },
                pickPicture = { pickWall.launch("image/*") },
                onOpenQuestBlock = { settings = false; qhOpenQuestBlock(ctx) },
                onHomeApp = { qhPickHomeApp(ctx) },
                onTurnOff = {
                    if (TermuxMode.strictNow(ctx) || vm.focusRunning()) {
                        android.widget.Toast.makeText(ctx, "Not while a Focus session is running", android.widget.Toast.LENGTH_SHORT).show()
                    } else { settings = false; QuestHomeMode.set(ctx, false); AppThemes.enforce(ctx) }
                },
                onReset = { QhStore.reset(ctx); vm.update { QhStore.load(ctx) }; vm.reloadApps() },
                onClose = { settings = false },
                onAddWidget = { pickWidget = true },
                onRemoveWidget = { id -> runCatching { (ctx as QuestHomeActivity).widgetHost.deleteAppWidgetId(id) }; vm.update { it.copy(hostWidgets = it.hostWidgets - id) } },
                onUnhide = { pkg -> vm.unhide(pkg) },
                askContacts = { askContacts.launch(android.Manifest.permission.READ_CONTACTS) },
                vm = vm,
            )
        }
        // the app is being dragged: a floating copy follows the finger
        val dk = drag.key
        if (dk != null && drag.moved > 24f) {
            val raw = dk.substringAfter(':')
            Box(
                Modifier.offset { androidx.compose.ui.unit.IntOffset(drag.pos.x.toInt() - 80, drag.pos.y.toInt() - 80) }
                    .size(60.dp).background(Color(homeTheme.tile).copy(alpha = 0.95f), qhShape(shapeId)),
                contentAlignment = Alignment.Center,
            ) {
                if (raw.startsWith("f:")) QhSvgIcon(QhSvg.APPS, Color(homeTheme.accent), 28.dp, filled = true)
                else QhIcon(raw.removePrefix("a:"), 38.dp)
            }
        }
        // launcher-only unlock: the price and the coin check inline, no flash of the app
        sheetPkg?.let { pk ->
            val a = appMap[pk]
            if (a == null) { sheetPkg = null } else {
                QhUnlockSheet(theme, vm, a, player.coins,
                    onClose = { sheetPkg = null }, onFull = { sheetPkg = null; qhLockPage(ctx, pk) },
                    onOpen = { sheetPkg = null; QhUse.bump(ctx, pk); qhLaunch(ctx, pk) })
            }
        }
    }
    }

    // ── pick an Android widget ──
    // ── v216: long press on empty home space ──
    if (homeMenu.isNotEmpty()) {
        val closeMenu = { homeMenu = "" }
        when (homeMenu) {
            "wall" -> QhDialog(theme, "Wallpaper", closeMenu) {
                QhMenuItem(theme, "Set phone wallpaper\u2026") {
                    closeMenu()
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(ctx, "Can't open the wallpaper picker", Toast.LENGTH_SHORT).show() }
                }
                QhMenuItem(theme, "Pick a picture for the launcher\u2026") { closeMenu(); pickWall.launch("image/*") }
                Text("Use the wall as", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    QhPill("Theme", st.wall == "svg", theme) { vm.update { it.copy(wall = "svg") } }
                    QhPill("Phone", st.wall == "phone", theme) { vm.update { it.copy(wall = "phone") } }
                    QhPill("Phone + theme", st.wall == "phonesvg", theme) { vm.update { it.copy(wall = "phonesvg") } }
                    QhPill("Colour", st.wall == "color", theme) { vm.update { it.copy(wall = "color") } }
                    if (QhWallFile.exists(ctx)) QhPill("My picture", st.wall == "picture", theme) { vm.update { it.copy(wall = "picture") } }
                }
                Text("Dim ${st.dim}%", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QhPill("\u2212 10", false, theme) { vm.update { it.copy(dim = (it.dim - 10).coerceIn(0, 80)) } }
                    QhPill("+ 10", false, theme) { vm.update { it.copy(dim = (it.dim + 10).coerceIn(0, 80)) } }
                }
                Text("Blur ${st.blur}%", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QhPill("\u2212 10", false, theme) { vm.update { it.copy(blur = (it.blur - 10).coerceIn(0, 100)) } }
                    QhPill("+ 10", false, theme) { vm.update { it.copy(blur = (it.blur + 10).coerceIn(0, 100)) } }
                }
            }
            "theme" -> QhDialog(theme, "Theme", closeMenu) {
                qhAllThemes(ctx).forEach { t -> QhMenuItem(theme, (if (st.theme == t.id) "\u2713  " else "") + t.name) { vm.update { it.copy(theme = t.id) } } }
                // v219: the other app themes can be equipped from here too
                val others = AppThemes.enabledList(ctx).filter { it != "home" }
                if (others.isNotEmpty()) Text("App theme", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                others.forEach { id -> QhMenuItem(theme, AppThemes.label(id)) {
                    if (vm.focusRunning()) { Toast.makeText(ctx, "Not while a Focus session is running", Toast.LENGTH_SHORT).show() }
                    else { closeMenu(); AppThemes.apply(ctx, id)?.let { err -> Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show() } }
                } }
            }
            "icons" -> QhDialog(theme, "Icons", closeMenu) {
                Text("Icons come from", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    com.krishna.questblock.data.QhSet.ICON_SRC.forEach { src -> QhPill(src, st.iconSrc == src, theme) { vm.update { it.copy(iconSrc = src) }; qhIconsChanged() } }
                }
                Text("Size ${st.iconSize}%", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QhPill("\u2212 10", false, theme) { vm.update { it.copy(iconSize = (it.iconSize - 10).coerceIn(70, 130)) } }
                    QhPill("+ 10", false, theme) { vm.update { it.copy(iconSize = (it.iconSize + 10).coerceIn(70, 130)) } }
                }
                Text("Columns ${st.cols}", fontSize = 12.sp, color = Color(theme.muted), modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4, 5, 6).forEach { n -> QhPill("$n", st.cols == n, theme) { vm.update { it.copy(cols = n) } } }
                }
                QhMenuItem(theme, if (st.labels) "Hide labels" else "Show labels") { vm.update { it.copy(labels = !it.labels) } }
            }
            else -> QhDialog(theme, "Home", closeMenu) {
                QhMenuItem(theme, "\uD83D\uDDBC  Wallpaper") { homeMenu = "wall" }
                QhMenuItem(theme, "\uD83E\uDDE9  Widgets") { closeMenu(); pickWidget = true }
                QhMenuItem(theme, "\uD83C\uDFA8  Theme") { homeMenu = "theme" }
                QhMenuItem(theme, "\u25FB  Icons and grid") { homeMenu = "icons" }
                QhMenuItem(theme, "\u2699  Launcher settings") { closeMenu(); settings = true }
                QhMenuItem(theme, "\u2694  Open QuestBlock") { closeMenu(); qhOpenQuestBlock(ctx) }
            }
        }
    }

    if (pickWidget) {
        val list = remember { runCatching { awm.installedProviders.sortedBy { it.loadLabel(ctx.packageManager).lowercase() } }.getOrDefault(emptyList()) }
        QhDialog(theme, "Add an Android widget", { pickWidget = false }) {
            if (list.isEmpty()) Text("No widgets found on this phone.", color = Color(theme.muted))
            list.forEach { info -> QhMenuItem(theme, info.loadLabel(ctx.packageManager)) { pickWidget = false; startWidget(info) } }
        }
    }

    // ── edit name and icon of one app ──
    editApp?.let { app -> QhEditDialog(theme, vm, app, shapeId, { editApp = null }, { iconPickPkg = app.pkg; pickIcon.launch("image/*") }) }

    // ── long-press menu of an app ──
    menuApp?.let { app ->
        val onHomePage = st.onHome(app.pkg)
        val inFolder = st.folderOf(app.pkg)
        val isOwn = app.pkg == own
        var more by remember(app.pkg) { mutableStateOf(false) }
        val shortcuts by produceState(emptyList<android.content.pm.ShortcutInfo>(), app.pkg) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                    if (!la.hasShortcutHostPermission()) emptyList() else la.getShortcuts(
                        android.content.pm.LauncherApps.ShortcutQuery().setPackage(app.pkg).setQueryFlags(
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                        android.os.Process.myUserHandle(),
                    )?.take(4) ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }
        QhDialog(theme, st.labelOf(app.pkg, app.label), { menuApp = null }) {
            shortcuts.forEach { sc ->
                QhMenuItem(theme, "\u2197 " + (sc.shortLabel?.toString() ?: sc.id)) {
                    runCatching { (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps).startShortcut(sc, null, null) }
                    menuApp = null
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                QhPill("Edit", false, theme) { editApp = app; menuApp = null }
                if (!isOwn) QhPill("App info", false, theme) { qhAppInfo(ctx, app.pkg); menuApp = null }
                if (!isOwn) QhPill("Uninstall", false, theme) { qhUninstall(ctx, app.pkg); menuApp = null }
            }
            QhMenuItem(theme, if (more) "Less \u25B4" else "More \u25BE") { more = !more }
            if (more) {
                if (!onHomePage && inFolder == null) QhMenuItem(theme, "Add to home") { vm.update { it.addHome(app.pkg) }; menuApp = null }
                if (onHomePage) {
                    QhMenuItem(theme, "Remove from home") { vm.update { it.removeHome(app.pkg) }; menuApp = null }
                    QhMenuItem(theme, "Move earlier") { vm.update { it.moveItem("a:${app.pkg}", -1) } }
                    QhMenuItem(theme, "Move later") { vm.update { it.moveItem("a:${app.pkg}", 1) } }
                    QhMenuItem(theme, "Move to previous page") { vm.update { it.moveToPage("a:${app.pkg}", -1) } }
                    QhMenuItem(theme, "Move to next page") { vm.update { it.moveToPage("a:${app.pkg}", 1) } }
                }
                if (app.pkg in st.dock) QhMenuItem(theme, "Remove from dock") { vm.update { it.removeDock(app.pkg) }; menuApp = null }
                else if (st.dock.size < st.dockMax) QhMenuItem(theme, "Add to dock") { vm.update { it.addDock(app.pkg) }; menuApp = null }
                QhMenuItem(theme, "Move to folder\u2026") { folderPick = app; menuApp = null }
                if (inFolder != null) QhMenuItem(theme, "Take out of folder (${inFolder.name})") { vm.update { it.removeFromFolder(inFolder.id, app.pkg) }; menuApp = null }
                if (!isOwn) {
                    QhMenuItem(theme, if (app.pkg in st.hidden) "Unhide" else "Hide") { if (app.pkg in st.hidden) vm.unhide(app.pkg) else vm.update { it.hide(app.pkg) }; menuApp = null }
                }
            }
        }
    }

    // ── pick a folder for an app ──
    folderPick?.let { app ->
        QhDialog(theme, "Move ${app.label} to", { folderPick = null }) {
            st.folders.forEach { f ->
                QhMenuItem(theme, f.name) { vm.update { it.addToFolder(f.id, app.pkg) }; folderPick = null }
            }
            QhMenuItem(theme, "New folder\u2026") { newFolderFor = app; folderPick = null }
        }
    }
    newFolderFor?.let { app ->
        QhNameDialog(theme, "New folder", "", onOk = { n -> vm.update { it.newFolder(n, listOf(app.pkg)) }; newFolderFor = null }, onDismiss = { newFolderFor = null })
    }

    // ── folders ──
    openFolder?.let { id ->
        val f = st.folder(id)
        if (f == null) { openFolder = null } else {
            QhDialog(theme, f.name, { openFolder = null }) {
                val list = f.apps.mapNotNull { appMap[it] }
                Box(Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
                        items(list) { app ->
                            CompositionLocalProvider(LocalQh provides st) {
                                QhAppTile(app, theme, shapeId, isLocked(app.pkg), isDim(app.pkg), true, { launch(app.pkg) }, { menuApp = app })
                            }
                        }
                    }
                }
            }
        }
    }
    menuFolder?.let { id ->
        val f = st.folder(id)
        if (f == null) { menuFolder = null } else {
            QhDialog(theme, f.name, { menuFolder = null }) {
                QhMenuItem(theme, "Rename") { renameFolderId = id; menuFolder = null }
                QhMenuItem(theme, "Move earlier") { vm.update { it.moveItem("f:$id", -1) } }
                QhMenuItem(theme, "Move later") { vm.update { it.moveItem("f:$id", 1) } }
                QhMenuItem(theme, "Delete folder (apps go back to the home pages)", danger = true) { vm.update { it.deleteFolder(id) }; menuFolder = null }
            }
        }
    }
    renameFolderId?.let { id ->
        val f = st.folder(id)
        if (f == null) { renameFolderId = null } else {
            QhNameDialog(theme, "Rename folder", f.name, onOk = { n -> vm.update { it.renameFolder(id, n) }; renameFolderId = null }, onDismiss = { renameFolderId = null })
        }
    }
}


// ═══════════════════════ unlock sheet (launcher-only blocking) ═══════════════════════
/** A bottom sheet with the price and the coin check, so a locked app can be unlocked without leaving Quest Home. */
@Composable
private fun QhUnlockSheet(theme: QhTheme, vm: QuestHomeViewModel, app: QhApp, coins: Int, onClose: () -> Unit, onFull: () -> Unit, onOpen: () -> Unit) {
    var blocks by remember { mutableStateOf(((vm.blocked.value.firstOrNull { it.packageName == app.pkg }?.unlockMinutes ?: 15) / 5).coerceIn(1, 144)) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val price = vm.price(app.pkg, blocks)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { onClose() }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color(theme.bottom).copy(alpha = 1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}.navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Color(theme.tile), qhShape(theme.shape)), contentAlignment = Alignment.Center) { QhIcon(app.pkg, 32.dp) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("${app.label} is locked", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(theme.text))
                    Text("You have $coins coins", fontSize = 13.sp, color = Color(theme.muted))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text("\u2212", fontSize = 26.sp, color = Color(theme.accent), modifier = Modifier.clickable { blocks = (blocks - 1).coerceAtLeast(1) }.padding(horizontal = 18.dp))
                Text("${blocks * 5} min", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(theme.text))
                Text("+", fontSize = 26.sp, color = Color(theme.accent), modifier = Modifier.clickable { blocks = (blocks + 1).coerceAtMost(144) }.padding(horizontal = 18.dp))
            }
            if (coins >= price) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(theme.accent), modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    busy = true
                    vm.unlockApp(app.pkg, blocks) { ok, msg -> busy = false; if (ok) onOpen() else err = msg }
                }) {
                    Text(if (busy) "Unlocking\u2026" else "Unlock for $price coins", color = Color(theme.top), fontWeight = FontWeight.Black, fontSize = 16.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
                }
            } else {
                Text("Not enough coins: $price needed. Finish a quest first.", color = Color(0xFFFFB84D), fontSize = 14.sp)
            }
            if (err.isNotEmpty()) Text(err, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("More options", color = Color(theme.muted), fontSize = 13.sp, modifier = Modifier.clickable { onFull() }.padding(6.dp))
                Text("Cancel", color = Color(theme.muted), fontSize = 13.sp, modifier = Modifier.clickable { onClose() }.padding(6.dp))
            }
        }
    }
}

// ═══════════════════════ edit one app ═══════════════════════
private val QH_TINTS = listOf(0xFF7C5CFFL, 0xFF2D9CDBL, 0xFF27AE60L, 0xFFF2994AL, 0xFFEB5757L, 0xFFBB6BD9L, 0xFF56CCF2L, 0xFFF2C94CL)

@Composable
private fun QhEditDialog(theme: QhTheme, vm: QuestHomeViewModel, app: QhApp, shapeId: String, onClose: () -> Unit, onGallery: () -> Unit) {
    val st = vm.st
    var label by remember { mutableStateOf(st.appLabels[app.pkg] ?: "") }
    val cur = st.appIcons[app.pkg]
    var src by remember { mutableStateOf(when { cur == null -> "default"; cur.startsWith("emoji:") -> "emoji"; cur.startsWith("letters:") -> "letters"; else -> cur }) }
    var text by remember { mutableStateOf(cur.orEmpty().let { if (it.contains(':')) it.substringAfter(':') else "" }) }
    var shape by remember { mutableStateOf(st.appShapes[app.pkg] ?: "") }
    var tint by remember { mutableStateOf(st.appColors[app.pkg] ?: 0L) }
    val acc = Color(theme.accent)
    @Composable fun Pills(opts: List<String>, now: String, name: (String) -> String = { it }, pick: (String) -> Unit) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { o -> QhPill(name(o), o == now, theme) { pick(o) } }
        }
    }
    AlertDialog(
        onDismissRequest = onClose, containerColor = Color(theme.bottom),
        title = { Text(app.label, color = Color(theme.text), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(24) }, singleLine = true, label = { Text("Name on the launcher") }, placeholder = { Text(app.label) })
                Text("Icon", color = Color(theme.muted), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Pills(listOf("default", "pack", "image", "emoji", "letters"), src, { when (it) { "default" -> "Default"; "pack" -> "From pack"; "image" -> "Gallery"; "emoji" -> "Emoji"; else -> "Letters" } }) {
                    src = it
                    if (it == "image") onGallery()
                }
                if (src == "emoji" || src == "letters") OutlinedTextField(value = text, onValueChange = { text = it.take(if (src == "emoji") 4 else 2) }, singleLine = true, label = { Text(if (src == "emoji") "One emoji" else "One or two letters") })
                Text("Shape (this app only)", color = Color(theme.muted), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Pills(listOf("") + QH_SHAPES, shape, { if (it.isEmpty()) "theme" else it }) { shape = it }
                Text("Tile colour", color = Color(theme.muted), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(30.dp).background(Color(theme.tile), CircleShape).clickable { tint = 0L }) { if (tint == 0L) Text("\u2713", Modifier.align(Alignment.Center), color = acc) }
                    QH_TINTS.forEach { c -> Box(Modifier.size(30.dp).background(Color(c), CircleShape).clickable { tint = c }) { if (tint == c) Text("\u2713", Modifier.align(Alignment.Center), color = Color.White) } }
                }
                Text("Reset everything for this app", color = Color(0xFFFF6B6B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp).clickable {
                        com.krishna.questblock.data.QhIcons.deleteImage(vm.appContext(), app.pkg)
                        qhIconsChanged()
                        vm.update { it.copy(appLabels = it.appLabels - app.pkg, appIcons = it.appIcons - app.pkg, appShapes = it.appShapes - app.pkg, appColors = it.appColors - app.pkg) }
                        onClose()
                    })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val icon: String? = when (src) {
                    "default" -> null
                    "pack" -> "pack"
                    "image" -> if (com.krishna.questblock.data.QhIcons.hasImage(vm.appContext(), app.pkg)) "image" else null
                    "emoji" -> text.trim().takeIf { it.isNotEmpty() }?.let { "emoji:$it" }
                    else -> text.trim().takeIf { it.isNotEmpty() }?.let { "letters:${it.uppercase()}" }
                }
                vm.update { s0 ->
                    s0.copy(
                        appLabels = if (label.isBlank()) s0.appLabels - app.pkg else s0.appLabels + (app.pkg to label.trim()),
                        appIcons = if (icon == null) s0.appIcons - app.pkg else s0.appIcons + (app.pkg to icon),
                        appShapes = if (shape.isEmpty()) s0.appShapes - app.pkg else s0.appShapes + (app.pkg to shape),
                        appColors = if (tint == 0L) s0.appColors - app.pkg else s0.appColors + (app.pkg to tint),
                    )
                }
                qhIconsChanged()
                onClose()
            }) { Text("Save", color = acc, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = Color(theme.muted)) } },
    )
}
