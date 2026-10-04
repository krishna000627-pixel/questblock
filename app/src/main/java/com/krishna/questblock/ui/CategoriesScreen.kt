package com.krishna.questblock.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.krishna.questblock.data.AppCategorizer
import com.krishna.questblock.data.Prefs
import com.krishna.questblock.data.USAGE_CATEGORIES
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fun rules are a commitment: loosening one waits until midnight. */
fun commitToast(ctx: Context) {
    android.widget.Toast.makeText(ctx, "\uD83D\uDD12 Fun rules are a commitment: this applies at midnight", android.widget.Toast.LENGTH_LONG).show()
}

data class CatApp(val pkg: String, val label: String, val auto: String)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: Prefs,
) : ViewModel() {
    private val _apps = MutableStateFlow<List<CatApp>>(emptyList())
    val apps: StateFlow<List<CatApp>> = _apps
    private val _chosen = MutableStateFlow(prefs.categoryMap())
    /** package -> category picked by hand */
    val chosen: StateFlow<Map<String, String>> = _chosen
    private val _safe = MutableStateFlow(prefs.funSafeSet())
    /** fun apps that are never cut mid-use */
    val safe: StateFlow<Set<String>> = _safe
    /** Returns true if applied now, false if it waits until midnight (fun rules are a commitment). */
    fun toggleSafe(pkg: String): Boolean {
        val applied = prefs.changeFunSafe(pkg, pkg !in _safe.value)
        _safe.value = prefs.funSafeSet()
        return applied
    }

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            _apps.value = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                .map { CatApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), AppCategorizer.autoCategory(pm, it.activityInfo.packageName)) }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            _loaded.value = true
        }
    }

    /** Tap a category: pick it. Tap the picked one again: back to automatic. */
    fun pick(app: CatApp, cat: String): Boolean {
        val current = _chosen.value[app.pkg] ?: app.auto
        val hasOverride = _chosen.value.containsKey(app.pkg)
        val applied: Boolean
        if (current == cat && hasOverride) applied = prefs.changeCategory(app.pkg, null, current, app.auto)
        else if (current == cat) return true
        else applied = prefs.changeCategory(app.pkg, cat, current, cat)
        _chosen.value = prefs.categoryMap()
        return applied
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val chosen by vm.chosen.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val safeSet by vm.safe.collectAsStateWithLifecycle()
    val toastCtx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }

    fun effective(a: CatApp) = chosen[a.pkg] ?: a.auto
    val shown = apps.filter { a ->
        (query.isBlank() || a.label.contains(query, ignoreCase = true)) &&
            (filter == "all" || effective(a) == filter)
    }

    Column(Modifier.fillMaxSize().background(ColorBg).padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Column {
                Text("App categories", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = ColorText)
                Text("Tap an icon to move an app. Tap it again to go back to automatic.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))

        // filter chips with live counts
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterPill("All ${apps.size}", filter == "all") { filter = "all" } }
            items(USAGE_CATEGORIES) { c ->
                val n = apps.count { effective(it) == c.id }
                FilterPill("${c.emoji} ${c.short} $n", filter == c.id) { filter = c.id }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!loaded) {
            Text("Loading apps\u2026", color = ColorMuted, modifier = Modifier.padding(16.dp))
        } else if (shown.isEmpty()) {
            Text("No apps here.", color = ColorMuted, modifier = Modifier.padding(16.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { a ->
                val eff = effective(a)
                val manual = chosen.containsKey(a.pkg)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ColorCard).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(a.pkg, a.label)
                    Column(Modifier.weight(1f)) {
                        Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ColorText)
                        Text(if (manual) "set by you" else "automatic", style = MaterialTheme.typography.labelSmall, color = ColorMuted)
                        if (eff == "fun") {
                            val isSafe = a.pkg in safeSet
                            Text(
                                if (isSafe) "\uD83D\uDEE1 Never cut mid-use" else "Cut when fun limit hits",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSafe) ColorPrimary else ColorMuted,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { if (!vm.toggleSafe(a.pkg)) commitToast(toastCtx) }.padding(vertical = 2.dp),
                            )
                        }
                    }
                    USAGE_CATEGORIES.forEach { c ->
                        val sel = eff == c.id
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(if (sel) ColorPrimary.copy(alpha = 0.22f) else ColorSurfaceHigh)
                                .clickable { if (!vm.pick(a, c.id)) commitToast(toastCtx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c.emoji, fontSize = 17.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) ColorPrimary.copy(alpha = 0.2f) else ColorSurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) ColorPrimary else ColorMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge)
    }
}
