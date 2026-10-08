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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// MP Board Class 12: Physics 14, Chemistry 10, Maths 13, Hindi 18 (Aroh 15 + Vitan 3), English 19 (Flamingo 8 prose + 5 poems, Vistas 6). You can change any of them.
private val DEFAULT_CHAPTERS = mapOf("P" to 14, "C" to 10, "M" to 13, "H" to 18, "E" to 19)
private val SUBJECTS = listOf("P" to "Physics", "C" to "Chemistry", "M" to "Maths", "H" to "Hindi", "E" to "English")

/** Chapter checklist for PCMHE. You type how many chapters a subject has, then tick them off. Saved on the phone. */
@Composable
fun ChaptersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var rev by remember { mutableIntStateOf(0) }   // bump to re-read after every change

    fun total(id: String) = sp.getInt("chap_total_$id", DEFAULT_CHAPTERS[id] ?: 0)
    fun done(id: String): Set<Int> =
        (sp.getString("chap_done_$id", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()

    val tick = rev   // reading it here makes the screen refresh after each change
    val allTotal = SUBJECTS.sumOf { total(it.first) } + 0 * tick
    val allDone = SUBJECTS.sumOf { s -> done(s.first).count { it in 1..total(s.first) } }
    val allPct = if (allTotal == 0) 0 else allDone * 100 / allTotal

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("\uD83D\uDCDA Chapters", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }
        Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (allTotal == 0) "Type how many chapters each subject has" else "Syllabus $allPct% done  ($allDone / $allTotal)",
                color = ColorText, fontWeight = FontWeight.SemiBold)
            if (allTotal > 0) {
                LinearProgressIndicator(progress = { allPct / 100f }, modifier = Modifier.fillMaxWidth(), color = ColorPrimary, trackColor = ColorDivider)
                val exam = java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 17).timeInMillis
                val days = Math.ceil((exam - System.currentTimeMillis()) / 86_400_000.0).toInt()
                val left = allTotal - allDone
                if (days > 0 && left > 0) {
                    val studyDays = if (days > 20) days - 10 else days   // keep the last 10 days for revision
                    val perWeek = Math.ceil(left / (studyDays / 7.0)).toInt()
                    Text("$days days to exam (17 Nov)  \u00B7  $left chapters left  \u00B7  about $perWeek chapters / week" +
                        if (days > 20) ", then 10 days revision" else "",
                        style = MaterialTheme.typography.bodySmall, color = ColorWarning, fontWeight = FontWeight.SemiBold)
                } else if (days <= 0) Text("Exam time. All the best!", style = MaterialTheme.typography.bodySmall, color = ColorWarning)
                Text(when {
                    allPct < 30 -> "Long way to go. Study first, everything else after."
                    allPct < 70 -> "Good pace. Keep ticking."
                    allPct < 100 -> "Almost there."
                    else -> "Syllabus complete. Revise."
                }, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }

        SUBJECTS.forEach { (id, name) ->
            val t = total(id)
            val d = done(id)
            val dn = d.count { it in 1..t }
            Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = ColorText, fontWeight = FontWeight.Bold)
                        Text(if (t == 0) "No chapters set" else "$dn / $t done", style = MaterialTheme.typography.bodySmall,
                            color = if (t > 0 && dn == t) ColorPrimary else ColorMuted)
                    }
                    var text by remember(id) { mutableStateOf(if (t == 0) "" else t.toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            val n = v.filter { it.isDigit() }.take(3)
                            text = n
                            sp.edit().putInt("chap_total_$id", (n.toIntOrNull() ?: 0).coerceAtMost(200)).apply()
                            rev++
                        },
                        label = { Text("Chapters") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }
                if (t > 0) {
                    LinearProgressIndicator(progress = { dn / t.toFloat() }, modifier = Modifier.fillMaxWidth(), color = ColorPrimary, trackColor = ColorDivider)
                    (1..t).chunked(6).forEach { rowNums ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            rowNums.forEach { n ->
                                val on = n in d
                                Box(
                                    Modifier.weight(1f).height(38.dp)
                                        .background(if (on) ColorPrimary else ColorSurfaceHigh, RoundedCornerShape(10.dp))
                                        .clickable {
                                            val next = if (on) d - n else d + n
                                            sp.edit().putString("chap_done_$id", next.sorted().joinToString(",")).apply()
                                            rev++
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$n", color = if (on) ColorOnPrimary else ColorText, fontWeight = FontWeight.SemiBold) }
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
