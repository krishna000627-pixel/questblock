package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.StudyTracker
import com.krishna.questblock.data.USAGE_CATEGORIES
import com.krishna.questblock.data.UsageTracker
import com.krishna.questblock.data.YtSplit
import com.krishna.questblock.data.usageCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppUse(val pkg: String, val label: String, val ms: Long, val cat: String)
data class UsageUi(val totalMs: Long = 0L, val byCat: Map<String, Long> = emptyMap(), val apps: List<AppUse> = emptyList(), val loaded: Boolean = false, val music: List<AppUse> = emptyList())

@HiltViewModel
class UsageViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: GameRepository,
    private val prefs: Prefs,
) : ViewModel() {
    private val _ui = MutableStateFlow(UsageUi())
    val ui: StateFlow<UsageUi> = _ui

    fun hasAccess() = UsageTracker.hasAccess(ctx)

    /** range: 0 = today, 1 = yesterday, 2 = last 7 days */
    fun load(range: Int) {
        viewModelScope.launch {
            if (!UsageTracker.hasAccess(ctx)) { _ui.value = UsageUi(loaded = true); return@launch }
            // pay any study coins that are due, so the numbers here and the coin balance agree
            runCatching { StudyTracker.sweep(ctx, repo, prefs) }
            val result = withContext(Dispatchers.IO) {
                val (from, to) = UsageTracker.rangeFor(range)
                val pm = ctx.packageManager
                val hidden = UsageTracker.hiddenPackages(ctx)
                val fgRes = UsageTracker.foreground(ctx, from, to)
                val raw = YtSplit.apply(ctx, prefs, fgRes.perPkg, from, to)
                // v173: YouTube shows as two rows, (study) and (fun)
                val ytPkg = YtSplit.PKGS.firstOrNull { raw.containsKey(it) } ?: YtSplit.PKGS[0]
                val ytSplit = raw.containsKey(YtSplit.FUN_KEY) || raw.containsKey(YtSplit.STUDY_KEY)
                val apps = raw.entries
                    .filter { it.value >= 30_000L && (YtSplit.isPseudo(it.key) || (it.key !in hidden && pm.getLaunchIntentForPackage(it.key) != null)) }
                    .map { e ->
                        val k = e.key
                        val cat = AppCategorizer.categoryOf(prefs, pm, k)
                        when {
                            k == YtSplit.FUN_KEY -> AppUse(ytPkg, "YouTube (fun)", e.value, "fun")
                            k == YtSplit.STUDY_KEY -> AppUse(ytPkg, "YouTube (study)", e.value, "study")
                            k == ytPkg && ytSplit -> AppUse(k, if (cat == "fun") "YouTube (fun)" else if (cat == "study") "YouTube (study)" else "YouTube", e.value, cat)
                            else -> AppUse(k, UsageTracker.labelOf(pm, k), e.value, cat)
                        }
                    }
                    .sortedByDescending { it.ms }
                val byCat = apps.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.ms } }
                val music = runCatching { UsageTracker.musicByApp(ctx, from, to, fgRes.spans) }.getOrDefault(emptyMap())
                    .filter { it.value >= 60_000L }
                    .map { (pkg, ms) ->
                        AppUse(pkg, if (pkg.isEmpty()) "Unknown app" else UsageTracker.labelOf(pm, pkg), ms,
                            if (pkg.isEmpty()) "neutral" else AppCategorizer.categoryOf(prefs, pm, pkg))
                    }
                    .sortedByDescending { it.ms }
                UsageUi(apps.sumOf { it.ms }, byCat, apps, true, music)
            }
            _ui.value = result
        }
    }

    /** Returns true if applied now, false if it waits until midnight (fun rules are a commitment). */
    fun setCategory(pkg: String, cat: String?, range: Int): Boolean {
        val pm = ctx.packageManager
        val old = AppCategorizer.categoryOf(prefs, pm, pkg)
        val new = cat ?: AppCategorizer.autoCategory(pm, pkg)
        val applied = prefs.changeCategory(pkg, cat, old, new)
        load(range)
        return applied
    }
}

internal fun fmtDur(ms: Long): String {
    val m = ms / 60_000L
    return when {
        ms < 60_000L -> "<1m"
        m < 60 -> "${m}m"
        else -> "${m / 60}h %02dm".format(m % 60)
    }
}

