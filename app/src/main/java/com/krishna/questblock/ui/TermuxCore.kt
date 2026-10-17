package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Job

/**
 * v207: the Termux theme. When it is on, the whole app is one terminal (TermuxShell) and the lock
 * pages are terminal pages too (TermuxBlockScreen). Nothing opens a normal page.
 */
var TermuxOn by mutableStateOf(false)

object TermuxMode {
    private const val KEY = "termux_theme"
    fun load(ctx: Context) {
        TermuxOn = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getBoolean(KEY, false)
    }
    fun set(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        TermuxOn = on
    }
    /** A strict Focus session is running right now (same test MainScreen uses). */
    fun strictNow(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        return sp.getBoolean("focus_strict", false) && sp.getLong("focus_until_cache", 0L) > System.currentTimeMillis()
    }
}

/** Colours of the terminal (taken from the Termux screenshots). */
object TC {
    val bg = Color(0xFF000000)
    val text = Color(0xFFE6E6E6)
    val prompt = Color(0xFF3DDC84)
    val ok = Color(0xFF3FB950)
    val warn = Color(0xFFE3B341)
    val cyan = Color(0xFF39C5CF)
    val dim = Color(0xFF8B949E)
    val err = Color(0xFFF85149)
}

private val TAGS: Map<String, Color> = mapOf(
    "g" to TC.ok, "y" to TC.warn, "c" to TC.cyan, "d" to TC.dim, "r" to TC.err, "p" to TC.prompt, "w" to TC.text,
)

/** Tiny markup: {g} green, {y} yellow, {c} cyan, {d} dim, {r} red, {p} prompt green, {b} bold, {/} reset. */
fun termuxMarkup(s: String): AnnotatedString = buildAnnotatedString {
    var color = TC.text
    var bold = false
    var i = 0
    var start = 0
    fun flush(end: Int) {
        if (end > start) {
            pushStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
            append(s.substring(start, end))
            pop()
        }
    }
    while (i < s.length) {
        if (s[i] == '{') {
            val close = s.indexOf('}', i)
            if (close >= i + 2 && close <= i + 3) {
                val tag = s.substring(i + 1, close)
                val known = tag == "/" || tag == "b" || TAGS.containsKey(tag)
                if (known) {
                    flush(i)
                    if (tag == "/") { color = TC.text; bold = false }
                    else if (tag == "b") { bold = true }
                    else { color = TAGS.getValue(tag) }
                    i = close + 1
                    start = i
                    continue
                }
            }
        }
        i++
    }
    flush(s.length)
}

/** Makes user text safe to print (no markup braces). */
fun String.tm(): String = this.replace("{", "(").replace("}", ")")

/** One terminal session: its own scrollback, prompt and an optional waiting question. */
class TermuxSession(val id: Int, var name: String) {
    val lines = mutableStateListOf<String>()
    var pending by mutableStateOf<((String) -> Unit)?>(null)
    var prompt by mutableStateOf("~/QuestBlock $")
    var job: Job? = null
    /** Shown live (every second) as the last lines of the session while set, e.g. the Focus timer. */
    var live by mutableStateOf<List<String>>(emptyList())

    fun out(text: String) {
        text.split("\n").forEach { lines.add(it) }
        if (lines.size > 1500) repeat(300) { if (lines.isNotEmpty()) lines.removeAt(0) }
    }
    fun ok(m: String) = out("{g}\u2713{/} $m")
    fun warn(m: String) = out("{y}!{/} $m")
    fun err(m: String) = out("{r}\u2717{/} $m")
    fun step(m: String) = out("==> $m")
    fun dim(m: String) = out("{d}$m{/}")
    fun head(m: String) = out("{b}${m.uppercase()}{/}")
}

/** What the terminal needs from whoever runs the commands. */
interface TermuxHost {
    val sessions: List<TermuxSession>
    val history: List<String>
    fun run(session: TermuxSession, line: String)
    fun cancel(session: TermuxSession)
    fun suggestions(input: String): List<String>
    fun newSession(): TermuxSession
    fun closeSession(s: TermuxSession)
}

/** Words and flags of a command line. Boolean flags take no value, every other flag takes the next word. */
class TermuxArgs(val pos: List<String>, val flags: Map<String, String>) {
    fun has(vararg names: String) = names.any { flags.containsKey(it) }
    fun value(vararg names: String): String? = names.firstNotNullOfOrNull { flags[it] }
    fun rest(from: Int): String = pos.drop(from).joinToString(" ")
}

private val BOOL_FLAGS = setOf("--strict", "--today", "--done", "--overdue", "--focus", "--week", "--all", "--yes", "-y", "--study", "--open", "--once")

fun termuxTokenize(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var quote: Char? = null
    var any = false
    for (ch in line) {
        if (quote != null) {
            if (ch == quote) quote = null else sb.append(ch)
        } else if (ch == '"' || ch == '\'') {
            quote = ch
            any = true
        } else if (ch.isWhitespace()) {
            if (sb.isNotEmpty() || any) { out.add(sb.toString()); sb.clear(); any = false }
        } else {
            sb.append(ch)
        }
    }
    if (sb.isNotEmpty() || any) out.add(sb.toString())
    return out
}

fun termuxParse(tokens: List<String>): TermuxArgs {
    val pos = ArrayList<String>()
    val flags = LinkedHashMap<String, String>()
    var i = 0
    while (i < tokens.size) {
        val t = tokens[i]
        val isFlag = t.length > 1 && t.startsWith("-") && t.drop(1).toDoubleOrNull() == null
        if (isFlag) {
            if (t in BOOL_FLAGS) {
                flags[t] = "1"
            } else if (i + 1 < tokens.size) {
                flags[t] = tokens[i + 1]
                i++
            } else {
                flags[t] = ""
            }
        } else {
            pos.add(t)
        }
        i++
    }
    return TermuxArgs(pos, flags)
}

/** [########--] style bar. */
fun termuxBar(frac: Float, width: Int = 10): String {
    val f = frac.coerceIn(0f, 1f)
    val n = Math.round(f * width)
    return "[" + "#".repeat(n) + "-".repeat(width - n) + "]"
}

/** The page a notification asked for, as the command that shows it. */
fun termuxCommandForRoute(route: String): String = when (route) {
    "home" -> "status"
    "quests" -> "quests"
    "focus" -> "focus status"
    "vault" -> "vault ls"
    "profile" -> "whoami"
    "stats" -> "stats"
    "usage" -> "usage"
    "weekly" -> "weekly"
    "attendance" -> "attendance"
    "chapters" -> "chapters"
    "marks" -> "marks"
    "settings" -> "set ls"
    "selftest" -> "selftest"
    "debuglog" -> "debuglog"
    "versions" -> "versions"
    "ideas" -> "ideas"
    "about" -> "about"
    "shop" -> "shop ls"
    "coins" -> "coins log"
    "hub" -> "hub"
    "hub_goals" -> "goals"
    "hub_badges" -> "badges"
    "hub_rules" -> "limits"
    "hub_sites" -> "sites ls"
    "reliability" -> "reliability"
    "categories" -> "categories ls"
    else -> "status"
}
