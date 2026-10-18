@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.krishna.questblock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MONO = FontFamily.Monospace
private val DRAWER_BG = Color(0xFF161618)
private val DRAWER_SEL = Color(0xFF222428)

/** v208: a tap with no ripple. The default ripple drew a white box over the black terminal. */
@Composable
private fun Modifier.tclick(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

private fun wordBack(t: String, pos: Int): Int {
    var i = pos.coerceIn(0, t.length)
    while (i > 0 && t[i - 1] == ' ') i--
    while (i > 0 && t[i - 1] != ' ') i--
    return i
}

private fun wordForward(t: String, pos: Int): Int {
    var i = pos.coerceIn(0, t.length)
    while (i < t.length && t[i] == ' ') i++
    while (i < t.length && t[i] != ' ') i++
    return i
}

/**
 * The terminal screen: scrollback, prompt line, Focus strip, tap chips, the two extra-key rows and the sessions drawer.
 * [preTyped] is put in the prompt line (not run) when the screen opens from a notification.
 */
@Composable
fun TermuxTerminal(
    host: TermuxHost,
    preTyped: String = "",
    onPreTypedUsed: () -> Unit = {},
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            // v208: the window itself is black too, so no light edge shows while the keyboard moves
            window.setBackgroundDrawableResource(android.R.color.black)
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = false
        }
    }

    val sessions = host.sessions
    val active = host.activeIndex.coerceIn(0, (sessions.size - 1).coerceAtLeast(0))
    val session = sessions[active]

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var histPos by remember { mutableIntStateOf(-1) }
    var drawer by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var kbOn by remember { mutableStateOf(true) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current

    fun setText(t: String) { input = TextFieldValue(t, TextRange(t.length)) }
    fun refocus() { if (kbOn) runCatching { focus.requestFocus() } }
    fun toggleKb() {
        kbOn = !kbOn
        if (kbOn) { runCatching { focus.requestFocus() }; kb?.show() } else kb?.hide()
    }

    LaunchedEffect(preTyped) {
        if (preTyped.isNotEmpty()) { setText(preTyped); onPreTypedUsed() }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // follow the newest line (and the live lines)
    val total = session.lines.size + session.live.size
    LaunchedEffect(total, active) { if (total > 0) listState.scrollToItem(total - 1) }

    fun submit() {
        val line = input.text
        val pend = session.pending
        session.out("{p}${if (pend != null) "?" else session.prompt}{/} ${line.tm()}")
        input = TextFieldValue("")
        histPos = -1
        if (line.isNotBlank() || pend != null) host.run(session, line)
    }

    fun ctrlC() {
        // Ctrl+C: stop the running command, answer no to a waiting question, or clear the line
        if (session.job?.isActive == true || session.pending != null) { session.out("{d}^C{/}"); host.cancel(session) }
        setText("")
    }

    /** Typing with CTRL or ALT armed: the next letter is a shortcut, not text. */
    fun onType(nv: TextFieldValue) {
        val old = input.text
        if ((ctrl || alt) && nv.text.length == old.length + 1 && nv.text.startsWith(old)) {
            val ch = nv.text.last().lowercaseChar()
            val wasCtrl = ctrl
            ctrl = false
            alt = false
            val pos = input.selection.end
            if (wasCtrl) {
                when (ch) {
                    'c' -> ctrlC()
                    'l' -> session.lines.clear()
                    'u' -> setText("")
                    'a' -> input = input.copy(selection = TextRange(0))
                    'e' -> input = input.copy(selection = TextRange(old.length))
                    'w' -> { val p = wordBack(old, pos); input = TextFieldValue(old.substring(0, p) + old.substring(pos), TextRange(p)) }
                    else -> input = nv
                }
            } else {
                when (ch) {
                    'b' -> input = input.copy(selection = TextRange(wordBack(old, pos)))
                    'f' -> input = input.copy(selection = TextRange(wordForward(old, pos)))
                    else -> input = nv
                }
            }
            return
        }
        input = nv
        histPos = -1
    }

    val chips = host.suggestions(input.text)
    val hint = host.hint(input.text)
    val strip = host.focusStrip

    BackHandler(enabled = settingsOpen || drawer) { if (settingsOpen) settingsOpen = false else drawer = false }

    Box(
        Modifier.fillMaxSize().background(TC.bg).systemBarsPadding().imePadding()
            .pointerInput(Unit) {
                var startX = 0f
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { startX = it.x; acc = 0f },
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                    onHorizontalDrag = { _, dx -> acc += dx; if (startX < 160f && acc > 90f) drawer = true },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // scrollback
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), state = listState) {
                itemsIndexed(session.lines) { _, l ->
                    Text(termuxMarkup(l), fontFamily = MONO, fontSize = 13.sp, lineHeight = 17.sp, color = TC.text)
                }
                items(session.live) { l ->
                    Text(termuxMarkup(l), fontFamily = MONO, fontSize = 13.sp, lineHeight = 17.sp, color = TC.text)
                }
            }
            // prompt line
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    termuxMarkup("{p}${if (session.pending != null) "?" else session.prompt}{/} "),
                    fontFamily = MONO, fontSize = 14.sp,
                )
                BasicTextField(
                    value = input,
                    onValueChange = { onType(it) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    textStyle = TextStyle(color = TC.text, fontFamily = MONO, fontSize = 14.sp),
                    cursorBrush = SolidColor(TC.prompt),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
            }
            // v208: Focus strip: the timer while a session runs. Tap it to jump to the focus session and back.
            if (strip.isNotEmpty()) {
                Text(
                    termuxMarkup(strip), fontFamily = MONO, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().tclick { host.toggleFocusSession() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            // v208: one line of usage for the command being typed
            if (hint.isNotEmpty()) {
                Text(termuxMarkup("{d}${hint.tm()}{/}"), fontFamily = MONO, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            // tap chips (autocomplete)
            if (chips.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips) { c ->
                        Text(
                            c.label, fontFamily = MONO, fontSize = 12.sp, color = TC.prompt,
                            modifier = Modifier.border(1.dp, TC.dim, androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .tclick { setText(c.full); refocus() }.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            // extra keys, row 1
            Row(Modifier.fillMaxWidth()) {
                TKey("ESC") { if (settingsOpen) settingsOpen = false else if (drawer) drawer = false else setText(""); refocus() }
                TKey("\u2630") { drawer = !drawer }
                TKey("\u21D5") { toggleKb() }
                TKey("HOME") { input = input.copy(selection = TextRange(0)); refocus() }
                TKey("\u2191", repeat = true) {
                    val h = host.history
                    if (h.isNotEmpty()) { histPos = if (histPos < 0) h.size - 1 else (histPos - 1).coerceAtLeast(0); setText(h[histPos]) }
                    refocus()
                }
                TKey("END") { input = input.copy(selection = TextRange(input.text.length)); refocus() }
                TKey("PGUP", repeat = true) { scope.launch { listState.scrollToItem((listState.firstVisibleItemIndex - 18).coerceAtLeast(0)) } }
            }
            // row 2
            Row(Modifier.fillMaxWidth()) {
                TKey("\u21C6") { chips.firstOrNull()?.let { setText(it.full) }; refocus() }
                TKey("CTRL", armed = ctrl) { ctrl = !ctrl; if (ctrl) alt = false; refocus() }
                TKey("ALT", armed = alt) { alt = !alt; if (alt) ctrl = false; refocus() }
                TKey("\u2190", repeat = true) { input = input.copy(selection = TextRange((input.selection.start - 1).coerceAtLeast(0))); refocus() }
                TKey("\u2193", repeat = true) {
                    val h = host.history
                    if (histPos >= 0) {
                        if (histPos < h.size - 1) { histPos++; setText(h[histPos]) } else { histPos = -1; setText("") }
                    }
                    refocus()
                }
                TKey("\u2192", repeat = true) { input = input.copy(selection = TextRange((input.selection.end + 1).coerceAtMost(input.text.length))); refocus() }
                TKey("PGDN", repeat = true) { scope.launch { listState.scrollToItem((listState.firstVisibleItemIndex + 18).coerceAtMost((total - 1).coerceAtLeast(0))) } }
            }
        }

        // sessions drawer
        if (drawer) {
            Box(Modifier.fillMaxSize().tclick { drawer = false })
            Column(Modifier.fillMaxHeight().width(280.dp).background(DRAWER_BG).padding(8.dp)) {
                Text("\u2699", color = TC.text, fontSize = 26.sp, modifier = Modifier.tclick { drawer = false; settingsOpen = true }.padding(6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    sessions.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().background(if (i == active) DRAWER_SEL else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "[${s.id}] ${s.name}", fontFamily = MONO, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (i == active) TC.prompt else TC.text,
                                modifier = Modifier.weight(1f).tclick { host.select(i); drawer = false }.padding(vertical = 18.dp, horizontal = 8.dp),
                            )
                            Text(
                                "\u2715", color = TC.err, fontFamily = MONO, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.tclick { host.closeSession(s) }.padding(horizontal = 16.dp, vertical = 18.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("KEYBOARD", color = TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).tclick { toggleKb(); drawer = false }.padding(vertical = 14.dp))
                    Text("NEW SESSION", color = TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).tclick { host.newSession(); drawer = false }.padding(vertical = 14.dp))
                }
            }
        }

        // v208: the gear opens this page. It is not a session: nothing it shows is added to a scrollback.
        if (settingsOpen) TermuxSettingsPage(host = host, onClose = { settingsOpen = false })
    }
}

/** The settings page: tap a switch to flip it, tap any other row to type a new value. */
@Composable
private fun TermuxSettingsPage(host: TermuxHost, onClose: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val rows = remember(tick) { host.settingRows() }

    fun save(key: String, value: String) {
        val err = host.settingSet(key, value)
        msg = err ?: "saved"
        if (err == null) editing = null
        scope.launch { delay(250L); tick++ }
    }

    Column(Modifier.fillMaxSize().background(TC.bg).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u2039 back", color = TC.prompt, fontFamily = MONO, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.tclick { onClose() }.padding(12.dp))
            Text(termuxMarkup("{b}SETTINGS{/}"), fontFamily = MONO, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (msg.isNotEmpty()) {
            Text(termuxMarkup(if (msg == "saved") "{g}\u2713 saved{/}" else "{r}\u2717{/} ${msg.tm()}"), fontFamily = MONO, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(rows, key = { it.key }) { r ->
                Column(
                    Modifier.fillMaxWidth().tclick {
                        if (r.kind == "bool") save(r.key, if (r.value == "on") "off" else "on")
                        else { editing = r.key; text = TextFieldValue(r.value, TextRange(r.value.length)) }
                    }.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(termuxMarkup("{c}${r.key}{/}"), fontFamily = MONO, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            termuxMarkup(if (r.kind == "bool") (if (r.value == "on") "{g}on{/}" else "{d}off{/}") else "{y}${(if (r.value.isBlank()) "-" else r.value).tm()}{/}"),
                            fontFamily = MONO, fontSize = 14.sp,
                        )
                    }
                    Text(termuxMarkup("{d}${r.desc.tm()}{/}"), fontFamily = MONO, fontSize = 11.sp)
                }
            }
        }
        val key = editing
        if (key != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(termuxMarkup("{p}$key={/} "), fontFamily = MONO, fontSize = 14.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = TC.text, fontFamily = MONO, fontSize = 14.sp),
                    cursorBrush = SolidColor(TC.prompt), singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save(key, text.text) }),
                )
                Text("set", color = TC.prompt, fontFamily = MONO, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.tclick { save(key, text.text) }.padding(10.dp))
                Text("\u2715", color = TC.dim, fontFamily = MONO, fontSize = 14.sp,
                    modifier = Modifier.tclick { editing = null }.padding(10.dp))
            }
        }
    }
}

@Composable
private fun RowScope.TKey(label: String, armed: Boolean = false, repeat: Boolean = false, onClick: () -> Unit) {
    val current by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    Text(
        label, color = if (armed) TC.bg else TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
            .background(if (armed) TC.prompt else TC.bg)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    current()
                    if (repeat) {
                        // holding an arrow or PGUP/PGDN repeats it
                        val job = scope.launch { delay(350L); while (true) { current(); delay(60L) } }
                        tryAwaitRelease()
                        job.cancel()
                    } else {
                        tryAwaitRelease()
                    }
                })
            }.padding(vertical = 13.dp),
    )
}
