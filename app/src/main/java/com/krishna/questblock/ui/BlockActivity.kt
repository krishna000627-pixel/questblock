package com.krishna.questblock.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.krishna.questblock.data.GameRepository
import com.krishna.questblock.data.Player
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val blocked = repo.blockedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val player = repo.player.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Player())

    fun unlock(pkg: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.unlock(pkg)) }
    }
}

@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    private var target by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            QuestTheme {
                BlockScreen(
                    pkg = target,
                    onHome = ::goHome,
                    onQuests = ::openQuests,
                    onUnlocked = ::openTarget,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(EXTRA_PKG).orEmpty()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    private fun openQuests() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun openTarget() {
        packageManager.getLaunchIntentForPackage(target)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finishAndRemoveTask()
    }

    companion object {
        const val EXTRA_PKG = "target_pkg"
    }
}

@Composable
fun BlockScreen(
    pkg: String,
    onHome: () -> Unit,
    onQuests: () -> Unit,
    onUnlocked: () -> Unit,
    vm: BlockViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val rule = blocked.firstOrNull { it.packageName == pkg }
    val label = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔒", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "$label is locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You have 🪙 ${player.coins}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(24.dp))

            if (rule != null) {
                val missing = rule.cost - player.coins
                Button(
                    onClick = {
                        vm.unlock(pkg) { ok ->
                            if (ok) onUnlocked()
                            else Toast.makeText(ctx, "Not enough coins", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = missing <= 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unlock ${rule.unlockMinutes} min · ${rule.cost} 🪙") }
                if (missing > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Need $missing more coins. Finish a quest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onQuests, modifier = Modifier.fillMaxWidth()) { Text("Do a quest") }
            TextButton(onClick = onHome) { Text("Go home") }
        }
    }
}
