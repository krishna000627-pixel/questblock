package com.krishna.questblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.data.Flags as FxFlags
import com.krishna.questblock.util.SystemEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** v257: one theme of the Theme effects page. [id] is the flag name without the fx_ prefix. */
private class FxTheme(
    val id: String, val name: String, val style: Boolean, val color: Color,
    val ring: String, val bar: String, val level: String, val pop: String,
    val strip: String = "", val board: String = "",
)

private val FxPurple = Color(0xFF7F77DD)
private val FxTeal = Color(0xFF1D9E75)
private val FxCoral = Color(0xFFD85A30)
private val FxPink = Color(0xFFD4537E)
private val FxGray = Color(0xFF888780)
private val FxBlue = Color(0xFF378ADD)
private val FxGreen = Color(0xFF639922)
private val FxAmber = Color(0xFFBA7517)
private val FxRed = Color(0xFFE24B4A)

private val FX_THEMES: List<FxTheme> = listOf(
    FxTheme("system", "System", true, FxTeal, "HUD ring", "XP blocks", "bracket level up", "[SYSTEM] words", "[ SYSTEM ] strip, PLAYER STATUS", "cut corners, brackets, scan line"),
    FxTheme("ninja", "Ninja Scroll", true, FxRed, "ink brush ring", "ink brush bar", "red seal", "DONE", "no strip, seal before titles", "square paper cards"),
    FxTheme("hashira", "Hashira Mist", true, FxBlue, "breathing mist ring", "pill bar", "mist", "Form 1 to 3", "no strip", "round frosted cards"),
    FxTheme("monarch", "Shadow Monarch", true, FxPurple, "violet ring with motes", "glow bar", "ARISE", "shadow army counter", "ARISE strip, shadow army", "glowing edge line"),
    FxTheme("gothic", "Gothic Halo", true, FxRed, "double ring, diamond", "line bar", "red halo", "rite words", "MEMENTO strip, days left", "inner frame"),
    FxTheme("guild", "Guild Board", true, FxAmber, "brass stud ring", "plank bar", "wax seal", "Copper Silver Gold", "GUILD BOARD strip", "brass studs, tilted notices"),
    FxTheme("pixel", "Pixel Arcade", true, FxGreen, "pixel ring", "block bar", "confetti", "STAGE words", "PLAYER 1 strip", "hard shadow"),
    FxTheme("handheld", "Handheld", true, FxGreen, "dot matrix ring", "LCD bar", "LEVEL UP box", "SAVING box", "QUESTBOY strip", "LCD inner frame"),
    FxTheme("blueprint", "Blueprint", true, FxBlue, "drafting ring", "dimension line", "REVISION ACCEPTED", "TYPE words", "SHEET 01 strip", "corner marks, dimension line"),
    FxTheme("sunset", "Sunset", false, FxCoral, "sun rays", "sun bar", "NEW DAY", "Golden hour", "golden hour strip with a sun", "warm glow and a sun corner"),
    FxTheme("ocean", "Ocean", false, FxBlue, "ripple rings", "wave bar", "DEEPER", "Tide is in", "tide strip with a wave line", "wave line on every card"),
    FxTheme("forest", "Forest", false, FxGreen, "leaves on the arc", "vine bar", "GROWTH", "Grown", "trail strip with leaves", "leaf corner and a vine"),
    FxTheme("neon", "Neon", false, FxPink, "glow tube", "glow tube", "LEVEL UP sign", "NICE", "ON AIR strip, glow line", "glowing top and bottom lines"),
    FxTheme("scifi", "Sci-Fi", false, FxTeal, "36 segments", "16 slanted cells", "UPGRADE COMPLETE", "SYNC COMPLETE", "SYS strip with slanted cells", "corner ticks"),
    FxTheme("amoled", "AMOLED", false, FxGray, "hairline", "hairline", "thin ring", "Done", "hairline strip", "one dot, hairline"),
    FxTheme("terminal", "Terminal", false, FxGreen, "60 ticks, cursor", "text bar", "level_up typed", "exit 0", "prompt strip user@questblock", "block cursor corner"),
    FxTheme("paper", "Paper", false, FxGray, "pencil sketch", "pencil line", "NEW CHAPTER", "Noted", "daily notes strip, pencil line", "tape and a folded corner"),
    FxTheme("linen", "Linen", false, FxAmber, "stitched arc", "stitches", "WELL DONE", "Stitched", "stitched strip", "stitched border"),
    FxTheme("sage", "Sage", false, FxGreen, "dotted, sprout", "sprout line", "BLOOM", "Sprouted", "sprout strip, dotted line", "sprout corner"),
    FxTheme("sky", "Sky", false, FxBlue, "sun and cloud", "sun and cloud", "SOAR", "Clear skies", "clear skies strip with a cloud", "cloud corner"),
    FxTheme("lilac", "Lilac", false, FxPurple, "12 petals", "14 flower beads", "BLOOM", "In bloom", "in bloom strip with petals", "flower corner"),
    FxTheme("chalk", "Chalk", false, FxGray, "chalk lines", "chalk, tally ticks", "A+", "Marked", "class strip, chalk line", "chalk underline and dust"),
    FxTheme("clay", "Clay", false, FxCoral, "puffy arc", "puffy pill", "BOING!", "Squish", "soft pill strip", "glossy highlight"),
    FxTheme("rose", "Rose", false, FxPink, "heart at the end", "thin bar, heart", "LOVELY", "Lovely", "with love strip, hearts", "heart corner"),
    FxTheme("indigo", "Indigo", false, FxPurple, "stars, crescent", "star trail", "STARGAZER", "Shooting star", "night sky strip with stars", "star corner"),
    FxTheme("mono", "Mono", false, FxGray, "thin line", "2 px rule", "number fades in", "Done.", "hairline strip", "hairline border, one accent tick"),
    FxTheme("whitespace", "Whitespace", false, FxGray, "hairline", "hairline rule", "two quiet lines", "Done", "hairline strip", "no borders, dividers only; lock page shows the time"),
    FxTheme("zen", "Zen", false, FxGray, "ens\u014D brush circle", "brush stroke", "number in a brush circle", "Done", "light line strip", "soft pebble cards with a light bottom line"),
    FxTheme("carnival", "Carnival", false, FxPink, "marquee bulbs", "candy stripe bar", "fireworks, light border", "TA-DA! confetti", "bunting strip", "striped top, confetti dots"),
    FxTheme("baroque", "Baroque", false, FxAmber, "gold double ring", "gold frame bar", "wax seal", "Gilded", "gold double rule, diamond", "gold inner frame, corner diamonds"),
    FxTheme("vaporwave", "Vaporwave Arcade", false, FxPurple, "gradient ring, sun", "glitch bar", "sun rises over a grid", "C O M B O", "INSERT COIN strip", "gradient line, small sun"),
)

