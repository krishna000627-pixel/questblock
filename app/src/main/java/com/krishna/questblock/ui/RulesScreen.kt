@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.krishna.questblock.data.EXAM_FUN_LIMIT_MIN
import com.krishna.questblock.data.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(private val prefs: Prefs) : ViewModel() {
    var autoVerifyOn by mutableStateOf(prefs.autoVerify); private set
    var examOn by mutableStateOf(prefs.examMode); private set
    var weekendMin by mutableIntStateOf(prefs.weekendFunLimitMin); private set
    var penaltyCoins by mutableIntStateOf(prefs.focusExitPenalty); private set
    var quietEnabledOn by mutableStateOf(prefs.quietEnabled); private set
    var quietFrom by mutableIntStateOf(prefs.quietStartMin); private set
    var quietUntil by mutableIntStateOf(prefs.quietEndMin); private set
    var studyEnabledOn by mutableStateOf(prefs.studyBlockEnabled); private set
    var studyFrom by mutableIntStateOf(prefs.studyStartMin); private set
    var studyUntil by mutableIntStateOf(prefs.studyEndMin); private set

    fun updateAutoVerify(v: Boolean) { prefs.autoVerify = v; autoVerifyOn = v }
    fun updateExam(v: Boolean) { prefs.examMode = v; examOn = v }
    fun updateWeekend(v: Int) { prefs.weekendFunLimitMin = v.coerceIn(0, 360); weekendMin = prefs.weekendFunLimitMin }
    fun updatePenalty(v: Int) { prefs.focusExitPenalty = v; penaltyCoins = prefs.focusExitPenalty }
    fun updateQuietEnabled(v: Boolean) { prefs.quietEnabled = v; quietEnabledOn = v }
    fun updateQuietFrom(v: Int) { prefs.quietStartMin = v; quietFrom = prefs.quietStartMin }
    fun updateQuietUntil(v: Int) { prefs.quietEndMin = v; quietUntil = prefs.quietEndMin }
    fun updateStudyEnabled(v: Boolean) { prefs.studyBlockEnabled = v; studyEnabledOn = v }
    fun updateStudyFrom(v: Int) { prefs.studyStartMin = v; studyFrom = prefs.studyStartMin }
    fun updateStudyUntil(v: Int) { prefs.studyEndMin = v; studyUntil = prefs.studyEndMin }
}

private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
fun RulesScreen(onBack: () -> Unit, vm: RulesViewModel = hiltViewModel()) {
    LazyColumn(
        Modifier.fillMaxSize().background(ColorBg),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Text("Rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
            }
        }
        item {
            HubCard {
                Text("🤖 Automation", fontWeight = FontWeight.Bold, color = ColorPrimary, style = MaterialTheme.typography.titleSmall)
                SwitchRow("Auto-complete quests",
                    "A Study quest with a time in its name (\"Physics 45 min\") or a coding quest ticks itself from app time. \"Keep Fun under 60 min\" ticks after 22:00 if you stayed under.",
                    vm.autoVerifyOn) { vm.updateAutoVerify(it) }
            }
        }
        item {
            HubCard {
                Text("📚 Exam mode", fontWeight = FontWeight.Bold, color = ColorPrimary, style = MaterialTheme.typography.titleSmall)
                SwitchRow("Exam mode",
                    "Fun limit capped at $EXAM_FUN_LIMIT_MIN min, study target 6 h, 4 focus sessions, 8 quests, and Fun & Games apps locked 08:00–20:00 (or your study hours).",
                    vm.examOn) { vm.updateExam(it) }
            }
        }
        item {
            HubCard {
                Text("🔒 Lock windows", fontWeight = FontWeight.Bold, color = ColorPrimary, style = MaterialTheme.typography.titleSmall)
                SwitchRow("Quiet hours", "Fun & Games apps stay locked in this window, for example at night.", vm.quietEnabledOn) { vm.updateQuietEnabled(it) }
                if (vm.quietEnabledOn) {
                    StepRow("From", hhmm(vm.quietFrom), { vm.updateQuietFrom(vm.quietFrom - 30) }, { vm.updateQuietFrom(vm.quietFrom + 30) })
                    StepRow("Until", hhmm(vm.quietUntil), { vm.updateQuietUntil(vm.quietUntil - 30) }, { vm.updateQuietUntil(vm.quietUntil + 30) })
                }
                SwitchRow("Study hours", "Fun & Games apps stay locked in this window.", vm.studyEnabledOn) { vm.updateStudyEnabled(it) }
                if (vm.studyEnabledOn) {
                    StepRow("From", hhmm(vm.studyFrom), { vm.updateStudyFrom(vm.studyFrom - 30) }, { vm.updateStudyFrom(vm.studyFrom + 30) })
                    StepRow("Until", hhmm(vm.studyUntil), { vm.updateStudyUntil(vm.studyUntil - 30) }, { vm.updateStudyUntil(vm.studyUntil + 30) })
                }
                Text("A game already open is not cut when a window starts; it is locked the next time you open it.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        item {
            HubCard {
                Text("⚖️ Limits and penalties", fontWeight = FontWeight.Bold, color = ColorPrimary, style = MaterialTheme.typography.titleSmall)
                StepRow("Weekend fun limit", if (vm.weekendMin == 0) "same" else "${vm.weekendMin} min",
                    { vm.updateWeekend(vm.weekendMin - 15) }, { vm.updateWeekend(vm.weekendMin + 15) })
                StepRow("Focus give-up penalty", if (vm.penaltyCoins == 0) "off" else "${vm.penaltyCoins} \uD83E\uDE99",
                    { vm.updatePenalty(vm.penaltyCoins - 5) }, { vm.updatePenalty(vm.penaltyCoins + 5) })
                Text("The penalty is only charged after the first minute of a focus session.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ColorText)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = ColorText, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
        Text(value, color = ColorText, fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 70.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
    }
}
