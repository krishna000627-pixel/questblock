package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun dayKey(c: Calendar) = String.format(Locale.US, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

@HiltViewModel
class AttendanceViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    fun coins(delta: Int, detail: String) { viewModelScope.launch { repo.attendanceCoins(delta, detail) } }
}

private const val SCHOOL_COINS = 5
private const val COACHING_COINS = 3
private fun rewardFor(what: String) = if (what == "s") SCHOOL_COINS else COACHING_COINS
private fun labelFor(what: String) = when (what) { "s" -> "School"; "c1" -> "Coaching 1"; else -> "Coaching 2" }

/** School (once a day) and coaching (two classes a day) attendance. Saved on the phone as att_<yyyymmdd>_<s|c1|c2> = P or A. */
@Composable
fun AttendanceScreen(onBack: () -> Unit, vm: AttendanceViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    val todayKey = dayKey(Calendar.getInstance())
    var selected by remember { mutableStateOf(todayKey) }

    fun get(day: String, what: String): String = sp.getString("att_${day}_$what", "") ?: ""
    // School is a leave on Sundays unless you marked it yourself. Leave (L) is never counted and gives no coins.
    fun isSunday(day: String): Boolean = Calendar.getInstance().apply {
        set(day.substring(0, 4).toInt(), day.substring(4, 6).toInt() - 1, day.substring(6, 8).toInt())
    }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    fun eff(day: String, what: String): String {
        val v = get(day, what)
        return if (v.isEmpty() && what == "s" && isSunday(day)) "L" else v
    }
    fun set(day: String, what: String, v: String) {
        if (day < todayKey) return                              // past days are closed
        if (day > todayKey && (v == "P" || v == "A")) return   // no Present/Absent for days that have not come yet
        val e = sp.edit()
        if (v.isEmpty()) e.remove("att_${day}_$what") else e.putString("att_${day}_$what", v)
        // coins: Present earns, anything else takes back exactly what this slot gave (attcoin_<day>_<slot>)
        val given = sp.getInt("attcoin_${day}_$what", 0)
        val reward = if (v == "P") rewardFor(what) else 0
        if (reward != given) {
            vm.coins(reward - given, (if (reward > given) "Present: " else "Changed: ") + labelFor(what) + " " + day.substring(6, 8) + "/" + day.substring(4, 6))
            if (reward == 0) e.remove("attcoin_${day}_$what") else e.putInt("attcoin_${day}_$what", reward)
        }
        e.apply(); tick++
    }

    // coins for today's slots that were marked Present before attendance coins existed (once per slot)
    LaunchedEffect(Unit) {
        val e = sp.edit(); var changed = false
        listOf("s", "c1", "c2").forEach { what ->
            if (get(todayKey, what) == "P" && sp.getInt("attcoin_${todayKey}_$what", 0) == 0) {
                vm.coins(rewardFor(what), "Present: " + labelFor(what) + " " + todayKey.substring(6, 8) + "/" + todayKey.substring(4, 6))
                e.putInt("attcoin_${todayKey}_$what", rewardFor(what)); changed = true
            }
        }
        if (changed) { e.apply(); tick++ }
    }

    val refresh = tick
    val startDay = sp.getString("att_start", "20261008") ?: "20261008"   // counting starts here; earlier days are ignored
    val monthCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, monthOffset) }
    val monthPrefix = String.format(Locale.US, "%04d%02d", monthCal.get(Calendar.YEAR), monthCal.get(Calendar.MONTH) + 1)

    // Percentages: whole year-to-date and the shown month
    var sP = 0; var sA = 0; var cP = 0; var cA = 0; var msP = 0; var msA = 0; var mcP = 0; var mcA = 0
    sp.all.forEach { (k, v) ->
        if (!k.startsWith("att_") || k.length < 14) return@forEach
        val day = k.substring(4, 12); val what = k.substring(13)
        if (day < startDay) return@forEach
        val inMonth = day.startsWith(monthPrefix)
        if (what == "s") { if (v == "P") { sP++; if (inMonth) msP++ } else if (v == "A") { sA++; if (inMonth) msA++ } }
        else if (what == "c1" || what == "c2") { if (v == "P") { cP++; if (inMonth) mcP++ } else if (v == "A") { cA++; if (inMonth) mcA++ } }
    }
    fun pct(p: Int, a: Int) = if (p + a == 0) "-" else "${p * 100 / (p + a)}%"
    val unused = refresh

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("\uD83D\uDCC5 Attendance", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = ColorText)
        }

        // Summary
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("\uD83C\uDFEB School", pct(msP, msA), "this month  \u00B7  overall ${pct(sP, sA)}\n$msP present, $msA absent"),
                Triple("\uD83D\uDCDA Coaching", pct(mcP, mcA), "this month  \u00B7  overall ${pct(cP, cA)}\n$mcP present, $mcA absent"),
            ).forEach { (title, big, small) ->
                Column(Modifier.weight(1f).background(ColorSurface, RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(title, color = ColorMuted, style = MaterialTheme.typography.labelLarge)
                    Text(big, color = ColorPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(small, color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Selected day
        val selCal = Calendar.getInstance().apply {
            set(selected.substring(0, 4).toInt(), selected.substring(4, 6).toInt() - 1, selected.substring(6, 8).toInt())
        }
        Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(selCal.time),
                    color = ColorText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (selected == todayKey) TextButton(onClick = {
                    set(selected, "s", "P"); set(selected, "c1", "P"); set(selected, "c2", "P")
                }) { Text("All present") }
            }
            listOf("s" to "\uD83C\uDFEB School", "c1" to "\uD83D\uDCDA Coaching 1", "c2" to "\uD83D\uDCDA Coaching 2").forEach { (what, label) ->
                val cur = eff(selected, what)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, color = ColorText, modifier = Modifier.weight(1f))
                    listOf("P" to "\u2713", "A" to "\u2715", "L" to "\uD83C\uDF34").forEach { (v, t) ->
                        val on = cur == v
                        val c = when (v) { "P" -> ColorPrimary; "A" -> ColorDanger; else -> ColorMuted }
                        val locked = selected < todayKey || (selected > todayKey && v != "L")
                        Box(
                            Modifier.height(36.dp).width(46.dp).clip(RoundedCornerShape(50))
                                .background(if (on) c else ColorSurfaceHigh)
                                .alpha(if (locked) 0.35f else 1f)
                                .clickable(enabled = !locked) { set(selected, what, if (on) "" else v) },
                            contentAlignment = Alignment.Center,
                        ) { Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on && v != "L") ColorOnPrimary else ColorText) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(enabled = selected >= todayKey, onClick = {
                    set(selected, "s", "L"); set(selected, "c1", "L"); set(selected, "c2", "L")
                }) { Text("All leave", color = ColorMuted) }
            }
        }

        // Month calendar
        Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { monthOffset-- }, enabled = monthPrefix > startDay.substring(0, 6)) { Text("\u2039") }
                Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(monthCal.time), color = ColorText,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { if (monthOffset < 0) monthOffset++ }, enabled = monthOffset < 0) { Text("\u203A") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, Modifier.weight(1f), color = ColorMuted, style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val lead = monthCal.get(Calendar.DAY_OF_WEEK) - 1
            val dim = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val cells = List(lead) { 0 } + (1..dim).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { d ->
                        if (d == 0) { Spacer(Modifier.weight(1f)) } else {
                            val key = monthPrefix + String.format(Locale.US, "%02d", d)
                            val vals = listOf(eff(key, "s"), get(key, "c1"), get(key, "c2"))
                            val counted = vals.filter { it != "L" }
                            val pN = vals.count { it == "P" }
                            val aN = vals.count { it == "A" }
                            val openN = counted.count { it.isEmpty() }
                            val bg = when {
                                aN > 0 && pN > 0 -> ColorWarning.copy(alpha = 0.65f)                 // present + absent mixed: orange
                                aN > 0 && openN == 0 -> ColorDanger.copy(alpha = 0.65f)              // everything absent: strong red
                                aN > 0 -> ColorDanger.copy(alpha = 0.38f)                            // absent, rest not marked yet: light red
                                pN > 0 && openN == 0 -> ColorPrimary.copy(alpha = 0.70f)             // everything present: strong green
                                pN > 0 -> androidx.compose.ui.graphics.lerp(ColorPrimary, ColorWarning, 0.5f).copy(alpha = 0.50f) // some present, rest not marked: olive
                                vals.any { it == "L" } -> ColorMuted.copy(alpha = 0.30f)
                                else -> ColorSurfaceHigh
                            }
                            val future = key > todayKey || key < startDay
                            Box(
                                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                                    .then(if (key == selected) Modifier.border(2.dp, ColorText, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable(enabled = !future) { selected = key },
                                contentAlignment = Alignment.Center,
                            ) { Text("$d", color = if (future) ColorMuted else ColorText, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Counting from ${startDay.substring(6, 8)}/${startDay.substring(4, 6)}/${startDay.substring(0, 4)}. Grey = leave.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        // 📃 Mood: one tap a day, follows the selected day above. Saved as mood_<yyyymmdd> = 1..5
        val moods = listOf("\uD83D\uDE1E", "\uD83D\uDE15", "\uD83D\uDE10", "\uD83D\uDE42", "\uD83D\uDE04")
        val moodNow = sp.getInt("mood_$selected", 0)
        var weekSum = 0; var weekN = 0
        run {
            val c = Calendar.getInstance()
            repeat(7) {
                val v = sp.getInt("mood_${dayKey(c)}", 0)
                if (v in 1..5) { weekSum += v; weekN++ }
                c.add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        Column(Modifier.fillMaxWidth().background(ColorSurface, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("\uD83D\uDCC3 Mood", color = ColorText, fontWeight = FontWeight.Bold)
            Text("How was ${SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(selCal.time)}?", color = ColorMuted, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                moods.forEachIndexed { i, face ->
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (moodNow == i + 1) ColorPrimary.copy(alpha = 0.35f) else ColorSurfaceHigh)
                            .clickable {
                                if (moodNow == i + 1) sp.edit().remove("mood_$selected").apply()
                                else sp.edit().putInt("mood_$selected", i + 1).apply()
                                tick++
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(face, fontSize = 26.sp) }
                }
            }
            Text(
                if (weekN == 0) "Last 7 days: no check-ins yet"
                else "Last 7 days: ${moods[Math.round(weekSum.toFloat() / weekN).coerceIn(1, 5) - 1]} average ($weekN check-in${if (weekN > 1) "s" else ""})",
                color = ColorMuted, style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = {
            sp.edit().putString("att_start", todayKey).apply(); monthOffset = 0; selected = todayKey; tick++
        }, modifier = Modifier.fillMaxWidth()) { Text("Start counting from today", color = ColorMuted) }
        Spacer(Modifier.height(16.dp))
    }
}
