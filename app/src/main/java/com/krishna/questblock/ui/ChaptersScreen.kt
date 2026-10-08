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

private data class Subj(val id: String, val name: String, val names: List<String>, val extras: List<String> = emptyList()) {
    val base: Int get() = names.size
    val total: Int get() = base + extras.size
}

// MP Board Class 12 (names from the guide's contents pages): Physics 14, Chemistry 10, Maths 13, Hindi 18 lessons + 7 sections, English 19 lessons + 3 sections
private val SUBJECTS = listOf(
    Subj("P", "Physics", listOf(
        "Electric Charge and Electric Field", "Electrostatic Potential and Capacitance", "Current Electricity",
        "Moving Charges and Magnetism", "Magnetism and Matter", "Electromagnetic Induction", "Alternating Current",
        "Electromagnetic Waves", "Ray Optics and Optical Instruments", "Wave Optics",
        "Dual Nature of Radiation and Matter", "Atoms", "Nuclei",
        "Semiconductor Electronics: Materials, Devices and Simple Circuits")),
    Subj("C", "Chemistry", listOf(
        "Solutions", "Electrochemistry", "Chemical Kinetics", "d- and f-block Elements", "Co-ordination Compounds",
        "Haloalkanes and Haloarenes", "Alcohols, Phenols and Ethers", "Aldehydes, Ketones and Carboxylic Acids",
        "Amines", "Bio-molecules")),
    Subj("M", "Maths", listOf(
        "Relations and Functions", "Inverse Trigonometric Functions", "Matrices", "Determinants",
        "Continuity and Differentiability (5A) + Differentiation (5B)", "Application of Derivatives",
        "Integration (7A) + Definite Integral (7B)", "Application of Integrals", "Differential Equations",
        "Vector Algebra", "Three-dimensional Geometry", "Linear Programming", "Probability")),
    Subj("H", "Hindi \u0939\u093f\u0928\u094d\u0926\u0940", listOf(
        "\u0906\u0924\u094d\u092e\u092a\u0930\u093f\u091a\u092f / \u090f\u0915 \u0917\u0940\u0924\u2014\u0926\u093f\u0928 \u091c\u0932\u094d\u0926\u0940-\u091c\u0932\u094d\u0926\u0940 \u0922\u0932\u0924\u093e \u0939\u0948!",
        "\u092a\u0924\u0902\u0917",
        "\u0915\u0935\u093f\u0924\u093e \u0915\u0947 \u092c\u0939\u093e\u0928\u0947 / \u092c\u093e\u0924 \u0938\u0940\u0927\u0940 \u0925\u0940 \u092a\u0930",
        "\u0915\u0948\u092e\u0930\u0947 \u092e\u0947\u0902 \u092c\u0902\u0926 \u0905\u092a\u093e\u0939\u093f\u091c",
        "\u0909\u0937\u093e",
        "\u092c\u093e\u0926\u0932 \u0930\u093e\u0917",
        "\u0915\u0935\u093f\u0924\u093e\u0935\u0932\u0940 (\u0909\u0924\u094d\u0924\u0930 \u0915\u093e\u0902\u0921 \u0938\u0947) / \u0932\u0915\u094d\u0937\u094d\u092e\u0923-\u092e\u0942\u0930\u094d\u091a\u094d\u091b\u093e \u0914\u0930 \u0930\u093e\u092e \u0915\u093e \u0935\u093f\u0932\u093e\u092a",
        "\u0930\u0941\u092c\u093e\u0907\u092f\u093e\u0901",
        "\u091b\u094b\u091f\u093e \u092e\u0947\u0930\u093e \u0916\u0947\u0924 / \u092c\u0917\u0941\u0932\u094b\u0902 \u0915\u0947 \u092a\u0902\u0916",
        "\u092d\u0915\u094d\u0924\u093f\u0928",
        "\u092c\u093e\u091c\u093e\u0930 \u0926\u0930\u094d\u0936\u0928",
        "\u0915\u093e\u0932\u0947 \u092e\u0947\u0918\u093e \u092a\u093e\u0928\u0940 \u0926\u0947",
        "\u092a\u0939\u0932\u0935\u093e\u0928 \u0915\u0940 \u0922\u094b\u0932\u0915",
        "\u0936\u093f\u0930\u0940\u0937 \u0915\u0947 \u092b\u0942\u0932",
        "\u0936\u094d\u0930\u092e \u0935\u093f\u092d\u093e\u091c\u0928 \u0914\u0930 \u091c\u093e\u0924\u093f-\u092a\u094d\u0930\u0925\u093e / \u092e\u0947\u0930\u0940 \u0915\u0932\u094d\u092a\u0928\u093e \u0915\u093e \u0906\u0926\u0930\u094d\u0936 \u0938\u092e\u093e\u091c",
        "\u0938\u093f\u0932\u094d\u0935\u0930 \u0935\u0948\u0921\u093f\u0902\u0917",
        "\u091c\u0942\u091d",
        "\u0905\u0924\u0940\u0924 \u092e\u0947\u0902 \u0926\u092c\u0947 \u092a\u093e\u0901\u0935"),
        listOf("\u0939\u093f\u0928\u094d\u0926\u0940 \u0938\u093e\u0939\u093f\u0924\u094d\u092f \u0915\u093e \u0907\u0924\u093f\u0939\u093e\u0938", "\u0935\u094d\u092f\u093e\u0915\u0930\u0923", "\u092a\u0924\u094d\u0930-\u0932\u0947\u0916\u0928", "\u0905\u0928\u0941\u091a\u094d\u091b\u0947\u0926 \u0932\u0947\u0916\u0928", "\u0928\u093f\u092c\u0902\u0927 \u0932\u0947\u0916\u0928", "\u0905\u092a\u0920\u093f\u0924 \u092c\u094b\u0927", "\u0905\u092d\u093f\u0935\u094d\u092f\u0915\u094d\u0924\u093f \u0914\u0930 \u092e\u093e\u0927\u094d\u092f\u092e")),
    Subj("E", "English", listOf(
        "The Last Lesson", "Lost Spring", "Deep Water", "The Rattrap", "Indigo", "Poets and Pancakes", "The Interview", "Going Places",
        "My Mother at Sixty-six", "Keeping Quiet", "A Thing of Beauty", "A Roadside Stand", "Aunt Jennifer's Tigers",
        "The Third Level", "The Tiger King", "Journey to the End of the Earth", "The Enemy", "On the Face of It", "Memories of Childhood"),
        listOf("Reading Comprehension", "Writing Skills", "Grammar")),
)

