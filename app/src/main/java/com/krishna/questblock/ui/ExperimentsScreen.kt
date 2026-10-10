@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.krishna.questblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import com.krishna.questblock.data.TestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.krishna.questblock.data.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExperimentsViewModel @Inject constructor(val prefs: Prefs, val repo: com.krishna.questblock.data.GameRepository) : ViewModel()

/** Hub → Experiments: the optional features, next to Rules. Every switch is yours; nothing changes by itself. */
@Composable
fun ExperimentsScreen(onBack: () -> Unit, vm: ExperimentsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(ColorBg).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
            Text("Experiments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
        }
        TestModeCard(vm)
        OptionalFeaturesSection(vm.prefs, ctx)
        Spacer(Modifier.height(12.dp))
    }
}

/** One-time 2 hour test: unlimited coins and no fun limit. */
@Composable
private fun TestModeCard(vm: ExperimentsViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var left by remember { mutableLongStateOf(0L) }
    var used by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { TestMode.load(ctx) }
        vm.repo.settleTestMode()
        used = TestMode.used(); left = TestMode.remainingMs()
    }
    LaunchedEffect(left > 0L) {
        while (left > 0L) {
            delay(1000L)
            left = TestMode.remainingMs()
            if (left <= 0L) vm.repo.settleTestMode()
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = ColorSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("\uD83E\uDDEA Test mode", color = ColorText, fontWeight = FontWeight.SemiBold)
            if (left > 0L) {
                val t = left / 1000L
                Text("On: %d:%02d:%02d left. Coins and the fun limit are unlimited. Everything goes back after.".format(t / 3600, (t % 3600) / 60, t % 60),
                    style = MaterialTheme.typography.bodySmall, color = ColorPrimary)
            } else if (used) {
                Text("Test mode was already used on this phone.", style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            } else {
                Text("2 hours, one time only: unlimited coins and no fun limit, to try the app. Your real coins come back after.",
                    style = MaterialTheme.typography.bodySmall, color = ColorMuted)
                Button(onClick = {
                    if (!armed) armed = true else scope.launch {
                        val ok = withContext(Dispatchers.IO) { TestMode.start(ctx) }
                        if (ok) { vm.repo.startTestCoins(); used = true; left = TestMode.remainingMs() } else note = "Could not start test mode"
                        armed = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(if (armed) "Tap again: starts 2 hours, one time only" else "Start test mode (2 h, once)")
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ColorDanger) }
        }
    }
}
