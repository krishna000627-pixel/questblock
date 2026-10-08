package com.krishna.questblock.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
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
    Subj("H", "\u0939\u093f\u0928\u094d\u0926\u0940", listOf(
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

private data class SubjStyle(val emoji: String, val color: Color, val groups: List<Pair<Int, String>> = emptyList())

private fun styleOf(id: String): SubjStyle = when (id) {
    "P" -> SubjStyle("\u269B\uFE0F", Color(0xFF4FC3F7))
    "C" -> SubjStyle("\uD83E\uDDEA", Color(0xFF81C784))
    "M" -> SubjStyle("\uD83D\uDCD0", Color(0xFFFFB74D))
    "H" -> SubjStyle("\uD83D\uDCD6", Color(0xFFE57373), listOf(
        1 to "\u0915\u093e\u0935\u094d\u092f \u0916\u0923\u094d\u0921",
        10 to "\u0917\u0926\u094d\u092f \u0916\u0923\u094d\u0921",
        16 to "\u0935\u093f\u0924\u093e\u0928 \u092d\u093e\u0917-2",
        19 to "\u0905\u0928\u094d\u092f \u0916\u0923\u094d\u0921"))
    else -> SubjStyle("\u270D\uFE0F", Color(0xFFBA68C8), listOf(
        1 to "Flamingo \u00B7 Prose", 9 to "Flamingo \u00B7 Poetry", 14 to "Vistas", 20 to "Sections"))
}

/** Circular progress with the percent in the middle. */
@Composable
private fun Ring(pct: Int, color: Color, size: Int = 58) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        val track = ColorDivider
        Canvas(Modifier.fillMaxSize()) {
            val w = 7.dp.toPx()
            val inset = w / 2
            val d = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), d, style = Stroke(w))
            drawArc(color, -90f, 360f * pct / 100f, false, Offset(inset, inset), d, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$pct%", color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}

private fun labelOf(s: Subj, n: Int): String = if (n <= s.base) s.names[n - 1] else s.extras[n - s.base - 1]

/** PCMHE chapter checklist overview. Study mode counts chapters done; Revision mode counts chapters revised (only done ones). */
@Composable
fun ChaptersScreen(
    onBack: () -> Unit,
    onOpenSubject: (String, Boolean) -> Unit = { _, _ -> },
    onOpenMarks: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var revMode by rememberSaveable { mutableStateOf(false) }

    // Re-read on every composition (returning from a subject page recomposes this screen).
    val allTotal = SUBJECTS.sumOf { it.total }
    val allDone = SUBJECTS.sumOf { readSet(sp, "done", it).size }
    val allRev = SUBJECTS.sumOf { s -> readSet(sp, "rev", s).intersect(readSet(sp, "done", s)).size }
    val donePct = allDone * 100 / allTotal
    val revPct = allRev * 100 / allTotal
    val accent = if (revMode) ColorAccent else ColorPrimary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("\uD83D\uDCDA Chapters", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        // Study / Revision toggle (only here; subject pages follow it)
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

        // Overall card: tap = Marks page
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ColorSurface)
                .clickable { onOpenMarks() }.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (revMode) "$revPct%" else "$donePct%", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = accent)
                    Text(if (revMode) "revised  ($allRev / $allTotal)" else "studied  ($allDone / $allTotal)",
                        style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(ColorSurfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("\uD83D\uDCDD Marks \u203A", color = ColorText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                }
            }
            LinearProgressIndicator(progress = { (if (revMode) allRev else allDone) / allTotal.toFloat() },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = accent, trackColor = ColorDivider)
            if (!revMode) {
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
        }

        // Subject cards: tap opens that subject's own page
        SUBJECTS.forEach { s ->
            val st = styleOf(s.id)
            val doneSet = readSet(sp, "done", s)
            val revSet = readSet(sp, "rev", s).intersect(doneSet)
            val shown = if (revMode) revSet.size else doneSet.size
            val pct = shown * 100 / s.total
            val next: String? = if (revMode) (1..s.total).firstOrNull { it in doneSet && it !in revSet }?.let { labelOf(s, it) }
                                else (1..s.total).firstOrNull { it !in doneSet }?.let { labelOf(s, it) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ColorSurface)
                    .clickable { onOpenSubject(s.id, revMode) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Ring(pct, st.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${st.emoji}  ${s.name}", color = ColorText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("$shown / ${s.total} ${if (revMode) "revised" else "done"}",
                        style = MaterialTheme.typography.bodySmall, color = if (shown == s.total) st.color else ColorMuted)
                    Text(
                        when {
                            next != null -> (if (revMode) "Revise next: " else "Next: ") + next
                            revMode -> "All revised \u2713"
                            else -> "All chapters done \u2713"
                        },
                        style = MaterialTheme.typography.labelMedium, color = ColorMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("\u203A", color = ColorMuted, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One subject on its own page. The mode (Study / Revision) is the one picked on the Chapters page. */
@Composable
fun ChapterSubjectScreen(subjectId: String, startRev: Boolean, onBack: () -> Unit) {
    val s = SUBJECTS.firstOrNull { it.id == subjectId } ?: run { onBack(); return }
    val st = styleOf(s.id)
    val revMode = startRev
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    val doneSet = remember(tick) { readSet(sp, "done", s) }
    val revSet = remember(tick) { readSet(sp, "rev", s).intersect(doneSet) }
    val shown = if (revMode) revSet.size else doneSet.size
    val pct = shown * 100 / s.total

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
    fun ChapterRow(n: Int) {
        val isDone = n in doneSet
        val isRev = n in revSet
        val lit = if (revMode) isRev else isDone
        val tag = when {
            revMode && isRev -> "Revised"
            revMode && isDone -> "Revise"
            revMode -> "Study first"
            isDone -> "Studied"
            else -> ""
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(if (lit) st.color.copy(alpha = 0.16f) else ColorSurface)
                .clickable { tap(n) }.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(if (lit) st.color else ColorSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (lit) "\u2713" else (if (n <= s.base) "$n" else "\u2022"), fontWeight = FontWeight.Bold,
                    color = if (lit) ColorOnPrimary else ColorMuted, style = MaterialTheme.typography.labelLarge)
            }
            Text(labelOf(s, n), color = if (revMode && !isDone) ColorMuted else ColorText,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (tag.isNotEmpty()) Text(tag, color = if (lit) st.color else ColorMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("${st.emoji}  ${s.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ColorSurface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Ring(pct, st.color, 72)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$shown / ${s.total} ${if (revMode) "revised" else "studied"}", color = ColorText,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Box(Modifier.clip(RoundedCornerShape(50)).background((if (revMode) ColorAccent else ColorPrimary).copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(if (revMode) "\uD83D\uDD01 Revision mode" else "\uD83D\uDCD6 Study mode",
                        color = if (revMode) ColorAccent else ColorPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        for (n in 1..s.total) {
            st.groups.firstOrNull { it.first == n }?.let { (_, title) ->
                Text(title, style = MaterialTheme.typography.labelLarge, color = st.color,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            ChapterRow(n)
        }
        Spacer(Modifier.height(16.dp))
    }
}
