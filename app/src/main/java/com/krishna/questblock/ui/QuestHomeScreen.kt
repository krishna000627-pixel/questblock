@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
package com.krishna.questblock.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
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

private val WIDGET_NAMES = listOf("clock" to "Clock", "status" to "Status", "focus" to "Focus", "quests" to "Quests", "homework" to "Homework")

/** v213: accents are stripped (Ä, É count as A, E); digits, symbols and anything else go under "#". */
private fun letterOf(label: String): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    val base = java.text.Normalizer.normalize(first.toString(), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (base in 'A'..'Z') base else '#'
}

private val LocalQh = compositionLocalOf { QhState() }

private val WALL_PHOTO = setOf("phone", "phonesvg", "picture")

// ═══════════════════════ small building blocks ═══════════════════════
@Composable
private fun QhIcon(pkg: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val bmp by produceState(QhIconCache.get(pkg), pkg) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { QhIconCache.load(ctx, pkg) }
    }
    val b = bmp
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    else Spacer(Modifier.size(size))
}

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

@Composable
private fun QhTile(
    theme: QhTheme, shapeId: String, label: String, locked: Boolean, dim: Boolean, showLabel: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit,
) {
    val look = LocalQh.current
    val boxSize = (56f * look.iconSize / 100f).dp
    Column(
        modifier.combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 6.dp, horizontal = 2.dp).alpha(if (dim) 0.35f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(boxSize).background(if (look.tile) Color(theme.tile) else Color.Transparent, qhShape(shapeId)), contentAlignment = Alignment.Center) {
            icon()
            if (locked) {
                // v213: the badge sits on the corner of any icon shape, a little outside the tile so it is never cut
                Box(Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(18.dp).background(Color(theme.top).copy(alpha = 1f), CircleShape), contentAlignment = Alignment.Center) {
                    QhSvgIcon(QhSvg.LOCK, Color(theme.accent), 12.dp)
                }
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
    showLabel: Boolean = true, onClick: () -> Unit, onLong: () -> Unit,
) {
    val look = LocalQh.current
    QhTile(theme, shapeId, look.labelOf(app.pkg, app.label), locked, dim, showLabel, onClick, onLong) { QhIcon(app.pkg, (36f * look.iconSize / 100f).dp) }
}

@Composable
private fun QhFolderTile(folder: QhFolder, theme: QhTheme, shapeId: String, onClick: () -> Unit, onLong: () -> Unit) {
    QhTile(theme, shapeId, folder.name, false, false, true, onClick, onLong) {
        val first = folder.apps.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { first.take(2).forEach { QhIcon(it, 20.dp) } }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { first.drop(2).forEach { QhIcon(it, 20.dp) } }
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
private fun QhWidgets(theme: QhTheme, vm: QuestHomeViewModel, widgets: Set<String>, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val player by vm.player.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val now = vm.now
    val clock by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(10_000L) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        if ("clock" in widgets) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(clock)), fontSize = 52.sp, fontWeight = FontWeight.Light, color = Color(theme.text))
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(clock)), fontSize = 14.sp, color = Color(theme.muted))
                }
                Box(Modifier.size(44.dp).background(Color(theme.tile), CircleShape).clickable { onSearch() }, contentAlignment = Alignment.Center) {
                    QhSvgIcon(QhSvg.SEARCH, Color(theme.text), 22.dp)
                }
            }
        }
        if ("status" in widgets) {
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
        if ("focus" in widgets) {
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
                    Text("\u26A1 Start a Focus session", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(theme.text))
                }
            }
        }
        if ("quests" in widgets) {
            val dayEnd = nextRunDayStart(0, now) - 1
            val open: List<TaskEntity> = tasks.filter { !it.done && it.isAvailableNow(now) && it.isForToday(dayEnd) }.take(3)
            QhCard(theme) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quests today", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(theme.muted),
                        modifier = Modifier.clickable { qhOpenQuestBlock(ctx, "quests") })
                    if (open.isEmpty()) Text("Nothing open. Nice.", fontSize = 13.sp, color = Color(theme.text))
                    open.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.complete(t) }) {
                            Box(Modifier.size(18.dp).background(Color.Transparent, CircleShape).background(Color(theme.text).copy(alpha = 0.12f), CircleShape))
                            Text(t.title, fontSize = 14.sp, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    if (open.isNotEmpty()) Text("Tap one to finish it", fontSize = 11.sp, color = Color(theme.muted))
                }
            }
        }
        if ("homework" in widgets) {
            QhCard(theme, onClick = { qhOpenQuestBlock(ctx, "homework") }) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("\uD83D\uDCDD Homework due soon", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(theme.muted))
                    if (vm.hw.isEmpty()) Text("Nothing in the next 24 hours", fontSize = 13.sp, color = Color(theme.text))
                    vm.hw.take(3).forEach {
                        Text("${com.krishna.questblock.data.Hw.subjectName(it.subj)}: ${it.text}  \u00b7  ${com.krishna.questblock.data.Hw.dueLabel(it.due, now)}",
                            fontSize = 13.sp, color = Color(theme.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ═══════════════════════ app drawer ═══════════════════════
@Composable
private fun QhDrawer(
    theme: QhTheme, shapeId: String, st: QhState, apps: List<QhApp>, query: String, focusSearch: Boolean,
    isLocked: (String) -> Boolean, isDim: (String) -> Boolean, isShown: (String) -> Boolean,
    onQuery: (String) -> Unit, onClose: () -> Unit, onLaunch: (String) -> Unit, onLong: (QhApp) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) { if (focusSearch) { runCatching { fr.requestFocus() }; kb?.show() } }

    val visible = remember(apps, st.hidden, st.searchHidden, st.appLabels, query, isShown) {
        val q = query.trim().lowercase()
        apps.filter { a ->
            val name = st.labelOf(a.pkg, a.label)
            val showHidden = q.isNotEmpty() && st.searchHidden
            (a.pkg !in st.hidden || showHidden) && isShown(a.pkg) && (q.isEmpty() || name.lowercase().contains(q))
        }.sortedWith(compareBy<QhApp>({ letterOf(st.labelOf(it.pkg, it.label)) != '#' }, { st.labelOf(it.pkg, it.label).lowercase() }))
    }
    val entries: List<Any> = remember(visible, query) {
        if (query.isNotBlank()) visible
        else buildList<Any> {
            var last = ' '
            visible.forEach { a ->
                val l = letterOf(st.labelOf(a.pkg, a.label))
                if (l != last) { add(l); last = l }
                add(a)
            }
        }
    }
    val letters = remember(entries) { entries.filterIsInstance<Char>() }

    CompositionLocalProvider(LocalQh provides st.copy(labelLines = 2, labels = true)) {
    // v213: fully opaque (the theme colour had a see-through alpha), and it swallows taps so nothing behind it reacts
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
                        QhSvgIcon(QhSvg.SEARCH, Color(theme.muted), 20.dp)
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(st.cols), state = grid,
                    contentPadding = PaddingValues(start = 12.dp, end = 26.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries.size, span = { i -> if (entries[i] is Char) GridItemSpan(st.cols) else GridItemSpan(1) }) { i ->
                        val e = entries[i]
                        if (e is Char) {
                            Text(e.toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(theme.accent), modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp))
                        } else if (e is QhApp) {
                            QhAppTile(e, theme, shapeId, isLocked(e.pkg), isDim(e.pkg), true, { onLaunch(e.pkg) }, { onLong(e) })
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
    }
    }
}

// ═══════════════════════ the wall ═══════════════════════
/** v213: svg (theme picture), phone wallpaper (the window is transparent), both, a plain colour or an own picture, then a dim layer. */
@Composable
private fun QhWall(st: QhState, theme: QhTheme, stamp: Int) {
    val ctx = LocalContext.current
    val photoWall = st.wall in WALL_PHOTO
    val dim = if (photoWall) maxOf(st.dim, 15) else st.dim     // photo walls always get a light dim so the text stays readable
    Box(Modifier.fillMaxSize()) {
        when (st.wall) {
            "phone" -> Unit
            "phonesvg" -> QhBackground(theme, fill = false)
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
                } else QhBackground(theme)
            }
            else -> QhBackground(theme)
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
    val theme = qhTheme(st.theme)
    val homeTheme = if (st.wall in WALL_PHOTO) qhReadable(theme) else theme    // light text on a photo wall; dialogs and the drawer keep the real theme
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

    LaunchedEffect(QuestHomeBus.homePress) {
        drawer = false; query = ""; settings = false; openFolder = null; menuApp = null; menuFolder = null
        if (pager.currentPage != 0) pager.animateScrollToPage(0)
    }
    LaunchedEffect(QuestHomeBus.openSettings) { if (QuestHomeBus.openSettings > 0) settings = true }
    BackHandler(enabled = true) {
        when {
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

    fun openDrawer(search: Boolean) { query = ""; focusSearch = search; drawer = true }
    val isLocked: (String) -> Boolean = { pkg ->
        val r: BlockedApp? = blocked.firstOrNull { it.packageName == pkg }
        r != null && !r.isStudyApp && r.unlockedUntil <= now
    }
    val isDim: (String) -> Boolean = { pkg -> running && pkg != own && pkg !in vm.focusApps && !st.focusHome }
    // v213: Focus home and "hide locked apps" take apps out of sight (not out of the Vault)
    val isShown: (String) -> Boolean = { pkg ->
        pkg == own || !((st.focusHome && running && pkg !in vm.focusApps) || (st.lockedHide && isLocked(pkg)))
    }
    // v213: launcher-only lock. Only apps that are simply locked go straight to the lock page; apps with a daily limit or a
    // time window are left to accessibility (the launcher does not know their state).
    val plainLocked: (String) -> Boolean = { pkg ->
        val r: BlockedApp? = blocked.firstOrNull { it.packageName == pkg }
        r != null && !r.isStudyApp && r.unlockedUntil <= now && r.dailyLimitMin == 0 && r.schedStart < 0
    }
    val launch: (String) -> Unit = { pkg ->
        if (pkg != own && st.block != "accessibility" && plainLocked(pkg)) qhLockPage(ctx, pkg)
        else if (running && st.focusHome && pkg != own && pkg !in vm.focusApps) qhOpenQuestBlock(ctx, "focus")
        else qhLaunch(ctx, pkg)
        drawer = false; query = ""; openFolder = null
    }
    val act: (String) -> Unit = { a ->
        when (a) {
            "lock" -> qhLockScreen(ctx)
            "drawer" -> openDrawer(false)
            "search" -> openDrawer(true)
            "settings" -> settings = true
            "questblock" -> qhOpenQuestBlock(ctx)
            "focus" -> qhOpenQuestBlock(ctx, "focus")
        }
    }

    CompositionLocalProvider(LocalQh provides st) {
    Box(Modifier.fillMaxSize()) {
        QhWall(st, theme, wallStamp)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val entries = st.pages.getOrNull(page).orEmpty()
                Box(
                    Modifier.fillMaxSize()
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(st.cols), userScrollEnabled = page == 0,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.fillMaxSize(),
                    ) {
                        if (page == 0) item(span = { GridItemSpan(st.cols) }) { QhWidgets(homeTheme, vm, st.widgets) { openDrawer(true) } }
                        items(entries) { item ->
                            if (item.startsWith("a:")) {
                                val app = appMap[item.removePrefix("a:")]
                                if (app != null && isShown(app.pkg)) QhAppTile(app, homeTheme, shapeId, isLocked(app.pkg), isDim(app.pkg), true, { launch(app.pkg) }, { menuApp = app })
                            } else {
                                val f = st.folder(item.removePrefix("f:"))
                                if (f != null) QhFolderTile(f, homeTheme, shapeId, { openFolder = f.id }, { menuFolder = f.id })
                            }
                        }
                    }
                }
            }
            if (pageCount > 1) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pageCount) { i ->
                        Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp)
                            .background(if (i == pager.currentPage) Color(homeTheme.accent) else Color(homeTheme.muted).copy(alpha = 0.5f), CircleShape))
                    }
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
            // dock: up to four apps and the drawer button
            Surface(shape = RoundedCornerShape(28.dp), color = Color(homeTheme.dock), modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    st.dock.forEach { pkg ->
                        val app = appMap[pkg]
                        if (app != null && isShown(pkg)) QhAppTile(app, homeTheme, shapeId, isLocked(pkg), isDim(pkg), false, { launch(pkg) }, { menuApp = app })
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
        }

        if (drawer) {
            QhDrawer(
                theme, shapeId, st, vm.apps, query, focusSearch, isLocked, isDim, isShown,
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
                    } else { settings = false; QuestHomeMode.set(ctx, false) }
                },
                onReset = { QhStore.reset(ctx); vm.update { QhStore.load(ctx) }; vm.reloadApps() },
                onClose = { settings = false },
            )
        }
    }
    }

    // ── long-press menu of an app ──
    menuApp?.let { app ->
        val onHomePage = st.onHome(app.pkg)
        val inFolder = st.folderOf(app.pkg)
        val isOwn = app.pkg == own
        QhDialog(theme, app.label, { menuApp = null }) {
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
                QhMenuItem(theme, "Hide") { vm.update { it.hide(app.pkg) }; menuApp = null }
                QhMenuItem(theme, "App info") { qhAppInfo(ctx, app.pkg); menuApp = null }
                QhMenuItem(theme, "Uninstall", danger = true) { qhUninstall(ctx, app.pkg); menuApp = null }
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