/** v257: how many theme switches are on, for the entry card on the flags page. */
internal fun themeFxOnCount(): Int = FX_THEMES.count { FxFlags.get("fx_" + it.id) }
internal fun themeFxTotal(): Int = FX_THEMES.size

/** v257: the Theme effects subpage of Flags: master switch (themefx) and one switch (fx_<theme>) per theme. Same flags as the terminal. */
@Composable
fun ThemeFxFlagsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    val pv = previewId
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var covering by remember { mutableStateOf(false) }
    if (pv != null) { ThemeFxTryPage(themeId = pv, onBack = { restoreRealTheme(ctx); covering = true; previewId = null }); return }
    if (covering) { ThemeSwitchCover(onDone = { covering = false }); return }
    val master = FxFlags.get("themefx")
    val onCount = FX_THEMES.count { FxFlags.get("fx_" + it.id) }
    fun guarded(block: () -> Unit) {
        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running" else { block(); msg = "" }
    }
    val shown = FX_THEMES.filter { filter == 0 || (filter == 1 && it.style) || (filter == 2 && !it.style) }
    val chips = listOf("All ${FX_THEMES.size}", "Style ${FX_THEMES.count { it.style }}", "Shop ${FX_THEMES.count { !it.style }}")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("Flags", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    Text("Theme effects", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Theme extras (all)", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Text("Master switch for every theme below", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                        }
                        Switch(checked = master, onCheckedChange = { v -> guarded { FxFlags.set(ctx, "themefx", v) } })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (master) "$onCount of ${FX_THEMES.size} themes on" else "Master switch is off",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = ColorMuted,
                        )
                        TextButton(onClick = { guarded { FX_THEMES.forEach { FxFlags.set(ctx, "fx_" + it.id, true) }; FxFlags.set(ctx, "themefx", true) } }) { Text("All on") }
                        TextButton(onClick = { guarded { FX_THEMES.forEach { FxFlags.set(ctx, "fx_" + it.id, false) } } }) { Text("All off") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEachIndexed { i, label ->
                    val sel = filter == i
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) ColorPrimary.copy(0.25f) else ColorSurface)
                            .border(1.dp, if (sel) ColorPrimary else ColorDivider, RoundedCornerShape(20.dp))
                            .clickable { filter = i }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (sel) ColorPrimary else ColorMuted,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        shown.forEach { t ->
            item(key = t.id) {
                val own = FxFlags.get("fx_" + t.id)
                val active = master && own
                val expanded = open == t.id
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { open = if (expanded) null else t.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(t.color.copy(alpha = if (active) 1f else 0.35f)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.SemiBold, color = if (active) ColorText else ColorMuted)
                                Text(t.level + " · " + t.pop, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                            }
                            Switch(checked = own, onCheckedChange = { v -> guarded { FxFlags.set(ctx, "fx_" + t.id, v) } })
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                                val s = size.height
                                val stroke = 4.dp.toPx()
                                val d = s - stroke
                                drawArc(
                                    color = t.color.copy(alpha = 0.25f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(d, d), style = Stroke(stroke),
                                )
                                drawArc(
                                    color = t.color, startAngle = -90f, sweepAngle = 250f, useCenter = false,
                                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round),
                                )
                                val x0 = s + 10.dp.toPx()
                                val w = size.width - x0
                                val bh = 8.dp.toPx()
                                val y = (s - bh) / 2
                                drawRoundRect(color = t.color.copy(alpha = 0.25f), topLeft = Offset(x0, y), size = Size(w, bh), cornerRadius = CornerRadius(bh / 2))
                                drawRoundRect(color = t.color, topLeft = Offset(x0, y), size = Size(w * 0.64f, bh), cornerRadius = CornerRadius(bh / 2))
                            }
                            Spacer(Modifier.height(6.dp))
                            FxLine("Focus ring", t.ring)
                            FxLine("XP bar", t.bar)
                            FxLine("Level up", t.level)
                            FxLine("Quest done pop", t.pop)
                            if (t.strip.isNotEmpty()) FxLine("Header strip", t.strip)
                            if (t.board.isNotEmpty()) FxLine("Quest cards", t.board)
                            FxLine("Lock screen", "emblem with padlock")
                            TextButton(onClick = { previewId = t.id }) { Text("Try it") }
                        }
                    }
                }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = ColorMuted, style = MaterialTheme.typography.bodySmall) }
        item {
            Text("Tap a theme to see what it changes. The switches are the same flags as fx_<theme> in the terminal (flags).",
                color = ColorMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FxLine(label: String, value: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text(label, modifier = Modifier.width(112.dp), color = ColorText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(value, color = ColorMuted, fontSize = 13.sp)
    }
}

/**
 * v258: plays the effects of a theme without any XP, coins or level up. v261: one theme per page (no theme chips), opened only by
 * tapping a theme row and then Try it; the word "preview" is gone from the page. While this page is open the app is put in the chosen theme
 * (in memory only: ShopTheme is never saved here and is put back when the page closes), so the real XP bar, Focus ring, level up,
 * quest done pop and lock emblem of that theme are drawn by the same code the app uses. [themeId] is the theme to show.
 */
@Composable
fun ThemeFxTryPage(themeId: String, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember { ShopTheme }
    val sel = remember { FX_THEMES.firstOrNull { it.id == themeId }?.id ?: FX_THEMES[0].id }
    var xp by remember { mutableFloatStateOf(0.64f) }
    var ring by remember { mutableFloatStateOf(0.7f) }
    var emblemKey by remember { mutableIntStateOf(0) }
    SideEffect { if (ShopTheme != "theme_" + sel) ShopTheme = "theme_" + sel }
    val penaltyBefore = remember { SystemEvents.penaltyOn }
    // v262: everything the page touches is put back when it closes: the saved theme, the penalty look, the Shadow Army counter, the last level and reward numbers, and any pop or level up still showing
    val armyBefore = remember { SystemEvents.armyCount }
    val armySavedBefore = remember { ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE).getInt("shadow_army", 0) }
    val lastLevelBefore = remember { SystemEvents.lastLevel }
    val lastCoinsBefore = remember { SystemEvents.lastCoins }
    val lastXpBefore = remember { SystemEvents.lastXp }
    DisposableEffect(Unit) {
        onDispose {
            val sp = ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE)
            ShopTheme = sp.getString("shop_theme", null) ?: original
            SystemEvents.penaltyOn = penaltyBefore
            SystemEvents.armyCount = armyBefore
            sp.edit().putInt("shadow_army", armySavedBefore).apply()
            SystemEvents.lastLevel = lastLevelBefore; SystemEvents.lastCoins = lastCoinsBefore; SystemEvents.lastXp = lastXpBefore
            SystemEvents.cutStamp++
        }
    }

    val theme = FX_THEMES.first { it.id == sel }
    val fxOn = AnyFx.on
    val blocked = TermuxOn
    // v263: the track follows the theme (the default one stayed lavender or muddy purple in every theme)
    val sliderColors = SliderDefaults.colors(
        thumbColor = ColorPrimary, activeTrackColor = ColorPrimary, inactiveTrackColor = ColorPrimary.copy(alpha = 0.22f),
        activeTickColor = ColorPrimary, inactiveTickColor = ColorPrimary.copy(alpha = 0.4f),
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text("Theme effects", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                Text(theme.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
            }
        }
        Text(
            "Nothing here gives XP or coins. The app only wears the chosen theme while this page is open.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = ColorMuted,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (blocked) item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("The Termux look or a picture background is on, so theme effects are not drawn. Turn it off to see them.",
                        modifier = Modifier.padding(12.dp), color = ColorMuted, style = MaterialTheme.typography.bodySmall)
                }
            } else if (!fxOn) item {
                Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(theme.name + " effects are off, so the normal bar and ring show.", color = ColorText, fontWeight = FontWeight.SemiBold)
                        Button(
                            onClick = { FxFlags.set(ctx, "themefx", true); FxFlags.set(ctx, "fx_" + sel, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                        ) { Text("Turn on " + theme.name + " effects") }
                    }
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Quest card", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Text("Read chapter 5 and solve 10 questions", color = ColorText, modifier = Modifier.padding(top = 6.dp))
                            Text("+25 XP  +10 coins", color = ColorMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                            Text("The strip at the top of the page and the cards of this page wear the theme too.", color = ColorMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("XP bar", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Slider(value = xp, onValueChange = { xp = it }, colors = sliderColors)
                            FxXpBar(xp, (xp * 100).toInt())
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Focus ring (time left)", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Slider(value = ring, onValueChange = { ring = it }, colors = sliderColors)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FxFocusRing(ring, com.krishna.questblock.data.fmtLeft((ring * 25 * 60_000L).toLong()))
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Level up, quest done and penalty", fontWeight = FontWeight.SemiBold, color = ColorText)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { SystemEvents.levelUp(5) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                                ) { Text("Level up") }
                                Button(
                                    onClick = {
                                        val army = SystemEvents.armyCount
                                        val sp = ctx.getSharedPreferences("questblock", android.content.Context.MODE_PRIVATE)
                                        val savedArmy = sp.getInt("shadow_army", 0)
                                        SystemEvents.questDone(25, 10)
                                        if (sel == "monarch") scope.launch {
                                            delay(400)
                                            SystemEvents.armyCount = army
                                            sp.edit().putInt("shadow_army", savedArmy).apply()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                                ) { Text("Quest done") }
                            }
                            if (sel == "system" || sel == "pixel") {
                                Button(
                                    onClick = {
                                        val was = SystemEvents.penaltyOn
                                        SystemEvents.penalty()
                                        scope.launch { delay(4000); SystemEvents.penaltyOn = was }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                                ) { Text("Penalty (focus broken)") }
                                Text(
                                    if (sel == "system") "System turns red with PENALTY ZONE for 4 s here (90 s in a real penalty)." else "Pixel shows GAME OVER.",
                                    color = ColorMuted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Lock screen", fontWeight = FontWeight.SemiBold, color = ColorText, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            key(emblemKey) {
                                FxLockEmblem { Text("QB", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ColorText) }
                            }
                            FxLockWord()
                            TextButton(onClick = { emblemKey++ }) { Text("Replay") }
                        }
                    }
                }
            }
        }
    }
}
