package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private data class MarkTest(val id: String, val name: String, val emoji: String)

// Order of the school year: MT 1, Quarterly, MT 2, Half-yearly, MT 3
private val TESTS = listOf(
    MarkTest("mt1", "MT 1", "\uD83D\uDCDD"),
    MarkTest("qtr", "Quarterly", "\uD83D\uDCC5"),
    MarkTest("mt2", "MT 2", "\uD83D\uDCDD"),
    MarkTest("half", "Half-yearly", "\uD83C\uDFAF"),
    MarkTest("mt3", "MT 3", "\uD83D\uDCDD"),
)

private val MARK_SUBJECTS = listOf(
    "P" to "Physics", "C" to "Chemistry", "M" to "Maths",
    "H" to "\u0939\u093f\u0928\u094d\u0926\u0940", "E" to "English",
)

private data class Mark(val got: Float, val max: Float) {
    val pct: Float get() = if (max > 0f) got * 100f / max else 0f
}

private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)
private fun pctColor(p: Float): Color = when { p >= 75f -> ColorPrimary; p >= 50f -> ColorWarning; else -> ColorDanger }

private fun readMark(sp: android.content.SharedPreferences, test: String, subj: String): Mark? {
    val v = sp.getString("marks_${test}_$subj", null) ?: return null
    val p = v.split("|")
    val g = p.getOrNull(0)?.toFloatOrNull() ?: return null
    val m = p.getOrNull(1)?.toFloatOrNull() ?: return null
    return if (m > 0f && g in 0f..m) Mark(g, m) else null
}

/** Marks feeder: MT 1, Quarterly, MT 2, Half-yearly, MT 3 for every subject. Saved on the phone only. */
@Composable
fun MarksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<MarkTest, Pair<String, String>>?>(null) }

    val data: Map<String, Map<String, Mark>> = remember(tick) {
        TESTS.associate { t -> t.id to MARK_SUBJECTS.mapNotNull { (id, _) -> readMark(sp, t.id, id)?.let { id to it } }.toMap() }
    }
    fun totalPct(testId: String): Float? {
        val m = data[testId].orEmpty().values
        if (m.isEmpty()) return null
        return m.sumOf { it.got.toDouble() }.toFloat() * 100f / m.sumOf { it.max.toDouble() }.toFloat()
    }
    val entered = TESTS.filter { totalPct(it.id) != null }
    val latest = entered.lastOrNull()
    val prev = if (entered.size >= 2) entered[entered.size - 2] else null

    editing?.let { (test, subj) ->
        val cur = data[test.id]?.get(subj.first)
        // out-of remembered from the last entry of this subject in this test, else the last used anywhere, else 100
        var got by remember { mutableStateOf(cur?.let { fmt(it.got) } ?: "") }
        var max by remember { mutableStateOf(cur?.let { fmt(it.max) } ?: (sp.getString("marks_lastmax", "100") ?: "100")) }
        val g = got.toFloatOrNull(); val m = max.toFloatOrNull()
        val valid = g != null && m != null && m > 0f && g in 0f..m
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = ColorSurface,
            title = { Text("${test.name} \u00B7 ${subj.second}", color = ColorText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(got, { got = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, singleLine = true,
                        label = { Text("Marks obtained") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(max, { max = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, singleLine = true,
                        label = { Text("Out of") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (!valid && got.isNotEmpty()) Text("Marks must be between 0 and the total", color = ColorDanger, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    sp.edit().putString("marks_${test.id}_${subj.first}", "${fmt(g!!)}|${fmt(m!!)}").putString("marks_lastmax", fmt(m)).apply()
                    tick++; editing = null
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (cur != null) TextButton(onClick = {
                        sp.edit().remove("marks_${test.id}_${subj.first}").apply(); tick++; editing = null
                    }) { Text("Clear", color = ColorDanger) }
                    TextButton(onClick = { editing = null }) { Text("Cancel", color = ColorMuted) }
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("\uD83D\uDCDD Marks", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
        }

        // Summary of the latest test
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ColorSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (latest == null) {
                Text("No marks yet", color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Tap a subject under a test to add your marks.", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                val lp = totalPct(latest.id)!!
                Text("${fmt(Math.round(lp * 10f) / 10f)}%", color = pctColor(lp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displaySmall)
                Text("${latest.name} total", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                if (prev != null) {
                    val d = lp - totalPct(prev.id)!!
                    Text((if (d >= 0) "\u25B2 +" else "\u25BC ") + fmt(Math.round(d * 10f) / 10f) + "% since ${prev.name}",
                        color = if (d >= 0) ColorPrimary else ColorDanger, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
                val ms = data[latest.id].orEmpty()
                if (ms.size >= 2) {
                    val best = ms.maxByOrNull { it.value.pct }!!
                    val weak = ms.minByOrNull { it.value.pct }!!
                    fun nm(id: String) = MARK_SUBJECTS.first { it.first == id }.second
                    Text("\uD83C\uDFC6 Best: ${nm(best.key)} ${Math.round(best.value.pct)}%   \u00B7   \u26A0 Weakest: ${nm(weak.key)} ${Math.round(weak.value.pct)}%",
                        color = ColorText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // One card per test
        TESTS.forEach { t ->
            val ms = data[t.id].orEmpty()
            val tp = totalPct(t.id)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ColorSurface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.emoji}  ${t.name}", color = ColorText, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (tp != null) Text("${fmt(Math.round(tp * 10f) / 10f)}%", color = pctColor(tp), fontWeight = FontWeight.Bold)
                    else Text("\u23F3 Waiting", color = ColorMuted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
                MARK_SUBJECTS.forEach { subj ->
                    val m = ms[subj.first]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ColorSurfaceHigh)
                            .clickable { editing = t to subj }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(subj.second, color = ColorText, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(78.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ColorDivider)) {
                            if (m != null) Box(Modifier.fillMaxHeight().fillMaxWidth((m.pct / 100f).coerceIn(0f, 1f)).background(pctColor(m.pct)))
                        }
                        Text(if (m != null) "${fmt(m.got)}/${fmt(m.max)}" else "+ add",
                            color = if (m != null) ColorText else ColorPrimary, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(70.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
