package com.krishna.questblock.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class Subj(val id: String, val name: String, val total: Int)

// MP Board Class 12 (fixed): Physics 14, Chemistry 10, Maths 13, Hindi 18 (Aroh 15 + Vitan 3), English 19 (Flamingo 13 + Vistas 6)
private val SUBJECTS = listOf(
    Subj("P", "Physics", 14), Subj("C", "Chemistry", 10), Subj("M", "Maths", 13),
    Subj("H", "Hindi", 18), Subj("E", "English", 19),
)

/** PCMHE chapter checklist. Study mode ticks chapters done; Revision mode ticks chapters revised (only done ones). */
@Composable
fun ChaptersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }          // bump after every change
    var revMode by remember { mutableStateOf(false) }
    val open = remember { mutableStateMapOf<String, Boolean>() }

    // Always clamped to the real chapter range, so stale or wrong saved numbers can never count.
    fun read(kind: String, s: Subj): Set<Int> =
        (sp.getString("chap_${kind}_${s.id}", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
            .filter { it in 1..s.total }.toSet()
    fun save(kind: String, s: Subj, v: Set<Int>) =
        sp.edit().putString("chap_${kind}_${s.id}", v.sorted().joinToString(",")).apply()

    val refresh = tick
    val allTotal = SUBJECTS.sumOf { it.total }
    val allDone = SUBJECTS.sumOf { read("done", it).size } + 0 * refresh
    val allRev = SUBJECTS.sumOf { s -> read("rev", s).intersect(read("done", s)).size }
    val donePct = allDone * 100 / allTotal
    val revPct = allRev * 100 / allTotal

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("\uD83D\uDCDA Chapters", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        // Study / Revision toggle
        Row(Modifier.fillMaxWidth().background(ColorSurfaceHigh, RoundedCornerShape(50)).padding(4.dp)) {
            listOf(false to "\uD83D\uDCD6 Study", true to "\uD83D\uDD01 Revision").forEach { (mode, label) ->
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                        .background(if (revMode == mode) (if (mode) ColorAccent else ColorPrimary) else ColorSurfaceHigh)
                        .clickable { revMode = mode },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontWeight = FontWeight.SemiBold,
                    color = if (revMode == mode) ColorOnPrimary else ColorText) }
            }
        }

        // Overall card
        Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("$donePct%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ColorPrimary)
                    Text("studied  ($allDone / $allTotal)", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$revPct%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ColorAccent)
                    Text("revised  ($allRev / $allTotal)", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
            }
            LinearProgressIndicator(progress = { (if (revMode) allRev else allDone) / allTotal.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = if (revMode) ColorAccent else ColorPrimary, trackColor = ColorDivider)
            val exam = java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 17).timeInMillis
            val days = Math.ceil((exam - System.currentTimeMillis()) / 86_400_000.0).toInt()
            val left = allTotal - allDone
            if (days > 0 && left > 0) {
                val studyDays = if (days > 20) days - 10 else days   // keep the last 10 days for revision
                val perWeek = Math.ceil(left / (studyDays / 7.0)).toInt()
                Text("$days days to exam (17 Nov)  \u00B7  $left chapters left  \u00B7  about $perWeek / week" +
                    if (days > 20) ", then 10 days revision" else "",
                    style = MaterialTheme.typography.bodySmall, color = ColorWarning, fontWeight = FontWeight.SemiBold)
            } else if (days > 0) {
                Text("$days days to exam. Everything studied. Revise!", style = MaterialTheme.typography.bodySmall,
                    color = ColorWarning, fontWeight = FontWeight.SemiBold)
            } else Text("Exam time. All the best!", style = MaterialTheme.typography.bodySmall, color = ColorWarning)
        }

        // Subjects (tap a card to open its chapters)
        SUBJECTS.forEach { s ->
            val doneSet = read("done", s)
            val revSet = read("rev", s).intersect(doneSet)
            val shown = if (revMode) revSet.size else doneSet.size
            val isOpen = open[s.id] == true
            val accent = if (revMode) ColorAccent else ColorPrimary
            Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp))
                .clickable { open[s.id] = !isOpen }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("$shown / ${s.total} ${if (revMode) "revised" else "done"}  \u00B7  ${shown * 100 / s.total}%",
                            style = MaterialTheme.typography.bodySmall, color = if (shown == s.total) accent else ColorMuted)
                    }
                    Text(if (isOpen) "\u25B4" else "\u25BE", color = ColorMuted, style = MaterialTheme.typography.titleMedium)
                }
                LinearProgressIndicator(progress = { shown / s.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = accent, trackColor = ColorDivider)
                if (isOpen) {
                    (1..s.total).chunked(6).forEach { rowNums ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            rowNums.forEach { n ->
                                val isDone = n in doneSet
                                val isRev = n in revSet
                                val bg = when {
                                    revMode && isRev -> ColorAccent
                                    revMode && isDone -> ColorPrimary.copy(alpha = 0.30f)
                                    !revMode && isDone -> ColorPrimary
                                    else -> ColorSurfaceHigh
                                }
                                val solid = (revMode && isRev) || (!revMode && isDone)
                                Box(
                                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                                        .clickable {
                                            if (revMode) {
                                                if (!isDone) {
                                                    Toast.makeText(ctx, "Study chapter $n first", Toast.LENGTH_SHORT).show()
                                                } else save("rev", s, if (isRev) revSet - n else revSet + n)
                                            } else {
                                                save("done", s, if (isDone) doneSet - n else doneSet + n)
                                                if (isDone) save("rev", s, revSet - n)   // un-studied chapters cannot stay revised
                                            }
                                            tick++
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (solid) "\u2713$n" else "$n", fontWeight = FontWeight.SemiBold,
                                    color = if (solid) ColorOnPrimary else if (revMode && !isDone) ColorMuted else ColorText) }
                            }
                            repeat(6 - rowNums.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