@Composable
fun UsageScreen(onBack: () -> Unit, onOpenCategories: () -> Unit, vm: UsageViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    var range by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf(vm.hasAccess()) }
    var editing by remember { mutableStateOf<AppUse?>(null) }

    // refresh on open / range change, and every 30 s so today keeps up; re-check the permission too
    LaunchedEffect(range, access) {
        while (true) {
            access = vm.hasAccess()
            vm.load(range)
            delay(30_000L)
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1500L); val a = vm.hasAccess(); if (a != access) access = a } }

    Column(Modifier.fillMaxSize().background(ColorBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("App usage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
        }

        if (!access) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ColorCard).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Allow usage access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorText)
                Text("QuestBlock reads how long each app was on screen. It also pays your study coins from the same record. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium, color = ColorMuted)
                Button(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                ) { Text("Open usage access settings", fontWeight = FontWeight.Bold) }
                Text("Find QuestBlock in the list and switch it on, then come back.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            return@Column
        }

        // range chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to 0, "Yesterday" to 1, "7 days" to 2).forEach { (label, r) ->
                val sel = range == r
                Box(Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) ColorPrimary.copy(alpha = 0.2f) else ColorSurfaceHigh)
                    .clickable { range = r }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(label, color = if (sel) ColorPrimary else ColorMuted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // total + stacked bar
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ColorCard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Screen time", style = MaterialTheme.typography.labelLarge, color = ColorMuted)
            Text(if (ui.loaded) fmtDur(ui.totalMs) else "\u2026", fontSize = 38.sp, fontWeight = FontWeight.Black, color = ColorText)
            if (ui.totalMs > 0) {
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
                    USAGE_CATEGORIES.forEach { c ->
                        val v = ui.byCat[c.id] ?: 0L
                        if (v > 0) Box(Modifier.weight(v.toFloat()).height(12.dp).background(catColor(c.id)))
                    }
                }
            }
        }

        // four category cards (2 x 2)
        USAGE_CATEGORIES.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { c ->
                    val v = ui.byCat[c.id] ?: 0L
                    val pct = if (ui.totalMs > 0) (v * 100 / ui.totalMs).toInt() else 0
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(ColorCard).padding(12.dp)) {
                        Text("${c.emoji} ${c.label}", style = MaterialTheme.typography.labelLarge, color = catColor(c.id), fontWeight = FontWeight.Bold)
                        Text(fmtDur(v), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ColorText)
                        Text("$pct%", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                    }
                }
            }
        }

        // music played while the app was not on screen, recorded per app
        if (ui.loaded) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ColorCard).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83C\uDFA7 Music off screen", style = MaterialTheme.typography.labelLarge, color = ColorWarning, fontWeight = FontWeight.Bold)
                    Text(fmtDur(ui.music.sumOf { it.ms }), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ColorText)
                }
                if (ui.music.isEmpty()) {
                    Text("None yet. Play music in an app, leave it, and check back.", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                } else {
                    ui.music.forEach { m ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIcon(m.pkg, m.label, 30.dp)
                            Column(Modifier.weight(1f)) {
                                Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = ColorText)
                                if (m.cat == "fun") Text("counts as Fun & Games", style = MaterialTheme.typography.labelSmall, color = ColorWarning)
                            }
                            Text(fmtDur(m.ms), color = ColorMuted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // apps grouped by category
        if (ui.loaded && ui.apps.isEmpty()) {
            Text("No app time recorded for this period yet.", color = ColorMuted)
        }
        USAGE_CATEGORIES.forEach { c ->
            val list = ui.apps.filter { it.cat == c.id }
            if (list.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c.emoji} ${c.label}", fontWeight = FontWeight.Bold, color = catColor(c.id))
                    Text(fmtDur(ui.byCat[c.id] ?: 0L), color = ColorMuted, fontWeight = FontWeight.SemiBold)
                }
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorCard)
                        .clickable { onOpenCategories() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(a.pkg, a.label)
                        Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(fmtDur(a.ms), color = ColorMuted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        OutlinedButton(onClick = onOpenCategories, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Manage app categories")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun catColor(id: String) = when (id) {
    "study" -> ColorPrimary
    "coding" -> ColorAccent
    "fun" -> ColorWarning
    else -> ColorMuted
}