private fun readSet(sp: android.content.SharedPreferences, kind: String, s: Subj): Set<Int> =
    (sp.getString("chap_${kind}_${s.id}", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        .filter { it in 1..s.total }.toSet()

private fun saveSet(sp: android.content.SharedPreferences, kind: String, s: Subj, v: Set<Int>) =
    sp.edit().putString("chap_${kind}_${s.id}", v.sorted().joinToString(",")).apply()

/** PCMHE chapter checklist. Study mode ticks chapters done; Revision mode ticks chapters revised (only done ones). */
@Composable
fun ChaptersScreen(onBack: () -> Unit, onOpenSubject: (String, Boolean) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }          // bump after every change
    var revMode by remember { mutableStateOf(false) }

    fun read(kind: String, s: Subj): Set<Int> = readSet(sp, kind, s)

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
            Column {
                Text(if (revMode) "$revPct%" else "$donePct%", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = if (revMode) ColorAccent else ColorPrimary)
                Text(if (revMode) "revised  ($allRev / $allTotal)" else "studied  ($allDone / $allTotal)",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            LinearProgressIndicator(progress = { (if (revMode) allRev else allDone) / allTotal.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = if (revMode) ColorAccent else ColorPrimary, trackColor = ColorDivider)
            val exam = java.util.GregorianCalendar(2026, java.util.Calendar.NOVEMBER, 17).timeInMillis
            if (!revMode) {
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
        }

        // Subjects (tap a card to open that subject's own page)
        SUBJECTS.forEach { s ->
            val doneSet = read("done", s)
            val revSet = read("rev", s).intersect(doneSet)
            val shown = if (revMode) revSet.size else doneSet.size
            val accent = if (revMode) ColorAccent else ColorPrimary
            Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp))
                .clickable { onOpenSubject(s.id, revMode) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("$shown / ${s.total} ${if (revMode) "revised" else "done"}  \u00B7  ${shown * 100 / s.total}%",
                            style = MaterialTheme.typography.bodySmall, color = if (shown == s.total) accent else ColorMuted)
                    }
                    Text("\u203A", color = ColorMuted, style = MaterialTheme.typography.headlineSmall)
                }
                LinearProgressIndicator(progress = { shown / s.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = accent, trackColor = ColorDivider)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One subject on its own page: every chapter with its exact name. Tap to tick (Study) or tick revised (Revision). */
@Composable
fun ChapterSubjectScreen(subjectId: String, startRev: Boolean, onBack: () -> Unit) {
    val s = SUBJECTS.firstOrNull { it.id == subjectId } ?: run { onBack(); return }
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    var revMode by remember { mutableStateOf(startRev) }
    val doneSet = remember(tick) { readSet(sp, "done", s) }
    val revSet = remember(tick) { readSet(sp, "rev", s).intersect(doneSet) }
    val shown = if (revMode) revSet.size else doneSet.size
    val accent = if (revMode) ColorAccent else ColorPrimary

    fun tap(n: Int) {
        val isDone = n in doneSet
        val isRev = n in revSet
        if (revMode) {
            if (!isDone) Toast.makeText(ctx, "Study it first", Toast.LENGTH_SHORT).show()
            else saveSet(sp, "rev", s, if (isRev) revSet - n else revSet + n)
        } else {
            saveSet(sp, "done", s, if (isDone) doneSet - n else doneSet + n)
            if (isDone) saveSet(sp, "rev", s, revSet - n)
        }
        tick++
    }

    @Composable
    fun ChapterRow(n: Int, label: String, numLabel: String) {
        val isDone = n in doneSet
        val isRev = n in revSet
        val solid = (revMode && isRev) || (!revMode && isDone)
        val bg = when {
            revMode && isRev -> ColorAccent
            revMode && isDone -> ColorPrimary.copy(alpha = 0.30f)
            !revMode && isDone -> ColorPrimary
            else -> ColorSurface
        }
        val fg = if (solid) ColorOnPrimary else if (revMode && !isDone) ColorMuted else ColorText
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                .clickable { tap(n) }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (solid) "\u2713" else numLabel, color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            Text(label, color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                Text("$shown / ${s.total} ${if (revMode) "revised" else "studied"}  \u00B7  ${shown * 100 / s.total}%",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
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
        LinearProgressIndicator(progress = { shown / s.total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = accent, trackColor = ColorDivider)
        s.names.forEachIndexed { i, name -> ChapterRow(i + 1, name, "${i + 1}.") }
        if (s.extras.isNotEmpty()) {
            Text(if (s.id == "H") "\u0916\u0902\u0921 / \u0935\u093f\u0937\u092f" else "Sections", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
            s.extras.forEachIndexed { i, name -> ChapterRow(s.base + i + 1, name, "\u2022") }
        }
        Spacer(Modifier.height(16.dp))
    }
}
