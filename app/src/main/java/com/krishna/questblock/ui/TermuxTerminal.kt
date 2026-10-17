@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/**
 * The terminal screen: scrollback, prompt line, tap chips, the two extra-key rows and the sessions drawer.
 * [preTyped] is put in the prompt line (not run) when the screen opens from a notification.
 */
@Composable
fun TermuxTerminal(
    host: TermuxHost,
    preTyped: String = "",
    onPreTypedUsed: () -> Unit = {},
    onGear: () -> Unit = {},
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = false
        }
    }

    var active by remember { mutableIntStateOf(0) }
    val sessions = host.sessions
    if (active >= sessions.size) active = sessions.size - 1
    val session = sessions[active.coerceAtLeast(0)]

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var histPos by remember { mutableIntStateOf(-1) }
    var drawer by remember { mutableStateOf(false) }
    var kbOn by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope2()
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current

    fun setText(t: String) { input = TextFieldValue(t, TextRange(t.length)) }

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

    fun applyChip(chip: String) {
        val t = input.text
        val base = if (t.isEmpty() || t.endsWith(" ")) t else t.substringBeforeLast(' ', "").let { if (it.isEmpty()) "" else "$it " }
        setText("$base$chip ")
    }

    val chips = host.suggestions(input.text)

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
                    onValueChange = { input = it; histPos = -1 },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    textStyle = TextStyle(color = TC.text, fontFamily = MONO, fontSize = 14.sp),
                    cursorBrush = SolidColor(TC.prompt),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                )
            }
            // tap chips (autocomplete)
            if (chips.isNotEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(chips) { c ->
                        Text(
                            c, fontFamily = MONO, fontSize = 12.sp, color = TC.prompt,
                            modifier = Modifier.border(1.dp, TC.dim, androidx.compose.foundation.shape.RoundedCornerShape(50))
                                .clickable { applyChip(c) }.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            // extra keys, row 1
            Row(Modifier.fillMaxWidth()) {
                TKey("ESC") { setText("") }
                TKey("\u2630") { drawer = !drawer }
                TKey("\u21D5") { scope.launch { if (total > 0) listState.scrollToItem(total - 1) } }
                TKey("HOME") { input = input.copy(selection = TextRange(0)) }
                TKey("\u2191") {
                    val h = host.history
                    if (h.isNotEmpty()) { histPos = if (histPos < 0) h.size - 1 else (histPos - 1).coerceAtLeast(0); setText(h[histPos]) }
                }
                TKey("END") { input = input.copy(selection = TextRange(input.text.length)) }
                TKey("PGUP") { scope.launch { listState.scrollToItem((listState.firstVisibleItemIndex - 18).coerceAtLeast(0)) } }
            }
            // row 2
            Row(Modifier.fillMaxWidth()) {
                TKey("\u21C6") { chips.firstOrNull()?.let { applyChip(it) } }
                TKey("CTRL") {
                    // Ctrl+C: stop the running command, answer no to a waiting question, or clear the line
                    if (session.job?.isActive == true || session.pending != null) { session.out("{d}^C{/}"); host.cancel(session) }
                    else setText("")
                }
                TKey("ALT") { setText(input.text + "--") }
                TKey("\u2190") { input = input.copy(selection = TextRange((input.selection.start - 1).coerceAtLeast(0))) }
                TKey("\u2193") {
                    val h = host.history
                    if (histPos >= 0) {
                        if (histPos < h.size - 1) { histPos++; setText(h[histPos]) } else { histPos = -1; setText("") }
                    }
                }
                TKey("\u2192") { input = input.copy(selection = TextRange((input.selection.end + 1).coerceAtMost(input.text.length))) }
                TKey("PGDN") { scope.launch { listState.scrollToItem((listState.firstVisibleItemIndex + 18).coerceAtMost((total - 1).coerceAtLeast(0))) } }
            }
        }

        // sessions drawer
        if (drawer) {
            Box(Modifier.fillMaxSize().clickable { drawer = false })
            Column(Modifier.fillMaxHeight().width(280.dp).background(TC.bg).border(1.dp, TC.dim).padding(8.dp)) {
                Text("\u2699", color = TC.text, fontSize = 26.sp, modifier = Modifier.clickable { drawer = false; session.out("{p}${session.prompt}{/} set ls"); host.run(session, "set ls") }.padding(6.dp))
                Column(Modifier.weight(1f)) {
                    sessions.forEachIndexed { i, s ->
                        Text(
                            "[${s.id}] ${s.name}", fontFamily = MONO, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (i == active) TC.prompt else TC.text,
                            modifier = Modifier.fillMaxWidth().background(if (i == active) androidx.compose.ui.graphics.Color(0xFF222428) else TC.bg)
                                .clickable { active = i; drawer = false }.padding(vertical = 18.dp, horizontal = 8.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("KEYBOARD", color = TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clickable {
                            kbOn = !kbOn
                            if (kbOn) { runCatching { focus.requestFocus() }; kb?.show() } else kb?.hide()
                            drawer = false
                        }.padding(vertical = 14.dp))
                    Text("NEW SESSION", color = TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clickable {
                            host.newSession(); active = host.sessions.size - 1; drawer = false
                        }.padding(vertical = 14.dp))
                }
                if (sessions.size > 1 && active > 0) {
                    Text("CLOSE SESSION", color = TC.dim, fontFamily = MONO, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { val s = session; active = 0; host.closeSession(s); drawer = false }.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TKey(label: String, onClick: () -> Unit) {
    Text(
        label, color = TC.text, fontFamily = MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f).clickable { onClick() }.padding(vertical = 13.dp),
    )
}

@Composable
private fun rememberCoroutineScope2() = androidx.compose.runtime.rememberCoroutineScope()
