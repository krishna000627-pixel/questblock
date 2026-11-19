package com.krishna.questblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.CompanionStore
import com.krishna.questblock.data.Flags
import com.krishna.questblock.data.PowerFeatures
import com.krishna.questblock.util.Sounds

/**
 * v297: Command Deck — unified, zero-emoji control center for the 55 new Focus, RPG, Pet, Study, Wellness, and System features.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PowerSuiteScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { PowerFeatures.load(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val tabs = listOf("Focus", "RPG", "Study", "Wellness", "System")

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { Sounds.uiClose(); onBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText)
            }
            Column(Modifier.weight(1f)) {
                Text("Command Deck", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColorText)
                Text("55 integrated tools · Zero emojis · Flag-controlled", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, title ->
                val sel = tab == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) ColorPrimary else ColorCard)
                        .border(1.dp, if (sel) ColorPrimary else ColorDivider, RoundedCornerShape(10.dp))
                        .bounceClick { Sounds.uiTab(); tab = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = if (sel) Color.Black else ColorText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (msg.isNotEmpty()) {
                item {
                    Surface(
                        color = ColorPrimary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ColorPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(msg, modifier = Modifier.padding(10.dp), color = ColorPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when (tab) {
                0 -> {
                    item {
                        if (Flags.get("focus_presets")) DeckCard("Focus Presets & Break Timer") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15 to 3, 25 to 5, 50 to 10, 90 to 15).forEach { (fMin, bMin) ->
                                    val active = PowerFeatures.focusPresetMin == fMin
                                    PillChip("${fMin}m / ${bMin}m", active) {
                                        PowerFeatures.focusPresetMin = fMin
                                        PowerFeatures.breakMin = bMin
                                        PowerFeatures.save(ctx)
                                        msg = "Focus preset set to $fMin min focus / $bMin min break"
                                    }
                                }
                            }
                            Text("Subject Tag", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PowerFeatures.SUBJECTS.forEach { subj ->
                                    PillChip(subj, PowerFeatures.subjectTag == subj) {
                                        PowerFeatures.subjectTag = subj
                                        PowerFeatures.save(ctx)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (Flags.get("urge_shield")) DeckCard("Daily Focus Goal & Urge Shield") {
                            val frac = (PowerFeatures.focusDoneTodayMin / PowerFeatures.dailyFocusGoalMin.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                            Text("Progress: ${PowerFeatures.focusDoneTodayMin} / ${PowerFeatures.dailyFocusGoalMin} min", fontWeight = FontWeight.SemiBold, color = ColorText)
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)), color = ColorPrimary, trackColor = ColorSurfaceHigh, strokeCap = StrokeCap.Round)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("+15 min logged", false) {
                                    PowerFeatures.focusDoneTodayMin += 15
                                    PowerFeatures.dealBossDamage(ctx, 30)
                                    Sounds.coin()
                                    msg = "Logged +15m focus and dealt 30 Boss DMG!"
                                }
                                PillChip("Resisted Urge (${PowerFeatures.urgesResisted})", false) {
                                    PowerFeatures.logUrgeResisted(ctx)
                                    Sounds.streak()
                                    msg = "Urge resisted! +25 Boss DMG dealt."
                                }
                            }
                            OutlinedTextField(
                                value = PowerFeatures.sessionIntention,
                                onValueChange = { PowerFeatures.sessionIntention = it.take(80); PowerFeatures.save(ctx) },
                                label = { Text("Session intention") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        if (Flags.get("focus_mixer")) DeckCard("Focus Audio Mixer & 10m Checkpoint") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Low", "Medium", "High").forEachIndexed { idx, lbl ->
                                    PillChip("Vol: $lbl", PowerFeatures.ambientLevel == idx) {
                                        PowerFeatures.ambientLevel = idx
                                        PowerFeatures.save(ctx)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("10-minute checkpoint chime", color = ColorText, style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = PowerFeatures.autoChime10m, onCheckedChange = {
                                    PowerFeatures.autoChime10m = it
                                    PowerFeatures.save(ctx)
                                    Sounds.uiToggle(it)
                                })
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        if (Flags.get("boss_battle")) DeckCard("Weekly Study Boss Battle") {
                            val hpFrac = (PowerFeatures.bossHp / PowerFeatures.bossMaxHp.toFloat()).coerceIn(0f, 1f)
                            Text("Boss HP: ${PowerFeatures.bossHp} / ${PowerFeatures.bossMaxHp}  (Total DMG: ${PowerFeatures.bossDamageTotal})", fontWeight = FontWeight.Bold, color = ColorText)
                            LinearProgressIndicator(progress = { hpFrac }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)), color = Color(0xFFFF6165), trackColor = ColorSurfaceHigh, strokeCap = StrokeCap.Round)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Strike Boss (25 DMG)", false) {
                                    PowerFeatures.dealBossDamage(ctx, 25)
                                    Sounds.combo(2)
                                    msg = "Dealt 25 DMG to the weekly boss!"
                                }
                                PillChip("Roll Daily Chest", false) {
                                    Sounds.shopBuy()
                                    msg = PowerFeatures.rollDailyChest(ctx)
                                }
                            }
                        }
                    }
                    item {
                        if (Flags.get("hero_titles")) DeckCard("Hero Title, Quota & Coin Vault") {
                            Text("Active Title: ${PowerFeatures.heroTitle}", fontWeight = FontWeight.Bold, color = ColorPrimary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PowerFeatures.HERO_TITLES.forEach { t ->
                                    PillChip(t, PowerFeatures.heroTitle == t) {
                                        PowerFeatures.heroTitle = t
                                        PowerFeatures.save(ctx)
                                    }
                                }
                            }
                            Text("Vaulted Savings: ${PowerFeatures.vaultedCoins} G · Daily Quota: ${PowerFeatures.dailyQuestQuota} quests · Crit: ${PowerFeatures.critChancePct}%", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Quota +1", false) { PowerFeatures.dailyQuestQuota = (PowerFeatures.dailyQuestQuota + 1).coerceAtMost(15); PowerFeatures.save(ctx) }
                                PillChip("Quota -1", false) { PowerFeatures.dailyQuestQuota = (PowerFeatures.dailyQuestQuota - 1).coerceAtLeast(1); PowerFeatures.save(ctx) }
                            }
                        }
                    }
                    item {
                        if (Flags.get("daily_fortune")) DeckCard("Daily Fortune & Companion Energy") {
                            Text("Daily Fortune: \"${PowerFeatures.dailyFortune()}\"", style = MaterialTheme.typography.bodyMedium, color = ColorText)
                            Text("Companion Energy: ${PowerFeatures.petEnergy}%", style = MaterialTheme.typography.labelMedium, color = ColorPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Pet Nap (+30% Energy)", false) {
                                    PowerFeatures.napPet(ctx)
                                    Sounds.petFeed()
                                    msg = "Companion rested! Energy is now ${PowerFeatures.petEnergy}%."
                                }
                                PillChip("Crown: ${if (PowerFeatures.petCrownVisible) "ON" else "OFF"}", PowerFeatures.petCrownVisible) {
                                    PowerFeatures.petCrownVisible = !PowerFeatures.petCrownVisible
                                    PowerFeatures.save(ctx)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        var cardIdx by remember { mutableIntStateOf(0) }
                        var flipped by remember { mutableStateOf(false) }
                        var newQ by remember { mutableStateOf("") }
                        var newA by remember { mutableStateOf("") }
                        val cards = PowerFeatures.flashcardsRaw.split(";;").mapNotNull {
                            val parts = it.split("|")
                            if (parts.size >= 2) parts[0] to parts[1] else null
                        }.ifEmpty { listOf("No flashcards" to "Add one below") }
                        val cur = cards[cardIdx % cards.size]
                        if (Flags.get("flashcards")) DeckCard("Quick Flashcard Drill (${(cardIdx % cards.size) + 1}/${cards.size})") {
                            Surface(
                                color = ColorSurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ColorPrimary.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth().bounceClick { flipped = !flipped },
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (flipped) "ANSWER" else "QUESTION", style = MaterialTheme.typography.labelSmall, color = ColorPrimary, fontWeight = FontWeight.Bold)
                                    Text(if (flipped) cur.second else cur.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
                                    Text("Tap card to flip", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Next Card", false) { cardIdx++; flipped = false }
                                PillChip("Flip", false) { flipped = !flipped }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(value = newQ, onValueChange = { newQ = it }, label = { Text("Q") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = newA, onValueChange = { newA = it }, label = { Text("A") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            TextButton(onClick = {
                                if (newQ.isNotBlank() && newA.isNotBlank()) {
                                    PowerFeatures.flashcardsRaw = PowerFeatures.flashcardsRaw + ";;${newQ.trim()}|${newA.trim()}"
                                    PowerFeatures.save(ctx)
                                    newQ = ""; newA = ""
                                    Sounds.questAdd()
                                    msg = "Flashcard added!"
                                }
                            }) { Text("Add Flashcard", fontWeight = FontWeight.Bold) }
                        }
                    }
                    item {
                        if (Flags.get("exam_countdown") || Flags.get("scratchpad")) DeckCard("Exam Countdown & Scratchpad") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(PowerFeatures.examName, fontWeight = FontWeight.Bold, color = ColorText)
                                    Text("${PowerFeatures.examDaysLeft} days remaining", color = ColorPrimary, fontWeight = FontWeight.Black)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PillChip("-1d", false) { PowerFeatures.examDaysLeft = (PowerFeatures.examDaysLeft - 1).coerceAtLeast(0); PowerFeatures.save(ctx) }
                                    PillChip("+1d", false) { PowerFeatures.examDaysLeft += 1; PowerFeatures.save(ctx) }
                                }
                            }
                            OutlinedTextField(
                                value = PowerFeatures.scratchpad,
                                onValueChange = { PowerFeatures.scratchpad = it.take(400); PowerFeatures.save(ctx) },
                                label = { Text("Study scratchpad / brain dump") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                            )
                        }
                    }
                    item {
                        if (Flags.get("exam_countdown")) DeckCard("Weak Topics & Daily Wins") {
                            Text("Weak Topics: ${PowerFeatures.weakTopics.replace("|", " · ")}", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                            OutlinedTextField(
                                value = PowerFeatures.dailyWins,
                                onValueChange = { PowerFeatures.dailyWins = it.take(240); PowerFeatures.save(ctx) },
                                label = { Text("Today's wins (1-3 lines)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                    }
                }

                3 -> {
                    item {
                        if (Flags.get("water_tracker")) DeckCard("Hydration & 20-20-20 Eye Breaks") {
                            Text("Water Today: ${PowerFeatures.waterGlasses} / 8 glasses", fontWeight = FontWeight.Bold, color = ColorText)
                            LinearProgressIndicator(progress = { (PowerFeatures.waterGlasses / 8f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)), color = Color(0xFF50B5E9), trackColor = ColorSurfaceHigh, strokeCap = StrokeCap.Round)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("+1 Glass", false) { PowerFeatures.waterGlasses = (PowerFeatures.waterGlasses + 1).coerceAtMost(16); PowerFeatures.save(ctx); Sounds.coin() }
                                PillChip("Reset Water", false) { PowerFeatures.waterGlasses = 0; PowerFeatures.save(ctx) }
                                PillChip("Eye Break (${PowerFeatures.eyeBreaks})", false) { PowerFeatures.eyeBreaks += 1; PowerFeatures.save(ctx); Sounds.chime() }
                            }
                        }
                    }
                    item {
                        if (Flags.get("morning_night_routines")) DeckCard("Morning & Night Routine Checklists") {
                            Text("Morning Routine", fontWeight = FontWeight.Bold, color = ColorPrimary)
                            listOf("Hydrate & Sunlight", "Review Today's Quests", "No Scrolling 30m").forEachIndexed { idx, label ->
                                val bit = 1 shl idx
                                val checked = (PowerFeatures.morningChecks and bit) != 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = checked, onCheckedChange = {
                                        PowerFeatures.morningChecks = if (it) PowerFeatures.morningChecks or bit else PowerFeatures.morningChecks and bit.inv()
                                        PowerFeatures.save(ctx); Sounds.uiToggle(it)
                                    })
                                    Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Night Wind-Down", fontWeight = FontWeight.Bold, color = ColorPrimary)
                            listOf("Pack Desk for Tomorrow", "Log Daily Wins", "Phone Away Before Bed").forEachIndexed { idx, label ->
                                val bit = 1 shl idx
                                val checked = (PowerFeatures.nightChecks and bit) != 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = checked, onCheckedChange = {
                                        PowerFeatures.nightChecks = if (it) PowerFeatures.nightChecks or bit else PowerFeatures.nightChecks and bit.inv()
                                        PowerFeatures.save(ctx); Sounds.uiToggle(it)
                                    })
                                    Text(label, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                4 -> {
                    item {
                        if (Flags.get("audio_haptic_presets")) DeckCard("Audio, Haptics & UI Presets") {
                            Text("Audio Tone Preset", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Crisp", "Warm", "Retro").forEachIndexed { idx, lbl ->
                                    PillChip(lbl, PowerFeatures.audioPreset == idx) { PowerFeatures.audioPreset = idx; PowerFeatures.save(ctx); Sounds.uiTap() }
                                }
                            }
                            Text("Haptic Strength", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Light", "Medium", "Strong").forEachIndexed { idx, lbl ->
                                    PillChip(lbl, PowerFeatures.hapticPreset == idx) { PowerFeatures.hapticPreset = idx; PowerFeatures.save(ctx); Sounds.uiTap() }
                                }
                            }
                        }
                    }
                    item {
                        DeckCard("Quick Flag Presets & Snapshot Export") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillChip("Maximal FX On", false) {
                                    listOf("anim_master", "anim_transitions", "anim_stagger", "anim_press", "anim_counters", "anim_xp_shimmer", "anim_hold_fx", "anim_ambient_bg", "anim_combo", "ui_redesign", "theme_strings", "theme_ux_v2").forEach {
                                        Flags.set(ctx, it, true)
                                    }
                                    msg = "All animations & UI/UX upgrades enabled!"
                                }
                                PillChip("Minimal Mode", false) {
                                    listOf("anim_ambient_bg", "anim_combo", "anim_xp_shimmer").forEach { Flags.set(ctx, it, false) }
                                    msg = "Minimalist mode enabled!"
                                }
                            }
                            PillChip("Copy JSON Stats Snapshot", false) {
                                val st = CompanionStore.state
                                val json = """{"title":"${PowerFeatures.heroTitle}","focusTodayMin":${PowerFeatures.focusDoneTodayMin},"urgesResisted":${PowerFeatures.urgesResisted},"bossDmg":${PowerFeatures.bossDamageTotal},"pet":"${st.activeId}","petLv":${st.level},"water":${PowerFeatures.waterGlasses}}"""
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("QuestBlock Snapshot", json))
                                Sounds.shopBuy()
                                msg = "Copied JSON snapshot to clipboard!"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = ColorText)
            content()
        }
    }
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ColorPrimary else ColorSurface)
            .border(1.dp, if (selected) ColorPrimary else ColorDivider, RoundedCornerShape(10.dp))
            .bounceClick(playSound = false, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else ColorText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Compact Home entry card to open the 55-feature Command Deck. */
@Composable
fun PowerSuiteMiniCard(onOpenPowerSuite: () -> Unit, modifier: Modifier = Modifier) {
    if (!Flags.get("power_suite")) return
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().bounceClick(onClick = onOpenPowerSuite),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Command Deck · ${PowerFeatures.heroTitle}", fontWeight = FontWeight.Bold, color = ColorText, style = MaterialTheme.typography.bodyMedium)
                Text("Focus ${PowerFeatures.focusDoneTodayMin}m · Boss ${PowerFeatures.bossHp} HP · Water ${PowerFeatures.waterGlasses}/8 · Tap to open", color = ColorMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text("OPEN", color = ColorPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
    }
}
